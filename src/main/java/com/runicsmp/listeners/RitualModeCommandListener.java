package com.runicsmp.listeners;

import com.runicsmp.RunicSMP;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.List;

/**
 * /ritualmode — when enabled, ONLY ritual crafting works.
 * All other rune abilities are disabled until toggled off.
 */
public class RitualModeCommandListener implements CommandExecutor, TabCompleter {

    private final RunicSMP plugin;
    private boolean ritualModeActive = false;

    public RitualModeCommandListener(RunicSMP plugin) { this.plugin = plugin; }

    public boolean isRitualModeActive() { return ritualModeActive; }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("runicsmp.admin")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }

        ritualModeActive = !ritualModeActive;

        if (ritualModeActive) {
            Bukkit.broadcast(Component.text(
                    "§6§l⚗ RITUAL MODE ENABLED — Only crafting rituals are active. All rune abilities disabled!")
                    .color(NamedTextColor.GOLD));
        } else {
            Bukkit.broadcast(Component.text(
                    "§a§l⚗ RITUAL MODE DISABLED — Rune abilities are active again!")
                    .color(NamedTextColor.GREEN));
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command c,
                                                 @NotNull String l, @NotNull String[] a) {
        return List.of();
    }
}
