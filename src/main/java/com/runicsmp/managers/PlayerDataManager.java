package com.runicsmp.managers;

import com.runicsmp.RunicSMP;
import com.runicsmp.data.PlayerRuneData;
import com.runicsmp.data.RuneType;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/**
 * Persists player rune data (equipped runes, lifesteal hearts) across restarts.
 * Saves to plugins/RunicSMP/playerdata/<uuid>.yml
 */
public class PlayerDataManager implements Listener {

    private final RunicSMP plugin;
    private final File dataFolder;

    public PlayerDataManager(RunicSMP plugin) {
        this.plugin = plugin;
        this.dataFolder = new File(plugin.getDataFolder(), "playerdata");
        dataFolder.mkdirs();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    // ── Save/Load ─────────────────────────────────────────────────────────────

    public void savePlayer(Player player) {
        PlayerRuneData data = plugin.getRuneManager().getData(player);
        File file = new File(dataFolder, player.getUniqueId() + ".yml");
        YamlConfiguration cfg = new YamlConfiguration();

        cfg.set("name", player.getName());
        cfg.set("primary", data.hasPrimary() ? data.getPrimaryRune().name() : null);
        cfg.set("secondary", data.hasSecondary() ? data.getSecondaryRune().name() : null);
        cfg.set("lifesteal-bonus-hearts", data.getLifeStealBonusHearts());

        try { cfg.save(file); }
        catch (IOException e) { plugin.getLogger().warning("Failed to save data for " + player.getName()); }
    }

    public void loadPlayer(Player player) {
        File file = new File(dataFolder, player.getUniqueId() + ".yml");
        if (!file.exists()) return;

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        PlayerRuneData data = plugin.getRuneManager().getData(player);

        // Load primary rune
        String primary = cfg.getString("primary");
        if (primary != null) {
            try {
                RuneType rune = RuneType.valueOf(primary);
                data.setPrimaryRune(rune);
                plugin.getRuneManager().applyPassiveEffects(player, rune);
            } catch (IllegalArgumentException ignored) {}
        }

        // Load secondary rune
        String secondary = cfg.getString("secondary");
        if (secondary != null) {
            try {
                RuneType rune = RuneType.valueOf(secondary);
                data.setSecondaryRune(rune);
                plugin.getRuneManager().applyPassiveEffects(player, rune);
            } catch (IllegalArgumentException ignored) {}
        }

        // Give wind mace if wind rune is equipped
        if (data.hasPrimary() && data.getPrimaryRune() == com.runicsmp.data.RuneType.WIND) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () ->
                plugin.getRuneManager().giveWindMace(player), 2L);
        }

        // Load lifesteal bonus hearts
        int bonusHearts = cfg.getInt("lifesteal-bonus-hearts", 0);
        if (bonusHearts > 0) {
            data.setLifeStealBonusHearts(bonusHearts);
            double newMax = 20.0 + bonusHearts * 2.0;
            player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).setBaseValue(newMax);
        }
    }

    public void saveAll() {
        for (Player p : Bukkit.getOnlinePlayers()) savePlayer(p);
    }

    // ── Events ────────────────────────────────────────────────────────────────

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // Load after 1 tick so RuneManager has initialised the data
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> loadPlayer(event.getPlayer()), 1L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        savePlayer(event.getPlayer());
    }
}
