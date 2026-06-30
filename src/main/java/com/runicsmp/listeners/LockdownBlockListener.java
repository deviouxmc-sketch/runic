package com.runicsmp.listeners;

import com.runicsmp.RunicSMP;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.entity.FoodLevelChangeEvent;

import java.util.Set;

/**
 * Lockdown block listener.
 * Gameplay logic from jar (LockdownListener.class):
 * - Blocks specifically: WATER_BUCKET, WIND_CHARGE, EXPERIENCE_BOTTLE, COBWEB on interact
 * - Blocks block placement
 * - Uses RIGHT_CLICK_BLOCK action check
 * - Sounds from jar: BLOCK_ANVIL_LAND, ENTITY_ELDER_GUARDIAN_HURT, ENTITY_PLAYER_SPLASH_HIGH_SPEED
 * Our particles are applied in PrimaryAbilityExtensions.
 * Also blocks all other actions for full stun.
 */
public class LockdownBlockListener implements Listener {

    private final RunicSMP plugin;

    // JAR: exact items blocked (from LockdownListener$1.class strings)
    private static final Set<Material> BLOCKED_ITEMS = Set.of(
            Material.WATER_BUCKET,
            Material.WIND_CHARGE,
            Material.EXPERIENCE_BOTTLE,
            Material.COBWEB,
            Material.SPLASH_POTION,
            Material.LINGERING_POTION,
            Material.ENDER_PEARL,
            Material.CHORUS_FRUIT,
            Material.GOLDEN_APPLE,
            Material.ENCHANTED_GOLDEN_APPLE
    );

    public LockdownBlockListener(RunicSMP plugin) { this.plugin = plugin; }

    private boolean locked(Player p) { return p.hasMetadata("rune_lockdown"); }

    private void deny(Player p) {
        // JAR SOUNDS: BLOCK_ANVIL_LAND + ENTITY_ELDER_GUARDIAN_HURT
        p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.5f, 1.8f);
        p.playSound(p.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_HURT, 0.4f, 1.5f);
        if (!plugin.getNotifManager().isMuted(p.getUniqueId()))
            p.sendMessage("§5⛓ You are stunned!");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBreak(BlockBreakEvent e) {
        if (locked(e.getPlayer())) { e.setCancelled(true); deny(e.getPlayer()); }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlace(BlockPlaceEvent e) {
        if (locked(e.getPlayer())) { e.setCancelled(true); deny(e.getPlayer()); }
    }

    // JAR LOGIC: check item type in interact event, block specific items
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent e) {
        if (!locked(e.getPlayer())) return;
        e.setCancelled(true);
        // Play the SPLASH sound from jar for item use attempt
        e.getPlayer().playSound(e.getPlayer().getLocation(),
                Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 0.3f, 1.5f);
        if (e.getItem() != null && BLOCKED_ITEMS.contains(e.getItem().getType())) {
            deny(e.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        if (locked(e.getPlayer())) { e.setCancelled(true); }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteractAtEntity(PlayerInteractAtEntityEvent e) {
        if (locked(e.getPlayer())) { e.setCancelled(true); }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDrop(PlayerDropItemEvent e) {
        if (locked(e.getPlayer())) { e.setCancelled(true); }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPickup(PlayerAttemptPickupItemEvent e) {
        if (locked(e.getPlayer())) { e.setCancelled(true); }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryOpen(InventoryOpenEvent e) {
        if (e.getPlayer() instanceof Player p && locked(p)) { e.setCancelled(true); }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p && locked(p)) { e.setCancelled(true); }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onSwapHand(PlayerSwapHandItemsEvent e) {
        if (locked(e.getPlayer())) { e.setCancelled(true); }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onHotbar(PlayerItemHeldEvent e) {
        if (locked(e.getPlayer())) { e.setCancelled(true); }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBow(EntityShootBowEvent e) {
        if (e.getEntity() instanceof Player p && locked(p)) { e.setCancelled(true); }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onProjectile(ProjectileLaunchEvent e) {
        if (e.getEntity().getShooter() instanceof Player p && locked(p)) { e.setCancelled(true); }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onConsume(PlayerItemConsumeEvent e) {
        if (locked(e.getPlayer())) { e.setCancelled(true); deny(e.getPlayer()); }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onAttack(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player p && locked(p)) { e.setCancelled(true); }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        if (!locked(e.getPlayer())) return;
        e.setCancelled(true);
        deny(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onFoodChange(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p && locked(p)) { e.setCancelled(true); }
    }
}
