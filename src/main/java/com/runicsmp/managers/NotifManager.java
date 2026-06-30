package com.runicsmp.managers;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Tracks players who have muted rune ability chat messages.
 */
public class NotifManager {
    private final Set<UUID> muted = new HashSet<>();

    public void toggle(UUID uuid) {
        if (muted.contains(uuid)) muted.remove(uuid);
        else muted.add(uuid);
    }

    public boolean isMuted(UUID uuid) { return muted.contains(uuid); }
}
