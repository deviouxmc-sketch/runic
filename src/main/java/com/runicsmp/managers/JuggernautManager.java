package com.runicsmp.managers;

import com.runicsmp.RunicSMP;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

/**
 * Juggernaut event — a designated player gets buffed, glowing, and their location
 * is broadcast every 5 minutes for 30 minutes total.
 */
public class JuggernautManager {

    private final RunicSMP plugin;
    private UUID juggernautId;
    private BossBar bossBar;
    private BukkitTask mainTask;
    private BukkitTask locationTask;

    public JuggernautManager(RunicSMP plugin) {
        this.plugin = plugin;
    }

    public boolean isActive() {
        return juggernautId != null;
    }

    public UUID getJuggernautId() {
        return juggernautId;
    }

    // ── Start the event ──────────────────────────────────────────────────────

    public boolean start(Player target) {
        if (isActive()) return false;

        juggernautId = target.getUniqueId();

        // Apply buffs
        target.getAttribute(Attribute.MAX_HEALTH).setBaseValue(40.0); // 20 hearts
        target.setHealth(40.0);
        target.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 1, false, true, true)); // Resistance II
        target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, Integer.MAX_VALUE, 0, false, false, true));

        // Boss bar
        bossBar = BossBar.bossBar(
                buildBossBarText(target),
                1.0f,
                BossBar.Color.RED,
                BossBar.Overlay.NOTCHED_10
        );
        for (Player p : Bukkit.getOnlinePlayers()) p.showBossBar(bossBar);

        // Initial broadcast
        broadcastLocation(target);

        // Location broadcast every 5 minutes (6000 ticks)
        locationTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Player p = Bukkit.getPlayer(juggernautId);
            if (p == null || !p.isOnline()) return;
            broadcastLocation(p);
        }, 6000L, 6000L);

        // End after 30 minutes (36000 ticks)
        mainTask = Bukkit.getScheduler().runTaskLater(plugin, this::end, 36000L);

        Component announce = Component.text("")
                .append(Component.text("⚔ JUGGERNAUT EVENT ", NamedTextColor.DARK_RED, TextDecoration.BOLD))
                .append(Component.text("» ", NamedTextColor.GRAY))
                .append(Component.text(target.getName(), NamedTextColor.RED, TextDecoration.BOLD))
                .append(Component.text(" has become the Juggernaut! Hunt them down!", NamedTextColor.GRAY));
        Bukkit.broadcast(announce);

        return true;
    }

    // ── Remove / end the event ───────────────────────────────────────────────

    public boolean remove() {
        if (!isActive()) return false;
        end();
        return true;
    }

    private void end() {
        Player target = Bukkit.getPlayer(juggernautId);
        if (target != null) {
            target.removePotionEffect(PotionEffectType.RESISTANCE);
            target.removePotionEffect(PotionEffectType.GLOWING);
            target.getAttribute(Attribute.MAX_HEALTH).setBaseValue(20.0); // back to 10 hearts
            if (target.getHealth() > 20.0) target.setHealth(20.0);
            target.sendMessage("§c§lThe Juggernaut event has ended.");
        }

        if (bossBar != null) {
            for (Player p : Bukkit.getOnlinePlayers()) p.hideBossBar(bossBar);
            bossBar = null;
        }

        if (mainTask != null) { mainTask.cancel(); mainTask = null; }
        if (locationTask != null) { locationTask.cancel(); locationTask = null; }

        if (juggernautId != null) {
            Component end = Component.text("⚔ The Juggernaut event has ended.")
                    .color(NamedTextColor.DARK_RED);
            Bukkit.broadcast(end);
        }

        juggernautId = null;
    }

    // ── Location broadcasting ────────────────────────────────────────────────

    private void broadcastLocation(Player target) {
        var loc = target.getLocation();
        String coords = (int) loc.getX() + ", " + (int) loc.getY() + ", " + (int) loc.getZ();
        String world = loc.getWorld().getName();

        Component msg = Component.text("")
                .append(Component.text("⚔ Juggernaut ", NamedTextColor.RED, TextDecoration.BOLD))
                .append(Component.text(target.getName(), NamedTextColor.YELLOW, TextDecoration.BOLD))
                .append(Component.text(" is at ", NamedTextColor.GRAY))
                .append(Component.text(coords, NamedTextColor.WHITE, TextDecoration.BOLD))
                .append(Component.text(" in ", NamedTextColor.GRAY))
                .append(Component.text(world, NamedTextColor.WHITE));
        Bukkit.broadcast(msg);

        if (bossBar != null) {
            bossBar.name(buildBossBarText(target));
        }
    }

    private Component buildBossBarText(Player target) {
        var loc = target.getLocation();
        String coords = (int) loc.getX() + ", " + (int) loc.getY() + ", " + (int) loc.getZ();
        return Component.text("⚔ Juggernaut: ", NamedTextColor.RED)
                .append(Component.text(target.getName(), NamedTextColor.YELLOW))
                .append(Component.text(" — Last seen: ", NamedTextColor.GRAY))
                .append(Component.text(coords, NamedTextColor.WHITE));
    }

    // Call when a new player joins so they see the boss bar
    public void showBossBarTo(Player player) {
        if (bossBar != null) player.showBossBar(bossBar);
    }

    public org.bukkit.event.Listener createJoinListener() {
        return new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void onJoin(org.bukkit.event.player.PlayerJoinEvent event) {
                showBossBarTo(event.getPlayer());
            }
        };
    }
}
