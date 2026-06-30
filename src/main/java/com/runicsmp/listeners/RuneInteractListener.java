package com.runicsmp.listeners;

import com.runicsmp.RunicSMP;
import com.runicsmp.data.PlayerRuneData;
import com.runicsmp.data.RuneType;
import com.runicsmp.managers.RuneManager;
import com.runicsmp.runes.RuneAbilityHandler;
import com.runicsmp.runes.SecondaryAbilityHandler;
import com.runicsmp.utils.RuneItemBuilder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class RuneInteractListener implements Listener {

    // Multi-ability primaries: ability 2 triggered via Shift+Offhand (F key)
    private static final java.util.Set<com.runicsmp.data.RuneType> MULTI_ABILITY_PRIMARIES = java.util.Set.of(
        com.runicsmp.data.RuneType.DRAGON,
        com.runicsmp.data.RuneType.WARDEN,
        com.runicsmp.data.RuneType.FIRE
    );

    @EventHandler(priority = EventPriority.HIGH)
    public void onOffhandSwap(org.bukkit.event.player.PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (!player.isSneaking()) return;

        PlayerRuneData data = runeManager.getData(player);
        if (!data.hasPrimary()) return;

        com.runicsmp.data.RuneType rune = data.getPrimaryRune();
        if (!MULTI_ABILITY_PRIMARIES.contains(rune)) return;

        event.setCancelled(true);
        abilityHandler.activatePrimary(player, true); // true = ability 2
    }

    private final RunicSMP plugin;
    private final RuneManager runeManager;
    private final RuneAbilityHandler abilityHandler;
    private final SecondaryAbilityHandler secondaryHandler;

    public RuneAbilityHandler getAbilityHandler() { return abilityHandler; }
    public SecondaryAbilityHandler getSecondaryHandler() { return secondaryHandler; }

    public RuneInteractListener(RunicSMP plugin) {
        this.plugin = plugin;
        this.runeManager = plugin.getRuneManager();
        this.abilityHandler = new RuneAbilityHandler(plugin);
        this.secondaryHandler = new SecondaryAbilityHandler(plugin);
    }

    // First join handling moved to FirstJoinListener

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getRitualManager().cancelRitual(event.getPlayer().getUniqueId());
        PlayerRuneData data = runeManager.getData(event.getPlayer());
        if (data.hasPrimary()) runeManager.removePassiveEffects(event.getPlayer(), data.getPrimaryRune());
        if (data.hasSecondary()) runeManager.removePassiveEffects(event.getPlayer(), data.getSecondaryRune());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        Action action = event.getAction();

        // ── Energy charge item ────────────────────────────────────────────────
        int energyCharge = EnergyCommandListener.getEnergyCharge(item);
        if (energyCharge > 0) {
            if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
                event.setCancelled(true);
                item.setAmount(item.getAmount() - 1);
                int overflow = plugin.getEnergyManager().addEnergy(player, energyCharge);
                PlayerRuneData d = runeManager.getData(player);
                player.sendMessage("§a⚡ Absorbed §e" + energyCharge + " §aenergy! Now at §e"
                        + d.getEnergyLevel() + "/5§a."
                        + (overflow > 0 ? " §7+" + overflow + " banked." : ""));
            }
            return;
        }

        // ── Rune item ─────────────────────────────────────────────────────────
        if (!RuneItemBuilder.isRune(item)) return;
        RuneType rune = RuneItemBuilder.getRuneType(item);
        if (rune == null) return;

        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(true);
            handleRightClick(player, item, rune, player.isSneaking());
        } else if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            event.setCancelled(true);
            handleLeftClick(player, item, rune);
        }
    }

    private void handleRightClick(Player player, ItemStack item, RuneType rune, boolean shifting) {
        PlayerRuneData data = runeManager.getData(player);
        boolean alreadyEquipped = (rune.isPrimary() && rune.equals(data.getPrimaryRune()))
                || (rune.isSecondary() && rune.equals(data.getSecondaryRune()));

        if (alreadyEquipped) {
            // Fire ability
            if (rune.isPrimary()) {
                abilityHandler.activatePrimary(player, shifting);
            } else {
                secondaryHandler.activateSecondary(player);
            }
        } else {
            // Check if inventory is full AND they already have a rune equipped in that slot
            boolean hasOldRune = (rune.isPrimary() && data.hasPrimary()) || (rune.isSecondary() && data.hasSecondary());
            boolean invFull = player.getInventory().firstEmpty() == -1;

            if (invFull && hasOldRune) {
                java.util.Map<java.util.UUID, RuneType> pending = plugin.getRuneManager().getPendingFullInvEquip();
                    player.sendMessage("§c⚠ Please open an inventory slot before switching runes!");
                    return;
            }

            // Equip — auto-unequip old rune handled inside equipRune
            boolean success = runeManager.equipRune(player, rune);
            if (success) {
                item.setAmount(item.getAmount() - 1);
                plugin.getServer().getScheduler().runTaskLater(plugin,
                        () -> plugin.getHudManager().updateHud(player), 1L);
                player.sendMessage("§aEquipped §e" + rune.getDisplayName() + "§a!");
            }
        }
    }

    private void handleLeftClick(Player player, ItemStack item, RuneType rune) {
        PlayerRuneData data = runeManager.getData(player);
        if ((rune.isPrimary() && rune.equals(data.getPrimaryRune()))
                || (rune.isSecondary() && rune.equals(data.getSecondaryRune()))) {
            runeManager.unequipRune(player, rune);
        } else {
            player.sendMessage("§7Left-click: this rune isn't currently equipped.");
        }
    }

    @EventHandler
    public void onPlayerToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        PlayerRuneData data = runeManager.getData(player);
        if (data.hasPrimary() && data.getPrimaryRune() == RuneType.DRAGON) {
            if (!player.isGliding() && !player.isOnGround()) {
                event.setCancelled(true);
                player.setGliding(true);
                player.getWorld().spawnParticle(org.bukkit.Particle.PORTAL,
                        player.getLocation().add(0, 1, 0), 10, 0.3, 0.3, 0.3, 0.05);
            }
        }
    }
}
