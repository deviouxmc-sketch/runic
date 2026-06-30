package com.runicsmp.listeners;

import com.runicsmp.RunicSMP;
import com.runicsmp.data.PlayerRuneData;
import com.runicsmp.data.RuneType;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * /ability           → trigger primary ability (ability 1)
 * /ability secondary → trigger secondary rune ability
 * Shift+offhand      → trigger primary ability 2 (for multi-ability primaries)
 */
public class AbilityCommandListener implements CommandExecutor, TabCompleter {

    private final RunicSMP plugin;

    public AbilityCommandListener(RunicSMP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("§cPlayers only."); return true; }

        PlayerRuneData data = plugin.getRuneManager().getData(player);

        if (args.length > 0 && args[0].equalsIgnoreCase("secondary")) {
            // /ability secondary → secondary rune ability
            if (!data.hasSecondary()) {
                player.sendMessage("§cYou don't have a secondary rune equipped.");
                return true;
            }
            plugin.getSecondaryHandler().activateSecondary(player);
        } else {
            // /ability → primary ability 1
            if (!data.hasPrimary()) {
                player.sendMessage("§cYou don't have a primary rune equipped.");
                return true;
            }
            plugin.getAbilityHandler().activatePrimary(player, false);
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command c,
                                                 @NotNull String l, @NotNull String[] a) {
        if (a.length == 1) return List.of("secondary");
        return List.of();
    }
}
