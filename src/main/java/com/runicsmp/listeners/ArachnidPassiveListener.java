package com.runicsmp.listeners;

import com.runicsmp.RunicSMP;
import com.runicsmp.data.RuneType;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * Arachnid Rune:
 * Passive – Web Walker: 50% speed through cobwebs (Weaving effect)
 * Ability – Web Prison: 3x3 webs around nearby enemies + Poison III 5s, webs last 5s
 */
public class ArachnidPassiveListener implements Listener {

    private final RunicSMP plugin;

    public ArachnidPassiveListener(RunicSMP plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private boolean hasArachnid(Player player) {
        var data = plugin.getRuneManager().getData(player);
        return data.hasPrimary() && data.getPrimaryRune() == RuneType.ARACHNID;
    }

    // Web Walker passive
    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!hasArachnid(player)) return;
        if (player.getLocation().getBlock().getType() != Material.COBWEB) return;
        if (!player.hasPotionEffect(PotionEffectType.WEAVING)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.WEAVING, 60, 0, false, false, false));
        }
    }

    // Web Prison ability — called from RuneAbilityHandler
    public void activateWebPrison(Player player) {
        int cd = 30;
        Location center = player.getLocation();
        World w = player.getWorld();

        for (org.bukkit.entity.Entity nearby : player.getNearbyEntities(6, 6, 6)) {
            if (!(nearby instanceof Player target)) continue;
            if (plugin.getTrustManager().isTrustedUUID(player.getUniqueId(), target.getUniqueId())) continue;

            target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 100, 2, false, true, true));

            List<Location> webLocs = new ArrayList<>();
            Location tLoc = target.getLocation();
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    Location wl = tLoc.clone().add(x, 0, z);
                    if (wl.getBlock().getType() == Material.AIR) {
                        wl.getBlock().setType(Material.COBWEB);
                        webLocs.add(wl.clone());
                    }
                }
            }

            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                for (Location wl : webLocs)
                    if (wl.getBlock().getType() == Material.COBWEB)
                        wl.getBlock().setType(Material.AIR);
            }, 100L);
        }

        w.spawnParticle(Particle.DUST, center.clone().add(0, 1, 0),
                25, 1.2, 0.5, 1.2, new Particle.DustOptions(Color.fromRGB(180, 180, 180), 2f));
        w.playSound(center, Sound.BLOCK_COBWEB_PLACE, 1f, 0.7f);
        w.playSound(center, Sound.ENTITY_SPIDER_AMBIENT, 1f, 0.8f);
        player.sendMessage("§7⬡ Web Prison!");
    }
}
