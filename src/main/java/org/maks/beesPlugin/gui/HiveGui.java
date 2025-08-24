package org.maks.beesPlugin.gui;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.ItemMeta;
import org.maks.beesPlugin.config.BeesConfig;
import org.maks.beesPlugin.config.DroneConfig;
import org.maks.beesPlugin.hive.BeeType;
import org.maks.beesPlugin.hive.Hive;
import org.maks.beesPlugin.hive.HiveManager;
import org.maks.beesPlugin.hive.Tier;
import org.maks.beesPlugin.item.BeeItems;

import java.util.*;

public class HiveGui implements Listener {

    private final HiveManager hiveManager;
    private final BeesConfig config;
    private final Map<UUID, Integer> open = new HashMap<>();

    public HiveGui(HiveManager hiveManager, BeesConfig config) {
        this.hiveManager = hiveManager;
        this.config = config;
    }

    public void open(Player player, int index) {
        List<Hive> list = hiveManager.getHives(player.getUniqueId());
        if (index < 0 || index >= list.size()) {
            player.sendMessage(ChatColor.RED + "No hive at that index");
            return;
        }
        Hive hive = list.get(index);
        Inventory inv = Bukkit.createInventory(player, 54, "Hive " + (index + 1));

        ItemStack filler = createPane(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, filler);
        }

        ItemStack workerPlaceholder = createPane(Material.LIGHT_BLUE_STAINED_GLASS_PANE, ChatColor.GRAY + "Worker Slot");
        ItemStack dronePlaceholder = createPane(Material.LIME_STAINED_GLASS_PANE, ChatColor.GRAY + "Drone Slot");
        ItemStack queenPlaceholder = createPane(Material.YELLOW_STAINED_GLASS_PANE, ChatColor.GRAY + "Queen Slot");

        for (int i = 0; i < config.workerSlots && i < WORKER_SLOTS.length; i++) {
            inv.setItem(WORKER_SLOTS[i], workerPlaceholder);
        }
        for (int i = 0; i < config.droneSlots && i < DRONE_SLOTS.length; i++) {
            inv.setItem(DRONE_SLOTS[i], dronePlaceholder);
        }
        inv.setItem(QUEEN_SLOT, queenPlaceholder);

        int w = 0;
        for (Tier t : hive.getWorkers()) {
            if (w >= WORKER_SLOTS.length) break;
            inv.setItem(WORKER_SLOTS[w++], BeeItems.createBee(BeeType.WORKER, t));
        }
        if (hive.getQueen() != null) {
            inv.setItem(QUEEN_SLOT, BeeItems.createBee(BeeType.QUEEN, hive.getQueen()));
        }
        int d = 0;
        for (Tier t : hive.getDrones()) {
            if (d >= DRONE_SLOTS.length) break;
            inv.setItem(DRONE_SLOTS[d++], BeeItems.createBee(BeeType.DRONE, t));
        }
        for (int i = 0; i < HONEY_STORAGE_SLOTS.length; i++) {
            Tier t = Tier.values()[i];
            int amount = hive.getHoneyStored().get(t);
            ItemStack stack = amount > 0 ? BeeItems.createHoney(t) : createPane(Material.BLACK_STAINED_GLASS_PANE, " ");
            if (amount > 0) {
                stack.setAmount(Math.min(64, amount));
            }
            inv.setItem(HONEY_STORAGE_SLOTS[i], stack);
        }

        for (int i = 0; i < LARVA_STORAGE_SLOTS.length; i++) {
            Tier t = Tier.values()[i];
            int amount = hive.getLarvaeStored().get(t);
            ItemStack stack = amount > 0 ? BeeItems.createBee(BeeType.LARVA, t) : createPane(Material.BLACK_STAINED_GLASS_PANE, " ");
            if (amount > 0) {
                stack.setAmount(Math.min(64, amount));
            }
            inv.setItem(LARVA_STORAGE_SLOTS[i], stack);
        }

        inv.setItem(HONEY_RATE_SLOT, createHoneyRateInfo(hive));
        inv.setItem(LARVA_RATE_SLOT, createLarvaRateInfo(hive));
        player.openInventory(inv);
        open.put(player.getUniqueId(), index);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        UUID id = event.getWhoClicked().getUniqueId();
        if (!open.containsKey(id)) return;
        int raw = event.getRawSlot();
        Inventory top = event.getView().getTopInventory();
        if (raw < top.getSize()) {
            BeeType beeSlot = slotType(raw);
            if (beeSlot != null) {
                event.setCancelled(true);
                ItemStack cursor = event.getCursor();
                ItemStack current = event.getCurrentItem();
                if (cursor != null && !cursor.getType().isAir()) {
                    BeeItems.BeeItem bee = BeeItems.parse(cursor);
                    if (bee == null || bee.type() != beeSlot) {
                        return;
                    }
                    // Prevent replacing an existing bee so it doesn't get lost
                    if (current != null && !current.getType().isAir()
                            && !current.getType().toString().endsWith("GLASS_PANE")) {
                        return;
                    }
                    if (event.getClick() == ClickType.RIGHT && cursor.getAmount() > 1) {
                        ItemStack one = cursor.clone();
                        one.setAmount(1);
                        top.setItem(raw, one);
                        cursor.setAmount(cursor.getAmount() - 1);
                        event.getView().setCursor(cursor);
                    } else if (cursor.getAmount() == 1) {
                        top.setItem(raw, cursor);
                        event.getView().setCursor(null);
                    } else {
                        return;
                    }
                    refreshInfo(top);
                } else if (current != null && !current.getType().toString().endsWith("GLASS_PANE")) {
                    top.setItem(raw, null);
                    event.getView().setCursor(current);
                    refreshInfo(top);
                }
            } else if (isHoneySlot(raw) || isLarvaSlot(raw) || isInfoSlot(raw)) {
                event.setCancelled(true);
            } else {
                event.setCancelled(true);
            }
        } else {
            if (event.isShiftClick()) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        UUID id = event.getWhoClicked().getUniqueId();
        if (!open.containsKey(id)) return;
        for (int slot : event.getRawSlots()) {
            if (slot < event.getView().getTopInventory().getSize()) {
                event.setCancelled(true);
                break;
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        Inventory inv = event.getInventory();
        if (inv.getSize() != 54) return;

        UUID id = event.getPlayer().getUniqueId();
        Integer idx = open.remove(id);
        if (idx == null) return;

        List<Hive> list = hiveManager.getHives(id);
        if (idx < 0 || idx >= list.size()) return;
        Hive hive = list.get(idx);

        hive.setQueen(null);
        hive.getWorkers().clear();
        hive.getDrones().clear();

        ItemStack queenStack = QUEEN_SLOT < inv.getSize() ? inv.getItem(QUEEN_SLOT) : null;
        if (queenStack != null) {
            if (queenStack.getType().toString().endsWith("GLASS_PANE")) {
                // placeholder, ignore
            } else {
                BeeItems.BeeItem bee = BeeItems.parse(queenStack);
                if (bee != null && bee.type() == BeeType.QUEEN && queenStack.getAmount() == 1) {
                    hive.setQueen(bee.tier());
                }
            }
        }

        for (int i = 0; i < config.workerSlots && i < WORKER_SLOTS.length; i++) {
            int slot = WORKER_SLOTS[i];
            if (slot >= inv.getSize()) break;
            ItemStack it = inv.getItem(slot);
            if (it == null) continue;
            if (it.getType().toString().endsWith("GLASS_PANE")) continue;
            BeeItems.BeeItem bee = BeeItems.parse(it);
            if (bee != null && bee.type() == BeeType.WORKER && it.getAmount() == 1) {
                hive.getWorkers().add(bee.tier());
            }
        }

        for (int i = 0; i < config.droneSlots && i < DRONE_SLOTS.length; i++) {
            int slot = DRONE_SLOTS[i];
            if (slot >= inv.getSize()) break;
            ItemStack it = inv.getItem(slot);
            if (it == null) continue;
            if (it.getType().toString().endsWith("GLASS_PANE")) continue;
            BeeItems.BeeItem bee = BeeItems.parse(it);
            if (bee != null && bee.type() == BeeType.DRONE && it.getAmount() == 1) {
                hive.getDrones().add(bee.tier());
            }
        }

        // update honey storage
        for (int i = 0; i < HONEY_STORAGE_SLOTS.length; i++) {
            int slot = HONEY_STORAGE_SLOTS[i];
            ItemStack stack = slot < inv.getSize() ? inv.getItem(slot) : null;
            Tier tier = Tier.values()[i];
            int amount = 0;
            if (stack != null && stack.getType() == Material.HONEY_BOTTLE) {
                Tier parsed = BeeItems.parseHoney(stack);
                if (parsed == tier) {
                    amount = Math.min(stack.getAmount(), config.honeyStorageLimit);
                }
            }
            hive.getHoneyStored().put(tier, amount);
        }

        // update larva storage
        for (int i = 0; i < LARVA_STORAGE_SLOTS.length; i++) {
            int slot = LARVA_STORAGE_SLOTS[i];
            ItemStack stack = slot < inv.getSize() ? inv.getItem(slot) : null;
            Tier tier = Tier.values()[i];
            int amount = 0;
            if (stack != null && stack.getType() == Material.COOKIE) {
                BeeItems.BeeItem bee = BeeItems.parse(stack);
                if (bee != null && bee.type() == BeeType.LARVA && bee.tier() == tier) {
                    amount = Math.min(stack.getAmount(), config.larvaeStorageLimit);
                }
            }
            hive.getLarvaeStored().put(tier, amount);
        }

        hiveManager.saveHive(id, hive);
    }

    private static final int QUEEN_SLOT = 13;
    private static final int[] WORKER_SLOTS = {10,11,12,14,15,16};
    private static final int[] DRONE_SLOTS = {28,29,30,32,33,34};
    private static final int HONEY_RATE_SLOT = 37;
    private static final int[] HONEY_STORAGE_SLOTS = {38,39,40};
    private static final int[] LARVA_STORAGE_SLOTS = {47,48,49};
    private static final int LARVA_RATE_SLOT = 46;

    private BeeType slotType(int slot) {
        if (slot == QUEEN_SLOT) return BeeType.QUEEN;
        for (int i = 0; i < config.workerSlots && i < WORKER_SLOTS.length; i++) {
            if (slot == WORKER_SLOTS[i]) return BeeType.WORKER;
        }
        for (int i = 0; i < config.droneSlots && i < DRONE_SLOTS.length; i++) {
            if (slot == DRONE_SLOTS[i]) return BeeType.DRONE;
        }
        return null;
    }

    private boolean isHoneySlot(int slot) {
        for (int s : HONEY_STORAGE_SLOTS) {
            if (slot == s) return true;
        }
        return false;
    }

    private boolean isLarvaSlot(int slot) {
        for (int s : LARVA_STORAGE_SLOTS) {
            if (slot == s) return true;
        }
        return false;
    }

    private boolean isInfoSlot(int slot) {
        return slot == HONEY_RATE_SLOT || slot == LARVA_RATE_SLOT;
    }
    private ItemStack createPane(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createHoneyRateInfo(Hive hive) {
        ItemStack item = new ItemStack(Material.HONEY_BOTTLE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "Honey Info");
        double rate = hive.honeyPerMinute(config);
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Per minute: " + ChatColor.WHITE + String.format(Locale.US, "%.1f", rate));
        lore.add(ChatColor.GRAY + "Units per bottle: " + ChatColor.WHITE + String.format(Locale.US, "%.0f", config.unitPerBottle));
        double bonus = hive.getQueen() != null ? config.queens.get(hive.getQueen()).rarityBonus() : 0;
        double rare = config.baseRare * (1.0 + bonus) * 100.0;
        double legend = config.baseLegendary * (1.0 + 0.5 * bonus) * 100.0;
        double common = Math.max(0, 100.0 - rare - legend);
        lore.add(ChatColor.GRAY + "Chance:");
        lore.add(ChatColor.WHITE + "I" + ChatColor.GRAY + ": " + String.format(Locale.US, "%.1f", common) + "%");
        lore.add(ChatColor.WHITE + "II" + ChatColor.GRAY + ": " + String.format(Locale.US, "%.1f", rare) + "%");
        lore.add(ChatColor.WHITE + "III" + ChatColor.GRAY + ": " + String.format(Locale.US, "%.1f", legend) + "%");
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createLarvaRateInfo(Hive hive) {
        ItemStack item = new ItemStack(Material.COOKIE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GREEN + "Larva Info");
        double rate = hive.larvaePerMinute(config);
        List<String> lore = new ArrayList<>();
        String fmt = rate < 1 ? "%.3f" : "%.1f";
        lore.add(ChatColor.GRAY + "Per minute: " + ChatColor.WHITE + String.format(Locale.US, fmt, rate));
        lore.add(ChatColor.GRAY + "Units per larva: " + ChatColor.WHITE + String.format(Locale.US, "%.0f", config.unitPerLarva));
        lore.add(ChatColor.GRAY + "Chance:");

        Map<Tier, Double> weights = new EnumMap<>(Tier.class);
        // base biases
        weights.put(Tier.I, 0.50);
        weights.put(Tier.II, 0.20);
        weights.put(Tier.III, 0.10);

        final double[][] LARVAE_DIST = {
                {0.80, 0.18, 0.02},
                {0.70, 0.25, 0.05},
                {0.60, 0.30, 0.10}
        };

        for (Tier droneTier : hive.getDrones()) {
            DroneConfig dc = config.drones.get(droneTier);
            double lpt = dc.larvaePerTick();
            int idx = droneTier.getLevel() - 1;
            double[] dist = LARVAE_DIST[idx];
            weights.merge(Tier.I, lpt * dist[0], Double::sum);
            weights.merge(Tier.II, lpt * dist[1], Double::sum);
            weights.merge(Tier.III, lpt * dist[2], Double::sum);
        }

        double total = 0.0;
        for (double w : weights.values()) total += w;
        for (Tier t : Tier.values()) {
            double pct = total > 0 ? (weights.getOrDefault(t, 0.0) / total) * 100.0 : (t == Tier.I ? 100.0 : 0.0);
            lore.add(ChatColor.WHITE + t.name() + ChatColor.GRAY + ": " + String.format(Locale.US, "%.1f", pct) + "%");
        }
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private void refreshInfo(Inventory inv) {
        Hive temp = new Hive(0);
        ItemStack queenStack = inv.getItem(QUEEN_SLOT);
        BeeItems.BeeItem queen = BeeItems.parse(queenStack);
        if (queen != null && queen.type() == BeeType.QUEEN) {
            temp.setQueen(queen.tier());
        }
        for (int i = 0; i < config.workerSlots && i < WORKER_SLOTS.length; i++) {
            ItemStack it = inv.getItem(WORKER_SLOTS[i]);
            BeeItems.BeeItem bee = BeeItems.parse(it);
            if (bee != null && bee.type() == BeeType.WORKER) {
                temp.getWorkers().add(bee.tier());
            }
        }
        for (int i = 0; i < config.droneSlots && i < DRONE_SLOTS.length; i++) {
            ItemStack it = inv.getItem(DRONE_SLOTS[i]);
            BeeItems.BeeItem bee = BeeItems.parse(it);
            if (bee != null && bee.type() == BeeType.DRONE) {
                temp.getDrones().add(bee.tier());
            }
        }
        inv.setItem(HONEY_RATE_SLOT, createHoneyRateInfo(temp));
        inv.setItem(LARVA_RATE_SLOT, createLarvaRateInfo(temp));
    }
}

