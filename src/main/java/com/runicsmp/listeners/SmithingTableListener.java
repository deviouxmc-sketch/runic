package com.runicsmp.listeners;

import com.runicsmp.RunicSMP;
import com.runicsmp.data.RuneType;
import com.runicsmp.managers.RuneCraftingManager;
import com.runicsmp.managers.RitualManager;
import com.runicsmp.managers.RuneManager;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.List;

/**
 * Handles shift + right-click on a Smithing Table to start a rune crafting ritual
 * or instantly craft a secondary rune.
 */
public class SmithingTableListener implements Listener {

    private final RunicSMP plugin;
    private final RuneCraftingManager craftingManager;
    private final RitualManager ritualManager;
    private final RuneManager runeManager;

    public SmithingTableListener(RunicSMP plugin) {
        this.plugin = plugin;
        this.craftingManager = plugin.getCraftingManager();
        this.ritualManager = plugin.getRitualManager();
        this.runeManager = plugin.getRuneManager();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSmithingTableShiftClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.SMITHING_TABLE) return;

        Player player = event.getPlayer();
        if (!player.isSneaking()) return;

        event.setCancelled(true);

        // Check God Pickaxe first (3 netherite scrap + 3 diamonds + 1 stick)
        if (com.runicsmp.listeners.GodPickaxeListener.hasIngredients(player)) {
            com.runicsmp.listeners.GodPickaxeListener.consumeIngredients(player);
            org.bukkit.inventory.ItemStack pick = com.runicsmp.listeners.GodPickaxeListener.createGodPickaxe();
            if (player.getInventory().firstEmpty() != -1) {
                player.getInventory().addItem(pick);
            } else {
                player.getWorld().dropItemNaturally(player.getLocation(), pick);
                player.sendMessage("§eInventory full — God Pickaxe dropped!");
            }
            player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_SMITHING_TABLE_USE, 1f, 1f);
            player.playSound(player.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.3f);
            player.getWorld().spawnParticle(org.bukkit.Particle.ENCHANT,
                    player.getLocation().add(0,1,0), 20, 0.5, 0.8, 0.5, 0.3);
            player.sendMessage("§d✦ You crafted the §5§lGod Pickaxe§d!");
            return;
        }

        // Check what rune they can craft
        RuneType rune = craftingManager.matchRecipe(player);

        if (rune == null) {
            player.sendMessage("§cYou don't have the required items to craft any rune.");
            sendRecipeHint(player);
            return;
        }

        List<org.bukkit.inventory.ItemStack> recipe = craftingManager.getRecipe(rune);

        if (rune.isPrimary()) {
            // Start a 15-minute ritual
            boolean started = ritualManager.startRitual(player, rune);
            if (started) {
                craftingManager.consumeItems(player, recipe);
                player.sendMessage("§6§lRitual begun! The §e" + rune.getDisplayName()
                        + " §6§lwill be forged in §e15 minutes§6§l. Your location is now being broadcast!");
            }
        } else {
            // Instant craft for secondaries
            craftingManager.consumeItems(player, recipe);

            // If they already have a secondary, give it back to inventory first
            var data = runeManager.getData(player);
            if (data.hasSecondary()) {
                RuneType old = data.getSecondaryRune();
                runeManager.unequipRune(player, old);
                player.sendMessage("§eYour old §7" + old.getDisplayName() + " §ehas been returned to your inventory.");
            }

            // Give new secondary rune
            player.getInventory().addItem(com.runicsmp.utils.RuneItemBuilder.createRune(rune));
            player.sendMessage("§a§lCrafted: §e" + rune.getDisplayName()
                    + "§a! Right-click it to equip it as your new secondary.");
        }
    }

    private void sendRecipeHint(Player player) {
        player.sendMessage("§7Available rune recipes (shift + smithing table):");
        for (RuneType rune : craftingManager.getCraftableRunes()) {
            List<org.bukkit.inventory.ItemStack> recipe = craftingManager.getRecipe(rune);
            StringBuilder sb = new StringBuilder("§e  " + rune.getDisplayName() + "§7: ");
            for (org.bukkit.inventory.ItemStack item : recipe) {
                sb.append(item.getAmount()).append("x ")
                  .append(formatMaterial(item.getType())).append(", ");
            }
            String line = sb.toString();
            if (line.endsWith(", ")) line = line.substring(0, line.length() - 2);
            player.sendMessage(line);
        }
    }

    private String formatMaterial(Material mat) {
        return mat.name().replace("_", " ").toLowerCase();
    }
}
