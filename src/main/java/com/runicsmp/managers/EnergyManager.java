package com.runicsmp.managers;

import com.runicsmp.RunicSMP;
import com.runicsmp.data.PlayerRuneData;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

/**
 * Manages the 5-level energy system.
 *
 * Levels:
 * 1 = Dormant    — no powers
 * 2 = Awakening  — weak passive
 * 3 = Passive    — full passive
 * 4 = Charged    — weak active
 * 5 = Maximum    — full active
 *
 * Bank: extra energy stored beyond level 5.
 * Gained by killing while at level 5.
 * Auto-refills when you die or drop below 5.
 */
public class EnergyManager {

    private final RunicSMP plugin;
    private BukkitTask regenTask;

    public EnergyManager(RunicSMP plugin) { this.plugin = plugin; }

    public void start() {
        // Energy does NOT regen over time — only gained from player kills
        // Task kept for future use but does nothing
        regenTask = new org.bukkit.scheduler.BukkitRunnable() {
            @Override public void run() { /* no passive regen */ }
        }.runTaskTimer(plugin, 200L, 200L);
    }

    public void stop() { if (regenTask != null) regenTask.cancel(); }

    // ── Energy operations ─────────────────────────────────────────────────────

    /**
     * Add energy to a player. Overflow goes to bank.
     * Returns how much overflowed into the bank.
     */
    public int addEnergy(Player player, int amount) {
        PlayerRuneData data = plugin.getRuneManager().getData(player);
        int current = data.getEnergyLevel();
        int newLevel = current + amount;
        int overflow = 0;

        if (newLevel > 5) {
            overflow = newLevel - 5;
            newLevel = 5;
        }

        int before = current;
        data.setEnergyLevel(newLevel);

        // Refresh passives on threshold crossings
        if (before < newLevel) {
            refreshPassives(player, data);
        }

        plugin.getHudManager().updateHud(player);
        return overflow;
    }

    /**
     * Force-set a player's energy level (admin command).
     */
    public void setEnergy(Player player, int level) {
        PlayerRuneData data = plugin.getRuneManager().getData(player);
        int before = data.getEnergyLevel();
        data.setEnergyLevel(level);

        if (level < before) {
            // Dropping energy — remove passives if too low
            if (level < 2) {
                if (data.hasPrimary()) plugin.getRuneManager().removePassiveEffects(player, data.getPrimaryRune());
                if (data.hasSecondary()) plugin.getRuneManager().removePassiveEffects(player, data.getSecondaryRune());
            }
        } else {
            refreshPassives(player, data);
        }
        plugin.getHudManager().updateHud(player);
    }

    /**
     * Called on kill while at level 5 — adds 1 to bank instead.
     */
    public void addToBank(Player player) {
        PlayerRuneData data = plugin.getRuneManager().getData(player);
        if (data.getEnergyLevel() < 5) return; // only bank if already maxed

        if (data.getEnergyBank() >= 10) {
            player.sendMessage("§7Energy bank is full (10/10)!");
            return;
        }

        data.addEnergyBank(1);
        player.sendMessage("§aEnergy banked! §e⚡ " + data.getEnergyBank() + "/10 §astored.");
        plugin.getHudManager().updateHud(player);
    }

    /**
     * Called on death or when energy drops below 5.
     * Drains bank into energy level.
     */
    public void drainBankIntoEnergy(Player player) {
        PlayerRuneData data = plugin.getRuneManager().getData(player);
        if (!data.hasEnergyBanked()) return;

        int needed = 5 - data.getEnergyLevel();
        if (needed <= 0) return;

        int drain = Math.min(needed, data.getEnergyBank());
        data.addEnergyBank(-drain);
        data.setEnergyLevel(data.getEnergyLevel() + drain);

        refreshPassives(player, data);
        plugin.getHudManager().updateHud(player);

        if (drain > 0) {
            player.sendMessage("§a⚡ Energy bank refilled your energy! §e("
                    + data.getEnergyLevel() + "/5§a) — §e" + data.getEnergyBank() + " §aremaining in bank.");
        }
    }

    /**
     * Consume energy for using an ability.
     */
    public void consumeEnergy(Player player, int amount) {
        PlayerRuneData data = plugin.getRuneManager().getData(player);
        data.setEnergyLevel(data.getEnergyLevel() - amount);

        if (data.getEnergyLevel() < 2) {
            if (data.hasPrimary()) plugin.getRuneManager().removePassiveEffects(player, data.getPrimaryRune());
            if (data.hasSecondary()) plugin.getRuneManager().removePassiveEffects(player, data.getSecondaryRune());
        }

        // Auto-drain bank if fell below 5
        drainBankIntoEnergy(player);
        plugin.getHudManager().updateHud(player);
    }

    // ── Passives ──────────────────────────────────────────────────────────────

    private void refreshPassives(Player player, PlayerRuneData data) {
        if (data.getEnergyLevel() >= 2) {
            if (data.hasPrimary()) plugin.getRuneManager().applyPassiveEffects(player, data.getPrimaryRune());
            if (data.hasSecondary()) plugin.getRuneManager().applyPassiveEffects(player, data.getSecondaryRune());
        }
    }
}
