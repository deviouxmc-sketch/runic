package com.runicsmp.listeners;

import com.runicsmp.RunicSMP;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

/**
 * Wind Spear — Speed Rune ability.
 * Right-click to throw: launches a wind charge that explodes on impact,
 * knocking back nearby players and launching the thrower.
 */
public class WindSpearListener implements Listener {

    private final RunicSMP plugin;
    private final NamespacedKey spearKey = new NamespacedKey("runicsmp", "wind_spear");

    public WindSpearListener(RunicSMP plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private boolean isWindSpear(ItemStack item) {
        if (item == null) return false;
        var meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(spearKey, PersistentDataType.BOOLEAN);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onThrow(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!isWindSpear(held)) return;

        event.setCancelled(true);

        // Consume the spear
        held.setAmount(held.getAmount() - 1);

        // Launch a wind charge projectile
        WindCharge wc = player.launchProjectile(WindCharge.class);
        wc.setVelocity(player.getEyeLocation().getDirection().normalize().multiply(2.0));
        wc.getPersistentDataContainer().set(spearKey, PersistentDataType.BOOLEAN, true);

        // Trail particles
        player.getWorld().spawnParticle(Particle.CLOUD, player.getEyeLocation(), 8, 0.2, 0.2, 0.2, 0.05);
        player.getWorld().spawnParticle(Particle.END_ROD, player.getEyeLocation(), 5, 0.2, 0.2, 0.2, 0.08);
        player.playSound(player.getLocation(), Sound.ENTITY_WIND_CHARGE_WIND_BURST, 1f, 1.2f);
    }

    @EventHandler
    public void onHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof WindCharge wc)) return;
        if (!wc.getPersistentDataContainer().has(spearKey, PersistentDataType.BOOLEAN)) return;
        if (!(wc.getShooter() instanceof Player shooter)) return;

        Location hitLoc = wc.getLocation();
        World w = hitLoc.getWorld();

        // Explosion effect
        w.spawnParticle(Particle.CLOUD, hitLoc, 20, 0.5, 0.5, 0.5, 0.1);
        w.spawnParticle(Particle.END_ROD, hitLoc, 15, 0.4, 0.4, 0.4, 0.15);
        w.spawnParticle(Particle.SMALL_GUST, hitLoc, 10, 0.3, 0.3, 0.3, 0.05);
        w.playSound(hitLoc, Sound.ENTITY_WIND_CHARGE_WIND_BURST, 1f, 0.7f);
        w.playSound(hitLoc, Sound.ENTITY_BREEZE_SHOOT, 0.8f, 0.9f);

        // Knockback nearby entities
        for (Entity nearby : w.getNearbyEntities(hitLoc, 4, 4, 4)) {
            if (!(nearby instanceof LivingEntity le)) continue;
            if (nearby.equals(shooter)) continue;
            Vector dir = nearby.getLocation().subtract(hitLoc).toVector().normalize();
            dir.setY(0.5);
            le.setVelocity(dir.multiply(2.5));
        }

        // Launch shooter toward impact direction (like a spear thrust)
        Vector boost = hitLoc.subtract(shooter.getLocation()).toVector().normalize();
        boost.setY(Math.max(boost.getY(), 0.3));
        shooter.setVelocity(boost.multiply(1.5));
    }
}
