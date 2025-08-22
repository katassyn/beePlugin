package org.maks.beesPlugin.hive;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
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
            // process offline progress before making hives available
            database.runInTransaction(conn -> {
                for (Hive hive : list) {
                    hive.tick(config, now);
                    try {
                        hiveDao.updateHive(conn, uuid, hive);
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
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

    public boolean collectAll(Player player) {
        UUID uuid = player.getUniqueId();
        List<Hive> list = getHives(uuid);
        List<ItemStack> items = new ArrayList<>();
        for (Hive hive : list) {
            for (Tier t : Tier.values()) {
                int honey = hive.getHoneyStored().get(t);
                for (int i = 0; i < honey; i++) {
                    items.add(BeeItems.createHoney(t));
                }
                int larva = hive.getLarvaeStored().get(t);
                for (int i = 0; i < larva; i++) {
                    items.add(BeeItems.createBee(BeeType.LARVA, t));
                }
            }
        }

        if (!hasInventorySpace(player, items)) {
            return false;
        }

        try {
            database.runInTransaction(conn -> {
                for (Hive hive : list) {
                    for (Tier t : Tier.values()) {
                        hive.getHoneyStored().put(t, 0);
                        hive.getLarvaeStored().put(t, 0);
                    }
                    try {
                        hiveDao.updateHive(conn, uuid, hive);
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }

        for (ItemStack it : items) {
            var leftover = player.getInventory().addItem(it);
            for (ItemStack s : leftover.values()) {
                player.getWorld().dropItem(player.getLocation(), s);
            }
        }
        return true;
    }

    public boolean hasInventorySpace(Player player, List<ItemStack> items) {
        PlayerInventory inv = player.getInventory();
        ItemStack[] contents = inv.getStorageContents().clone();
        for (ItemStack original : items) {
            ItemStack stack = original.clone();
            boolean placed = false;
            for (int i = 0; i < contents.length; i++) {
                ItemStack existing = contents[i];
                if (existing == null || existing.getType().isAir()) {
                    contents[i] = stack;
                    placed = true;
                    break;
                }
                if (existing.isSimilar(stack) && existing.getAmount() < existing.getMaxStackSize()) {
                    int space = existing.getMaxStackSize() - existing.getAmount();
                    if (space >= stack.getAmount()) {
                        existing.setAmount(existing.getAmount() + stack.getAmount());
                        placed = true;
                        break;
                    } else {
                        existing.setAmount(existing.getMaxStackSize());
                        stack.setAmount(stack.getAmount() - space);
                    }
                }
            }
            if (!placed) return false;
        }
        return true;
    }
}
