package com.runicsmp.managers;

import com.runicsmp.RunicSMP;
import com.runicsmp.data.PlayerRuneData;
import com.runicsmp.data.RuneType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Updates the tab list to show each player's equipped primary rune next to their name.
 */
public class TabListManager implements Listener {

    private final RunicSMP plugin;

    public TabListManager(RunicSMP plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        // Update tab list every 2 seconds for all players
        new BukkitRunnable() {
            @Override public void run() {
                for (Player p : plugin.getServer().getOnlinePlayers()) updateTab(p);
            }
        }.runTaskTimer(plugin, 20L, 40L);
    }

    public void updateTab(Player player) {
        PlayerRuneData data = plugin.getRuneManager().getData(player);
        Component displayName;

        if (data.hasPrimary()) {
            RuneType rune = data.getPrimaryRune();
            displayName = Component.text(player.getName())
                    .color(NamedTextColor.WHITE)
                    .append(Component.text(" ")
                    .append(Component.text("[" + rune.getDisplayName() + "]")
                            .color(NamedTextColor.LIGHT_PURPLE)
                            .decoration(TextDecoration.ITALIC, false)));
        } else {
            displayName = Component.text(player.getName()).color(NamedTextColor.WHITE);
        }

        player.playerListName(displayName);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () ->
                updateTab(event.getPlayer()), 5L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Reset to default on quit
        event.getPlayer().playerListName(null);
    }
}
