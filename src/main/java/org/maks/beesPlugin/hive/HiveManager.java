package org.maks.beesPlugin.hive;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.Bukkit;
import org.maks.beesPlugin.config.BeesConfig;
import org.maks.beesPlugin.dao.Database;
import org.maks.beesPlugin.dao.HiveDao;
import org.maks.beesPlugin.item.BeeItems;
import me.clip.placeholderapi.PlaceholderAPI;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class HiveManager {
    private final BeesConfig config;
    private final Database database;
    private final HiveDao hiveDao;
    private final Map<UUID, List<Hive>> hives = new ConcurrentHashMap<>();

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
            System.err.println("[BeesPlugin] Failed to load hives for player " + uuid + ": " + e.getMessage());
            hives.put(uuid, new ArrayList<>());
        }
    }

    public void unloadPlayer(UUID uuid) {
        // Save hives before unloading to prevent data loss
        List<Hive> playerHives = hives.get(uuid);
        if (playerHives != null) {
            for (Hive hive : playerHives) {
                saveHive(uuid, hive);
            }
        }
        hives.remove(uuid);
    }

    public Set<UUID> getLoadedPlayers() {
        return hives.keySet();
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
            System.err.println("[BeesPlugin] Failed to create hive for player " + uuid + ": " + e.getMessage());
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
            System.err.println("[BeesPlugin] Failed to save hive for player " + uuid + ": " + e.getMessage());
        }
    }

    public void tickAll(long now) {
        try {
            database.runInTransaction(conn -> {
                // Create snapshot to avoid ConcurrentModificationException
                Map<UUID, List<Hive>> snapshot = new HashMap<>(hives);
                for (Map.Entry<UUID, List<Hive>> entry : snapshot.entrySet()) {
                    UUID uuid = entry.getKey();
                    List<Hive> hiveList = entry.getValue();
                    // Create snapshot of hive list as well
                    List<Hive> hiveSnapshot = new ArrayList<>(hiveList);
                    for (Hive hive : hiveSnapshot) {
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
            System.err.println("[BeesPlugin] Failed to tick hives: " + e.getMessage());
        }
    }

    public boolean collectAll(Player player) {
        UUID uuid = player.getUniqueId();
        List<Hive> list = getHives(uuid);
        List<ItemStack> items = new ArrayList<>();

        // Get pet bonuses
        double honeyDoubleChance = getPetHoneyDoubleChance(player);

        // Track totals for quest events
        int totalHoneyT1 = 0, totalHoneyT2 = 0, totalHoneyT3 = 0;
        int totalLarvaeT1 = 0, totalLarvaeT2 = 0, totalLarvaeT3 = 0;

        for (Hive hive : list) {
            for (Tier t : Tier.values()) {
                int honey = hive.getHoneyStored().get(t);
                // Track tier-specific honey
                if (t == Tier.I) totalHoneyT1 += honey;
                else if (t == Tier.II) totalHoneyT2 += honey;
                else if (t == Tier.III) totalHoneyT3 += honey;

                for (int i = 0; i < honey; i++) {
                    items.add(BeeItems.createHoney(t));

                    // BEE pet special effect: 25% chance to double honey
                    if (honeyDoubleChance > 0 && Math.random() * 100 < honeyDoubleChance) {
                        items.add(BeeItems.createHoney(t));
                    }
                }
                int larva = hive.getLarvaeStored().get(t);
                // Track tier-specific larvae
                if (t == Tier.I) totalLarvaeT1 += larva;
                else if (t == Tier.II) totalLarvaeT2 += larva;
                else if (t == Tier.III) totalLarvaeT3 += larva;

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
            System.err.println("[BeesPlugin] Failed to collect items for player " + uuid + ": " + e.getMessage());
            return false;
        }

        for (ItemStack it : items) {
            var leftover = player.getInventory().addItem(it);
            for (ItemStack s : leftover.values()) {
                player.getWorld().dropItem(player.getLocation(), s);
            }
        }

        // Fire events for QuestSystem integration
        int totalHoney = totalHoneyT1 + totalHoneyT2 + totalHoneyT3;
        int totalLarvae = totalLarvaeT1 + totalLarvaeT2 + totalLarvaeT3;

        if (totalHoney > 0) {
            try {
                Class<?> listenerClass = Class.forName("org.maks.questsystem.listeners.BeesListener");
                Class<?> eventClass = null;
                for (Class<?> innerClass : listenerClass.getDeclaredClasses()) {
                    if (innerClass.getSimpleName().equals("HoneyCollectEvent")) {
                        eventClass = innerClass;
                        break;
                    }
                }
                if (eventClass != null) {
                    Object event = eventClass.getConstructor(Player.class, int.class, Object.class)
                            .newInstance(player, totalHoney, null);
                    Bukkit.getPluginManager().callEvent((org.bukkit.event.Event) event);

                    // Fire tier-specific events
                    if (totalHoneyT1 > 0) {
                        event = eventClass.getConstructor(Player.class, int.class, Object.class)
                                .newInstance(player, totalHoneyT1, Tier.I);
                        Bukkit.getPluginManager().callEvent((org.bukkit.event.Event) event);
                    }
                    if (totalHoneyT2 > 0) {
                        event = eventClass.getConstructor(Player.class, int.class, Object.class)
                                .newInstance(player, totalHoneyT2, Tier.II);
                        Bukkit.getPluginManager().callEvent((org.bukkit.event.Event) event);
                    }
                    if (totalHoneyT3 > 0) {
                        event = eventClass.getConstructor(Player.class, int.class, Object.class)
                                .newInstance(player, totalHoneyT3, Tier.III);
                        Bukkit.getPluginManager().callEvent((org.bukkit.event.Event) event);
                    }
                }
            } catch (Exception ignored) {
                // QuestSystem might not be loaded
            }
        }

        if (totalLarvae > 0) {
            try {
                Class<?> listenerClass = Class.forName("org.maks.questsystem.listeners.BeesListener");
                Class<?> eventClass = null;
                for (Class<?> innerClass : listenerClass.getDeclaredClasses()) {
                    if (innerClass.getSimpleName().equals("LarvaeCollectEvent")) {
                        eventClass = innerClass;
                        break;
                    }
                }
                if (eventClass != null) {
                    Object event = eventClass.getConstructor(Player.class, int.class)
                            .newInstance(player, totalLarvae);
                    Bukkit.getPluginManager().callEvent((org.bukkit.event.Event) event);
                }
            } catch (Exception ignored) {
                // QuestSystem might not be loaded
            }
        }

        return true;
    }

    public boolean hasInventorySpace(Player player, List<ItemStack> items) {
        PlayerInventory inv = player.getInventory();
        ItemStack[] contents = inv.getStorageContents().clone();
        
        // O(1) optimized approach: count available space by item type
        Map<String, Integer> availableSpace = new HashMap<>();
        int emptySlots = 0;
        
        // Single pass through inventory - O(36) constant time
        for (ItemStack slot : contents) {
            if (slot == null || slot.getType().isAir()) {
                emptySlots++;
            } else {
                String key = getItemKey(slot);
                int space = slot.getMaxStackSize() - slot.getAmount();
                availableSpace.put(key, availableSpace.getOrDefault(key, 0) + space);
            }
        }
        
        // Group items by type and count needed space - O(n)
        Map<String, Integer> needed = new HashMap<>();
        for (ItemStack item : items) {
            String key = getItemKey(item);
            needed.put(key, needed.getOrDefault(key, 0) + item.getAmount());
        }
        
        // Check if we have enough space - O(unique item types)
        int slotsNeeded = 0;
        for (Map.Entry<String, Integer> entry : needed.entrySet()) {
            String key = entry.getKey();
            int totalNeeded = entry.getValue();
            int spaceAvailable = availableSpace.getOrDefault(key, 0);
            
            if (totalNeeded > spaceAvailable) {
                // Need additional slots for overflow
                int maxStackSize = getMaxStackSize(key);
                int overflow = totalNeeded - spaceAvailable;
                slotsNeeded += (overflow + maxStackSize - 1) / maxStackSize; // Ceiling division
            }
        }
        
        return slotsNeeded <= emptySlots;
    }
    
    private String getItemKey(ItemStack item) {
        // Create unique key for item type (for stacking compatibility)
        return item.getType().toString() + "_" + 
               (item.hasItemMeta() ? item.getItemMeta().hashCode() : 0);
    }
    
    private int getMaxStackSize(String itemKey) {
        // Honey bottles have stack size of 16, bees likely 64
        // This is a simplified approach - in reality we'd need to check actual item
        if (itemKey.contains("HONEY_BOTTLE")) {
            return 16;
        }
        return 64; // Default for most items including custom bee items
    }

    // Pet integration methods
    private double getPetHoneySpeed(Player player) {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) return 0;
        String placeholder = PlaceholderAPI.setPlaceholders(player, "%petplugin_beekeeper_honey_speed%");
        try {
            return Double.parseDouble(placeholder);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private double getPetHoneyQuality(Player player) {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) return 0;
        String placeholder = PlaceholderAPI.setPlaceholders(player, "%petplugin_beekeeper_honey_quality%");
        try {
            return Double.parseDouble(placeholder);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private double getPetHoneyDoubleChance(Player player) {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) return 0;
        String placeholder = PlaceholderAPI.setPlaceholders(player, "%petplugin_beekeeper_double_honey%");
        try {
            return Double.parseDouble(placeholder);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // Method to get modified honey production rate for a player
    public double getModifiedHoneyRate(Player player, double baseRate) {
        double speedBonus = getPetHoneySpeed(player);
        return baseRate * (1.0 + speedBonus / 100.0);
    }

    // Method to check if honey should be upgraded to higher tier
    public Tier getModifiedHoneyTier(Player player, Tier originalTier) {
        double qualityBonus = getPetHoneyQuality(player);
        if (qualityBonus > 0) {
            double upgradeChance = qualityBonus / 100.0;
            if (Math.random() < upgradeChance) {
                // Upgrade tier if possible
                if (originalTier == Tier.I) return Tier.II;
                if (originalTier == Tier.II) return Tier.III;
            }
        }
        return originalTier;
    }
}
