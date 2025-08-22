package org.maks.beesPlugin.hive;

import org.maks.beesPlugin.config.BeesConfig;
import org.maks.beesPlugin.config.DroneConfig;
import org.maks.beesPlugin.config.QueenConfig;
import org.maks.beesPlugin.config.WorkerConfig;

import java.util.*;

public class Hive {
    private Tier queen; // null if none
    private final List<Tier> workers = new ArrayList<>();
    private final List<Tier> drones = new ArrayList<>();

    private double honeyUnits = 0;
    private double larvaeUnits = 0;

    private final EnumMap<Tier, Integer> honeyStored = new EnumMap<>(Tier.class);
    private final EnumMap<Tier, Integer> larvaeStored = new EnumMap<>(Tier.class);

    private long lastTick; // epoch seconds

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

    public void tick(BeesConfig config, long now) {
        long diff = now - lastTick;
        long maxSeconds = config.offlineCapHours * 3600L;
        if (diff > maxSeconds) diff = maxSeconds;
        long ticks = diff / config.tickSeconds;
        if (ticks <= 0) return;
        for (long i = 0; i < ticks; i++) {
            tickOnce(config);
        }
        lastTick += ticks * config.tickSeconds;
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
        Map<Tier, Double> larvaeWeights = new EnumMap<>(Tier.class);
        for (Tier t : drones) {
            DroneConfig dc = cfg.drones.get(t);
            cut += dc.honeyPenaltyPerTick();
            larvae += dc.larvaePerTick();
            larvaeWeights.merge(t, dc.larvaePerTick(), Double::sum);
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

        while (larvaeUnits >= 1.0) {
            larvaeUnits -= 1.0;
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
}
