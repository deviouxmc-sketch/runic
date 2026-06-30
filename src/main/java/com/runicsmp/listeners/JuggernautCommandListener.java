package com.runicsmp.listeners;

import com.runicsmp.RunicSMP;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * /jugg add <player> — start the Juggernaut event
 * /jugg remove — end the Juggernaut event
 */
public class JuggernautCommandListener implements CommandExecutor, TabCompleter {

    private final RunicSMP plugin;

    public JuggernautCommandListener(RunicSMP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("runicsmp.admin") && !(sender instanceof Player p && p.isOp())) {
            sender.sendMessage("§cNo permission.");
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage("§cUsage: /jugg <add|remove> [player]");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "add" -> {
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /jugg add <player>");
                    return true;
                }
                if (plugin.getJuggernautManager().isActive()) {
                    sender.sendMessage("§cA Juggernaut event is already active! Use /jugg remove first.");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage("§cPlayer not found or not online.");
                    return true;
                }
                boolean started = plugin.getJuggernautManager().start(target);
                if (started) {
                    sender.sendMessage("§a✦ Juggernaut event started for §e" + target.getName() + "§a!");
                } else {
                    sender.sendMessage("§cFailed to start event.");
                }
            }
            case "remove" -> {
                boolean removed = plugin.getJuggernautManager().remove();
                if (removed) {
                    sender.sendMessage("§a✦ Juggernaut event ended.");
                } else {
                    sender.sendMessage("§cNo active Juggernaut event.");
                }
            }
            default -> sender.sendMessage("§cUsage: /jugg <add|remove> [player]");
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) return List.of("add", "remove");
        if (args.length == 2 && args[0].equalsIgnoreCase("add")) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        }
        return List.of();
    }
}
