package com.runicsmp.utils;

import com.runicsmp.data.RuneType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;

/**
 * Builds rune ItemStacks using BOOK as base material.
 * Custom Model Data matches the range_dispatch thresholds in book.json exactly,
 * so the resource pack overrides the texture correctly.
 */
public class RuneItemBuilder {

    public static final String RUNE_KEY = "rune_type";
    private static NamespacedKey runeKey;

    public static void init(Plugin plugin) {
        runeKey = new NamespacedKey(plugin, RUNE_KEY);
    }

    public static NamespacedKey getRuneKey() { return runeKey; }

    private static final java.util.Map<String, String[]> RUNE_TOOLTIPS = new java.util.HashMap<>();
    static {
        RUNE_TOOLTIPS.put("WIND", new String[]{"Passive: Negate all fall damage", "Ability: Dash in your look direction", "Perk: Receive a soulbound Wind Mace"});
        RUNE_TOOLTIPS.put("STRENGTH", new String[]{"Passive: Permanent Strength I", "Ability: Strength II for 10s + red fury aura"});
        RUNE_TOOLTIPS.put("SHADOW", new String[]{"Passive: True invisibility — you simply vanish", "Ability: Identity scramblers active", "Your name appears as random characters in chat"});
        RUNE_TOOLTIPS.put("LOCKDOWN", new String[]{"Passive: None", "Ability: Stun a nearby enemy for 3 seconds", "They cannot move, attack, or use abilities"});
        RUNE_TOOLTIPS.put("DEFENDER", new String[]{"Passive: Permanent Resistance I", "Ability: Defensive shield burst"});
        RUNE_TOOLTIPS.put("ENCHANTMENT", new String[]{"Passive: Enhanced enchanting luck", "Ability: Enchantment surge"});
        RUNE_TOOLTIPS.put("DRAGON", new String[]{"Passive: Speed III · Strength II · No fall damage", "Ability: Fire a Dragon Breath beam — 4 hearts", "Shift+Ability: Dragon Leap — crash-land for AoE damage"});
        RUNE_TOOLTIPS.put("CLONE", new String[]{"Passive: None", "Ability: Create a clone decoy of yourself"});
        RUNE_TOOLTIPS.put("TRACKER", new String[]{"Passive: Track a player's location", "Ability: Reveal enemy positions"});
        RUNE_TOOLTIPS.put("THIEF", new String[]{"Passive: Permanent Luck", "Ability: Steal resources from nearby players"});
        RUNE_TOOLTIPS.put("SWAP", new String[]{"Passive: None", "Ability: Instantly swap positions with a player"});
        RUNE_TOOLTIPS.put("RESURRECTION", new String[]{"Passive: Cheat death once — respawn in place", "Like a living totem of undying"});
        RUNE_TOOLTIPS.put("HOARDER", new String[]{"Passive: Increased item pickup radius", "Ability: Magnet pull nearby items"});
        RUNE_TOOLTIPS.put("WARPED", new String[]{"Passive: Nether immunity", "Ability: Warp reality around you"});
        RUNE_TOOLTIPS.put("GRAVITY", new String[]{"Passive: Permanent Resistance I", "Ability: Pull all nearby enemies toward you"});
        RUNE_TOOLTIPS.put("LIGHTNING", new String[]{"Passive: Every 7 consecutive hits → strike lightning", "Ability: Storm all enemies in 5 blocks", "Deals 1.5 hearts every 2s for 10 seconds"});
        RUNE_TOOLTIPS.put("WARDEN", new String[]{"Passive: Every 10 hits → 2s Blindness + Darkness", "Ability: Sonic Boom beam — 4 hearts true damage"});
        RUNE_TOOLTIPS.put("ENDER", new String[]{"Passive: None", "Ability: Throw an Ender Pearl (bypasses ban)"});
        RUNE_TOOLTIPS.put("SPEED", new String[]{"Passive: Permanent Speed II", "Ability: Receive a Wind Spear throwable"});
        RUNE_TOOLTIPS.put("ARACHNID", new String[]{"Passive: Web Walker — 50% speed through cobwebs", "Ability: Web Prison — trap nearby enemies in webs", "Enemies receive Poison III for 5 seconds"});
        RUNE_TOOLTIPS.put("TIDAL", new String[]{"Passive: Water Breathing · Dolphin's Grace", "Ability: Unleash a tidal wave — massive knockback"});
        RUNE_TOOLTIPS.put("FIRE", new String[]{"Passive: Permanent Fire Resistance", "Ability: Summon Blaze Army to fight for you", "Shift+Ability: Magma Domain — 9x9 burning field"});
        RUNE_TOOLTIPS.put("VITALITY", new String[]{"Passive: 12 heart maximum health", "Ability: Instantly restore to full health & hunger"});
        RUNE_TOOLTIPS.put("HASTE", new String[]{"Passive: Permanent Haste I (Haste II with God Pick)", "Ability: Haste 255 for 10 seconds — instant mine"});
        RUNE_TOOLTIPS.put("TRADER", new String[]{"Passive: Permanent Hero of the Village I", "Ability: Hero of the Village surge for 20s"});
        RUNE_TOOLTIPS.put("FROST", new String[]{"Passive: Powder snow immunity · Walk on snow freely", "Ability: Freeze nearby enemies for 3 seconds", "They cannot move or jump — but can still eat/pot"});
        RUNE_TOOLTIPS.put("DASH", new String[]{"Passive: Subtle wind wisps follow you", "Ability: Dash in your look direction — 8 blocks", "Fall damage negated for 4s after dashing"});
    }

    public static ItemStack createRune(RuneType type) {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        // Display name
        meta.displayName(Component.text(type.getDisplayName())
                .color(namedColor(type))
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true));

        // Lore — rich tooltips per rune
        String runeKind = type.isPrimary() ? "§7[§6Primary§7]" : "§7[§bSecondary§7]";
        java.util.List<Component> lore = new java.util.ArrayList<>();
        lore.add(Component.text("").decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(runeKind).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("").decoration(TextDecoration.ITALIC, false));

        // Per-rune description
        String[] tips = RUNE_TOOLTIPS.get(type.name());
        if (tips != null) {
            for (String tip : tips) {
                lore.add(Component.text(tip).color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false));
            }
            lore.add(Component.text("").decoration(TextDecoration.ITALIC, false));
        }

        if (type.isPrimary()) {
            lore.add(Component.text("▶ /ability — Ability 1").color(NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
            if (type == com.runicsmp.data.RuneType.DRAGON || type == com.runicsmp.data.RuneType.WARDEN
                    || type == com.runicsmp.data.RuneType.FIRE) {
                lore.add(Component.text("▶ Shift+Offhand (F) — Ability 2").color(NamedTextColor.GOLD)
                        .decoration(TextDecoration.ITALIC, false));
            }
        } else {
            lore.add(Component.text("▶ /ability secondary — Use ability").color(NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));
        }
        lore.add(Component.text("▶ Left-click to unequip").color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("✦ Shift+Smithing Table to craft").color(NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);

        // ── CRITICAL: Custom Model Data must match book.json range_dispatch threshold ──
        meta.setCustomModelData(type.getCustomModelData());

        // Store rune type in PDC for identification
        meta.getPersistentDataContainer().set(runeKey, PersistentDataType.STRING, type.name());

        item.setItemMeta(meta);
        return item;
    }

    public static RuneType getRuneType(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        String value = meta.getPersistentDataContainer().get(runeKey, PersistentDataType.STRING);
        if (value == null) return null;
        try {
            return RuneType.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static boolean isRune(ItemStack item) {
        return getRuneType(item) != null;
    }

    private static NamedTextColor namedColor(RuneType type) {
        return switch (type.getColor()) {
            case RED -> NamedTextColor.RED;
            case DARK_RED -> NamedTextColor.DARK_RED;
            case GOLD -> NamedTextColor.GOLD;
            case YELLOW -> NamedTextColor.YELLOW;
            case GREEN -> NamedTextColor.GREEN;
            case AQUA -> NamedTextColor.AQUA;
            case DARK_AQUA -> NamedTextColor.DARK_AQUA;
            case BLUE -> NamedTextColor.BLUE;
            case LIGHT_PURPLE -> NamedTextColor.LIGHT_PURPLE;
            case DARK_PURPLE -> NamedTextColor.DARK_PURPLE;
            case GRAY -> NamedTextColor.GRAY;
            case DARK_GRAY -> NamedTextColor.DARK_GRAY;
            case WHITE -> NamedTextColor.WHITE;
            default -> NamedTextColor.WHITE;
        };
    }
}
