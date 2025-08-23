package org.maks.beesPlugin.item;

import org.bukkit.ChatColor;
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
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
        String color = switch (tier) {
            case I -> ChatColor.BLUE.toString();
            case II -> ChatColor.DARK_PURPLE.toString();
            case III -> ChatColor.GOLD.toString();
        };
        meta.setDisplayName(color + "[ " + tier.name() + " ] Honey Bottle");
        List<String> lore = new java.util.ArrayList<>();
        lore.add(ChatColor.ITALIC + "" + ChatColor.GRAY + "Applies a new " + ChatColor.WHITE + "Quality" + ChatColor.GRAY + " to an item.");
        switch (tier) {
            case I -> lore.addAll(List.of(
                    ChatColor.ITALIC + "" + ChatColor.GRAY + "Roll range: " + ChatColor.WHITE + "-10% " + ChatColor.GRAY + "to " + ChatColor.WHITE + "+10%.",
                    ChatColor.ITALIC + "" + ChatColor.GRAY + "Basic crafting material"
            ));
            case II -> lore.addAll(List.of(
                    ChatColor.ITALIC + "" + ChatColor.GRAY + "Roll range: " + ChatColor.WHITE + "0% " + ChatColor.GRAY + "to " + ChatColor.WHITE + "+20%.",
                    ChatColor.ITALIC + "" + ChatColor.GRAY + ChatColor.GREEN + "Rare" + ChatColor.GRAY + " crafting material"
            ));
            case III -> lore.addAll(List.of(
                    ChatColor.ITALIC + "" + ChatColor.GRAY + "Roll range: " + ChatColor.WHITE + "+10% " + ChatColor.GRAY + "to " + ChatColor.WHITE + "+30%.",
                    ChatColor.ITALIC + "" + ChatColor.GRAY + ChatColor.RED + "Legendary" + ChatColor.GRAY + " crafting material"
            ));
        }
        meta.setLore(lore);
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
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
        String color = switch (tier) {
            case I -> ChatColor.BLUE.toString();
            case II -> ChatColor.DARK_PURPLE.toString();
            case III -> ChatColor.GOLD.toString();
        };
        String name = color + "[ " + tier.name() + " ] " + switch (type) {
            case WORKER -> "Worker Bee";
            case DRONE -> "Drone Bee";
            case QUEEN -> "Queen Bee";
            case LARVA -> "Bee Larva";
        };
        List<String> lore = switch (type) {
            case QUEEN -> {
                String mult = switch (tier) {
                    case I -> "1.0x";
                    case II -> "1.2x";
                    case III -> "1.5x";
                };
                String chance = switch (tier) {
                    case I -> "+5%";
                    case II -> "+10%";
                    case III -> "+15%";
                };
                yield List.of(
                        ChatColor.ITALIC + "" + ChatColor.GRAY + "Hive multiplier: " + ChatColor.WHITE + mult,
                        ChatColor.ITALIC + "" + ChatColor.GRAY + "Rarer honey chance: " + ChatColor.GREEN + chance

                );
            }
            case WORKER -> {
                String prod = switch (tier) {
                    case I -> "0.50";
                    case II -> "0.75";
                    case III -> "1.00";
                };
                yield List.of(ChatColor.ITALIC + "" + ChatColor.GRAY + "Base honey production: " + ChatColor.WHITE + prod);
            }
            case DRONE -> {
                String larvae = switch (tier) {
                    case I -> "0.50";
                    case II -> "0.75";
                    case III -> "1.00";
                };
                String penalty = switch (tier) {
                    case I -> "-1.00";
                    case II -> "-0.75";
                    case III -> "-0.50";
                };
                yield List.of(
                        ChatColor.ITALIC + "" + ChatColor.GRAY + "Larvae production: " + ChatColor.WHITE + larvae,
                        ChatColor.ITALIC + "" + ChatColor.GRAY + "Reduces base honey production: " + ChatColor.WHITE + penalty
                );
            }
            case LARVA -> List.of(ChatColor.ITALIC + "" + ChatColor.GRAY + "Can transform into any type of bee.");

        };
        meta.setLore(lore);
        meta.setUnbreakable(true);
        item.setItemMeta(meta);
        return item;
    }

    public record BeeItem(BeeType type, Tier tier) {}

    private static final Pattern NAME_PATTERN = Pattern.compile("\\[ ([^\\]]+) \\] (.+)");

    public static BeeItem parse(ItemStack item) {
        if (item == null) return null;
        Material type = item.getType();
        if (type != Material.BREAD && type != Material.COOKIE) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return null;
        String stripped = meta.getDisplayName().replaceAll("§[0-9a-fk-or]", "");
        Matcher m = NAME_PATTERN.matcher(stripped);
        if (!m.find()) return null;
        String token = m.group(1);
        Tier tier;
        try {
            tier = Tier.fromLevel(Integer.parseInt(token));
        } catch (NumberFormatException e) {
            try {
                tier = Tier.valueOf(token);
            } catch (IllegalArgumentException ex) {
                return null;
            }
        }
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
        String token = m.group(1);
        try {
            return Tier.fromLevel(Integer.parseInt(token));
        } catch (NumberFormatException e) {
            try {
                return Tier.valueOf(token);
            } catch (IllegalArgumentException ex) {
                return null;
            }
        }
    }
}
