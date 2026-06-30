package com.runicsmp.managers;

import com.runicsmp.RunicSMP;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Manages the trust system.
 * isTrusted(caster, target) = true means caster has trusted target,
 * so target is EXEMPT from caster's rune effects (shadow blind, lockdown stun etc).
 * Persists to trust_data.yml.
 */
public class TrustManager {

    private final RunicSMP plugin;
    private final Map<UUID, Set<UUID>> trustMap = new HashMap<>();
    private final File dataFile;

    public TrustManager(RunicSMP plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "trust_data.yml");
        load();
    }

    public void trust(Player owner, Player target) {
        trustMap.computeIfAbsent(owner.getUniqueId(), k -> new HashSet<>())
                .add(target.getUniqueId());
        save();
    }

    public void untrust(Player owner, Player target) {
        Set<UUID> s = trustMap.get(owner.getUniqueId());
        if (s != null) { s.remove(target.getUniqueId()); save(); }
    }

    /**
     * Returns true if owner has trusted target — target is exempt from owner's rune effects.
     */
    /**
     * Returns true if owner has trusted target.
     * owner = the rune caster, target = the player being affected.
     * If owner trusts target, target is EXEMPT from owner's rune effects.
     */
    public boolean isTrusted(Player owner, Player target) {
        if (owner == null || target == null) return false;
        if (owner.getUniqueId().equals(target.getUniqueId())) return true; // always trust self
        Set<UUID> s = trustMap.get(owner.getUniqueId());
        if (s == null) return false;
        return s.contains(target.getUniqueId());
    }

    /** UUID-based version for use when Player objects aren't available */
    public boolean isTrustedUUID(java.util.UUID ownerUUID, java.util.UUID targetUUID) {
        if (ownerUUID == null || targetUUID == null) return false;
        if (ownerUUID.equals(targetUUID)) return true;
        Set<UUID> s = trustMap.get(ownerUUID);
        return s != null && s.contains(targetUUID);
    }

    public Set<UUID> getTrusted(UUID ownerUUID) {
        return trustMap.getOrDefault(ownerUUID, Collections.emptySet());
    }

    private void load() {
        if (!dataFile.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(dataFile);
        if (!cfg.contains("trust")) return;
        for (String ownerStr : cfg.getConfigurationSection("trust").getKeys(false)) {
            try {
                UUID owner = UUID.fromString(ownerStr);
                List<String> trusted = cfg.getStringList("trust." + ownerStr);
                Set<UUID> set = new HashSet<>();
                for (String t : trusted) {
                    try { set.add(UUID.fromString(t)); } catch (Exception ignored) {}
                }
                trustMap.put(owner, set);
            } catch (Exception ignored) {}
        }
    }

    private void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<UUID, Set<UUID>> e : trustMap.entrySet()) {
            List<String> list = new ArrayList<>();
            e.getValue().forEach(u -> list.add(u.toString()));
            cfg.set("trust." + e.getKey().toString(), list);
        }
        try {
            plugin.getDataFolder().mkdirs();
            cfg.save(dataFile);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save trust_data.yml: " + ex.getMessage());
        }
    }
}
