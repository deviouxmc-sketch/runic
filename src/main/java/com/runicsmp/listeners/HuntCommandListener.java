package com.runicsmp.listeners;

import com.runicsmp.RunicSMP;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class HuntCommandListener implements CommandExecutor, TabCompleter {

    private final RunicSMP plugin;

    public HuntCommandListener(RunicSMP plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player) || !player.isOp()) {
            sender.sendMessage("§cOnly operators can use this.");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage("§cUsage: /hunt <start|stop>");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "start" -> {
                boolean started = plugin.getScavengerHuntManager().start();
                sender.sendMessage(started ? "§a✦ Scavenger Hunt started!" : "§cA hunt is already active.");
            }
            case "stop" -> {
                boolean stopped = plugin.getScavengerHuntManager().stop();
                sender.sendMessage(stopped ? "§a✦ Scavenger Hunt stopped." : "§cNo active hunt.");
            }
            case "progress" -> {
                // /hunt progress set <player> <item_name>
                if (args.length < 4 || !args[1].equalsIgnoreCase("set")) {
                    sender.sendMessage("§cUsage: /hunt progress set <player> <item_name>");
                    sender.sendMessage("§7Example: §e/hunt progress set Stalari Blaze_Rod");
                    return true;
                }
                if (!plugin.getScavengerHuntManager().isActive()) {
                    sender.sendMessage("§cNo active scavenger hunt.");
                    return true;
                }
                org.bukkit.entity.Player target = org.bukkit.Bukkit.getPlayer(args[2]);
                if (target == null) { sender.sendMessage("§cPlayer not found or not online."); return true; }

                String itemName = args[3].toUpperCase().replace("-", "_").replace(" ", "_");
                int idx = plugin.getScavengerHuntManager().getItemIndex(itemName);
                if (idx == -1) {
                    sender.sendMessage("§cItem §e" + args[3] + "§c not found in the hunt list.");
                    sender.sendMessage("§7Use the material name e.g. §eBlaze_Rod§7, §eSea_Lantern§7, §eAncient_Debris");
                    return true;
                }
                plugin.getScavengerHuntManager().setProgress(target, idx);
                sender.sendMessage("§a✦ Set §e" + target.getName() + "§a's progress to Item " + (idx+1) + ": §e" + plugin.getScavengerHuntManager().formatItemName(idx));
                target.sendMessage("§eYour scavenger hunt progress was set to Item " + (idx+1) + ": §f" + plugin.getScavengerHuntManager().formatItemName(idx));
            }
            default -> sender.sendMessage("§cUsage: /hunt <start|stop|progress>");
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command c,
                                                 @NotNull String l, @NotNull String[] a) {
        if (a.length == 1) return List.of("start", "stop");
        return List.of();
    }
}
