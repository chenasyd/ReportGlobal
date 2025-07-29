package org.a.reportGlobal;

import org.a.reportGlobal.commands.ReportAdminCommand;
import org.a.reportGlobal.commands.ReportCommand;
import org.a.reportGlobal.commands.ReportGlobalCommand;
import org.a.reportGlobal.database.DatabaseManager;
import org.a.reportGlobal.listeners.AdminActionListener;
import org.bukkit.plugin.java.JavaPlugin;

public final class ReportGlobal extends JavaPlugin {
    private DatabaseManager databaseManager;
    private AdminActionListener adminActionListener;

    @Override
    public void onEnable() {
        // 保存默认配置
        saveDefaultConfig();
        
        // 初始化数据库管理器
        initDatabase();
        
        // 注册命令
        getCommand("reportglobal").setExecutor(new ReportGlobalCommand(this));
        
        // 注册举报命令
        ReportCommand reportCommand = new ReportCommand(this, databaseManager);
        getCommand("report").setExecutor(reportCommand);
        getCommand("report").setTabCompleter(reportCommand);
        
        // 注册举报管理命令
        ReportAdminCommand reportAdminCommand = new ReportAdminCommand(this, databaseManager);
        getCommand("reportadmin").setExecutor(reportAdminCommand);
        getCommand("reportadmin").setTabCompleter(reportAdminCommand);

        getLogger().info("ReportGlobal 插件已成功启动!");
    }

    private void initDatabase() {
        try {
            // 如果已存在数据库连接，先关闭
            if (databaseManager != null) {
                databaseManager.close();
            }
            
            // 创建新的数据库连接
            databaseManager = new DatabaseManager(this);
            
            // 注册事件监听器
            if (adminActionListener != null) {
                // 取消注册旧的监听器
                adminActionListener = null;
            }
            
            adminActionListener = new AdminActionListener(this, databaseManager);
            getServer().getPluginManager().registerEvents(adminActionListener, this);
        } catch (Exception e) {
            getLogger().severe("初始化数据库失败: " + e.getMessage());
        }
    }
    
    /**
     * 重新加载配置并重新初始化数据库连接
     */
    @Override
    public void reloadConfig() {
        super.reloadConfig();
        initDatabase();
        getLogger().info("配置已重新加载，数据库连接已更新");
    }

    @Override
    public void onDisable() {
        // 关闭数据库连接
        if (databaseManager != null) {
            databaseManager.close();
        }
        getLogger().info("ReportGlobal 插件已关闭!");
    }
}
