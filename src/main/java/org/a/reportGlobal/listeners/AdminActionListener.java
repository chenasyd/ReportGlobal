package org.a.reportGlobal.listeners;

import org.a.reportGlobal.ReportGlobal;
import org.a.reportGlobal.database.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.server.ServerCommandEvent;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AdminActionListener implements Listener {
    private final ReportGlobal plugin;
    private final DatabaseManager databaseManager;
    // 匹配ban命令的正则表达式
    private final Pattern banPattern = Pattern.compile("^/?ban\\s+(\\S+)(?:\\s+.*)?$", Pattern.CASE_INSENSITIVE);
    private final Pattern banIpPattern = Pattern.compile("^/?ban-ip\\s+(\\S+)(?:\\s+.*)?$", Pattern.CASE_INSENSITIVE);

    public AdminActionListener(ReportGlobal plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    /**
     * 监听玩家执行的命令，捕获ban命令
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (event.isCancelled()) {
            return;
        }

        String command = event.getMessage();
        Player admin = event.getPlayer();
        
        // 检查是否是ban命令
        Matcher banMatcher = banPattern.matcher(command);
        Matcher banIpMatcher = banIpPattern.matcher(command);
        
        if (banMatcher.matches()) {
            String targetPlayer = banMatcher.group(1);
            handleBanAction(admin.getName(), targetPlayer);
        } else if (banIpMatcher.matches()) {
            String targetIp = banIpMatcher.group(1);
            handleBanAction(admin.getName(), targetIp + "(IP)");
        }
    }
    
    /**
     * 监听控制台执行的命令，捕获ban命令
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onServerCommand(ServerCommandEvent event) {
        String command = event.getCommand();
        
        // 检查是否是ban命令
        Matcher banMatcher = banPattern.matcher(command);
        Matcher banIpMatcher = banIpPattern.matcher(command);
        
        if (banMatcher.matches()) {
            String targetPlayer = banMatcher.group(1);
            handleBanAction("控制台", targetPlayer);
        } else if (banIpMatcher.matches()) {
            String targetIp = banIpMatcher.group(1);
            handleBanAction("控制台", targetIp + "(IP)");
        }
    }

    /**
     * 处理封禁操作
     */
    private void handleBanAction(String adminName, String targetPlayer) {
        // 记录到数据库
        databaseManager.logAction("BAN", adminName, targetPlayer);

        // 获取配置的消息格式
        String message = plugin.getConfig().getString("messages.ban", "&c管理员 &e%admin% &c封禁了玩家 &e%player%");
        message = ChatColor.translateAlternateColorCodes('&', message)
                .replace("%admin%", adminName)
                .replace("%player%", targetPlayer);

        // 全服广播
        Bukkit.broadcastMessage(message);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerKick(PlayerKickEvent event) {
        // 如果事件被取消，不进行处理
        if (event.isCancelled()) {
            return;
        }

        // 默认为控制台
        String adminName = "控制台";
        
        // 更安全的方式获取踢出者，避免使用可能在不同版本中行为不同的API
        try {
            if (event.getPlayer().getLastDamageCause() != null && 
                event.getPlayer().getLastDamageCause().getEntity() != null) {
                if (event.getPlayer().getLastDamageCause().getEntity() instanceof Player) {
                    Player damager = (Player) event.getPlayer().getLastDamageCause().getEntity();
                    adminName = damager.getName();
                }
            }
        } catch (Exception e) {
            // 如果出现任何异常，忽略并使用默认值
            plugin.getLogger().warning("获取踢出者信息时出错: " + e.getMessage());
        }

        String targetPlayer = event.getPlayer().getName();

        // 记录到数据库
        databaseManager.logAction("KICK", adminName, targetPlayer);

        // 获取配置的消息格式
        String message = plugin.getConfig().getString("messages.kick", "&c管理员 &e%admin% &c踢出了玩家 &e%player%");
        message = ChatColor.translateAlternateColorCodes('&', message)
                .replace("%admin%", adminName)
                .replace("%player%", targetPlayer);

        // 全服广播
        Bukkit.broadcastMessage(message);
    }
}
