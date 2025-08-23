package org.maks.beesPlugin.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.maks.beesPlugin.hive.Tier;
import org.maks.beesPlugin.hive.BeeType;

import java.util.EnumMap;
import java.util.Map;

public class BeesConfig {
    public final int tickSeconds;
    public final double unitPerBottle;
    public final double unitPerLarva;
    public final int offlineCapHours;

    public final double baseRare;
    public final double baseLegendary;

    public final Map<Tier, WorkerConfig> workers = new EnumMap<>(Tier.class);
    public final Map<Tier, DroneConfig> drones = new EnumMap<>(Tier.class);
    public final Map<Tier, QueenConfig> queens = new EnumMap<>(Tier.class);

    public final int workerSlots;
    public final int droneSlots;
    public final int queenSlots;

    public final int maxHivesPerPlayer;
    public final double hiveCostBase;
    public final double hiveCostGrowth;

    public final int honeyStorageLimit;
    public final int larvaeStorageLimit;

    public final Map<Tier, InfusionCost> infusionCost = new EnumMap<>(Tier.class);
    public final Map<Tier, Map<BeeType, Double>> infusionTypeWeights = new EnumMap<>(Tier.class);
    public final Map<Tier, TierShift> infusionTierShift = new EnumMap<>(Tier.class);

    public BeesConfig(FileConfiguration config) {
        ConfigurationSection bees = config.getConfigurationSection("bees");
        tickSeconds = bees.getInt("tick_seconds", 10);
        unitPerBottle = bees.getDouble("unit_per_bottle", 60.0);
        unitPerLarva = bees.getDouble("unit_per_larva", 60.0);
        offlineCapHours = bees.getInt("offline_cap_hours", 8);

        ConfigurationSection rarity = bees.getConfigurationSection("rarity");
        baseRare = rarity.getDouble("base_rare", 0.05);
        baseLegendary = rarity.getDouble("base_legendary", 0.005);

        ConfigurationSection workerSec = bees.getConfigurationSection("workers");
        for (String key : workerSec.getKeys(false)) {
            Tier tier = Tier.valueOf(key);
            workers.put(tier, new WorkerConfig(workerSec.getConfigurationSection(key).getDouble("base_honey_per_tick")));
        }

        ConfigurationSection droneSec = bees.getConfigurationSection("drones");
        for (String key : droneSec.getKeys(false)) {
            Tier tier = Tier.valueOf(key);
            ConfigurationSection cs = droneSec.getConfigurationSection(key);
            drones.put(tier, new DroneConfig(cs.getDouble("honey_penalty_per_tick"), cs.getDouble("larvae_per_tick")));
        }

        ConfigurationSection queenSec = bees.getConfigurationSection("queens");
        for (String key : queenSec.getKeys(false)) {
            Tier tier = Tier.valueOf(key);
            ConfigurationSection cs = queenSec.getConfigurationSection(key);
            queens.put(tier, new QueenConfig(cs.getDouble("multiplier"), cs.getDouble("rarity_bonus")));
        }

        ConfigurationSection slots = bees.getConfigurationSection("hive_slots");
        workerSlots = slots.getInt("worker", 6);
        droneSlots = slots.getInt("drone", 2);
        queenSlots = slots.getInt("queen", 1);

        ConfigurationSection ownership = bees.getConfigurationSection("ownership");
        maxHivesPerPlayer = ownership.getInt("max_hives_per_player", 10);
        ConfigurationSection hiveCost = ownership.getConfigurationSection("hive_cost");
        hiveCostBase = hiveCost.getDouble("base", 100000000);
        hiveCostGrowth = hiveCost.getDouble("growth", 2.0);

        honeyStorageLimit = bees.getInt("honey_storage_limit", 16);
        larvaeStorageLimit = bees.getInt("larvae_storage_limit", 64);

        ConfigurationSection infCost = bees.getConfigurationSection("infusion_cost");
        for (String key : infCost.getKeys(false)) {
            Tier tier = Tier.valueOf(key);
            ConfigurationSection cs = infCost.getConfigurationSection(key);
            infusionCost.put(tier, new InfusionCost(
                    cs.getInt("honey_I", 0),
                    cs.getInt("honey_II", 0),
                    cs.getInt("honey_III", 0)
            ));
        }

        ConfigurationSection typeWeights = bees.getConfigurationSection("infusion_type_weights");
        for (String key : typeWeights.getKeys(false)) {
            Tier tier = Tier.valueOf(key);
            ConfigurationSection cs = typeWeights.getConfigurationSection(key);
            Map<BeeType, Double> map = new EnumMap<>(BeeType.class);
            map.put(BeeType.WORKER, cs.getDouble("worker"));
            map.put(BeeType.DRONE, cs.getDouble("drone"));
            map.put(BeeType.QUEEN, cs.getDouble("queen"));
            infusionTypeWeights.put(tier, map);
        }

        ConfigurationSection tierShift = bees.getConfigurationSection("infusion_tier_shift");
        for (String key : tierShift.getKeys(false)) {
            Tier tier = Tier.valueOf(key);
            ConfigurationSection cs = tierShift.getConfigurationSection(key);
            infusionTierShift.put(tier, new TierShift(
                    cs.getDouble("down1"),
                    cs.getDouble("same"),
                    cs.getDouble("up1"),
                    cs.getDouble("up2")
            ));
        }
    }

    public record InfusionCost(int honeyI, int honeyII, int honeyIII) {}

    public record TierShift(double down1, double same, double up1, double up2) {}
}
