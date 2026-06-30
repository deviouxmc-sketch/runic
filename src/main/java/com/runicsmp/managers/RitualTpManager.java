package com.runicsmp.managers;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Stores clickable TP destinations from ritual location broadcasts.
 * Each entry expires after 5 minutes.
 */
public class RitualTpManager {

    // Map<crafterUUID, TpEntry>
    private final Map<UUID, TpEntry> entries = new HashMap<>();

    public void registerLocation(UUID crafterUUID, Location loc, long expiryMs) {
        entries.put(crafterUUID, new TpEntry(loc, expiryMs));
    }

    /**
     * Attempt to TP a player to the crafter's location.
     * Returns false if expired or not found.
     */
    public boolean tpToRitual(Player requester, String crafterName) {
        // Find by name
        for (Map.Entry<UUID, TpEntry> e : entries.entrySet()) {
            Player crafter = org.bukkit.Bukkit.getPlayer(e.getKey());
            String name = crafter != null ? crafter.getName()
                    : org.bukkit.Bukkit.getOfflinePlayer(e.getKey()).getName();
            if (crafterName.equalsIgnoreCase(name)) {
                TpEntry entry = e.getValue();
                if (System.currentTimeMillis() > entry.expiryMs) {
                    entries.remove(e.getKey());
                    requester.sendMessage("§cThat ritual TP has expired!");
                    return false;
                }
                requester.teleport(entry.location);
                requester.sendMessage("§aTeleported to §e" + crafterName + "§a's ritual location!");
                return true;
            }
        }
        requester.sendMessage("§cNo active ritual TP found for §e" + crafterName + "§c.");
        return false;
    }

    private static class TpEntry {
        final Location location;
        final long expiryMs;
        TpEntry(Location location, long expiryMs) {
            this.location = location;
            this.expiryMs = expiryMs;
        }
    }
}
