package org.maks.beesPlugin.dao;

import java.sql.*;
import java.util.function.Consumer;

/**
 * Simple wrapper around a JDBC connection to a SQLite database.
 * Responsible for creating the schema and running small transactions.
 */
public class Database {
    private final String url;

    public Database(String url) throws SQLException {
        this.url = url;
        init();
    }

    private void init() throws SQLException {
        try (Connection conn = getConnection(); Statement st = conn.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS players(" +
                    "uuid TEXT PRIMARY KEY\n" +
                    ")");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS hives(" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "player_uuid TEXT NOT NULL," +
                    "last_tick INTEGER NOT NULL," +
                    "honey_i INTEGER NOT NULL DEFAULT 0," +
                    "honey_ii INTEGER NOT NULL DEFAULT 0," +
                    "honey_iii INTEGER NOT NULL DEFAULT 0," +
                    "larvae_i INTEGER NOT NULL DEFAULT 0," +
                    "larvae_ii INTEGER NOT NULL DEFAULT 0," +
                    "larvae_iii INTEGER NOT NULL DEFAULT 0," +
                    "queen INTEGER" +
                    ")");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS hive_bees(" +
                    "hive_id INTEGER NOT NULL," +
                    "type TEXT NOT NULL," +
                    "slot INTEGER NOT NULL," +
                    "tier INTEGER NOT NULL" +
                    ")");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS bee_locker(" +
                    "player_uuid TEXT NOT NULL," +
                    "type TEXT NOT NULL," +
                    "tier INTEGER NOT NULL," +
                    "amount INTEGER NOT NULL," +
                    "PRIMARY KEY(player_uuid,type,tier)" +
                    ")");
        }
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url);
    }

    /**
     * Execute the given consumer within a SQL transaction.
     */
    public void runInTransaction(Consumer<Connection> consumer) throws SQLException {
        try (Connection conn = getConnection()) {
            try {
                conn.setAutoCommit(false);
                consumer.accept(conn);
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                if (e instanceof SQLException se) throw se;
                else throw new SQLException("Transaction failed", e);
            }
        }
    }
}
