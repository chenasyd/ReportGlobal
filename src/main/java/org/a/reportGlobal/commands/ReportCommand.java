package org.a.reportGlobal.commands;

import org.a.reportGlobal.ReportGlobal;
import org.a.reportGlobal.database.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class ReportCommand implements CommandExecutor, TabCompleter {
    private final ReportGlobal plugin;
    private final DatabaseManager databaseManager;
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    public ReportCommand(ReportGlobal plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "此命令只能由玩家执行！");
            return true;
        }

        Player player = (Player) sender;

        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "用法: /report <玩家名> <原因>");
            return true;
        }

        // 检查被举报玩家是否存在
        String targetName = args[0];
        Player target = Bukkit.getPlayerExact(targetName);

        // 如果目标玩家不在线，我们仍然允许举报，但给出提示
        if (target == null) {
            player.sendMessage(ChatColor.YELLOW + "警告：玩家 " + targetName + " 当前不在线，但举报仍会被记录。");
        } else {
            targetName = target.getName(); // 使用正确的大小写

            // 检查是否举报自己
            if (target.equals(player)) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    plugin.getConfig().getString("messages.report-self", "&c你不能举报自己！")));
                return true;
            }

            // 检查目标是否有豁免权限
            if (target.hasPermission("reportglobal.exempt")) {
                player.sendMessage(ChatColor.RED + "此玩家不能被举报。");
                return true;
            }
        }

        // 检查冷却时间
        int cooldownTime = plugin.getConfig().getInt("report.cooldown", 300);
        if (cooldownTime > 0) {
            long lastReportTime = cooldowns.getOrDefault(player.getUniqueId(), 0L);
            long currentTime = System.currentTimeMillis() / 1000;
            long timeLeft = lastReportTime + cooldownTime - currentTime;

            if (timeLeft > 0) {
                String cooldownMsg = plugin.getConfig().getString("messages.report-cooldown", "&c你需要等待 %time% 秒才能再次举报。");
                cooldownMsg = cooldownMsg.replace("%time%", String.valueOf(timeLeft));
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', cooldownMsg));
                return true;
            }
        }

        // 构建举报原因
        StringBuilder reasonBuilder = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            reasonBuilder.append(args[i]).append(" ");
        }
        String reason = reasonBuilder.toString().trim();

        // 检查原因长度
        int minLength = plugin.getConfig().getInt("report.min-reason-length", 10);
        int maxLength = plugin.getConfig().getInt("report.max-reason-length", 200);

        if (reason.length() < minLength) {
            player.sendMessage(ChatColor.RED + "举报原因太短！至少需要 " + minLength + " 个字符。");
            return true;
        }

        if (reason.length() > maxLength) {
            player.sendMessage(ChatColor.RED + "举报原因太长！最多允许 " + maxLength + " 个字符。");
            return true;
        }

        // 记录举报
        databaseManager.logReport(player.getName(), targetName, reason);

        // 更新冷却时间
        if (cooldownTime > 0) {
            cooldowns.put(player.getUniqueId(), System.currentTimeMillis() / 1000);
        }

        // 发送确认消息给举报者
        String reportSentMsg = plugin.getConfig().getString("messages.report-sent", "&a你的举报已提交！管理员会尽快处理。");
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', reportSentMsg));

        // 通知管理员
        String reportReceivedMsg = plugin.getConfig().getString("messages.report-received",
                "&e[举报] &c%reporter% &e举报了 &c%player% &e原因: &c%reason%");
        reportReceivedMsg = reportReceivedMsg
                .replace("%reporter%", player.getName())
                .replace("%player%", targetName)
                .replace("%reason%", reason);

        String finalMsg = ChatColor.translateAlternateColorCodes('&', reportReceivedMsg);

        // 发送给所有有权限的管理员
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("reportglobal.admin")) {
                p.sendMessage(finalMsg);
            }
        }

        // 同时记录到控制台
        plugin.getLogger().info(ChatColor.stripColor(finalMsg));

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // 第一个参数是玩家名，提供在线玩家列表
            String partialName = args[0].toLowerCase();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(partialName) &&
                    !player.getName().equalsIgnoreCase(sender.getName()) && // 不包括自己
                    !player.hasPermission("reportglobal.exempt")) { // 不包括豁免的玩家
                    completions.add(player.getName());
                }
            }
        } else if (args.length == 2) {
            // 第二个参数是原因，提供预设的原因列表
            String partialReason = args[1].toLowerCase();
            List<String> reasons = plugin.getConfig().getStringList("report.reasons");

            for (String reason : reasons) {
                if (reason.toLowerCase().startsWith(partialReason)) {
                    completions.add(reason);
                }
            }
        }

        return completions;
    }
}
