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
        for (int slot : HONEY_STORAGE_SLOTS) {
            inv.setItem(slot, null);
        }
        for (int slot : LARVA_STORAGE_SLOTS) {
            inv.setItem(slot, null);
        }

        int hi = 0;
        for (Tier t : Tier.values()) {
            int amount = hive.getHoneyStored().get(t);
            ItemStack stack = amount > 0 ? BeeItems.createHoney(t) : null;
            if (stack != null) {
                stack.setAmount(Math.min(64, amount));
            }
            inv.setItem(HONEY_STORAGE_SLOTS[hi++], stack);
        }

        int li = 0;
        for (Tier t : Tier.values()) {
            int amount = hive.getLarvaeStored().get(t);
            ItemStack stack = amount > 0 ? BeeItems.createBee(BeeType.LARVA, t) : null;
            if (stack != null) {
                stack.setAmount(Math.min(64, amount));
            }
            inv.setItem(LARVA_STORAGE_SLOTS[li++], stack);
        }

        inv.setItem(HONEY_RATE_SLOT, createHoneyRateInfo(hive));
        inv.setItem(HONEY_CHANCE_SLOT, createHoneyChanceInfo(hive));
        inv.setItem(LARVA_RATE_SLOT, createLarvaRateInfo(hive));
        inv.setItem(LARVA_CHANCE_SLOT, createLarvaChanceInfo(hive));
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
            if (!isBeeSlot(raw)) {
                event.setCancelled(true);
                return;
            }
            ItemStack cursor = event.getCursor();
            ItemStack current = event.getCurrentItem();
            BeeType expected = slotType(raw);
            if (cursor != null && !cursor.getType().isAir()) {
                BeeItems.BeeItem bee = BeeItems.parse(cursor);
                if (bee == null || bee.type() != expected || cursor.getAmount() != 1) {
                    event.setCancelled(true);
                    return;
                }
            } else if (current != null && current.getType().toString().endsWith("GLASS_PANE")) {
                event.setCancelled(true);
                return;
            }
            if (event.isShiftClick() || event.getClick() == ClickType.NUMBER_KEY ||
                    event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
                event.setCancelled(true);
            }
        } else {
            // Bottom inventory interaction
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
                } else {
                    giveBack((Player) event.getPlayer(), queenStack);
                    inv.setItem(QUEEN_SLOT, null);
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
            } else {
                giveBack((Player) event.getPlayer(), it);
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
            } else {
                giveBack((Player) event.getPlayer(), it);
            }
        }
        hiveManager.saveHive(id, hive);
    }

    private static final int QUEEN_SLOT = 22;
    private static final int[] WORKER_SLOTS = {10,11,12,13,14,15};
    private static final int[] DRONE_SLOTS = {28,29,30,31,32,33};
    private static final int HONEY_RATE_SLOT = 36;
    private static final int[] HONEY_STORAGE_SLOTS = {37,38,39};
    private static final int HONEY_CHANCE_SLOT = 40;
    private static final int[] LARVA_STORAGE_SLOTS = {41,42,43};
    private static final int LARVA_RATE_SLOT = 44;
    private static final int LARVA_CHANCE_SLOT = 45;


    private boolean isBeeSlot(int slot) {
        return slotType(slot) != null;
    }

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

    private ItemStack createPane(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }

    private void giveBack(Player player, ItemStack stack) {
        Map<Integer, ItemStack> left = player.getInventory().addItem(stack);
        for (ItemStack s : left.values()) {
            player.getWorld().dropItem(player.getLocation(), s);
        }
    }

    private ItemStack createHoneyRateInfo(Hive hive) {
        ItemStack item = new ItemStack(Material.HONEY_BOTTLE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "Honey Rate");
        double rate = hive.honeyPerMinute(config);
        meta.setLore(List.of(ChatColor.GRAY + "Per minute: " + ChatColor.WHITE + String.format(Locale.US, "%.1f", rate)));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createHoneyChanceInfo(Hive hive) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "Honey Chance");
        List<String> lore = new ArrayList<>();
        double bonus = hive.getQueen() != null ? config.queens.get(hive.getQueen()).rarityBonus() : 0;
        double rare = config.baseRare * (1.0 + bonus) * 100.0;
        double legend = config.baseLegendary * (1.0 + 0.5 * bonus) * 100.0;
        double common = Math.max(0, 100.0 - rare - legend);
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
        meta.setDisplayName(ChatColor.GREEN + "Larva Rate");
        double rate = hive.larvaePerMinute(config);
        meta.setLore(List.of(ChatColor.GRAY + "Per minute: " + ChatColor.WHITE + String.format(Locale.US, "%.1f", rate)));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createLarvaChanceInfo(Hive hive) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GREEN + "Larva Chance");
        List<String> lore = new ArrayList<>();
        Map<Tier, Double> weights = new EnumMap<>(Tier.class);
        for (Tier t : hive.getDrones()) {
            weights.merge(t, config.drones.get(t).larvaePerTick(), Double::sum);
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
}

