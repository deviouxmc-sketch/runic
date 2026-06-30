package com.runicsmp.managers;

import com.runicsmp.RunicSMP;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Tracks which players have joined for the first time.
 * Persists to plugins/RunicSMP/first_joins.yml so it survives restarts.
 * Once a UUID is recorded, the first-join roll NEVER fires again.
 */
public class FirstJoinManager {

    private final RunicSMP plugin;
    private final File dataFile;
    private YamlConfiguration config;
    private final Set<UUID> joinedPlayers = new HashSet<>();

    public FirstJoinManager(RunicSMP plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "first_joins.yml");
        load();
    }

    private void load() {
        if (!dataFile.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Could not create first_joins.yml: " + e.getMessage());
            }
        }
        config = YamlConfiguration.loadConfiguration(dataFile);
        if (config.contains("joined")) {
            for (String s : config.getStringList("joined")) {
                try { joinedPlayers.add(UUID.fromString(s)); }
                catch (IllegalArgumentException ignored) {}
            }
        }
    }

    private void save() {
        config.set("joined", joinedPlayers.stream().map(UUID::toString).toList());
        try { config.save(dataFile); }
        catch (IOException e) { plugin.getLogger().warning("Could not save first_joins.yml: " + e.getMessage()); }
    }

    /** Returns true if this is genuinely the player's first time on the server. */
    public boolean isFirstJoin(UUID uuid) {
        return !joinedPlayers.contains(uuid);
    }

    /** Mark a player as having joined. Call this right after their first-join roll. */
    public void markJoined(UUID uuid) {
        joinedPlayers.add(uuid);
        save();
    }
}
