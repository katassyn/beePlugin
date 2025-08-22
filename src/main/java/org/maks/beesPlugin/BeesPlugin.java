package org.maks.beesPlugin;

import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.maks.beesPlugin.command.HiveCommand;
import org.maks.beesPlugin.config.BeesConfig;
import org.maks.beesPlugin.gui.HiveGui;
import org.maks.beesPlugin.gui.HiveMenuGui;
import org.maks.beesPlugin.gui.InfusionGui;
import org.maks.beesPlugin.hive.HiveManager;
import net.milkbowl.vault.economy.Economy;

public final class BeesPlugin extends JavaPlugin {

    private BeesConfig beesConfig;
    private HiveManager hiveManager;
    private Economy economy;
    private HiveGui hiveGui;
    private HiveMenuGui hiveMenuGui;
    private InfusionGui infusionGui;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        beesConfig = new BeesConfig(getConfig());
        hiveManager = new HiveManager(beesConfig);
        setupEconomy();
        hiveGui = new HiveGui(hiveManager, beesConfig);
        infusionGui = new InfusionGui(beesConfig);
        hiveMenuGui = new HiveMenuGui(hiveManager, beesConfig, economy, hiveGui, infusionGui);

        if (getCommand("hive") != null) {
            getCommand("hive").setExecutor(new HiveCommand(hiveMenuGui));
        }
        getServer().getPluginManager().registerEvents(hiveGui, this);
        getServer().getPluginManager().registerEvents(hiveMenuGui, this);
        getServer().getPluginManager().registerEvents(infusionGui, this);

        long tick = beesConfig.tickSeconds * 20L;
        getServer().getScheduler().runTaskTimer(this, () -> {
            long now = System.currentTimeMillis() / 1000;
            hiveManager.tickAll(now);
        }, tick, tick);
    }

    private void setupEconomy() {
        try {
            RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
            if (rsp != null) {
                economy = rsp.getProvider();
            }
        } catch (NoClassDefFoundError e) {
            getLogger().warning("Vault not found, economy disabled");
        }
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}

