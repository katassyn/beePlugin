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
        inv.setItem(HONEY_SLOT, createPane(Material.ORANGE_STAINED_GLASS_PANE, ChatColor.GRAY + "Honey"));
        inv.setItem(LARVA_SLOT, createPane(Material.WHITE_STAINED_GLASS_PANE, ChatColor.GRAY + "Larva"));
        ItemStack button = new ItemStack(Material.ANVIL);
        ItemMeta bm = button.getItemMeta();
        bm.setDisplayName(ChatColor.AQUA + "Infuse");
        button.setItemMeta(bm);
        inv.setItem(INFUSE_SLOT, button);
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
            } else if (raw == HONEY_SLOT) {
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
            } else if (raw == INFUSE_SLOT) {
                event.setCancelled(true);
                Player player = (Player) event.getWhoClicked();
                Inventory inv = top;
                ItemStack larvaStack = inv.getItem(LARVA_SLOT);
                ItemStack honeyStack = inv.getItem(HONEY_SLOT);
                BeeItems.BeeItem larva = BeeItems.parse(larvaStack);
                Tier honeyTier = BeeItems.parseHoney(honeyStack);
                if (larva == null || larva.type() != BeeType.LARVA || larvaStack.getAmount() != 1 || honeyTier == null) {

                    return;
                }
                BeesConfig.InfusionCost cost = config.infusionCost.get(larva.tier());
                int required = switch (honeyTier) {
                    case I -> cost.honeyI();
                    case II -> cost.honeyII();
                    case III -> cost.honeyIII();
                };
                if (honeyStack.getAmount() < required) {

                    return;
                }
                honeyStack.setAmount(honeyStack.getAmount() - required);
                if (honeyStack.getAmount() > 0) {
                    inv.setItem(HONEY_SLOT, honeyStack);
                } else {
                    inv.setItem(HONEY_SLOT, createPane(Material.ORANGE_STAINED_GLASS_PANE, ChatColor.GRAY + "Honey"));
                }
                inv.setItem(LARVA_SLOT, createPane(Material.WHITE_STAINED_GLASS_PANE, ChatColor.GRAY + "Larva"));
                performInfusion(player, larva.tier());
            } else {
                event.setCancelled(true);
                return;
            }
            if (event.isShiftClick() || event.getClick() == ClickType.NUMBER_KEY ||
                    event.getAction() == InventoryAction.COLLECT_TO_CURSOR ||
                    event.getAction().name().contains("DROP")) {
                event.setCancelled(true);
            }
        } else {
            if (event.isShiftClick()) {
                event.setCancelled(true);
            }
        }
    }

    private void performInfusion(Player player, Tier larvaTier) {
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
        returnItem(player, inv.getItem(LARVA_SLOT));
        returnItem(player, inv.getItem(HONEY_SLOT));
    }

    private void returnItem(Player player, ItemStack stack) {
        if (stack == null || stack.getType().isAir() || stack.getType().toString().endsWith("GLASS_PANE")) return;
        Map<Integer, ItemStack> left = player.getInventory().addItem(stack);
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

    private ItemStack createPane(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }

    private static final int HONEY_SLOT = 3;
    private static final int LARVA_SLOT = 5;
    private static final int INFUSE_SLOT = 4;
}

