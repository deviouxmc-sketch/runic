package com.runicsmp.managers;

import com.runicsmp.RunicSMP;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/**
 * Scavenger Hunt — 20 items from easy to hard.
 * Players get a persistent action bar showing their current item to find.
 * First to collect all 20 wins.
 */
public class ScavengerHuntManager implements Listener {

    private final RunicSMP plugin;
    private boolean active = false;
    private final Map<UUID, Integer> progress = new HashMap<>();
    private BukkitTask hudTask;
    private final Map<UUID, net.kyori.adventure.bossbar.BossBar> playerBossBars = new HashMap<>();

    // ── 20 items easy → hard ──────────────────────────────────────────────────
    private static final List<Material> ITEMS = List.of(
        Material.OAK_LOG,               // 1  easy
        Material.WHEAT,                 // 2  easy
        Material.SAND,                  // 3  easy
        Material.CACTUS,                // 4  easy
        Material.SUGAR_CANE,            // 5  easy
        Material.PUMPKIN,               // 6  easy-medium
        Material.SEA_LANTERN,           // 7  medium (ocean monument)
        Material.AMETHYST_SHARD,        // 8  medium
        Material.HONEY_BOTTLE,          // 9  medium
        Material.NAUTILUS_SHELL,        // 10 medium
        Material.BLAZE_ROD,             // 11 medium-hard
        Material.GOAT_HORN,             // 12 medium-hard
        Material.RABBIT_FOOT,           // 13 hard (rare mob drop)
        Material.GHAST_TEAR,            // 14 hard
        Material.WITHER_SKELETON_SKULL, // 15 hard
        Material.HEART_OF_THE_SEA,      // 16 hard (buried treasure)
        Material.ECHO_SHARD,            // 17 hard (ancient city)
        Material.ANCIENT_DEBRIS,        // 18 very hard
        Material.TRIDENT,               // 19 brutal
        Material.SPONGE                 // 20 legendary
    );

    public ScavengerHuntManager(RunicSMP plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public boolean isActive() { return active; }

    public boolean start() {
        if (active) return false;
        active = true;
        progress.clear();

        for (Player p : Bukkit.getOnlinePlayers()) {
            progress.put(p.getUniqueId(), 0);
            giveBossBar(p, 0);
        }

        // Boss bars handle display — just update them every second
        hudTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                int idx = progress.getOrDefault(p.getUniqueId(), 0);
                updateBossBar(p, idx);
            }
        }, 20L, 20L);

        // Broadcast start
        Component announce = Component.text("")
                .append(Component.text("🔍 SCAVENGER HUNT STARTED! ", NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text("Find all 20 items to win! First item shown above.", NamedTextColor.YELLOW));
        Bukkit.broadcast(announce);

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);
        }
        return true;
    }

    public boolean stop() {
        if (!active) return false;
        active = false;
        for (Map.Entry<UUID, net.kyori.adventure.bossbar.BossBar> e : playerBossBars.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p != null) p.hideBossBar(e.getValue());
        }
        playerBossBars.clear();
        progress.clear();
        if (hudTask != null) { hudTask.cancel(); hudTask = null; }
        Bukkit.broadcast(Component.text("🔍 Scavenger Hunt has ended.").color(NamedTextColor.GOLD));
        return true;
    }

    @EventHandler
    public void onPickup(org.bukkit.event.entity.EntityPickupItemEvent event) {
        if (!active) return;
        if (!(event.getEntity() instanceof Player player)) return;

        int idx = progress.getOrDefault(player.getUniqueId(), -1);
        if (idx < 0 || idx >= ITEMS.size()) return;

        Material needed = ITEMS.get(idx);
        if (event.getItem().getItemStack().getType() != needed) return;

        // Advance progress
        int next = idx + 1;
        progress.put(player.getUniqueId(), next);
        updateBossBar(player, next);

        if (next >= ITEMS.size()) {
            // WINNER — broadcast and end the event
            Component win = Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                    .color(NamedTextColor.GOLD);
            Component winMsg = Component.text("🏆 " + player.getName() + " has won the Scavenger Hunt!")
                    .color(NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true);
            Component winSub = Component.text("They collected all 20 items first!")
                    .color(NamedTextColor.YELLOW);
            Bukkit.broadcast(Component.empty());
            Bukkit.broadcast(win);
            Bukkit.broadcast(winMsg);
            Bukkit.broadcast(winSub);
            Bukkit.broadcast(win);
            Bukkit.broadcast(Component.empty());
            for (Player p2 : Bukkit.getOnlinePlayers()) {
                p2.playSound(p2.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            }
            stop(); // end the event
            return;
        } else {
            String nextName = formatName(ITEMS.get(next));
            player.sendMessage(Component.text("✔ Got §e" + formatName(needed) + "§a! Next: ")
                    .color(NamedTextColor.GREEN)
                    .append(Component.text(nextName).color(NamedTextColor.YELLOW).decoration(TextDecoration.BOLD, true)));
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.5f);
        }
    }

    private String formatName(Material mat) {
        String name = mat.name().replace("_", " ").toLowerCase();
        String[] words = name.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String w : words) sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(" ");
        return sb.toString().trim();
    }

    // For late joiners
    public void addPlayer(Player player) {
        if (active && !progress.containsKey(player.getUniqueId())) {
            progress.put(player.getUniqueId(), 0);
            giveBossBar(player, 0);
        }
    }
    /** Called by HudManager to get the hunt text to append to the action bar */
    public String getHudText(org.bukkit.entity.Player player) {
        int idx = progress.getOrDefault(player.getUniqueId(), -1);
        if (idx < 0) return null;
        if (idx >= ITEMS.size()) return "Hunt Complete!";
        return "🔍 Item " + (idx + 1) + ": " + formatName(ITEMS.get(idx));
    }

    private void giveBossBar(Player player, int idx) {
        net.kyori.adventure.bossbar.BossBar bar = net.kyori.adventure.bossbar.BossBar.bossBar(
                buildBarText(idx),
                0.001f,
                net.kyori.adventure.bossbar.BossBar.Color.YELLOW,
                net.kyori.adventure.bossbar.BossBar.Overlay.PROGRESS
        );
        playerBossBars.put(player.getUniqueId(), bar);
        player.showBossBar(bar);
    }

    private void updateBossBar(Player player, int idx) {
        net.kyori.adventure.bossbar.BossBar bar = playerBossBars.get(player.getUniqueId());
        if (bar == null) { giveBossBar(player, idx); return; }
        bar.name(buildBarText(idx));
        bar.progress(0.001f); // near zero so bar is invisible
    }

    private Component buildBarText(int idx) {
        if (idx >= ITEMS.size()) {
            return Component.text("✦ Scavenger Hunt Complete! ✦").color(NamedTextColor.GOLD);
        }
        return Component.text("🔍 Scavenger Hunt — Item " + (idx + 1) + "/20: ")
                .color(NamedTextColor.YELLOW)
                .append(Component.text(formatName(ITEMS.get(idx))).color(NamedTextColor.WHITE));
    }

    public int getItemIndex(String materialName) {
        try {
            org.bukkit.Material mat = org.bukkit.Material.valueOf(materialName);
            return ITEMS.indexOf(mat);
        } catch (IllegalArgumentException e) {
            return -1;
        }
    }

    public void setProgress(Player player, int idx) {
        progress.put(player.getUniqueId(), idx);
        updateBossBar(player, idx);
    }

    public String formatItemName(int idx) {
        if (idx < 0 || idx >= ITEMS.size()) return "Unknown";
        return formatName(ITEMS.get(idx));
    }

}
