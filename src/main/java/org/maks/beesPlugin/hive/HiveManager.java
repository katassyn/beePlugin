package org.maks.beesPlugin.hive;

import org.bukkit.entity.Player;
import org.maks.beesPlugin.config.BeesConfig;
import org.maks.beesPlugin.dao.Database;
import org.maks.beesPlugin.dao.HiveDao;
import org.maks.beesPlugin.item.BeeItems;

import java.sql.SQLException;
import java.util.*;

public class HiveManager {
    private final BeesConfig config;
    private final Database database;
    private final HiveDao hiveDao;
    private final Map<UUID, List<Hive>> hives = new HashMap<>();

    public HiveManager(BeesConfig config, Database database, HiveDao hiveDao) {
        this.config = config;
        this.database = database;
        this.hiveDao = hiveDao;
    }

    public void loadPlayer(UUID uuid, long now) {
        try {
            List<Hive> list = hiveDao.loadHives(uuid, now);
            hives.put(uuid, list);
        } catch (SQLException e) {
            e.printStackTrace();
            hives.put(uuid, new ArrayList<>());
        }
    }

    public void unloadPlayer(UUID uuid) {
        hives.remove(uuid);
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
        try {
            database.runInTransaction(conn -> {
                try {
                    hiveDao.createHive(conn, uuid, hive);
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            });
            list.add(hive);
            return hive;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public void saveHive(UUID uuid, Hive hive) {
        try {
            database.runInTransaction(conn -> {
                try {
                    hiveDao.updateHive(conn, uuid, hive);
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void tickAll(long now) {
        try {
            database.runInTransaction(conn -> {
                for (Map.Entry<UUID, List<Hive>> entry : hives.entrySet()) {
                    UUID uuid = entry.getKey();
                    for (Hive hive : entry.getValue()) {
                        hive.tick(config, now);
                        try {
                            hiveDao.updateHive(conn, uuid, hive);
                        } catch (SQLException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
            });
        } catch (SQLException e) {
            e.printStackTrace();
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
            saveHive(player.getUniqueId(), hive);
        }
    }
}
