package org.maks.beesPlugin.dao;

import java.sql.*;
import java.util.UUID;

/** DAO for operations on the bees_players table. */
public class PlayerDao {
    private final Database db;

    public PlayerDao(Database db) {
        this.db = db;
    }

    public void createPlayer(UUID uuid) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("INSERT IGNORE INTO bees_players(uuid) VALUES (?)")) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        }
    }

    public void deletePlayer(UUID uuid) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM bees_players WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        }
    }

    public boolean exists(UUID uuid) throws SQLException {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM bees_players WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }
}
