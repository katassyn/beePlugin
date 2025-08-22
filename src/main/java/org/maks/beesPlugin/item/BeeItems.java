package org.maks.beesPlugin.item;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.maks.beesPlugin.hive.BeeType;
import org.maks.beesPlugin.hive.Tier;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BeeItems {

    public static ItemStack createHoney(Tier tier) {
        ItemStack item = new ItemStack(Material.HONEY_BOTTLE);
        ItemMeta meta = item.getItemMeta();
        meta.addEnchant(Enchantment.DURABILITY, 10, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
        String color = switch (tier) {
            case I -> "§9";
            case II -> "§5";
            case III -> "§6";
        };
        meta.setDisplayName(color + "[ " + tier.getLevel() + " ] Honey Bottle");
        meta.setLore(List.of("§o§7Applies a new §fQuality§7 to an item."));
        meta.setUnbreakable(true);
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack createBee(BeeType type, Tier tier) {
        Material material = switch (type) {
            case WORKER, QUEEN, DRONE -> Material.BREAD;
            case LARVA -> Material.COOKIE;
        };
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.addEnchant(Enchantment.DURABILITY, 10, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
        String color = switch (tier) {
            case I -> "§9";
            case II -> "§5";
            case III -> "§6";
        };
        String name = color + "[ " + tier.getLevel() + " ] " + switch (type) {
            case WORKER -> "Worker Bee";
            case DRONE -> "Drone Bee";
            case QUEEN -> "Queen Bee";
            case LARVA -> "Bee Larva";
        };
        meta.setDisplayName(name);
        meta.setUnbreakable(true);
        item.setItemMeta(meta);
        return item;
    }

    public record BeeItem(BeeType type, Tier tier) {}

    private static final Pattern NAME_PATTERN = Pattern.compile("\\[ (\\d) \\] (.+)");

    public static BeeItem parse(ItemStack item) {
        if (item == null) return null;
        Material type = item.getType();
        if (type != Material.BREAD && type != Material.COOKIE) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return null;
        String stripped = meta.getDisplayName().replaceAll("§[0-9a-fk-or]", "");
        Matcher m = NAME_PATTERN.matcher(stripped);
        if (!m.find()) return null;
        int level;
        try {
            level = Integer.parseInt(m.group(1));
        } catch (NumberFormatException e) {
            return null;
        }
        Tier tier = Tier.fromLevel(level);
        String name = m.group(2).toLowerCase();
        BeeType beeType;
        if (name.contains("queen")) beeType = BeeType.QUEEN;
        else if (name.contains("worker")) beeType = BeeType.WORKER;
        else if (name.contains("drone")) beeType = BeeType.DRONE;
        else if (name.contains("larva")) beeType = BeeType.LARVA;
        else return null;
        return new BeeItem(beeType, tier);
    }

    public static Tier parseHoney(ItemStack item) {
        if (item == null || item.getType() != Material.HONEY_BOTTLE) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return null;
        String stripped = meta.getDisplayName().replaceAll("§[0-9a-fk-or]", "");
        Matcher m = NAME_PATTERN.matcher(stripped);
        if (!m.find()) return null;
        int level;
        try {
            level = Integer.parseInt(m.group(1));
        } catch (NumberFormatException e) {
            return null;
        }
        return Tier.fromLevel(level);
    }
}
