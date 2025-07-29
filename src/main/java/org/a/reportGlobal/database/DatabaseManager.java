package org.a.reportGlobal.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.a.reportGlobal.ReportGlobal;
import org.bukkit.configuration.file.FileConfiguration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DatabaseManager {
    private final ReportGlobal plugin;
    private HikariDataSource dataSource;

    public DatabaseManager(ReportGlobal plugin) {
        this.plugin = plugin;
        initializeDatabase();
    }

    private void initializeDatabase() {
        FileConfiguration config = plugin.getConfig();

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(String.format("jdbc:mysql://%s:%d/%s",
                config.getString("database.host"),
                config.getInt("database.port"),
                config.getString("database.database")));
        hikariConfig.setUsername(config.getString("database.username"));
        hikariConfig.setPassword(config.getString("database.password"));

        // 配置连接池
        hikariConfig.setMaximumPoolSize(10);
        hikariConfig.setMinimumIdle(5);
        hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        try {
            dataSource = new HikariDataSource(hikariConfig);
            createTables();
        } catch (Exception e) {
            plugin.getLogger().severe("无法初始化数据库连接: " + e.getMessage());
        }
    }

    private void createTables() {
        String[] createTableSQLs = {
            "CREATE TABLE IF NOT EXISTS admin_actions (" +
            "    id INT AUTO_INCREMENT PRIMARY KEY," +
            "    action_type VARCHAR(10) NOT NULL," +
            "    admin_name VARCHAR(36) NOT NULL," +
            "    target_player VARCHAR(36) NOT NULL," +
            "    action_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
            ")",
            
            "CREATE TABLE IF NOT EXISTS reports (" +
            "    id INT AUTO_INCREMENT PRIMARY KEY," +
            "    reporter_name VARCHAR(36) NOT NULL," +
            "    target_player VARCHAR(36) NOT NULL," +
            "    reason TEXT NOT NULL," +
            "    server_name VARCHAR(50) NOT NULL," +
            "    report_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
            "    status VARCHAR(20) DEFAULT 'PENDING'," +
            "    handler_name VARCHAR(36) NULL," +
            "    handle_time TIMESTAMP NULL," +
            "    handle_comment TEXT NULL" +
            ")"
        };

        try (Connection conn = dataSource.getConnection()) {
            for (String sql : createTableSQLs) {
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.executeUpdate();
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("创建数据库表失败: " + e.getMessage());
        }
    }

    public void logAction(String actionType, String adminName, String targetPlayer) {
        String sql = "INSERT INTO admin_actions (action_type, admin_name, target_player) VALUES (?, ?, ?)";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, actionType);
            stmt.setString(2, adminName);
            stmt.setString(3, targetPlayer);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("记录管理员操作失败: " + e.getMessage());
        }
    }

    /**
     * 记录一个新的举报
     */
    public void logReport(String reporterName, String targetPlayer, String reason) {
        String sql = "INSERT INTO reports (reporter_name, target_player, reason, server_name) VALUES (?, ?, ?, ?)";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, reporterName);
            stmt.setString(2, targetPlayer);
            stmt.setString(3, reason);
            stmt.setString(4, plugin.getConfig().getString("server-name", "unknown"));
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("记录举报失败: " + e.getMessage());
        }
    }

    /**
     * 获取待处理的举报列表
     */
    public List<Map<String, Object>> getPendingReports() {
        String sql = "SELECT * FROM reports WHERE status = 'PENDING' ORDER BY report_time DESC";
        List<Map<String, Object>> reports = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                Map<String, Object> report = new HashMap<>();
                report.put("id", rs.getInt("id"));
                report.put("reporter_name", rs.getString("reporter_name"));
                report.put("target_player", rs.getString("target_player"));
                report.put("reason", rs.getString("reason"));
                report.put("server_name", rs.getString("server_name"));
                report.put("report_time", rs.getTimestamp("report_time"));
                reports.add(report);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("获取举报列表失败: " + e.getMessage());
        }

        return reports;
    }

    /**
     * 更新举报状态
     */
    public void updateReportStatus(int reportId, String status, String handlerName, String comment) {
        String sql = "UPDATE reports SET status = ?, handler_name = ?, handle_time = CURRENT_TIMESTAMP, handle_comment = ? WHERE id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, status);
            stmt.setString(2, handlerName);
            stmt.setString(3, comment);
            stmt.setInt(4, reportId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("更新举报状态失败: " + e.getMessage());
        }
    }

    /**
     * 获取玩家的举报历史
     */
    public List<Map<String, Object>> getPlayerReports(String playerName, boolean asReporter) {
        String sql = asReporter ? 
            "SELECT * FROM reports WHERE reporter_name = ? ORDER BY report_time DESC" :
            "SELECT * FROM reports WHERE target_player = ? ORDER BY report_time DESC";
        
        List<Map<String, Object>> reports = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerName);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> report = new HashMap<>();
                    report.put("id", rs.getInt("id"));
                    report.put("reporter_name", rs.getString("reporter_name"));
                    report.put("target_player", rs.getString("target_player"));
                    report.put("reason", rs.getString("reason"));
                    report.put("server_name", rs.getString("server_name"));
                    report.put("report_time", rs.getTimestamp("report_time"));
                    report.put("status", rs.getString("status"));
                    report.put("handler_name", rs.getString("handler_name"));
                    report.put("handle_time", rs.getTimestamp("handle_time"));
                    report.put("handle_comment", rs.getString("handle_comment"));
                    reports.add(report);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("获取玩家举报历史失败: " + e.getMessage());
        }

        return reports;
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}