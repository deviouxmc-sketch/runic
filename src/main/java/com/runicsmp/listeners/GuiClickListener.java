package com.runicsmp.listeners;

import com.runicsmp.RunicSMP;
import com.runicsmp.runes.PrimaryAbilityExtensions;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Handles GUI click events for Resurrection target picker.
 */
public class GuiClickListener implements Listener {

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGH)
    public void onMerchantTrade(org.bukkit.event.inventory.InventoryClickEvent event) {
        if (!(event.getInventory() instanceof org.bukkit.inventory.MerchantInventory)) return;
        if (!(event.getWhoClicked() instanceof org.bukkit.entity.Player)) return;

        // Cancel if any cursor or clicked item is a rune
        org.bukkit.inventory.ItemStack cursor = event.getCursor();
        org.bukkit.inventory.ItemStack clicked = event.getCurrentItem();

        if (com.runicsmp.utils.RuneItemBuilder.isRune(cursor) ||
            com.runicsmp.utils.RuneItemBuilder.isRune(clicked)) {
            event.setCancelled(true);
            event.getWhoClicked().sendMessage("§cYou cannot trade runes with villagers!");
        }
    }

    private static final String HISTORY_TITLE = "§5§lRune History — Pick a Rune";


    private final RunicSMP plugin;
    private final PrimaryAbilityExtensions ext;

    public GuiClickListener(RunicSMP plugin) {
        this.plugin = plugin;
        this.ext = new PrimaryAbilityExtensions(plugin);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!player.hasMetadata("res_gui_open")) return;

        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;

        ext.handleResurrectionClick(player, clicked);
    }
}
