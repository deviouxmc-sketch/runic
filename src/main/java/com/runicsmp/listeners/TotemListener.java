package com.runicsmp.listeners;

import com.runicsmp.RunicSMP;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityResurrectEvent;

/**
 * When a totem pops, reapply all rune passive effects after a short delay
 * so the player doesn't lose their rune buffs.
 */
public class TotemListener implements Listener {

    private final RunicSMP plugin;

    public TotemListener(RunicSMP plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTotemPop(EntityResurrectEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        // Delay 1 tick so the totem clears effects first, then we reapply
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            var data = plugin.getRuneManager().getData(player);

            // Reapply primary passive
            if (data.hasPrimary()) {
                plugin.getRuneManager().applyPassiveEffects(player, data.getPrimaryRune());
            }
            // Reapply secondary passive
            if (data.hasSecondary()) {
                plugin.getRuneManager().applyPassiveEffects(player, data.getSecondaryRune());
            }
        }, 1L);
    }
}
