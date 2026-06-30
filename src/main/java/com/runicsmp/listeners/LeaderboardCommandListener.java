package com.runicsmp.listeners;

import com.runicsmp.RunicSMP;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * /leaderboard — shows kill leaderboard with cool icons for top 3.
 */
public class LeaderboardCommandListener implements CommandExecutor, TabCompleter {

    private final RunicSMP plugin;

    public LeaderboardCommandListener(RunicSMP plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        List<Map.Entry<UUID, Integer>> top = plugin.getKillTracker().getTopKillers(10);

        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("  ⚔ Kill Leaderboard — Top 10 ⚔")
                .color(NamedTextColor.GOLD)
                .decoration(TextDecoration.BOLD, true));
        sender.sendMessage(Component.text("  ─────────────────────────")
                .color(NamedTextColor.DARK_GRAY));

        if (top.isEmpty()) {
            sender.sendMessage(Component.text("  No kills recorded yet.")
                    .color(NamedTextColor.GRAY));
        } else {
            for (int i = 0; i < top.size(); i++) {
                Map.Entry<UUID, Integer> entry = top.get(i);
                String name = plugin.getKillTracker().getName(entry.getKey());
                int killCount = entry.getValue();
                int rank = i + 1;

                Component line = buildLine(rank, name, killCount, sender);
                sender.sendMessage(line);
            }
        }

        sender.sendMessage(Component.text("  ─────────────────────────")
                .color(NamedTextColor.DARK_GRAY));

        // Show caller's own rank if they're a player
        if (sender instanceof Player player) {
            int myKills = plugin.getKillTracker().getKills(player.getUniqueId());
            if (myKills > 0) {
                // Find rank
                int myRank = 1;
                for (Map.Entry<UUID, Integer> e : top) {
                    if (e.getValue() > myKills) myRank++;
                }
                sender.sendMessage(Component.text("  Your rank: §e#" + myRank + " §7— §e" + myKills + " kills")
                        .color(NamedTextColor.GRAY));
            }
        }
        sender.sendMessage(Component.empty());
        return true;
    }

    private Component buildLine(int rank, String name, int kills, CommandSender sender) {
        String icon;
        NamedTextColor rankColor;
        NamedTextColor nameColor;

        switch (rank) {
            case 1 -> { icon = "☀ "; rankColor = NamedTextColor.GOLD; nameColor = NamedTextColor.YELLOW; }
            case 2 -> { icon = "✦ "; rankColor = NamedTextColor.GRAY; nameColor = NamedTextColor.WHITE; }
            case 3 -> { icon = "✧ "; rankColor = NamedTextColor.GOLD; nameColor = NamedTextColor.GOLD; }
            default -> { icon = "  "; rankColor = NamedTextColor.DARK_GRAY; nameColor = NamedTextColor.GRAY; }
        }

        boolean isMe = sender instanceof Player p && p.getName().equals(name);
        boolean isOnline = org.bukkit.Bukkit.getPlayerExact(name) != null;
        String onlineIndicator = isOnline ? "§a● " : "§8● ";

        return Component.text("  " + icon)
                .color(rankColor)
                .append(Component.text("#" + rank + " ")
                        .color(rankColor)
                        .decoration(TextDecoration.BOLD, rank <= 3))
                .append(Component.text(isOnline ? "● " : "◌ ")
                        .color(isOnline ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY))
                .append(Component.text(name)
                        .color(isMe ? NamedTextColor.AQUA : nameColor)
                        .decoration(TextDecoration.BOLD, rank == 1))
                .append(Component.text(" — ")
                        .color(NamedTextColor.DARK_GRAY))
                .append(Component.text(kills + " kill" + (kills != 1 ? "s" : ""))
                        .color(rank <= 3 ? NamedTextColor.RED : NamedTextColor.DARK_RED));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command c,
                                                 @NotNull String l, @NotNull String[] a) {
        return List.of();
    }
}
