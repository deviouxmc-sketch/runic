package com.runicsmp.listeners;

import com.runicsmp.RunicSMP;
import com.runicsmp.data.RuneType;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * Frost rune passive:
 * - Immune to powder snow freeze damage
 * - Can walk on/through powder snow without sinking
 */
public class FrostPassiveListener implements Listener {

    private final RunicSMP plugin;

    public FrostPassiveListener(RunicSMP plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private boolean hasFrost(Player player) {
        var data = plugin.getRuneManager().getData(player);
        return data.hasSecondary() && data.getSecondaryRune() == RuneType.FROST;
    }

    // Cancel freeze damage from powder snow
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFreezeDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getCause() != EntityDamageEvent.DamageCause.FREEZE) return;
        if (!hasFrost(player)) return;
        event.setCancelled(true);
    }

    // Walk over powder snow + cancel freeze ticks
    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!hasFrost(player)) return;
        if (player.getFreezeTicks() > 0) player.setFreezeTicks(0);
    }
}
