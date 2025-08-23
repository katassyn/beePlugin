package org.maks.beesPlugin.hive;

import org.maks.beesPlugin.config.BeesConfig;
import org.maks.beesPlugin.config.DroneConfig;
import org.maks.beesPlugin.config.QueenConfig;
import org.maks.beesPlugin.config.WorkerConfig;

import java.util.*;

public class Hive {
    private int id;
    private Tier queen; // null if none
    private final List<Tier> workers = new ArrayList<>();
    private final List<Tier> drones = new ArrayList<>();

    private double honeyUnits = 0;
    private double larvaeUnits = 0;

    private final EnumMap<Tier, Integer> honeyStored = new EnumMap<>(Tier.class);
    private final EnumMap<Tier, Integer> larvaeStored = new EnumMap<>(Tier.class);

    private long lastTick; // epoch seconds

    public int getId() { return id; }

    public void setId(int id) { this.id = id; }

    public long getLastTick() { return lastTick; }

    public void setLastTick(long lastTick) { this.lastTick = lastTick; }

    private final Random random = new Random();

    public Hive(long now) {
        this.lastTick = now;
        for (Tier t : Tier.values()) {
            honeyStored.put(t, 0);
            larvaeStored.put(t, 0);
        }
    }

    public Tier getQueen() {
        return queen;
    }

    public void setQueen(Tier queen) {
        this.queen = queen;
    }

    public List<Tier> getWorkers() {
        return workers;
    }

    public List<Tier> getDrones() {
        return drones;
    }

    public EnumMap<Tier, Integer> getHoneyStored() {
        return honeyStored;
    }

    public EnumMap<Tier, Integer> getLarvaeStored() {
        return larvaeStored;
    }

    /**
     * Calculates the amount of honey produced in a single tick based on the
     * current hive composition.
     */
    public double honeyPerTick(BeesConfig cfg) {
        if (queen == null) return 0.0;
        double base = 0;
        for (Tier t : workers) {
            WorkerConfig wc = cfg.workers.get(t);
            base += wc.baseHoneyPerTick();
        }
        double cut = 0;
        for (Tier t : drones) {
            DroneConfig dc = cfg.drones.get(t);
            cut += dc.honeyPenaltyPerTick();
        }
        QueenConfig qc = cfg.queens.get(queen);
        return Math.max(0, base - cut) * qc.multiplier();
    }

    /**
     * Calculates the amount of larvae produced in a single tick based on the
     * current hive composition.
     */
    public double larvaePerTick(BeesConfig cfg) {
        if (queen == null) return 0.0;
        double units = 0;
        for (Tier t : drones) {
            DroneConfig dc = cfg.drones.get(t);
            units += dc.larvaePerTick();
        }
        QueenConfig qc = cfg.queens.get(queen);
        return units * qc.multiplier() / cfg.unitPerLarva;
    }

    /**
     * @return honey production per minute.
     */
    public double honeyPerMinute(BeesConfig cfg) {
        return honeyPerTick(cfg) * (60.0 / cfg.tickSeconds);
    }

    /**
     * @return larvae production per minute.
     */
    public double larvaePerMinute(BeesConfig cfg) {
        return larvaePerTick(cfg) * (60.0 / cfg.tickSeconds);
    }

    public void tick(BeesConfig config, long now) {
        long diff = now - lastTick;
        long maxSeconds = config.offlineCapHours * 3600L;
        if (diff > maxSeconds) diff = maxSeconds;
        long ticks = diff / config.tickSeconds;
        if (ticks <= 0) return;

        if (isHoneyFull(config)) {
            lastTick = now;
            return;
        }

        long processed = 0;
        boolean full = false;
        while (processed < ticks) {
            tickOnce(config);
            processed++;
            if (isHoneyFull(config)) {
                full = true;
                break;
            }
        }

        if (full) {
            lastTick = now;
        } else {
            lastTick += processed * config.tickSeconds;
        }
    }

    private void tickOnce(BeesConfig cfg) {
        if (queen == null) return;
        QueenConfig qc = cfg.queens.get(queen);

        double base = 0;
        for (Tier t : workers) {
            WorkerConfig wc = cfg.workers.get(t);
            base += wc.baseHoneyPerTick();
        }
        double cut = 0;
        double larvae = 0;

        // bias towards lower tier larvae regardless of drones present
        Map<Tier, Double> larvaeWeights = new EnumMap<>(Tier.class);
        larvaeWeights.put(Tier.I, 0.50);
        larvaeWeights.put(Tier.II, 0.20);
        larvaeWeights.put(Tier.III, 0.10);

        // contribution distribution per drone tier (rows sum to 1.0)
        final double[][] LARVAE_DIST = {
                /* I   */ {0.80, 0.18, 0.02},
                /* II  */ {0.70, 0.25, 0.05},
                /* III */ {0.60, 0.30, 0.10}
        };

        for (Tier droneTier : drones) {
            DroneConfig dc = cfg.drones.get(droneTier);
            cut += dc.honeyPenaltyPerTick();
            double lpt = dc.larvaePerTick();
            larvae += lpt;

            int idx = droneTier.getLevel() - 1;
            double[] dist = LARVAE_DIST[idx];

            larvaeWeights.merge(Tier.I, lpt * dist[0], Double::sum);
            larvaeWeights.merge(Tier.II, lpt * dist[1], Double::sum);
            larvaeWeights.merge(Tier.III, lpt * dist[2], Double::sum);
        }
        double net = Math.max(0, base - cut) * qc.multiplier();
        double larv = larvae * qc.multiplier();

        honeyUnits += net;
        larvaeUnits += larv;

        while (honeyUnits >= cfg.unitPerBottle) {
            honeyUnits -= cfg.unitPerBottle;
            Tier quality = rollHoneyTier(cfg, qc);
            int current = honeyStored.get(quality);
            if (current < cfg.honeyStorageLimit) {
                honeyStored.put(quality, current + 1);
            }
        }

        while (larvaeUnits >= cfg.unitPerLarva) {
            larvaeUnits -= cfg.unitPerLarva;
            Tier tier = rollLarvaTier(larvaeWeights);
            int current = larvaeStored.get(tier);
            if (current < cfg.larvaeStorageLimit) {
                larvaeStored.put(tier, current + 1);
            }
        }
    }

    private Tier rollHoneyTier(BeesConfig cfg, QueenConfig qc) {
        double rareChance = cfg.baseRare * (1.0 + qc.rarityBonus());
        double legendChance = cfg.baseLegendary * (1.0 + 0.5 * qc.rarityBonus());
        double r = random.nextDouble();
        if (r < legendChance) return Tier.III;
        if (r < legendChance + rareChance) return Tier.II;
        return Tier.I;
    }

    private Tier rollLarvaTier(Map<Tier, Double> weights) {
        double total = 0;
        for (double w : weights.values()) total += w;
        if (total <= 0) return Tier.I;
        double r = random.nextDouble() * total;
        double cumulative = 0;
        for (Tier t : Tier.values()) {
            double w = weights.getOrDefault(t, 0.0);
            cumulative += w;
            if (r <= cumulative) return t;
        }
        return Tier.I;
    }

    private boolean isHoneyFull(BeesConfig cfg) {
        for (Tier t : Tier.values()) {
            if (honeyStored.get(t) < cfg.honeyStorageLimit) {
                return false;
            }
        }
        return true;
    }
}
