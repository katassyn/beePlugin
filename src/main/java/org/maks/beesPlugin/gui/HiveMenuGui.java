package org.maks.beesPlugin.gui;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.maks.beesPlugin.BeesPlugin;
import org.maks.beesPlugin.config.BeesConfig;
import org.maks.beesPlugin.hive.Hive;
import org.maks.beesPlugin.hive.HiveManager;
import org.maks.beesPlugin.hive.BeeType;
import org.maks.beesPlugin.hive.Tier;
import org.maks.beesPlugin.item.BeeItems;
import org.maks.beesPlugin.util.NumberFormatter;
import net.milkbowl.vault.economy.Economy;

import java.util.*;

public class HiveMenuGui implements Listener {

    private final HiveManager hiveManager;
    private final BeesConfig config;
    private final Economy economy;
    private final HiveGui hiveGui;
    private final Set<UUID> viewers = new HashSet<>();

    public HiveMenuGui(HiveManager hiveManager, BeesConfig config, Economy economy, HiveGui hiveGui) {
        this.hiveManager = hiveManager;
        this.config = config;
        this.economy = economy;
        this.hiveGui = hiveGui;
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(player, 27, "Your Hives");
        List<Hive> list = hiveManager.getHives(player.getUniqueId());
        for (int i = 0; i < config.maxHivesPerPlayer; i++) {
            ItemStack item;
            if (i < list.size()) {
                item = new ItemStack(Material.BEE_NEST);
                ItemMeta meta = item.getItemMeta();
                meta.setDisplayName(ChatColor.YELLOW + "Hive " + (i + 1));
                meta.setLore(List.of(ChatColor.GRAY + "Click to manage"));
                item.setItemMeta(meta);
            } else {
                double cost = hiveManager.getNextHiveCost(player.getUniqueId());
                item = new ItemStack(Material.BARRIER);
                ItemMeta meta = item.getItemMeta();
                meta.setDisplayName(ChatColor.RED + "Buy Hive");
                meta.setLore(List.of(ChatColor.GRAY + "Cost: " + NumberFormatter.format(cost)));
                item.setItemMeta(meta);
            }
            inv.setItem(i, item);
        }
        ItemStack collect = new ItemStack(Material.HONEY_BOTTLE);
        ItemMeta meta = collect.getItemMeta();
        meta.setDisplayName(ChatColor.GREEN + "Collect All");
        collect.setItemMeta(meta);
        inv.setItem(26, collect);

        ItemStack infuse = new ItemStack(Material.BEACON);
        ItemMeta m2 = infuse.getItemMeta();
        m2.setDisplayName(ChatColor.AQUA + "Infuse Larva");
        infuse.setItemMeta(m2);
        inv.setItem(25, infuse);
        viewers.add(player.getUniqueId());
        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        UUID id = event.getWhoClicked().getUniqueId();
        if (!viewers.contains(id)) return;
        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();
        if (slot < event.getView().getTopInventory().getSize()) {
            event.getView().setCursor(null);
        }
        List<Hive> list = hiveManager.getHives(id);
        if (slot < config.maxHivesPerPlayer) {
            if (slot < list.size()) {
                hiveGui.open(player, slot);
            } else {
                if (list.size() >= config.maxHivesPerPlayer) return;
                double cost = hiveManager.getNextHiveCost(id);
                if (economy != null) {
                    if (!economy.has(player, cost)) {
                        player.sendMessage(ChatColor.RED + "Not enough money");
                        return;
                    }
                    var resp = economy.withdrawPlayer(player, cost);
                    if (!resp.transactionSuccess()) {
                        player.sendMessage(ChatColor.RED + "Payment failed");
                        return;
                    }
                }
                boolean firstHive = list.isEmpty();
                Hive hive = hiveManager.createHive(id, System.currentTimeMillis() / 1000);
                if (hive != null && firstHive) {
                    var starters = List.of(
                            BeeItems.createBee(BeeType.QUEEN, Tier.I),
                            BeeItems.createBee(BeeType.WORKER, Tier.I),
                            BeeItems.createBee(BeeType.WORKER, Tier.I),
                            BeeItems.createBee(BeeType.DRONE, Tier.I)
                    );
                    for (ItemStack it : starters) {
                        var left = player.getInventory().addItem(it);
                        for (ItemStack s : left.values()) {
                            player.getWorld().dropItem(player.getLocation(), s);
                        }
                    }
                }
                player.sendMessage(ChatColor.GREEN + "Bought new hive");
                hiveGui.open(player, list.size() - 1);
            }
        } else if (slot == 26) {
            if (hiveManager.collectAll(player)) {
                player.sendMessage(ChatColor.GREEN + "Collected hive products");
            } else {
                player.sendMessage(ChatColor.RED + "Make space in your inventory first");
            }
            open(player);
        } else if (slot == 25) {
            player.closeInventory();
            var plugin = BeesPlugin.getPlugin(BeesPlugin.class);
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.addAttachment(plugin, "beesplugin.infuse", true, 20);
                player.performCommand("infuse");
            });
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        viewers.remove(event.getPlayer().getUniqueId());
    }
}

