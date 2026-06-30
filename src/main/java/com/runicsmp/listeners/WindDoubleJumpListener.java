package com.runicsmp.listeners;

import com.runicsmp.RunicSMP;
import com.runicsmp.data.RuneType;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Wind Rune double jump — exact Feather card logic.
 * Uses PlayerToggleFlightEvent (allowFlight trick) to detect double jump.
 * On double jump: GUST_EMITTER_SMALL x1 at location, velocity (0,1.8,0), ITEM_MACE_SMASH_AIR sound.
 * Trail: CLOUD x2 at player location every 2 ticks until on ground (dc.java exact).
 */
public class WindDoubleJumpListener implements Listener {

    private final RunicSMP plugin;
    private final Set<UUID> hasJumped = new HashSet<>();

    public WindDoubleJumpListener(RunicSMP plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        // Every tick: allow flight for airborne wind players so double-jump fires
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : plugin.getServer().getOnlinePlayers()) {
                if (!hasWind(p)) continue;
                if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) continue;
                if (p.isOnGround()) {
                    hasJumped.remove(p.getUniqueId());
                    p.setAllowFlight(true);
                } else if (p.isFlying()) {
                    p.setFlying(false);
                }
            }
        }, 0L, 1L);
    }

    private boolean hasWind(Player player) {
        var data = plugin.getRuneManager().getData(player);
        return data.hasPrimary() && data.getPrimaryRune() == RuneType.WIND;
    }

    // Reset allowFlight when wind rune is removed
    public void onUnequip(Player player) {
        if (player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR) {
            player.setAllowFlight(false);
            player.setFlying(false);
        }
        hasJumped.remove(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDoubleJump(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (!hasWind(player)) return;
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;
        if (hasJumped.contains(player.getUniqueId())) return;

        event.setCancelled(true);
        player.setFlying(false);
        player.setAllowFlight(false);
        hasJumped.add(player.getUniqueId());

        Location loc = player.getLocation();
        World w = player.getWorld();

        // Feather card exact: GUST_EMITTER_SMALL x1, velocity (0, 1.8, 0), ITEM_MACE_SMASH_AIR
        w.spawnParticle(Particle.GUST_EMITTER_SMALL, loc, 1, 0, 0, 0, 0);
        player.setVelocity(new Vector(0, 1.8, 0));
        w.playSound(loc, Sound.ITEM_MACE_SMASH_AIR, 1f, 1f);

        // dc.java trail: CLOUD x2 at player location every 2 ticks, cancel when on ground
        new BukkitRunnable() {
            @Override public void run() {
                if (player.isOnGround() || !player.isOnline()) { cancel(); return; }
                player.spawnParticle(Particle.CLOUD, player.getLocation(), 2, 0, 0, 0, 0);
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }
}
