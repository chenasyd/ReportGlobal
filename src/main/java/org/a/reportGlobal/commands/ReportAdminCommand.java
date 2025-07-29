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

import java.text.SimpleDateFormat;
import java.util.*;

public class ReportAdminCommand implements CommandExecutor, TabCompleter {
    private final ReportGlobal plugin;
    private final DatabaseManager databaseManager;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public ReportAdminCommand(ReportGlobal plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("reportglobal.admin")) {
            sender.sendMessage(ChatColor.RED + "你没有权限使用此命令！");
            return true;
        }

        if (args.length == 0) {
            sendHelpMessage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "list":
                listReports(sender);
                break;
            case "view":
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "用法: /reportadmin view <举报ID>");
                    return true;
                }
                viewReport(sender, args[1]);
                break;
            case "handle":
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "用法: /reportadmin handle <举报ID> <RESOLVED|REJECTED> [处理备注]");
                    return true;
                }
                handleReport(sender, args[1], args[2], args.length > 3 ? String.join(" ", Arrays.copyOfRange(args, 3, args.length)) : "");
                break;
            case "history":
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "用法: /reportadmin history <玩家名>");
                    return true;
                }
                viewPlayerHistory(sender, args[1]);
                break;
            case "help":
            default:
                sendHelpMessage(sender);
                break;
        }

        return true;
    }

    private void sendHelpMessage(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "===== 举报管理命令 =====");
        sender.sendMessage(ChatColor.YELLOW + "/reportadmin list" + ChatColor.WHITE + " - 列出待处理的举报");
        sender.sendMessage(ChatColor.YELLOW + "/reportadmin view <ID>" + ChatColor.WHITE + " - 查看举报详情");
        sender.sendMessage(ChatColor.YELLOW + "/reportadmin handle <ID> <RESOLVED|REJECTED> [备注]" + ChatColor.WHITE + " - 处理举报");
        sender.sendMessage(ChatColor.YELLOW + "/reportadmin history <玩家名>" + ChatColor.WHITE + " - 查看玩家的举报历史");
    }

    private void listReports(CommandSender sender) {
        List<Map<String, Object>> reports = databaseManager.getPendingReports();

        if (reports.isEmpty()) {
            sender.sendMessage(ChatColor.GREEN + "当前没有待处理的举报。");
            return;
        }

        sender.sendMessage(ChatColor.GOLD + "===== 待处理举报 (" + reports.size() + ") =====");

        for (Map<String, Object> report : reports) {
            int id = (int) report.get("id");
            String reporter = (String) report.get("reporter_name");
            String target = (String) report.get("target_player");
            Date reportTime = (Date) report.get("report_time");
            String server = (String) report.get("server_name");

            sender.sendMessage(ChatColor.YELLOW + "#" + id + " " +
                    ChatColor.RED + reporter + ChatColor.YELLOW + " 举报 " +
                    ChatColor.RED + target + ChatColor.YELLOW + " 于 " +
                    ChatColor.WHITE + dateFormat.format(reportTime) +
                    ChatColor.GRAY + " [" + server + "]");
        }

        sender.sendMessage(ChatColor.YELLOW + "使用 " + ChatColor.WHITE + "/reportadmin view <ID>" + ChatColor.YELLOW + " 查看详情");
    }

    private void viewReport(CommandSender sender, String idStr) {
        int id;
        try {
            id = Integer.parseInt(idStr);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "无效的举报ID！");
            return;
        }

        // 获取举报详情
        List<Map<String, Object>> reports = databaseManager.getPendingReports();
        Map<String, Object> report = null;

        for (Map<String, Object> r : reports) {
            if ((int) r.get("id") == id) {
                report = r;
                break;
            }
        }

        if (report == null) {
            sender.sendMessage(ChatColor.RED + "未找到ID为 " + id + " 的待处理举报！");
            return;
        }

        // 显示详情
        sender.sendMessage(ChatColor.GOLD + "===== 举报详情 #" + id + " =====");
        sender.sendMessage(ChatColor.YELLOW + "举报者: " + ChatColor.WHITE + report.get("reporter_name"));
        sender.sendMessage(ChatColor.YELLOW + "被举报者: " + ChatColor.WHITE + report.get("target_player"));
        sender.sendMessage(ChatColor.YELLOW + "服务器: " + ChatColor.WHITE + report.get("server_name"));
        sender.sendMessage(ChatColor.YELLOW + "时间: " + ChatColor.WHITE + dateFormat.format(report.get("report_time")));
        sender.sendMessage(ChatColor.YELLOW + "原因: " + ChatColor.WHITE + report.get("reason"));
        sender.sendMessage("");
        sender.sendMessage(ChatColor.YELLOW + "使用 " + ChatColor.WHITE + "/reportadmin handle " + id + " <RESOLVED|REJECTED> [备注]" + ChatColor.YELLOW + " 处理此举报");
    }

    private void handleReport(CommandSender sender, String idStr, String statusStr, String comment) {
        int id;
        try {
            id = Integer.parseInt(idStr);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "无效的举报ID！");
            return;
        }

        String status = statusStr.toUpperCase();
        if (!status.equals("RESOLVED") && !status.equals("REJECTED")) {
            sender.sendMessage(ChatColor.RED + "无效的状态！使用 RESOLVED 或 REJECTED");
            return;
        }

        // 更新举报状态
        String handlerName = sender instanceof Player ? sender.getName() : "CONSOLE";
        databaseManager.updateReportStatus(id, status, handlerName, comment);

        sender.sendMessage(ChatColor.GREEN + "举报 #" + id + " 已被标记为 " + status);

        // 通知其他管理员
        String message = ChatColor.GREEN + "管理员 " + ChatColor.YELLOW + handlerName +
                ChatColor.GREEN + " 将举报 #" + id + " 标记为 " + ChatColor.YELLOW + status;

        if (!comment.isEmpty()) {
            message += ChatColor.GREEN + " - 备注: " + ChatColor.WHITE + comment;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("reportglobal.admin") && !player.equals(sender)) {
                player.sendMessage(message);
            }
        }

        // 记录到控制台
        plugin.getLogger().info(ChatColor.stripColor(message));
    }

    private void viewPlayerHistory(CommandSender sender, String playerName) {
        // 获取玩家作为举报者的历史
        List<Map<String, Object>> asReporter = databaseManager.getPlayerReports(playerName, true);

        // 获取玩家作为被举报者的历史
        List<Map<String, Object>> asTarget = databaseManager.getPlayerReports(playerName, false);

        sender.sendMessage(ChatColor.GOLD + "===== 玩家 " + playerName + " 的举报历史 =====");

        if (asReporter.isEmpty() && asTarget.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "没有找到与此玩家相关的举报记录。");
            return;
        }

        if (!asReporter.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "作为举报者 (" + asReporter.size() + "):");
            for (Map<String, Object> report : asReporter) {
                int id = (int) report.get("id");
                String target = (String) report.get("target_player");
                Date reportTime = (Date) report.get("report_time");
                String status = (String) report.get("status");

                sender.sendMessage(ChatColor.YELLOW + "#" + id + " " +
                        ChatColor.WHITE + "举报 " + ChatColor.RED + target +
                        ChatColor.WHITE + " 于 " + dateFormat.format(reportTime) +
                        ChatColor.GRAY + " [" + status + "]");
            }
        }

        if (!asTarget.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "作为被举报者 (" + asTarget.size() + "):");
            for (Map<String, Object> report : asTarget) {
                int id = (int) report.get("id");
                String reporter = (String) report.get("reporter_name");
                Date reportTime = (Date) report.get("report_time");
                String status = (String) report.get("status");

                sender.sendMessage(ChatColor.YELLOW + "#" + id + " " +
                        ChatColor.RED + reporter + ChatColor.WHITE + " 举报于 " +
                        dateFormat.format(reportTime) +
                        ChatColor.GRAY + " [" + status + "]");
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subCommands = Arrays.asList("list", "view", "handle", "history", "help");
            String partialCommand = args[0].toLowerCase();

            for (String subCommand : subCommands) {
                if (subCommand.startsWith(partialCommand)) {
                    completions.add(subCommand);
                }
            }
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("history")) {
                String partialName = args[1].toLowerCase();
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getName().toLowerCase().startsWith(partialName)) {
                        completions.add(player.getName());
                    }
                }
            } else if (args[0].equalsIgnoreCase("handle")) {
                // 这里可以添加举报ID的补全，但通常ID是数字，不太适合补全
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("handle")) {
            List<String> statuses = Arrays.asList("RESOLVED", "REJECTED");
            String partialStatus = args[2].toUpperCase();

            for (String status : statuses) {
                if (status.startsWith(partialStatus)) {
                    completions.add(status);
                }
            }
        }

        return completions;
    }
}
