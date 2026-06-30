package com.runicsmp.listeners;

import com.runicsmp.RunicSMP;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerPortalEvent;

/**
 * Enforces server rule toggles set via ServerCommandListener.
 */
public class ServerRuleListener implements Listener {

    private final RunicSMP plugin;
    private final ServerCommandListener serverCmds;

    public ServerRuleListener(RunicSMP plugin, ServerCommandListener serverCmds) {
        this.plugin = plugin;
        this.serverCmds = serverCmds;
    }

    // Block PvP if disabled
    @EventHandler(priority = EventPriority.HIGH)
    public void onPvp(EntityDamageByEntityEvent event) {
        if (serverCmds.isPvpEnabled()) return;
        if (!(event.getDamager() instanceof Player)) return;
        if (!(event.getEntity() instanceof Player)) return;
        event.setCancelled(true);
        ((Player) event.getDamager()).sendMessage("§cPvP is currently disabled!");
    }

    // Block portal travel to disabled dimensions
    @EventHandler(priority = EventPriority.HIGH)
    public void onPortal(PlayerPortalEvent event) {
        World.Environment dest = event.getTo().getWorld().getEnvironment();

        if (dest == World.Environment.THE_END && !serverCmds.isEndEnabled()) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§5The End is currently disabled!");
        }

        if (dest == World.Environment.NETHER && !serverCmds.isNetherEnabled()) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cThe Nether is currently disabled!");
        }
    }
}
