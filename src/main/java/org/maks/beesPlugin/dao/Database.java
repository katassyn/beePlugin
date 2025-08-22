package org.maks.beesPlugin.dao;

import java.sql.*;
import java.util.function.Consumer;

/**
 * Simple wrapper around a JDBC connection to a MySQL database.
 * Responsible for creating the schema and running small transactions.
 */
public class Database {
    private final String url;
    private final String user;
    private final String password;

    public Database(String url, String user, String password) throws SQLException {
        this.url = url;
        this.user = user;
        this.password = password;
        init();
    }

    private void init() throws SQLException {
        try (Connection conn = getConnection(); Statement st = conn.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS players(" +
                    "uuid VARCHAR(36) PRIMARY KEY" +
                    ") ENGINE=InnoDB");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS hives(" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "player_uuid VARCHAR(36) NOT NULL," +
                    "last_tick BIGINT NOT NULL," +
                    "honey_i INT NOT NULL DEFAULT 0," +
                    "honey_ii INT NOT NULL DEFAULT 0," +
                    "honey_iii INT NOT NULL DEFAULT 0," +
                    "larvae_i INT NOT NULL DEFAULT 0," +
                    "larvae_ii INT NOT NULL DEFAULT 0," +
                    "larvae_iii INT NOT NULL DEFAULT 0," +
                    "queen INT" +
                    ") ENGINE=InnoDB");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS hive_bees(" +
                    "hive_id INT NOT NULL," +
                    "type VARCHAR(255) NOT NULL," +
                    "slot INT NOT NULL," +
                    "tier INT NOT NULL" +
                    ") ENGINE=InnoDB");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS bee_locker(" +
                    "player_uuid VARCHAR(36) NOT NULL," +
                    "type VARCHAR(255) NOT NULL," +
                    "tier INT NOT NULL," +
                    "amount INT NOT NULL," +
                    "PRIMARY KEY(player_uuid,type,tier)" +
                    ") ENGINE=InnoDB");
        }
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, user, password);
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
