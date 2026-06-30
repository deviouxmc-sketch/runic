package com.runicsmp.managers;

import org.bukkit.entity.Player;
import java.util.*;

/**
 * Tracks players under resurrection protection.
 * First death: cancelled, player survives on 1 heart.
 * Second death within window: player dies for real.
 */
public class ResurrectionManager {
    // Map<protectedUUID, expiryMs>
    private final Map<UUID, Long> protected_ = new HashMap<>();
    // Players who already used their resurrection (next death is real)
    private final Set<UUID> usedResurrection = new HashSet<>();
    private final Map<UUID, UUID> casterToTarget = new HashMap<>();

    public void protect(Player caster, Player target, long durationMs) {
        long expiry = System.currentTimeMillis() + durationMs;
        protected_.put(target.getUniqueId(), expiry);
        usedResurrection.remove(target.getUniqueId()); // reset if re-applied
        casterToTarget.put(caster.getUniqueId(), target.getUniqueId());
    }

    public boolean isProtected(Player target) {
        Long expiry = protected_.get(target.getUniqueId());
        if (expiry == null) return false;
        if (System.currentTimeMillis() > expiry) {
            protected_.remove(target.getUniqueId());
            usedResurrection.remove(target.getUniqueId());
            return false;
        }
        return true;
    }

    /** Returns true if this is the first death (save them). False = let them die. */
    public boolean consumeProtection(Player target) {
        UUID uuid = target.getUniqueId();
        if (!isProtected(target)) return false;
        if (usedResurrection.contains(uuid)) {
            // Already used once — remove and let them die
            protected_.remove(uuid);
            usedResurrection.remove(uuid);
            return false;
        }
        // First death — mark as used, keep protection active briefly
        usedResurrection.add(uuid);
        return true;
    }

    public void removeProtection(UUID uuid) {
        protected_.remove(uuid);
        usedResurrection.remove(uuid);
    }

    public long getExpiry(UUID uuid) { return protected_.getOrDefault(uuid, 0L); }
}
