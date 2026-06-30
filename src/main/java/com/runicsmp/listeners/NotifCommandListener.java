package com.runicsmp.listeners;

import com.runicsmp.RunicSMP;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.List;

/**
 * /runenotif — toggle rune ability chat messages on/off
 */
public class NotifCommandListener implements CommandExecutor, TabCompleter {
    private final RunicSMP plugin;
    public NotifCommandListener(RunicSMP plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return true; }
        plugin.getNotifManager().toggle(player.getUniqueId());
        boolean muted = plugin.getNotifManager().isMuted(player.getUniqueId());
        player.sendMessage(muted
                ? "§7Rune ability messages §cOFF§7. Use §e/runenotif§7 to re-enable."
                : "§7Rune ability messages §aON§7.");
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command c,
                                                 @NotNull String l, @NotNull String[] a) {
        return List.of();
    }
}
