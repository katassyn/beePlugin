package org.maks.beesPlugin.gui;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
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
        viewers.add(player.getUniqueId());
        player.openInventory(inv);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        if (!viewers.remove(id)) return;

        Player player = (Player) event.getPlayer();
        Inventory inv = event.getInventory();

        ItemStack larvaStack = inv.getItem(4);
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
            if (i == 4) continue;
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
            if (stack == null || stack.getType().isAir()) continue;
            Map<Integer, ItemStack> left = player.getInventory().addItem(stack);
            for (ItemStack s : left.values()) {
                player.getWorld().dropItem(player.getLocation(), s);
            }
        }
    }
}
