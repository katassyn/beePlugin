package org.maks.beesPlugin;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.maks.beesPlugin.dao.PlayerDao;
import org.maks.beesPlugin.hive.HiveManager;

import java.sql.SQLException;
import java.util.UUID;

/** Handles login/logout events to load and unload player hives. */
public class PlayerListener implements Listener {
    private final HiveManager hiveManager;
    private final PlayerDao playerDao;

    public PlayerListener(HiveManager hiveManager, PlayerDao playerDao) {
        this.hiveManager = hiveManager;
        this.playerDao = playerDao;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        try {
            playerDao.createPlayer(id);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        hiveManager.loadPlayer(id, System.currentTimeMillis() / 1000);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        hiveManager.unloadPlayer(event.getPlayer().getUniqueId());
    }
}
