package com.runicsmp.managers;

import com.runicsmp.RunicSMP;
import com.runicsmp.data.RuneType;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages the 15-minute primary rune crafting ritual.
 * - Shows a server-wide boss bar with countdown and rune name
 * - Broadcasts crafter's location every N seconds
 * - Broadcasts what dimension they're in
 * - On completion, gives the rune to the crafter
 */
public class RitualManager {

    private final RunicSMP plugin;

    // Active rituals: playerUUID -> RitualSession
    private final Map<UUID, RitualSession> activeRituals = new HashMap<>();

    public RitualManager(RunicSMP plugin) {
        this.plugin = plugin;
    }

    /**
     * Start a ritual for a player crafting a specific primary rune.
     * Returns false if this player already has an active ritual, or if this primary is already being crafted.
     */
    public boolean startRitual(Player player, RuneType rune) {
        if (activeRituals.containsKey(player.getUniqueId())) {
            player.sendMessage("§cYou already have an active ritual!");
            return false;
        }

        // Check if this rune is already being ritually crafted
        for (RitualSession s : activeRituals.values()) {
            if (s.getRune() == rune) {
                player.sendMessage("§cThe §e" + rune.getDisplayName() + " §cis already being crafted by someone!");
                return false;
            }
        }

        // Check if this primary already exists on the server
        if (plugin.getRuneManager().isPrimaryActive(rune)) {
            player.sendMessage("§cThis primary rune already exists on the server — it cannot be crafted again!");
            return false;
        }

        // Secondaries craft instantly (0 duration), primaries use full ritual time
        int durationSeconds = rune.isPrimary()
                ? plugin.getConfig().getInt("ritual.duration-seconds", 900)
                : 0;
        RitualSession session = new RitualSession(player, rune, durationSeconds);
        activeRituals.put(player.getUniqueId(), session);
        session.start();

        // Server-wide announcement
        Bukkit.broadcast(Component.text("⚗ A ritual has begun! ")
                .color(NamedTextColor.GOLD)
                .append(Component.text(player.getName()).color(NamedTextColor.YELLOW))
                .append(Component.text(" is crafting the ").color(NamedTextColor.GOLD))
                .append(Component.text(rune.getDisplayName()).color(NamedTextColor.RED))
                .append(Component.text("!").color(NamedTextColor.GOLD)));

        return true;
    }

    /**
     * Cancel a ritual (e.g. player dies or logs off).
     */
    public void cancelRitual(UUID playerId) {
        RitualSession session = activeRituals.remove(playerId);
        if (session != null) {
            session.cancel();
            Player p = Bukkit.getPlayer(playerId);
            if (p != null) {
                p.sendMessage("§cYour ritual has been cancelled!");
            }
            Bukkit.broadcast(Component.text("✗ The ritual for the ")
                    .color(NamedTextColor.RED)
                    .append(Component.text(session.getRune().getDisplayName()).color(NamedTextColor.YELLOW))
                    .append(Component.text(" has been cancelled.").color(NamedTextColor.RED)));
        }
    }

    public boolean hasActiveRitual(UUID playerId) {
        return activeRituals.containsKey(playerId);
    }

    // ── Inner class: RitualSession ────────────────────────────────────────────

    private class RitualSession {
        private final RunicSMP plugin = RitualManager.this.plugin;
        private Location ritualLocation; // fixed at start, never changes
        private final Player player;
        private final RuneType rune;
        private final int totalSeconds;
        private int secondsLeft;
        private BossBar bossBar;
        private BukkitTask tickTask;
        private BukkitTask broadcastTask;
        private final int broadcastInterval;

        RitualSession(Player player, RuneType rune, int totalSeconds) {
            this.player = player;
            this.rune = rune;
            this.totalSeconds = totalSeconds;
            this.secondsLeft = totalSeconds;
            this.broadcastInterval = 30; // always 30 seconds
        }

        RuneType getRune() { return rune; }

        void start() {
            // Store the ritual start location ONCE — coords never change
            ritualLocation = player.getLocation().clone();

            // Create boss bar
            bossBar = BossBar.bossBar(
                    buildTitle(),
                    1.0f,
                    BossBar.Color.PURPLE,
                    BossBar.Overlay.NOTCHED_20
            );

            // Show to all online players
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.showBossBar(bossBar);
            }

            // Tick every second
            tickTask = new BukkitRunnable() {
                @Override
                public void run() {
                    secondsLeft--;
                    if (secondsLeft <= 0) { complete(); return; }
                    bossBar.name(buildTitle());
                    bossBar.progress(Math.max(0f, (float) secondsLeft / totalSeconds));
                }
            }.runTaskTimer(plugin, 20L, 20L);

            // Broadcast immediately on start, then every 30 seconds
            broadcastLocation(); // immediate
            broadcastTask = new BukkitRunnable() {
                @Override
                public void run() {
                    if (!player.isOnline()) { cancelRitual(player.getUniqueId()); return; }
                    broadcastLocation();
                }
            }.runTaskTimer(plugin, broadcastInterval * 20L, broadcastInterval * 20L);
        }

        void cancel() {
            if (tickTask != null) tickTask.cancel();
            if (broadcastTask != null) broadcastTask.cancel();
            hideBossBar();
        }

        void complete() {
            if (tickTask != null) tickTask.cancel();
            if (broadcastTask != null) broadcastTask.cancel();
            hideBossBar();
            activeRituals.remove(player.getUniqueId());

            // Drop rune on top of the ritual start location (smithing table)
            // Use the fixed ritual location stored at start
            org.bukkit.Location dropLoc = ritualLocation != null
                    ? ritualLocation.clone().add(0.5, 1.2, 0.5) // on top of smithing table
                    : (player.isOnline() ? player.getLocation() : null);

            if (dropLoc != null) {
                dropLoc.getWorld().dropItem(dropLoc,
                        com.runicsmp.utils.RuneItemBuilder.createRune(rune));
            }

            if (player.isOnline()) {
                player.sendMessage("§a§lRitual complete! The §e" + rune.getDisplayName()
                        + " §a§lhas dropped at the smithing table!");
                player.playSound(player.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            }

            Bukkit.broadcast(Component.text("✦ The ritual is complete! ")
                    .color(NamedTextColor.GOLD)
                    .append(Component.text(player.getName()).color(NamedTextColor.YELLOW))
                    .append(Component.text(" has forged the ").color(NamedTextColor.GOLD))
                    .append(Component.text(rune.getDisplayName()).color(NamedTextColor.RED))
                    .append(Component.text("! It has dropped at the ritual site.").color(NamedTextColor.GOLD)));
        }

        private void hideBossBar() {
            if (bossBar == null) return;
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.hideBossBar(bossBar);
            }
        }

        private Component buildTitle() {
            int minutes = secondsLeft / 60;
            int seconds = secondsLeft % 60;
            String timeStr = String.format("%d:%02d", minutes, seconds);
            return Component.text("⚗ ")
                    .color(NamedTextColor.GOLD)
                    .append(Component.text(rune.getDisplayName()).color(NamedTextColor.RED))
                    .append(Component.text(" Ritual — ").color(NamedTextColor.GOLD))
                    .append(Component.text(timeStr).color(NamedTextColor.YELLOW))
                    .append(Component.text(" remaining").color(NamedTextColor.GOLD));
        }

        private void broadcastLocation() {
            // Always use the FIXED ritual start location, not the player's current position
            Location loc = ritualLocation != null ? ritualLocation : player.getLocation();
            String dimension = switch (loc.getWorld().getEnvironment()) {
                case NETHER -> "The Nether";
                case THE_END -> "The End";
                default -> "The Overworld";
            };
            String coords = String.format("%.0f, %.0f, %.0f", loc.getX(), loc.getY(), loc.getZ());

            Bukkit.broadcast(Component.text("⚗ Ritual Alert! ").color(NamedTextColor.GOLD)
                    .append(Component.text(player.getName()).color(NamedTextColor.YELLOW))
                    .append(Component.text(" is crafting ").color(NamedTextColor.RED))
                    .append(Component.text(rune.getDisplayName()).color(NamedTextColor.LIGHT_PURPLE))
                    .append(Component.text(" • Dimension: ").color(NamedTextColor.GRAY))
                    .append(Component.text(dimension).color(NamedTextColor.GOLD))
                    .append(Component.text(" • Coords: ").color(NamedTextColor.GRAY))
                    .append(Component.text(coords).color(NamedTextColor.YELLOW)));
        }
    }
}
