package org.maks.beesPlugin.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.maks.beesPlugin.gui.HiveMenuGui;

public class HiveCommand implements CommandExecutor {

    private final HiveMenuGui menu;

    public HiveCommand(HiveMenuGui menu) {
        this.menu = menu;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players");
            return true;
        }
        menu.open(player);
        return true;
    }
}

