package org.maks.beesPlugin.gui;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.maks.beesPlugin.config.BeesConfig;
import org.maks.beesPlugin.hive.BeeType;
import org.maks.beesPlugin.hive.Tier;
import org.maks.beesPlugin.item.BeeItems;

import java.util.*;

public class InfusionGui implements Listener {

    private final BeesConfig config;
    private final Set<UUID> viewers = new HashSet<>();
    private final Random random = new Random();

    public InfusionGui(BeesConfig config) {
        this.config = config;
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(player, 9, "Infuse Larva");
        ItemStack filler = createPane(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, filler);
        }
        ItemStack larvaPlaceholder = createPane(Material.WHITE_STAINED_GLASS_PANE, ChatColor.GRAY + "Larva");
        ItemStack honeyPlaceholder = createPane(Material.ORANGE_STAINED_GLASS_PANE, ChatColor.GRAY + "Honey");
        for (int slot : HONEY_SLOTS) {
            inv.setItem(slot, honeyPlaceholder);
        }
        inv.setItem(LARVA_SLOT, larvaPlaceholder);
        viewers.add(player.getUniqueId());
        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        UUID id = event.getWhoClicked().getUniqueId();
        if (!viewers.contains(id)) return;
        int raw = event.getRawSlot();
        Inventory top = event.getView().getTopInventory();
        if (raw < top.getSize()) {
            if (raw == LARVA_SLOT) {
                ItemStack cursor = event.getCursor();
                ItemStack current = event.getCurrentItem();
                if (cursor != null && !cursor.getType().isAir()) {
                    BeeItems.BeeItem bee = BeeItems.parse(cursor);
                    if (bee == null || bee.type() != BeeType.LARVA || cursor.getAmount() != 1) {
                        event.setCancelled(true);
                        return;
                    }
                } else if (current != null && current.getType().toString().endsWith("GLASS_PANE")) {
                    event.setCancelled(true);
                    return;
                }
            } else if (isHoneySlot(raw)) {
                ItemStack cursor = event.getCursor();
                ItemStack current = event.getCurrentItem();
                if (cursor != null && !cursor.getType().isAir()) {
                    Tier t = BeeItems.parseHoney(cursor);
                    if (t == null) {
                        event.setCancelled(true);
                        return;
                    }
                } else if (current != null && current.getType().toString().endsWith("GLASS_PANE")) {
                    event.setCancelled(true);
                    return;
                }
            } else {
                event.setCancelled(true);
                return;
            }
            if (event.isShiftClick() || event.getClick() == ClickType.NUMBER_KEY ||
                    event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        UUID id = event.getWhoClicked().getUniqueId();
        if (!viewers.contains(id)) return;
        for (int slot : event.getRawSlots()) {
            if (slot < event.getView().getTopInventory().getSize()) {
                event.setCancelled(true);
                break;
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        if (!viewers.remove(id)) return;

        Player player = (Player) event.getPlayer();
        Inventory inv = event.getInventory();

        ItemStack larvaStack = inv.getItem(LARVA_SLOT);
        BeeItems.BeeItem larva = BeeItems.parse(larvaStack);
        if (larva == null || larva.type() != BeeType.LARVA || larvaStack.getAmount() != 1) {
            returnItems(player, inv.getContents());
            player.sendMessage(ChatColor.RED + "Place exactly one larva in the middle.");
            return;
        }
        Tier larvaTier = larva.tier();
        BeesConfig.InfusionCost cost = config.infusionCost.get(larvaTier);
        if (cost == null) {
            returnItems(player, inv.getContents());
            return;
        }

        EnumMap<Tier, Integer> honeyCounts = new EnumMap<>(Tier.class);
        boolean invalid = false;
        for (int i = 0; i < inv.getSize(); i++) {
            if (i == LARVA_SLOT) continue;
            ItemStack stack = inv.getItem(i);
            if (stack == null) continue;
            Tier t = BeeItems.parseHoney(stack);
            if (t == null) {
                invalid = true;
                break;
            }
            honeyCounts.merge(t, stack.getAmount(), Integer::sum);
        }
        if (invalid) {
            returnItems(player, inv.getContents());
            player.sendMessage(ChatColor.RED + "Only honey bottles around the larva are allowed.");
            return;
        }

        if (honeyCounts.getOrDefault(Tier.I, 0) < cost.honeyI() ||
            honeyCounts.getOrDefault(Tier.II, 0) < cost.honeyII() ||
            honeyCounts.getOrDefault(Tier.III, 0) < cost.honeyIII()) {
            returnItems(player, inv.getContents());
            player.sendMessage(ChatColor.RED + "Not enough honey for infusion.");
            return;
        }

        // return excess honey
        int excessI = honeyCounts.getOrDefault(Tier.I, 0) - cost.honeyI();
        int excessII = honeyCounts.getOrDefault(Tier.II, 0) - cost.honeyII();
        int excessIII = honeyCounts.getOrDefault(Tier.III, 0) - cost.honeyIII();
        List<ItemStack> extras = new ArrayList<>();
        if (excessI > 0) {
            ItemStack hi = BeeItems.createHoney(Tier.I);
            hi.setAmount(excessI);
            extras.add(hi);
        }
        if (excessII > 0) {
            ItemStack hi = BeeItems.createHoney(Tier.II);
            hi.setAmount(excessII);
            extras.add(hi);
        }
        if (excessIII > 0) {
            ItemStack hi = BeeItems.createHoney(Tier.III);
            hi.setAmount(excessIII);
            extras.add(hi);
        }
        returnItems(player, extras.toArray(new ItemStack[0]));

        // determine bee type
        Map<BeeType, Double> weights = config.infusionTypeWeights.get(larvaTier);
        BeeType resultType = rollType(weights);

        BeesConfig.TierShift shift = config.infusionTierShift.get(larvaTier);
        int tierShift = rollTierShift(shift);
        int newLevel = Math.min(3, Math.max(1, larvaTier.getLevel() + tierShift));
        Tier resultTier = Tier.fromLevel(newLevel);

        ItemStack bee = BeeItems.createBee(resultType, resultTier);
        Map<Integer, ItemStack> left = player.getInventory().addItem(bee);
        for (ItemStack s : left.values()) {
            player.getWorld().dropItem(player.getLocation(), s);
        }
    }

    private BeeType rollType(Map<BeeType, Double> weights) {
        double total = 0;
        for (double d : weights.values()) total += d;
        double r = random.nextDouble() * total;
        double cumulative = 0;
        for (var e : weights.entrySet()) {
            cumulative += e.getValue();
            if (r <= cumulative) return e.getKey();
        }
        return BeeType.WORKER;
    }

    private int rollTierShift(BeesConfig.TierShift shift) {
        double r = random.nextDouble();
        if (r < shift.down1()) return -1;
        r -= shift.down1();
        if (r < shift.same()) return 0;
        r -= shift.same();
        if (r < shift.up1()) return 1;
        return 2;
    }

    private void returnItems(Player player, ItemStack[] items) {
        for (ItemStack stack : items) {
            if (stack == null || stack.getType().isAir() || stack.getType().toString().endsWith("GLASS_PANE")) continue;
            Map<Integer, ItemStack> left = player.getInventory().addItem(stack);
            for (ItemStack s : left.values()) {
                player.getWorld().dropItem(player.getLocation(), s);
            }
        }
    }

    private static final int LARVA_SLOT = 4;
    private static final int[] HONEY_SLOTS = {0,1,2,3,5,6,7,8};

    private boolean isHoneySlot(int slot) {
        for (int s : HONEY_SLOTS) if (slot == s) return true;
        return false;
    }

    private ItemStack createPane(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }
}
