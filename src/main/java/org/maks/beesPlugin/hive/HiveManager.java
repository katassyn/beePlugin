package org.maks.beesPlugin.hive;

import org.bukkit.entity.Player;
import org.maks.beesPlugin.config.BeesConfig;
import org.maks.beesPlugin.item.BeeItems;

import java.util.*;

public class HiveManager {
    private final BeesConfig config;
    private final Map<UUID, List<Hive>> hives = new HashMap<>();

    public HiveManager(BeesConfig config) {
        this.config = config;
    }

    public List<Hive> getHives(UUID uuid) {
        return hives.computeIfAbsent(uuid, u -> new ArrayList<>());
    }

    public double getNextHiveCost(UUID uuid) {
        int owned = getHives(uuid).size();
        return config.hiveCostBase * Math.pow(config.hiveCostGrowth, owned);
    }

    public Hive createHive(UUID uuid, long now) {
        List<Hive> list = getHives(uuid);
        if (list.size() >= config.maxHivesPerPlayer) {
            return null;
        }
        Hive hive = new Hive(now);
        list.add(hive);
        return hive;
    }

    public void tickAll(long now) {
        for (List<Hive> list : hives.values()) {
            for (Hive hive : list) {
                hive.tick(config, now);
            }
        }
    }

    public void collectAll(Player player) {
        List<Hive> list = getHives(player.getUniqueId());
        for (Hive hive : list) {
            for (Tier t : Tier.values()) {
                int honey = hive.getHoneyStored().get(t);
                int delivered = 0;
                for (int i = 0; i < honey; i++) {
                    var leftovers = player.getInventory().addItem(BeeItems.createHoney(t));
                    if (!leftovers.isEmpty()) {
                        // inventory full, put back remaining including this one
                        hive.getHoneyStored().put(t, honey - delivered);
                        break;
                    }
                    delivered++;
                }
                if (honey == delivered) {
                    hive.getHoneyStored().put(t, 0);
                }

                int larva = hive.getLarvaeStored().get(t);
                delivered = 0;
                for (int i = 0; i < larva; i++) {
                    var leftovers = player.getInventory().addItem(BeeItems.createBee(BeeType.LARVA, t));
                    if (!leftovers.isEmpty()) {
                        hive.getLarvaeStored().put(t, larva - delivered);
                        break;
                    }
                    delivered++;
                }
                if (larva == delivered) {
                    hive.getLarvaeStored().put(t, 0);
                }
            }
        }
    }
}
