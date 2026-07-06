package org.maks.beesPlugin.dao;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.*;
import java.util.function.Consumer;

/**
 * Simple wrapper around a JDBC connection to a MySQL database.
 * Responsible for creating the schema and running small transactions.
 * Uses HikariCP connection pooling for better performance.
 */
public class Database {
    private final HikariDataSource dataSource;

    public Database(String url, String user, String password) throws SQLException {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(user);
        config.setPassword(password);

        // Connection pool settings
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(5000); // 5 seconds max wait for connection
        config.setIdleTimeout(300000); // 5 minutes idle timeout
        config.setMaxLifetime(1800000); // 30 minutes max lifetime
        config.setLeakDetectionThreshold(60000); // 60 seconds leak detection

        // Performance optimizations
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");
        config.addDataSourceProperty("useLocalSessionState", "true");
        config.addDataSourceProperty("rewriteBatchedStatements", "true");
        config.addDataSourceProperty("cacheResultSetMetadata", "true");
        config.addDataSourceProperty("cacheServerConfiguration", "true");
        config.addDataSourceProperty("elideSetAutoCommits", "true");
        config.addDataSourceProperty("maintainTimeStats", "false");

        this.dataSource = new HikariDataSource(config);
        init();
    }

    private void init() throws SQLException {
        try (Connection conn = getConnection(); Statement st = conn.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS bees_players(" +
                    "uuid VARCHAR(36) PRIMARY KEY" +
                    ") ENGINE=InnoDB");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS bees_hives(" +
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
            st.executeUpdate("CREATE TABLE IF NOT EXISTS bees_hive_bees(" +
                    "hive_id INT NOT NULL," +
                    "type VARCHAR(255) NOT NULL," +
                    "slot INT NOT NULL," +
                    "tier INT NOT NULL" +
                    ") ENGINE=InnoDB");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS bees_locker(" +
                    "player_uuid VARCHAR(36) NOT NULL," +
                    "type VARCHAR(255) NOT NULL," +
                    "tier INT NOT NULL," +
                    "amount INT NOT NULL," +
                    "PRIMARY KEY(player_uuid,type,tier)" +
                    ") ENGINE=InnoDB");
        }
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
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
                try {
                    conn.rollback();
                } catch (SQLException rollbackException) {
                    e.addSuppressed(rollbackException);
                }
                if (e instanceof SQLException se) throw se;
                else throw new SQLException("Transaction failed", e);
            }
        }
    }

    /**
     * Close the connection pool. Should be called on plugin disable.
     */
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
