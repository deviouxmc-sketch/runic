package com.runicsmp.data;

import org.bukkit.ChatColor;

/**
 * All rune types with CMD matching book.json range_dispatch thresholds.
 * Primaries: 1001-1020, Secondaries: 2002-2004
 */
public enum RuneType {

    // ── PRIMARIES — CMD matches book.json thresholds ──────────────────────────
    WIND        (true,  1001, "\uE001", "Wind Rune",         ChatColor.AQUA),
    STRENGTH    (true,  1003, "\uE00B", "Strength Rune",     ChatColor.RED),
    SHADOW      (true,  1007, "\uE003", "Shadow Rune",       ChatColor.DARK_GRAY),
    LIFESTEAL   (true,  1014, "\uE004", "Lifesteal Rune",    ChatColor.DARK_RED),
    LOCKDOWN    (true,  1005, "\uE005", "Lockdown Rune",     ChatColor.DARK_PURPLE),
    TIDAL       (false, 2001, "\uE007", "Tidal Rune",        ChatColor.BLUE),
    DEFENDER    (true,  1009, "\uE00C", "Defender Rune",     ChatColor.GOLD),
    ENCHANTMENT (true,  1012, "\uE00D", "Enchantment Rune",  ChatColor.LIGHT_PURPLE),
    DRAGON      (true,  1013, "\uE00E", "Dragon Rune",       ChatColor.DARK_PURPLE),
    CLONE       (true,  1019, "\uE013", "Clone Rune",        ChatColor.YELLOW),
    TRACKER     (true,  1020, "\uE014", "Tracker Rune",      ChatColor.GREEN),
    THIEF       (true,  1010, "\uE002", "Thief Rune",        ChatColor.GRAY),
    TRADER      (false, 2005, "\uE00A", "Trader Rune",       ChatColor.GREEN),
    SWAP        (true,  1017, "\uE011", "Swap Rune",         ChatColor.WHITE),
    RESURRECTION(true,  1018, "\uE012", "Resurrection Rune", ChatColor.WHITE),
    HOARDER     (true,  1015, "\uE00F", "Hoarder Rune",      ChatColor.GOLD),
    WARPED      (true,  1016, "\uE010", "Warped Rune",       ChatColor.DARK_AQUA),

    GRAVITY     (true,  1021, "\uE015", "Gravity Rune",      ChatColor.DARK_PURPLE),
    LIGHTNING   (true,  1022, "\uE018", "Lightning Rune",    ChatColor.YELLOW),
    WARDEN      (true,  1023, "\uE019", "Warden Rune",       ChatColor.DARK_GREEN),
    ENDER       (true,  1024, "\uE01A", "Ender Rune",        ChatColor.DARK_PURPLE),
    SPEED       (true,  1025, "\uE01B", "Speed Rune",        ChatColor.WHITE),
    ARACHNID    (true,  1026, "\uE01C", "Arachnid Rune",     ChatColor.DARK_GREEN),
    SHAPESHIFTER(true,  1027, "\uE01D", "Shapeshifter Rune", ChatColor.DARK_AQUA),

    // ── SECONDARIES — CMD matches book.json thresholds ────────────────────────
    FIRE        (false, 2002, "\uE006", "Fire Rune",         ChatColor.RED),
    VITALITY    (false, 2003, "\uE008", "Vitality Rune",     ChatColor.LIGHT_PURPLE),
    HASTE       (false, 2004, "\uE009", "Haste Rune",        ChatColor.YELLOW),
    FROST       (false, 2006, "\uE016", "Frost Rune",        ChatColor.AQUA),
    DASH        (false, 2007, "\uE017", "Dash Rune",         ChatColor.WHITE);

    public static final String EMPTY_CHAR = "\uE000";

    private final boolean primary;
    private final int customModelData;
    private final String fontChar;
    private final String displayName;
    private final ChatColor color;

    RuneType(boolean primary, int customModelData, String fontChar, String displayName, ChatColor color) {
        this.primary = primary;
        this.customModelData = customModelData;
        this.fontChar = fontChar;
        this.displayName = displayName;
        this.color = color;
    }

    public boolean isPrimary() { return primary; }
    public boolean isSecondary() { return !primary; }
    public int getCustomModelData() { return customModelData; }
    public String getFontChar() { return fontChar; }
    public String getDisplayName() { return displayName; }
    public ChatColor getColor() { return color; }

    public String getColoredName() {
        return color + displayName;
    }
}
