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
            event.setCancelled(true);
            Player player = (Player) event.getWhoClicked();
            if (raw == LARVA_SLOT) {
                ItemStack cursor = event.getCursor();
                ItemStack current = event.getCurrentItem();
                if (cursor != null && !cursor.getType().isAir()) {
                    BeeItems.BeeItem bee = BeeItems.parse(cursor);
                    if (bee == null || bee.type() != BeeType.LARVA) {
                        return;
                    }
                    top.setItem(raw, cursor);
                    event.getView().setCursor(null);
                } else if (current != null && !current.getType().toString().endsWith("GLASS_PANE")) {
                    Map<Integer, ItemStack> left = player.getInventory().addItem(current);
                    for (ItemStack s : left.values()) {
                        player.getWorld().dropItem(player.getLocation(), s);
                    }
                    top.setItem(raw, createPane(Material.WHITE_STAINED_GLASS_PANE, ChatColor.GRAY + "Larva"));
                }
            } else if (raw == HONEY_SLOT) {
                ItemStack cursor = event.getCursor();
                ItemStack current = event.getCurrentItem();
                if (cursor != null && !cursor.getType().isAir()) {
                    Tier t = BeeItems.parseHoney(cursor);
                    if (t == null) {
                        return;
                    }
                    top.setItem(raw, cursor);
                    event.getView().setCursor(null);
                } else if (current != null && !current.getType().toString().endsWith("GLASS_PANE")) {
                    Map<Integer, ItemStack> left = player.getInventory().addItem(current);
                    for (ItemStack s : left.values()) {
                        player.getWorld().dropItem(player.getLocation(), s);
                    }
                    top.setItem(raw, createPane(Material.ORANGE_STAINED_GLASS_PANE, ChatColor.GRAY + "Honey"));
                }
            } else if (raw == INFUSE_SLOT) {
                ItemStack larvaStack = top.getItem(LARVA_SLOT);
                ItemStack honeyStack = top.getItem(HONEY_SLOT);
                BeeItems.BeeItem larva = BeeItems.parse(larvaStack);
                Tier honeyTier = BeeItems.parseHoney(honeyStack);
                if (larva == null || larva.type() != BeeType.LARVA || honeyTier == null) {
                    return;
                }
                int required = 1;
                int larvaCount = larvaStack.getAmount();
                int honeyCount = honeyStack.getAmount();
                int runs = Math.min(larvaCount, honeyCount / required);
                if (runs <= 0) {
                    return;
                }
                honeyCount -= runs * required;
                larvaCount -= runs;
                if (honeyCount > 0) {
                    honeyStack.setAmount(honeyCount);
                    top.setItem(HONEY_SLOT, honeyStack);
                } else {
                    top.setItem(HONEY_SLOT, createPane(Material.ORANGE_STAINED_GLASS_PANE, ChatColor.GRAY + "Honey"));
                }
                if (larvaCount > 0) {
                    larvaStack.setAmount(larvaCount);
                    top.setItem(LARVA_SLOT, larvaStack);
                } else {
                    top.setItem(LARVA_SLOT, createPane(Material.WHITE_STAINED_GLASS_PANE, ChatColor.GRAY + "Larva"));
                }
                for (int i = 0; i < runs; i++) {
                    performInfusion(player, larva.tier(), honeyTier);
                }
            }
        } else {
            if (event.isShiftClick() || event.getAction() == org.bukkit.event.inventory.InventoryAction.COLLECT_TO_CURSOR) {
                event.setCancelled(true);
            }
        }
    }

    private void performInfusion(Player player, Tier larvaTier, Tier honeyTier) {
        Map<BeeType, Double> baseWeights = config.infusionTypeWeights.get(larvaTier);
        double honeyMult = 1.0 + 0.25 * (honeyTier.getLevel() - 1);
        Map<BeeType, Double> adjustedWeights = new EnumMap<>(BeeType.class);
        for (Map.Entry<BeeType, Double> e : baseWeights.entrySet()) {
            double w = e.getValue();
            if (e.getKey() == BeeType.QUEEN) {
                w *= honeyMult;
            }
            adjustedWeights.put(e.getKey(), w);
        }
        BeeType resultType = rollType(adjustedWeights);

        BeesConfig.TierShift baseShift = config.infusionTierShift.get(larvaTier);
        double honeyBoost = 0.2 * (honeyTier.getLevel() - 1);
        double larvaBoost = 0.3 * (larvaTier.getLevel() - 1);

        double down1 = baseShift.down1();
        double same = baseShift.same();
        double up1 = baseShift.up1();
        double up2 = baseShift.up2();

        double bonus = honeyBoost + larvaBoost;
        up1 += bonus * up1;
        up2 += bonus * up2;
        down1 *= 1.0 - bonus / 2;
        same *= 1.0 - bonus / 2;

        double total = down1 + same + up1 + up2;
        down1 /= total;
        same /= total;
        up1 /= total;
        up2 /= total;

        double r = random.nextDouble();
        int tierShift;
        if (r < down1) tierShift = -1;
        else if (r < down1 + same) tierShift = 0;
        else if (r < down1 + same + up1) tierShift = 1;
        else tierShift = 2;

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
        Inventory inv = event.getInventory();
        Player player = (Player) event.getPlayer();
        ItemStack honey = inv.getItem(HONEY_SLOT);
        if (honey != null && !honey.getType().toString().endsWith("GLASS_PANE")) {
            Map<Integer, ItemStack> left = player.getInventory().addItem(honey);
            for (ItemStack s : left.values()) {
                player.getWorld().dropItem(player.getLocation(), s);
            }
        }
        ItemStack larva = inv.getItem(LARVA_SLOT);
        if (larva != null && !larva.getType().toString().endsWith("GLASS_PANE")) {
            Map<Integer, ItemStack> left = player.getInventory().addItem(larva);
            for (ItemStack s : left.values()) {
                player.getWorld().dropItem(player.getLocation(), s);
            }
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

