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
        Inventory inv = Bukkit.createInventory(player, 9, "Hive " + (index + 1));
        int w = 0;
        for (Tier t : hive.getWorkers()) {
            inv.setItem(w++, BeeItems.createBee(BeeType.WORKER, t));
        }
        if (hive.getQueen() != null) {
            inv.setItem(6, BeeItems.createBee(BeeType.QUEEN, hive.getQueen()));
        }
        int d = 7;
        for (Tier t : hive.getDrones()) {
            inv.setItem(d++, BeeItems.createBee(BeeType.DRONE, t));
        }
        open.put(player.getUniqueId(), index);
        player.openInventory(inv);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        Integer idx = open.remove(id);
        if (idx == null) return;

        List<Hive> list = hiveManager.getHives(id);
        if (idx < 0 || idx >= list.size()) return;
        Hive hive = list.get(idx);
        Inventory inv = event.getInventory();

        hive.setQueen(null);
        hive.getWorkers().clear();
        hive.getDrones().clear();

        ItemStack queenStack = inv.getItem(6);
        if (queenStack != null) {
            BeeItems.BeeItem bee = BeeItems.parse(queenStack);
            if (bee != null && bee.type() == BeeType.QUEEN && queenStack.getAmount() == 1) {
                hive.setQueen(bee.tier());
            } else {
                giveBack(event.getPlayer(), queenStack);
                inv.setItem(6, null);
            }
        }

        for (int i = 0; i < config.workerSlots; i++) {
            ItemStack it = inv.getItem(i);
            if (it == null) continue;
            BeeItems.BeeItem bee = BeeItems.parse(it);
            if (bee != null && bee.type() == BeeType.WORKER && it.getAmount() == 1) {
                hive.getWorkers().add(bee.tier());
            } else {
                giveBack(event.getPlayer(), it);
            }
        }

        for (int i = 0; i < config.droneSlots; i++) {
            int slot = 7 + i;
            ItemStack it = inv.getItem(slot);
            if (it == null) continue;
            BeeItems.BeeItem bee = BeeItems.parse(it);
            if (bee != null && bee.type() == BeeType.DRONE && it.getAmount() == 1) {
                hive.getDrones().add(bee.tier());
            } else {
                giveBack(event.getPlayer(), it);
            }
        }
        hiveManager.saveHive(id, hive);
    }

    private void giveBack(Player player, ItemStack stack) {
        Map<Integer, ItemStack> left = player.getInventory().addItem(stack);
        for (ItemStack s : left.values()) {
            player.getWorld().dropItem(player.getLocation(), s);
        }
    }
}

