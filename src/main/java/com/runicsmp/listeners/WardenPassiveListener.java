package com.runicsmp.listeners;

import com.runicsmp.RunicSMP;
import com.runicsmp.data.RuneType;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Warden Rune passive:
 * Every 10 consecutive hits on any target → apply Warden-style Darkness + Blindness for 2 seconds.
 * 30 second cooldown on the passive trigger.
 */
public class WardenPassiveListener implements Listener {

    private final RunicSMP plugin;
    private final Map<UUID, Integer> hitCount = new HashMap<>();
    private final Map<UUID, Long> passiveCooldown = new HashMap<>();

    public WardenPassiveListener(RunicSMP plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private boolean hasWarden(Player player) {
        var data = plugin.getRuneManager().getData(player);
        return data.hasPrimary() && data.getPrimaryRune() == RuneType.WARDEN;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof LivingEntity target)) return;
        if (!hasWarden(attacker)) return;

        // Skip trusted
        if (target instanceof Player tp &&
                plugin.getTrustManager().isTrustedUUID(attacker.getUniqueId(), tp.getUniqueId())) return;

        UUID aid = attacker.getUniqueId();
        int hits = hitCount.getOrDefault(aid, 0) + 1;
        hitCount.put(aid, hits);

        if (hits >= 10) {
            hitCount.put(aid, 0);

            // Check 30s cooldown
            long now = System.currentTimeMillis();
            if (passiveCooldown.getOrDefault(aid, 0L) > now) return;
            passiveCooldown.put(aid, now + 30000L);

            // Apply Warden blindness effect to target
            target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0, false, false, true)); // 2s
            target.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 40, 0, false, false, true)); // 2s
            target.getWorld().playSound(target.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 0.5f, 0.5f);

            attacker.sendMessage("§2⚡ Warden Sense triggered!");
        }
    }
}
