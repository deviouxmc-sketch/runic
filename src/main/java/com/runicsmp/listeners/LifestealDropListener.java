package com.runicsmp.listeners;

import com.runicsmp.RunicSMP;
import com.runicsmp.data.PlayerRuneData;
import com.runicsmp.data.RuneType;
import com.runicsmp.utils.RuneItemBuilder;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Tracks when a Lifesteal rune is physically dropped (not unequipped).
 *
 * Rules:
 * - Drop rune item → lose all bonus hearts immediately
 * - Die while rune is on your body (equipped OR in inventory) → lose all bonus hearts
 * - Unequip to inventory → keep hearts (handled by RuneManager)
 * - Withdraw → goes to inventory, hearts kept (until dropped or death)
 */
public class LifestealDropListener implements Listener {

    private final RunicSMP plugin;

    public LifestealDropListener(RunicSMP plugin) { this.plugin = plugin; }

    // ── Item drop — lose hearts ───────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        ItemStack dropped = event.getItemDrop().getItemStack();

        if (!RuneItemBuilder.isRune(dropped)) return;
        if (RuneItemBuilder.getRuneType(dropped) != RuneType.LIFESTEAL) return;

        // Player physically dropped the lifesteal rune — remove bonus hearts
        loseLifestealHearts(player, "§cYou dropped the Lifesteal Rune — bonus hearts lost!");
    }

    // ── Death — lose hearts ───────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        PlayerRuneData data = plugin.getRuneManager().getData(player);
        if (data.getLifeStealBonusHearts() <= 0) return;

        // Check if they have the rune on their body (equipped or in inventory)
        boolean hasRuneOnBody = hasLifestealOnBody(player, data);
        if (hasRuneOnBody) {
            // Schedule for after death so resurrection can still fire
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                loseLifestealHearts(player,
                        "§cYou died with the Lifesteal Rune — all bonus hearts lost!");
            }, 2L);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean hasLifestealOnBody(Player player, PlayerRuneData data) {
        // Equipped as primary
        if (data.hasPrimary() && data.getPrimaryRune() == RuneType.LIFESTEAL) return true;
        // In inventory (unequipped but carried)
        for (ItemStack item : player.getInventory().getContents()) {
            if (RuneItemBuilder.isRune(item) && RuneItemBuilder.getRuneType(item) == RuneType.LIFESTEAL)
                return true;
        }
        return false;
    }

    private void loseLifestealHearts(Player player, String message) {
        PlayerRuneData data = plugin.getRuneManager().getData(player);
        int bonus = data.getLifeStealBonusHearts();
        if (bonus <= 0) return;

        data.resetLifeStealBonusHearts();

        // Reset max health to base (accounting for vitality if equipped)
        double baseMax = 20.0;
        if (data.hasSecondary() && data.getSecondaryRune() == RuneType.VITALITY) {
            baseMax += plugin.getConfig().getInt("vitality-rune.bonus-hearts", 10);
        }
        try {
            player.getAttribute(Attribute.MAX_HEALTH).setBaseValue(baseMax);
            // Clamp current health
            if (player.getHealth() > baseMax) player.setHealth(baseMax);
        } catch (Exception ignored) {}

        if (!plugin.getNotifManager().isMuted(player.getUniqueId()))
            player.sendMessage(message);
    }
}
