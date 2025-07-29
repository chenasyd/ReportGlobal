package org.a.reportGlobal.commands;

import org.a.reportGlobal.ReportGlobal;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class ReportGlobalCommand implements CommandExecutor {
    private final ReportGlobal plugin;

    public ReportGlobalCommand(ReportGlobal plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("reportglobal.admin")) {
            sender.sendMessage(ChatColor.RED + "你没有权限使用此命令！");
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            // 重新加载配置文件
            plugin.reloadConfig();
            sender.sendMessage(ChatColor.GREEN + "ReportGlobal配置已重新加载！");
            return true;
        }

        // 显示帮助信息
        sender.sendMessage(ChatColor.YELLOW + "ReportGlobal 命令帮助：");
        sender.sendMessage(ChatColor.YELLOW + "/reportglobal reload " + ChatColor.WHITE + "- 重新加载配置文件");
        return true;
    }
}
