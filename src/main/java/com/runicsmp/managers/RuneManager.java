package com.runicsmp.managers;

import com.runicsmp.RunicSMP;
import com.runicsmp.data.PlayerRuneData;
import com.runicsmp.data.RuneType;
import com.runicsmp.utils.CooldownManager;
import com.runicsmp.utils.RuneItemBuilder;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class RuneManager {

    private final RunicSMP plugin;
    private final Map<UUID, PlayerRuneData> playerData = new HashMap<>();
    private final Map<UUID, RuneType> pendingFullInvEquip = new HashMap<>();
    private final CooldownManager cooldownManager = new CooldownManager();
    private final Set<RuneType> activePrimaries = new HashSet<>();

    public RuneManager(RunicSMP plugin) { this.plugin = plugin; }

    // ── Player Data ───────────────────────────────────────────────────────────

    public PlayerRuneData getData(Player player) {
        return playerData.computeIfAbsent(player.getUniqueId(), PlayerRuneData::new);
    }
    public PlayerRuneData getData(UUID uuid) { return playerData.get(uuid); }
    public boolean hasData(UUID uuid) { return playerData.containsKey(uuid); }
    public void removeData(UUID uuid) { playerData.remove(uuid); }

    // ── Equip ─────────────────────────────────────────────────────────────────

    /**
     * Equip a rune. If a rune is already in that slot it is automatically
     * unequipped and returned to the player's inventory first.
     * Returns false if the primary is already owned by another player.
     */
    public boolean equipRune(Player player, RuneType rune) {
        PlayerRuneData data = getData(player);

        if (rune.isPrimary()) {
            // Uniqueness check
            if (activePrimaries.contains(rune)) {
                UUID owner = getPrimaryOwner(rune);
                if (owner != null && !owner.equals(player.getUniqueId())) {
                    player.sendMessage("§cThis primary rune is already held by another player!");
                    return false;
                }
            }
            // Auto-unequip existing primary → remove from inventory entirely, throw on ground
            if (data.hasPrimary() && !data.getPrimaryRune().equals(rune)) {
                RuneType oldRune = data.getPrimaryRune();
                removePassiveEffects(player, oldRune);
                activePrimaries.remove(oldRune);
                data.setPrimaryRune(null);
                // Remove any copies of the old primary from inventory
                org.bukkit.inventory.ItemStack[] contents = player.getInventory().getContents();
                for (int i = 0; i < contents.length; i++) {
                    if (RuneItemBuilder.isRune(contents[i]) && oldRune.equals(RuneItemBuilder.getRuneType(contents[i]))) {
                        player.getInventory().setItem(i, null);
                    }
                }
                // Lifesteal goes to inventory (not floor) to preserve heart logic
                // All other primaries are dropped on the ground
                if (oldRune == RuneType.LIFESTEAL) {
                    // Lifesteal: always try inventory first, drop if full
                ItemStack oldLifestealItem = RuneItemBuilder.createRune(oldRune);
                if (player.getInventory().firstEmpty() != -1) {
                    player.getInventory().addItem(oldLifestealItem);
                } else {
                    player.getWorld().dropItemNaturally(player.getLocation(), oldLifestealItem);
                }
                } else {
                // Other primaries: always try inventory, drop to ground if full
                ItemStack oldRuneItem = RuneItemBuilder.createRune(oldRune);
                if (player.getInventory().firstEmpty() != -1) {
                    player.getInventory().addItem(oldRuneItem);
                } else {
                    player.getWorld().dropItemNaturally(player.getLocation(), oldRuneItem);
                }
                }
            }
            data.setPrimaryRune(rune);
            activePrimaries.add(rune);

        } else {
            // Auto-unequip existing secondary → give back to inventory silently
            if (data.hasSecondary() && !data.getSecondaryRune().equals(rune)) {
                RuneType old = data.getSecondaryRune();
                removePassiveEffects(player, old);
                data.setSecondaryRune(null);
                if (player.getInventory().firstEmpty() != -1) {
                    player.getInventory().addItem(RuneItemBuilder.createRune(old));
                    player.sendMessage("§eYour §7" + old.getDisplayName()
                            + "§e was unequipped and returned to your inventory.");
                }
            }
            data.setSecondaryRune(rune);
        }

        if (plugin.getHudManager() != null) {
            // Clear old cooldown display when swapping runes
            if (rune.isPrimary()) plugin.getHudManager().clearPrimaryCd(player.getUniqueId());
            else plugin.getHudManager().clearSecondaryCd(player.getUniqueId());
            plugin.getHudManager().updateHud(player);
        }
        applyPassiveEffects(player, rune);
        // Give wind mace when equipping wind, remove when equipping different primary
        if (rune == RuneType.WIND) {
            giveWindMace(player);
        } else if (rune.isPrimary()) {
            removeWindMace(player);
        }
        return true;
    }

    // ── Unequip ───────────────────────────────────────────────────────────────

    public boolean unequipRune(Player player, RuneType rune) {
        PlayerRuneData data = getData(player);

        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage("§cYou need a free inventory slot to unequip a rune!");
            return false;
        }

        if (rune.isPrimary() && rune.equals(data.getPrimaryRune())) {
            removePassiveEffects(player, rune);
            data.setPrimaryRune(null);
            activePrimaries.remove(rune);
            // Remove wind mace if unequipping wind
            if (rune == RuneType.WIND) removeWindMace(player);
            // Lifesteal: unequipping to inventory KEEPS bonus hearts
            // Hearts are only lost when the item is dropped or on death
        } else if (rune.isSecondary() && rune.equals(data.getSecondaryRune())) {
            removePassiveEffects(player, rune);
            data.setSecondaryRune(null);
        } else {
            return false;
        }

        player.getInventory().addItem(RuneItemBuilder.createRune(rune));
        // Clear HUD cooldown display on unequip (actual cooldown stays in CooldownManager)
        if (plugin.getHudManager() != null) {
            if (rune.isPrimary()) plugin.getHudManager().clearPrimaryCd(player.getUniqueId());
            else plugin.getHudManager().clearSecondaryCd(player.getUniqueId());
            plugin.getHudManager().updateHud(player);
        }
        player.sendMessage("§aUnequipped §e" + rune.getDisplayName() + "§a — back in your inventory.");
        return true;
    }

    // ── Passive Effects ───────────────────────────────────────────────────────

    public void applyPassiveEffects(Player player, RuneType rune) {
        PlayerRuneData data = getData(player);
        // Primaries always apply passives regardless of energy
        // Only secondaries are gated by energy (handled in SecondaryAbilityHandler)

        switch (rune) {
            case STRENGTH -> player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.STRENGTH, Integer.MAX_VALUE, 1, false, false, true)); // Perm Strength II
            case VITALITY -> {
                // Nerfed: max 12 hearts, no regen
                player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).setBaseValue(24.0);
            }
            case HASTE -> {
                int lvl = plugin.getConfig().getInt("haste-rune.passive-haste-level", 2) - 1;
                player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                        org.bukkit.potion.PotionEffectType.HASTE, Integer.MAX_VALUE, lvl, false, false, true));
            }
            case SHADOW -> {
                // True invisibility — no particles, no ambient, hides completely
                player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                        org.bukkit.potion.PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false, true));
            }
            case TIDAL -> {
                player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                        org.bukkit.potion.PotionEffectType.DOLPHINS_GRACE, Integer.MAX_VALUE, 0, false, false, true));
                player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                        org.bukkit.potion.PotionEffectType.WATER_BREATHING, Integer.MAX_VALUE, 0, false, false, true));
            }
            case DEFENDER -> player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 0, false, false, true));
            case FIRE -> player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.FIRE_RESISTANCE, Integer.MAX_VALUE, 0, false, false, true));
            case THIEF -> player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.LUCK, Integer.MAX_VALUE, 0, false, false, true));
            // TRADER passive now set in GRAVITY block above
            case GRAVITY -> player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 0, false, false, true));
            case DRAGON -> {
                player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                        org.bukkit.potion.PotionEffectType.SPEED, Integer.MAX_VALUE, 2, false, false, true)); // Speed III
                player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                        org.bukkit.potion.PotionEffectType.STRENGTH, Integer.MAX_VALUE, 1, false, false, true)); // Strength II
            }
            case WARDEN -> {
                // Debuff immunity — handled via CombatListener
                // Glowing aura + web walker handled via WardenPassiveListener
            }
            case SPEED -> player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.SPEED, Integer.MAX_VALUE, 1, false, false, true)); // Speed II
            case ARACHNID -> {} // passive handled via ArachnidPassiveListener (web walker)
            case TRADER -> player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.HERO_OF_THE_VILLAGE, Integer.MAX_VALUE, 0, false, false, true));
            default -> {}
        }
    }

    public void removePassiveEffects(Player player, RuneType rune) {
        switch (rune) {
            case STRENGTH -> {
                player.removePotionEffect(org.bukkit.potion.PotionEffectType.STRENGTH);
            }
            case VITALITY -> {
                double base = 20.0;
                player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).setBaseValue(base);
                if (player.getHealth() > base) player.setHealth(base);
            }
            case HASTE -> {
                player.removePotionEffect(org.bukkit.potion.PotionEffectType.HASTE);
            }
            case DRAGON -> {
                player.removePotionEffect(org.bukkit.potion.PotionEffectType.SPEED);
                player.removePotionEffect(org.bukkit.potion.PotionEffectType.STRENGTH);
            }
            case SPEED -> player.removePotionEffect(org.bukkit.potion.PotionEffectType.SPEED);
            case TRADER -> player.removePotionEffect(org.bukkit.potion.PotionEffectType.HERO_OF_THE_VILLAGE);
            case SHADOW -> {
                player.removePotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY);
            }
            case TIDAL -> {
                player.removePotionEffect(org.bukkit.potion.PotionEffectType.DOLPHINS_GRACE);
                player.removePotionEffect(org.bukkit.potion.PotionEffectType.WATER_BREATHING);
            }
            case DEFENDER -> {
                player.removePotionEffect(org.bukkit.potion.PotionEffectType.RESISTANCE);
            }
            case FIRE -> {
                player.removePotionEffect(org.bukkit.potion.PotionEffectType.FIRE_RESISTANCE);
            }
            case THIEF, TRADER -> {
                player.removePotionEffect(org.bukkit.potion.PotionEffectType.LUCK);
            }
            default -> {}
        }
        // Force HUD update to stop passive particles
        if (plugin.getHudManager() != null) {
            plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> plugin.getHudManager().updateHud(player), 1L);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    public CooldownManager getCooldowns() { return cooldownManager; }
    public java.util.Set<RuneType> getActivePrimariesSet() { return activePrimaries; }
    public boolean isPrimaryActive(RuneType rune) { return activePrimaries.contains(rune); }

    public UUID getPrimaryOwner(RuneType rune) {
        for (Map.Entry<UUID, PlayerRuneData> e : playerData.entrySet()) {
            if (rune.equals(e.getValue().getPrimaryRune())) return e.getKey();
        }
        return null;
    }

    public Map<UUID, PlayerRuneData> getAllPlayerData() { return Collections.unmodifiableMap(playerData); }
    public java.util.Map<UUID, RuneType> getPendingFullInvEquip() { return pendingFullInvEquip; }

    private static final String WIND_MACE_KEY = "wind_mace";

    public void giveWindMace(org.bukkit.entity.Player player) {
        removeWindMace(player); // prevent duplicates
        org.bukkit.inventory.ItemStack mace = new org.bukkit.inventory.ItemStack(org.bukkit.Material.MACE);
        org.bukkit.inventory.meta.ItemMeta meta = mace.getItemMeta();
        if (meta != null) {
            meta.displayName(net.kyori.adventure.text.Component.text("✦ Wind Mace")
                    .color(net.kyori.adventure.text.format.NamedTextColor.AQUA)
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)
                    .decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, true));
            meta.addEnchant(org.bukkit.enchantments.Enchantment.VANISHING_CURSE, 1, true);
            meta.setUnbreakable(true);
            meta.getPersistentDataContainer().set(
                new org.bukkit.NamespacedKey("runicsmp", WIND_MACE_KEY),
                org.bukkit.persistence.PersistentDataType.BOOLEAN, true);
            mace.setItemMeta(meta);
        }
        player.getInventory().addItem(mace);
        player.sendMessage("§b✦ Wind Mace added to your inventory!");
    }

    public void removeWindMace(org.bukkit.entity.Player player) {
        org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey("runicsmp", WIND_MACE_KEY);
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            org.bukkit.inventory.ItemStack item = player.getInventory().getItem(i);
            if (item == null) continue;
            org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.getPersistentDataContainer().has(key, org.bukkit.persistence.PersistentDataType.BOOLEAN)) {
                player.getInventory().setItem(i, null);
            }
        }
    }

}
