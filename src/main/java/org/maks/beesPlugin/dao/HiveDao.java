package org.maks.beesPlugin.dao;

import org.maks.beesPlugin.hive.Hive;
import org.maks.beesPlugin.hive.Tier;
import org.maks.beesPlugin.hive.BeeType;

import java.sql.*;
import java.util.*;
import java.util.UUID;

/** DAO encapsulating CRUD operations for hives and hive_bees. */
public class HiveDao {
    private final Database db;
    private final HiveBeeDao beeDao;

    public HiveDao(Database db, HiveBeeDao beeDao) {
        this.db = db;
        this.beeDao = beeDao;
    }

    public List<Hive> loadHives(UUID player, long now) throws SQLException {
        List<Hive> list = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT id,last_tick,queen,honey_i,honey_ii,honey_iii,larvae_i,larvae_ii,larvae_iii FROM hives WHERE player_uuid=?")) {
            ps.setString(1, player.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt("id");
                    long lastTick = rs.getLong("last_tick");
                    Hive hive = new Hive(now);
                    hive.setId(id);
                    hive.setQueen(rs.getObject("queen") == null ? null : Tier.fromLevel(rs.getInt("queen")));
                    hive.getHoneyStored().put(Tier.I, rs.getInt("honey_i"));
                    hive.getHoneyStored().put(Tier.II, rs.getInt("honey_ii"));
                    hive.getHoneyStored().put(Tier.III, rs.getInt("honey_iii"));
                    hive.getLarvaeStored().put(Tier.I, rs.getInt("larvae_i"));
                    hive.getLarvaeStored().put(Tier.II, rs.getInt("larvae_ii"));
                    hive.getLarvaeStored().put(Tier.III, rs.getInt("larvae_iii"));
                    hive.setLastTick(lastTick); // method we will add
                    hive.getWorkers().addAll(beeDao.loadBees(conn, id, BeeType.WORKER));
                    hive.getDrones().addAll(beeDao.loadBees(conn, id, BeeType.DRONE));
                    list.add(hive);
                }
            }
        }
        return list;
    }

    public int createHive(Connection conn, UUID player, Hive hive) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO hives(player_uuid,last_tick,queen) VALUES (?,?,?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, player.toString());
            ps.setLong(2, hive.getLastTick());
            if (hive.getQueen() == null) ps.setNull(3, Types.INTEGER); else ps.setInt(3, hive.getQueen().getLevel());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    int id = rs.getInt(1);
                    hive.setId(id);
                    return id;
                }
            }
        }
        throw new SQLException("Failed to insert hive");
    }

    public void updateHive(Connection conn, UUID player, Hive hive) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("UPDATE hives SET last_tick=?,queen=?,honey_i=?,honey_ii=?,honey_iii=?,larvae_i=?,larvae_ii=?,larvae_iii=? WHERE id=? AND player_uuid=?")) {
            ps.setLong(1, hive.getLastTick());
            if (hive.getQueen() == null) ps.setNull(2, Types.INTEGER); else ps.setInt(2, hive.getQueen().getLevel());
            ps.setInt(3, hive.getHoneyStored().get(Tier.I));
            ps.setInt(4, hive.getHoneyStored().get(Tier.II));
            ps.setInt(5, hive.getHoneyStored().get(Tier.III));
            ps.setInt(6, hive.getLarvaeStored().get(Tier.I));
            ps.setInt(7, hive.getLarvaeStored().get(Tier.II));
            ps.setInt(8, hive.getLarvaeStored().get(Tier.III));
            ps.setInt(9, hive.getId());
            ps.setString(10, player.toString());
            ps.executeUpdate();
        }
        beeDao.replaceBees(conn, hive.getId(), BeeType.WORKER, hive.getWorkers());
        beeDao.replaceBees(conn, hive.getId(), BeeType.DRONE, hive.getDrones());
    }

    public void deleteHive(Connection conn, int id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM hives WHERE id=?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM hive_bees WHERE hive_id=?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }
}
