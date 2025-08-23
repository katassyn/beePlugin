package org.maks.beesPlugin.dao;

import org.maks.beesPlugin.hive.BeeType;
import org.maks.beesPlugin.hive.Tier;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/** CRUD access to the bees_hive_bees table. */
public class HiveBeeDao {
    private final Database db;

    public HiveBeeDao(Database db) {
        this.db = db;
    }

    public List<Tier> loadBees(Connection conn, int hiveId, BeeType type) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT tier FROM bees_hive_bees WHERE hive_id=? AND type=? ORDER BY slot")) {
            ps.setInt(1, hiveId);
            ps.setString(2, type.name());
            try (ResultSet rs = ps.executeQuery()) {
                List<Tier> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(Tier.fromLevel(rs.getInt(1)));
                }
                return list;
            }
        }
    }

    public void replaceBees(Connection conn, int hiveId, BeeType type, List<Tier> bees) throws SQLException {
        try (PreparedStatement del = conn.prepareStatement("DELETE FROM bees_hive_bees WHERE hive_id=? AND type=?")) {
            del.setInt(1, hiveId);
            del.setString(2, type.name());
            del.executeUpdate();
        }
        try (PreparedStatement ins = conn.prepareStatement("INSERT INTO bees_hive_bees(hive_id,type,slot,tier) VALUES (?,?,?,?)")) {
            int slot = 0;
            for (Tier t : bees) {
                ins.setInt(1, hiveId);
                ins.setString(2, type.name());
                ins.setInt(3, slot++);
                ins.setInt(4, t.getLevel());
                ins.addBatch();
            }
            ins.executeBatch();
        }
    }
}
