package org.maks.beesPlugin.dao;

import org.maks.beesPlugin.hive.BeeType;
import org.maks.beesPlugin.hive.Tier;

import java.sql.*;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/** CRUD operations for the bee_locker table. */
public class BeeLockerDao {
    private final Database db;

    public BeeLockerDao(Database db) {
        this.db = db;
    }

    public Map<Tier, Integer> load(Connection conn, UUID player, BeeType type) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT tier,amount FROM bee_locker WHERE player_uuid=? AND type=?")) {
            ps.setString(1, player.toString());
            ps.setString(2, type.name());
            try (ResultSet rs = ps.executeQuery()) {
                Map<Tier, Integer> map = new EnumMap<>(Tier.class);
                while (rs.next()) {
                    map.put(Tier.fromLevel(rs.getInt(1)), rs.getInt(2));
                }
                return map;
            }
        }
    }

    public void upsert(Connection conn, UUID player, BeeType type, Tier tier, int amount) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO bee_locker(player_uuid,type,tier,amount) VALUES (?,?,?,?) " +
                "ON DUPLICATE KEY UPDATE amount=VALUES(amount)")) {
            ps.setString(1, player.toString());
            ps.setString(2, type.name());
            ps.setInt(3, tier.getLevel());
            ps.setInt(4, amount);
            ps.executeUpdate();
        }
    }

    public void delete(Connection conn, UUID player) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM bee_locker WHERE player_uuid=?")) {
            ps.setString(1, player.toString());
            ps.executeUpdate();
        }
    }
}
