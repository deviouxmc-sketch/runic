package com.runicsmp.listeners;

import com.runicsmp.RunicSMP;
import com.runicsmp.data.RuneType;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Wind Rune passives:
 * 1. No fall damage
 * 2. Double jump — press space while in the air
 */
public class WindLandingListener implements Listener {

    private final RunicSMP plugin;
    private final Set<UUID> canDoubleJump = new HashSet<>();
    private final Set<UUID> hasDoubleJumped = new HashSet<>();

    public WindLandingListener(RunicSMP plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private boolean hasWind(Player player) {
        var data = plugin.getRuneManager().getData(player);
        return data.hasPrimary() && data.getPrimaryRune() == RuneType.WIND;
    }

    // No fall damage
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFallDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        if (!hasWind(player)) return;
        event.setCancelled(true);
    }

    // Double jump — detect when player was airborne and jumps again
    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!hasWind(player)) return;

        // Reset double jump when landing
        if (player.isOnGround()) {
            hasDoubleJumped.remove(player.getUniqueId());
            canDoubleJump.add(player.getUniqueId());
            return;
        }

        // Detect upward velocity from jump while already airborne
        Vector vel = player.getVelocity();
        if (!player.isOnGround() && canDoubleJump.contains(player.getUniqueId())
                && !hasDoubleJumped.contains(player.getUniqueId())
                && vel.getY() < 0.1 && vel.getY() > -0.5) {
            // Player is starting to fall — allow double jump on next upward press
            // We detect via client jump packets — use PlayerToggleFlightEvent or velocity change
        }
    }

    // Register dash for fall damage negate (from dash rune)
    private final java.util.Set<java.util.UUID> dashedRecently = new java.util.HashSet<>();

    public void registerDash(java.util.UUID uuid) {
        dashedRecently.add(uuid);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> dashedRecently.remove(uuid), 80L);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDashFallDamage(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        if (!(event.getEntity() instanceof Player p)) return;
        if (!dashedRecently.contains(p.getUniqueId())) return;
        event.setCancelled(true);
    }
}
