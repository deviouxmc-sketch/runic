package com.runicsmp.listeners;

import com.runicsmp.RunicSMP;
import com.runicsmp.data.RuneType;
import com.runicsmp.managers.FirstJoinManager;
import com.runicsmp.managers.RuneManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Handles the first-ever join rune roll sequence.
 * Only fires ONCE per UUID, ever — stored in first_joins.yml.
 *
 * Animation sequence:
 * 1. Dramatic welcome message
 * 2. "Spinning wheel" of rune names cycling in chat for 3 seconds
 * 3. Particle burst around the player
 * 4. Final reveal with the chosen rune
 */
public class FirstJoinListener implements Listener {

    private final RunicSMP plugin;
    private final FirstJoinManager firstJoinManager;
    private final RuneManager runeManager;

    private static final RuneType[] SECONDARY_POOL = {
            RuneType.FIRE, RuneType.VITALITY, RuneType.HASTE,
            RuneType.TIDAL, RuneType.TRADER
    };

    public FirstJoinListener(RunicSMP plugin) {
        this.plugin = plugin;
        this.firstJoinManager = plugin.getFirstJoinManager();
        this.runeManager = plugin.getRuneManager();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (!firstJoinManager.isFirstJoin(player.getUniqueId())) {
            // Not first join — just reapply passives
            var data = runeManager.getData(player);
            if (data.hasPrimary()) runeManager.applyPassiveEffects(player, data.getPrimaryRune());
            if (data.hasSecondary()) runeManager.applyPassiveEffects(player, data.getSecondaryRune());
            plugin.getServer().getScheduler().runTaskLater(plugin,
                    () -> plugin.getHudManager().updateHud(player), 1L);
            return;
        }

        // ── FIRST JOIN — mark immediately so server crash can't double-roll ──
        firstJoinManager.markJoined(player.getUniqueId());

        // Pick the rune now but reveal it later
        RuneType chosen = SECONDARY_POOL[(int)(Math.random() * SECONDARY_POOL.length)];

        // Set starting energy to 3
        plugin.getEnergyManager().setEnergy(player, 3);

        // Start the animation sequence after 1 tick so they're fully spawned
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> playRollAnimation(player, chosen), 20L);
    }

    // ── Roll animation ────────────────────────────────────────────────────────

    private void playRollAnimation(Player player, RuneType chosen) {
        if (!player.isOnline()) return;

        // ── Step 1: Welcome header ────────────────────────────────────────────
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("  ✦ Welcome to Runic SMP ✦")
                .color(NamedTextColor.GOLD)
                .decoration(TextDecoration.BOLD, true));
        player.sendMessage(Component.text("  The runes have chosen you...")
                .color(NamedTextColor.GRAY));
        player.sendMessage(Component.text(""));

        // Dramatic sound
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 0.5f);
        player.playSound(player.getLocation(), Sound.ENTITY_ILLUSIONER_CAST_SPELL, 0.6f, 0.8f);

        // Particle aura around player
        spawnAura(player, Color.PURPLE, 20);

        // ── Step 2: Spinning wheel of rune names (6 ticks apart = 0.3s each) ─
        List<RuneType> shuffled = new ArrayList<>(List.of(SECONDARY_POOL));
        Collections.shuffle(shuffled);

        new BukkitRunnable() {
            int tick = 0;
            final int totalSpins = 18; // 18 * 4 ticks = ~3.6 seconds

            @Override public void run() {
                if (!player.isOnline()) { cancel(); return; }

                if (tick >= totalSpins) {
                    cancel();
                    // Step 3: Final reveal
                    revealRune(player, chosen);
                    return;
                }

                // Cycle through rune names, slowing down near end
                RuneType displayed = SECONDARY_POOL[tick % SECONDARY_POOL.length];
                long delay = tick < 12 ? 4L : tick < 16 ? 6L : 10L; // speeds up then slows

                // Show spinning rune name in action bar
                player.sendActionBar(Component.text("⚡ Rolling... ")
                        .color(NamedTextColor.GRAY)
                        .append(Component.text(displayed.getDisplayName())
                                .color(NamedTextColor.YELLOW)
                                .decoration(TextDecoration.BOLD, true)));

                // Particle flash
                player.getWorld().spawnParticle(Particle.ENCHANT,
                        player.getLocation().add(0, 1, 0), 8, 0.4, 0.6, 0.4, 0.1);

                // Sound tick — higher pitch as it slows
                float pitch = tick < 12 ? 1.0f : tick < 16 ? 1.3f : 1.6f;
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, pitch);

                tick++;
                // Re-schedule with variable delay for the slowing effect
                plugin.getServer().getScheduler().runTaskLater(plugin, this::run2, delay);
                cancel(); // cancel current fixed timer, using manual reschedule
            }

            // Manual run used for variable timing
            public void run2() {
                if (!player.isOnline()) return;

                if (tick >= totalSpins) {
                    revealRune(player, chosen);
                    return;
                }

                RuneType displayed = SECONDARY_POOL[tick % SECONDARY_POOL.length];
                long nextDelay = tick < 12 ? 3L : tick < 16 ? 5L : 8L;

                player.sendActionBar(Component.text("⚡ Rolling... ")
                        .color(NamedTextColor.GRAY)
                        .append(Component.text(displayed.getDisplayName())
                                .color(NamedTextColor.YELLOW)
                                .decoration(TextDecoration.BOLD, true)));

                player.getWorld().spawnParticle(Particle.ENCHANT,
                        player.getLocation().add(0, 1, 0), 8, 0.4, 0.6, 0.4, 0.1);

                float pitch = tick < 12 ? 1.0f : tick < 16 ? 1.3f : 1.6f;
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, pitch);

                tick++;
                plugin.getServer().getScheduler().runTaskLater(plugin, this::run2, nextDelay);
            }

        }.runTaskLater(plugin, 5L);
    }

    // ── Final reveal ──────────────────────────────────────────────────────────

    private void revealRune(Player player, RuneType chosen) {
        if (!player.isOnline()) return;

        // Give rune to inventory ONLY — player equips it themselves
        // Do NOT call equipRune to avoid duplicate (one in HUD, one in inventory)
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> plugin.getHudManager().updateHud(player), 1L);

        // Big particle burst matching the rune colour
        plugin.getParticleManager().playActiveEffect(player, chosen);

        // Sound fanfare
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);

        // Server-wide announce
        Bukkit.broadcast(Component.text("")
        );
        Bukkit.broadcast(
                Component.text("  ✦ ").color(NamedTextColor.GOLD)
                .append(Component.text(player.getName()).color(NamedTextColor.YELLOW)
                        .decoration(TextDecoration.BOLD, true))
                .append(Component.text(" joined Runic SMP and rolled the ").color(NamedTextColor.GOLD))
                .append(Component.text(chosen.getDisplayName()).color(NamedTextColor.AQUA)
                        .decoration(TextDecoration.BOLD, true))
                .append(Component.text("! ✦").color(NamedTextColor.GOLD))
        );
        Bukkit.broadcast(Component.text(""));

        // Personal message
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("  Your secondary rune: ")
                .color(NamedTextColor.GRAY)
                .append(Component.text(chosen.getDisplayName())
                        .color(NamedTextColor.AQUA)
                        .decoration(TextDecoration.BOLD, true)));
        player.sendMessage(Component.text("  This is your permanent secondary — keep it safe!")
                .color(NamedTextColor.GRAY));
        player.sendMessage(Component.text("  Right-click it to equip, right-click again to use its ability.")
                .color(NamedTextColor.DARK_GRAY));
        player.sendMessage(Component.text(""));

        // Give the physical rune item
        player.getInventory().addItem(com.runicsmp.utils.RuneItemBuilder.createRune(chosen));
        player.sendMessage(Component.text("  Your §e" + chosen.getDisplayName()
                + " §7has been added to your inventory.").color(NamedTextColor.GRAY));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void spawnAura(Player player, Color color, int count) {
        World world = player.getWorld();
        Location loc = player.getLocation().add(0, 1, 0);
        for (int i = 0; i < count; i++) {
            double angle = (Math.PI * 2 / count) * i;
            double x = loc.getX() + Math.cos(angle) * 1.2;
            double z = loc.getZ() + Math.sin(angle) * 1.2;
            world.spawnParticle(Particle.DUST,
                    new Location(world, x, loc.getY(), z),
                    1, 0, 0, 0, new Particle.DustOptions(color, 2.0f));
        }
    }
}
