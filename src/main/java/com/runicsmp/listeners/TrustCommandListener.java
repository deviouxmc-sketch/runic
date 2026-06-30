package com.runicsmp.listeners;

import com.runicsmp.RunicSMP;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.List;

public class TrustCommandListener implements CommandExecutor, TabCompleter {
    private final RunicSMP plugin;
    public TrustCommandListener(RunicSMP plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return true; }

        switch (label.toLowerCase()) {
            case "trust" -> {
                if (args.length < 1) { player.sendMessage("§cUsage: /trust <player>"); return true; }
                Player target = Bukkit.getPlayer(args[0]);
                if (target == null) { player.sendMessage("§cPlayer §e" + args[0] + "§c not found or not online."); return true; }
                if (target.equals(player)) { player.sendMessage("§cYou can't trust yourself."); return true; }
                plugin.getTrustManager().trust(player, target);
                player.sendMessage("§a✔ Trusted §e" + target.getName() + "§a — they are exempt from your Shadow, Lockdown, and other targeted effects.");
                target.sendMessage("§a" + player.getName() + " has trusted you. Their rune effects won't affect you.");
            }
            case "untrust" -> {
                if (args.length < 1) { player.sendMessage("§cUsage: /untrust <player>"); return true; }
                Player target = Bukkit.getPlayer(args[0]);
                if (target == null) { player.sendMessage("§cPlayer not found or not online."); return true; }
                plugin.getTrustManager().untrust(player, target);
                player.sendMessage("§eUntrusted §c" + target.getName() + "§e.");
                target.sendMessage("§e" + player.getName() + " has untrusted you.");
            }
            case "trustlist" -> {
                var trusted = plugin.getTrustManager().getTrusted(player.getUniqueId());
                if (trusted.isEmpty()) { player.sendMessage("§7You haven't trusted anyone. Use §e/trust <player>§7."); return true; }
                player.sendMessage("§6§lYour trusted players:");
                trusted.forEach(uuid -> {
                    String name = Bukkit.getOfflinePlayer(uuid).getName();
                    player.sendMessage("§7  - §e" + (name != null ? name : uuid.toString()));
                });
            }
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd,
                                                 @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        return List.of();
    }
}
