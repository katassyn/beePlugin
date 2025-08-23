package org.maks.beesPlugin.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.maks.beesPlugin.gui.InfusionGui;

public class InfuseCommand implements CommandExecutor {

    private final InfusionGui infusionGui;

    public InfuseCommand(InfusionGui infusionGui) {
        this.infusionGui = infusionGui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players");
            return true;
        }
        infusionGui.open(player);
        return true;
    }
}
