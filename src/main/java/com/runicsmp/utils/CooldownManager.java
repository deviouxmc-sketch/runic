package com.runicsmp.utils;

import com.runicsmp.data.RuneType;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Simple cooldown tracker per player per rune ability.
 */
public class CooldownManager {

    // Map<PlayerUUID, Map<RuneType, expiry timestamp in ms>>
    private final Map<UUID, Map<RuneType, Long>> cooldowns = new HashMap<>();

    /**
     * Sets a cooldown for a player's rune ability.
     * @param playerId  the player
     * @param rune      the rune
     * @param seconds   cooldown duration in seconds
     */
    public void setCooldown(UUID playerId, RuneType rune, int seconds) {
        cooldowns.computeIfAbsent(playerId, k -> new HashMap<>())
                .put(rune, System.currentTimeMillis() + (seconds * 1000L));
    }

    /**
     * Returns true if the player is still on cooldown for a given rune.
     */
    public boolean isOnCooldown(UUID playerId, RuneType rune) {
        Map<RuneType, Long> map = cooldowns.get(playerId);
        if (map == null) return false;
        Long expiry = map.get(rune);
        return expiry != null && expiry > System.currentTimeMillis();
    }

    /**
     * Returns remaining cooldown in seconds, or 0 if not on cooldown.
     */
    public int getRemainingSeconds(UUID playerId, RuneType rune) {
        Map<RuneType, Long> map = cooldowns.get(playerId);
        if (map == null) return 0;
        Long expiry = map.get(rune);
        if (expiry == null) return 0;
        long remaining = expiry - System.currentTimeMillis();
        return remaining > 0 ? (int) Math.ceil(remaining / 1000.0) : 0;
    }

    /**
     * Clears all cooldowns for a player (e.g. on death or rune drop).
     */
    public void clearCooldowns(UUID playerId) {
        cooldowns.remove(playerId);
    }
}
