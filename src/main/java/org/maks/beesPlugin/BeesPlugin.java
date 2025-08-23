package org.maks.beesPlugin;

import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.maks.beesPlugin.command.HiveCommand;
import org.maks.beesPlugin.config.BeesConfig;
import org.maks.beesPlugin.dao.*;
import org.maks.beesPlugin.gui.HiveGui;
import org.maks.beesPlugin.gui.HiveMenuGui;
import org.maks.beesPlugin.gui.InfusionGui;
import org.maks.beesPlugin.hive.HiveManager;
import net.milkbowl.vault.economy.Economy;

import java.sql.SQLException;

public final class BeesPlugin extends JavaPlugin {

    private BeesConfig beesConfig;
    private HiveManager hiveManager;
    private Economy economy;
    private HiveGui hiveGui;
    private HiveMenuGui hiveMenuGui;
    private InfusionGui infusionGui;
    private Database database;
    private PlayerDao playerDao;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        beesConfig = new BeesConfig(getConfig());
        try {
            String host = getConfig().getString("database.host", "localhost");
            String portStr = getConfig().getString("database.port", "3306");
            int port = Integer.parseInt(portStr);
            String dbName = getConfig().getString("database.name", "minecraft");
            String user = getConfig().getString("database.user", "root");
            String password = getConfig().getString("database.password", "");
            String url = "jdbc:mysql://" + host + ":" + port + "/" + dbName + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
            database = new Database(url, user, password);
        } catch (SQLException e) {
            getLogger().severe("Failed to init database: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        HiveBeeDao hiveBeeDao = new HiveBeeDao(database);
        HiveDao hiveDao = new HiveDao(database, hiveBeeDao);
        playerDao = new PlayerDao(database);
        hiveManager = new HiveManager(beesConfig, database, hiveDao);
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
        getServer().getPluginManager().registerEvents(new PlayerListener(hiveManager, playerDao), this);

        long tick = beesConfig.tickSeconds * 20L;
        getServer().getScheduler().runTaskTimer(this, () -> {
            long now = System.currentTimeMillis() / 1000;
            hiveManager.tickAll(now);
        }, tick, tick);
    }

    private void setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            getLogger().warning("Vault not found, economy disabled");
            return;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            getLogger().warning("No economy provider found, economy disabled");
            return;
        }
        economy = rsp.getProvider();
    }

    @Override
    public void onDisable() {
        // cleanup if needed
    }
}
