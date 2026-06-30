package com.runicsmp.listeners;

import com.runicsmp.RunicSMP;
import com.runicsmp.data.RuneType;
import org.bukkit.*;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class LightningPassiveListener implements Listener {

    private final RunicSMP plugin;
    private final Map<UUID, Integer> hitCount = new HashMap<>();
    private final Map<UUID, UUID> lastTarget = new HashMap<>();

    public LightningPassiveListener(RunicSMP plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private boolean hasLightning(Player player) {
        var data = plugin.getRuneManager().getData(player);
        return data.hasPrimary() && data.getPrimaryRune() == RuneType.LIGHTNING;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof LivingEntity target)) return;
        if (!hasLightning(attacker)) return;
        if (target instanceof Player tp &&
                plugin.getTrustManager().isTrustedUUID(attacker.getUniqueId(), tp.getUniqueId())) return;

        UUID aid = attacker.getUniqueId();
        UUID tid = target.getUniqueId();
        if (!tid.equals(lastTarget.get(aid))) {
            hitCount.put(aid, 0);
            lastTarget.put(aid, tid);
        }

        int hits = hitCount.getOrDefault(aid, 0) + 1;
        if (hits >= 7) {
            hitCount.put(aid, 0);
            conductorStrike(attacker, target);
        } else {
            hitCount.put(aid, hits);
        }
    }

    private void conductorStrike(Player attacker, LivingEntity target) {
        Location loc = target.getLocation();
        World w = target.getWorld();

        // Conductor card: dual ELECTRIC_SPARK rings — one at ground, one above
        spawnDualRings(w, loc, 1.5, 8);

        w.strikeLightningEffect(loc);
        w.spawnParticle(Particle.FLASH, loc, 1, 0, 0, 0, 0);
        w.spawnParticle(Particle.WITCH, loc.clone().add(0,1,0), 8, 0.3, 0.4, 0.3, 0.1);
        w.playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.5f, 1.3f);

        target.damage(4.0, attacker); // 2 hearts
    }

    // Conductor card: ELECTRIC_SPARK rings rising upward forming a cylinder
    public void spawnDualRings(World w, Location center, double radius, int points) {
        // Two ground rings (r and r+1) - Conductor card exact
        for (int i = 0; i < 120; i++) {
            double a = Math.PI * 2 / 120 * i;
            w.spawnParticle(Particle.ELECTRIC_SPARK, center.clone().add(Math.cos(a)*radius, 0.1, Math.sin(a)*radius), 1, 0, 0, 0, 0);
            w.spawnParticle(Particle.ELECTRIC_SPARK, center.clone().add(Math.cos(a)*(radius+1), 0.1, Math.sin(a)*(radius+1)), 1, 0, 0, 0, 0);
        }
        // Cylinder: rings rise upward creating a circular cage
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (t++ > 10) { cancel(); return; }
                double h = t * 0.2;
                for (int i = 0; i < 60; i++) {
                    double a = Math.PI * 2 / 60 * i;
                    w.spawnParticle(Particle.ELECTRIC_SPARK, center.clone().add(Math.cos(a)*radius, h, Math.sin(a)*radius), 1, 0, 0, 0, 0);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
