package com.runicsmp.managers;

import com.runicsmp.RunicSMP;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Tracks player kill counts, persisted to kills.yml.
 */
public class KillTracker {

    private final RunicSMP plugin;
    private final File dataFile;
    private final Map<UUID, Integer> kills = new HashMap<>();
    private final Map<UUID, String> names = new HashMap<>();

    public KillTracker(RunicSMP plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "kills.yml");
        load();
    }

    public void addKill(UUID uuid, String playerName) {
        kills.merge(uuid, 1, Integer::sum);
        names.put(uuid, playerName);
        save();
    }

    public int getKills(UUID uuid) { return kills.getOrDefault(uuid, 0); }

    public List<Map.Entry<UUID, Integer>> getTopKillers(int limit) {
        return kills.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .limit(limit)
                .toList();
    }

    public String getName(UUID uuid) {
        String n = names.get(uuid);
        if (n != null) return n;
        org.bukkit.OfflinePlayer op = org.bukkit.Bukkit.getOfflinePlayer(uuid);
        return op.getName() != null ? op.getName() : uuid.toString().substring(0, 8);
    }

    private void load() {
        if (!dataFile.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(dataFile);
        if (cfg.contains("kills")) {
            for (String key : cfg.getConfigurationSection("kills").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    kills.put(uuid, cfg.getInt("kills." + key));
                } catch (Exception ignored) {}
            }
        }
        if (cfg.contains("names")) {
            for (String key : cfg.getConfigurationSection("names").getKeys(false)) {
                try {
                    names.put(UUID.fromString(key), cfg.getString("names." + key));
                } catch (Exception ignored) {}
            }
        }
    }

    private void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        kills.forEach((k, v) -> cfg.set("kills." + k, v));
        names.forEach((k, v) -> cfg.set("names." + k, v));
        try { plugin.getDataFolder().mkdirs(); cfg.save(dataFile); }
        catch (IOException e) { plugin.getLogger().warning("Could not save kills.yml"); }
    }
}
