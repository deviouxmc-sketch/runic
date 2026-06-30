package com.runicsmp.listeners;

import com.runicsmp.RunicSMP;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class BroadcastCommandListener implements CommandExecutor, TabCompleter {

    private final RunicSMP plugin;

    public BroadcastCommandListener(RunicSMP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player) || !player.isOp()) {
            sender.sendMessage("§cOnly operators can use this.");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage("§cUsage: /broadcast <message>");
            return true;
        }

        String message = String.join(" ", args);

        Component divider = Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                .color(NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true);

        Component header = Component.text("  📢  BROADCAST  📢  ")
                .color(NamedTextColor.YELLOW).decoration(TextDecoration.BOLD, true);

        Component msg = Component.text("  " + message)
                .color(NamedTextColor.WHITE).decoration(TextDecoration.BOLD, true);

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(Component.empty());
            p.sendMessage(divider);
            p.sendMessage(header);
            p.sendMessage(Component.empty());
            p.sendMessage(msg);
            p.sendMessage(Component.empty());
            p.sendMessage(divider);
            p.sendMessage(Component.empty());
            p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 2f);
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command c,
                                                 @NotNull String l, @NotNull String[] a) {
        return List.of("<message>");
    }
}
