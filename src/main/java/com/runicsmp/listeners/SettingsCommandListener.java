package com.runicsmp.listeners;

import com.runicsmp.RunicSMP;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class SettingsCommandListener implements CommandExecutor, Listener {

    private final RunicSMP plugin;
    private final NamespacedKey settingKey = new NamespacedKey("runicsmp", "setting");
    private final NamespacedKey pageKey    = new NamespacedKey("runicsmp", "page");

    // Per-player settings stored in memory (persisted to their playerdata yml)
    // Keys: particles_level(0-3), rune_notifs(true/false), ability_messages(true/false),
    //       cooldown_display(true/false), hud_enabled(true/false), particle_others(true/false),
    //       sound_effects(true/false), combat_log(true/false)
    private final Map<UUID, Map<String, String>> playerSettings = new HashMap<>();

    private static final Map<String, String[]> SETTING_OPTIONS = new LinkedHashMap<>();
    private static final Map<String, String> SETTING_DEFAULT = new HashMap<>();
    static {
        SETTING_OPTIONS.put("particles_level",    new String[]{"Off", "Low", "Medium", "High"});
        SETTING_OPTIONS.put("rune_notifs",        new String[]{"Off", "On"});
        SETTING_OPTIONS.put("ability_messages",   new String[]{"Off", "On"});
        SETTING_OPTIONS.put("cooldown_display",   new String[]{"Off", "On"});
        SETTING_OPTIONS.put("hud_enabled",        new String[]{"Off", "On"});
        SETTING_OPTIONS.put("particle_others",    new String[]{"Off", "On"});
        SETTING_OPTIONS.put("sound_effects",      new String[]{"Off", "On"});
        SETTING_OPTIONS.put("combat_log",         new String[]{"Off", "On"});

        SETTING_DEFAULT.put("particles_level",    "High");
        SETTING_DEFAULT.put("rune_notifs",        "On");
        SETTING_DEFAULT.put("ability_messages",   "On");
        SETTING_DEFAULT.put("cooldown_display",   "On");
        SETTING_DEFAULT.put("hud_enabled",        "On");
        SETTING_DEFAULT.put("particle_others",    "On");
        SETTING_DEFAULT.put("sound_effects",      "On");
        SETTING_DEFAULT.put("combat_log",         "On");
    }

    public SettingsCommandListener(RunicSMP plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    // ── Public API ───────────────────────────────────────────────────────────

    public String getSetting(Player player, String key) {
        return playerSettings
                .computeIfAbsent(player.getUniqueId(), k -> new HashMap<>())
                .getOrDefault(key, SETTING_DEFAULT.getOrDefault(key, "On"));
    }

    public int getParticleLevel(Player player) {
        return switch (getSetting(player, "particles_level")) {
            case "Off"    -> 0;
            case "Low"    -> 1;
            case "Medium" -> 2;
            default       -> 3;
        };
    }

    public boolean isNotifEnabled(Player player) {
        return getSetting(player, "rune_notifs").equals("On");
    }

    // ── Command ──────────────────────────────────────────────────────────────

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("§cPlayers only."); return true; }

        if (args.length > 0 && args[0].equalsIgnoreCase("help")) {
            sendHelp(player);
            return true;
        }

        openPage(player, 1);
        return true;
    }

    // ── GUI ──────────────────────────────────────────────────────────────────

    private void openPage(Player player, int page) {
        if (page == 1) openPlayerSettings(player);
        else if (page == 2 && player.isOp()) openOpSettings(player);
        else openPlayerSettings(player);
    }

    private void openPlayerSettings(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54,
                Component.text("✦ RunicSMP Settings").color(NamedTextColor.DARK_PURPLE).decoration(TextDecoration.BOLD, true));

        // Fill border with dark glass
        ItemStack border = glass(Material.PURPLE_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 9; i++) inv.setItem(i, border);
        for (int i = 45; i < 54; i++) inv.setItem(i, border);
        for (int i = 9; i < 45; i += 9) inv.setItem(i, border);
        for (int i = 17; i < 45; i += 9) inv.setItem(i, border);

        // Settings items (slots 10-43 inner area)
        int[] slots = {10, 12, 14, 16, 28, 30, 32, 34};
        String[] keys = {"particles_level", "rune_notifs", "ability_messages", "cooldown_display",
                         "hud_enabled", "particle_others", "sound_effects", "combat_log"};

        Material[] icons = {
            Material.FIREWORK_ROCKET, Material.PAPER, Material.BOOK,
            Material.CLOCK, Material.PAINTING, Material.BLAZE_ROD,
            Material.NOTE_BLOCK, Material.WRITTEN_BOOK
        };
        String[] labels = {
            "Particle Level", "Rune Notifications", "Ability Messages",
            "Cooldown Display", "HUD Enabled", "Others' Particles",
            "Sound Effects", "Combat Log"
        };
        String[] descs = {
            "How many particles you see",
            "Show messages when rune activates",
            "Show ability activation chat messages",
            "Show cooldown timer in HUD",
            "Enable the action bar HUD",
            "See particles from other players",
            "Play sounds for rune abilities",
            "Show combat log messages"
        };

        for (int i = 0; i < keys.length; i++) {
            String current = getSetting(player, keys[i]);
            String[] opts = SETTING_OPTIONS.get(keys[i]);
            String next = nextOption(opts, current);
            inv.setItem(slots[i], settingItem(icons[i], labels[i], keys[i], current, next, descs[i], 1));
        }

        // Navigation: next page (OP only)
        if (player.isOp()) {
            inv.setItem(53, navItem(Material.ARROW, "§aOP Settings →", "page_2", 2));
        }

        // Close button
        inv.setItem(49, closeItem());

        player.openInventory(inv);
    }

    private void openOpSettings(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54,
                Component.text("✦ OP Settings").color(NamedTextColor.RED).decoration(TextDecoration.BOLD, true));

        // Border
        ItemStack border = glass(Material.RED_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 9; i++) inv.setItem(i, border);
        for (int i = 45; i < 54; i++) inv.setItem(i, border);
        for (int i = 9; i < 45; i += 9) inv.setItem(i, border);
        for (int i = 17; i < 45; i += 9) inv.setItem(i, border);

        // Op toggles
        String[] opKeys   = {"ritual_mode_block", "pvp_global", "end_access", "nether_access",
                              "broadcast_particles", "admin_debug", "drop_protection", "god_mode_test"};
        Material[] opIcons = {Material.FURNACE, Material.IRON_SWORD, Material.END_PORTAL_FRAME,
                               Material.NETHER_BRICK, Material.FIRE_CHARGE, Material.COMMAND_BLOCK,
                               Material.CHEST, Material.TOTEM_OF_UNDYING};
        String[] opLabels  = {"Ritual Mode Block", "Global PvP", "End Access",
                               "Nether Access", "Broadcast Particles", "Admin Debug Mode",
                               "Drop Protection", "God Mode (Test)"};
        String[] opDescs   = {"Block all abilities during ritual", "Toggle global PvP on/off",
                               "Allow end portal access", "Allow nether portal access",
                               "Broadcast particle events globally", "Show debug info in console",
                               "Prevent item drops on death", "Enable god mode for testing"};
        int[] opSlots = {10, 12, 14, 16, 28, 30, 32, 34};

        for (int i = 0; i < opKeys.length; i++) {
            String current = getSetting(player, opKeys[i]);
            if (current.equals("On") || current.equals("Off")) ; else current = "Off";
            String next = current.equals("On") ? "Off" : "On";
            inv.setItem(opSlots[i], settingItem(opIcons[i], opLabels[i], opKeys[i], current, next, opDescs[i], 2));
        }

        // Back button
        inv.setItem(45, navItem(Material.ARROW, "§7← Back to Settings", "page_1", 1));
        inv.setItem(49, closeItem());

        player.openInventory(inv);
    }

    // ── Click handler ─────────────────────────────────────────────────────────

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(event.getView().title());
        if (!title.contains("RunicSMP Settings") && !title.contains("OP Settings")) return;

        event.setCancelled(true);
        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType() == Material.AIR) return;

        var pdc = item.getItemMeta() == null ? null : item.getItemMeta().getPersistentDataContainer();
        if (pdc == null) return;

        // Nav button?
        if (pdc.has(pageKey, PersistentDataType.INTEGER)) {
            int targetPage = pdc.get(pageKey, PersistentDataType.INTEGER);
            player.closeInventory();
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> openPage(player, targetPage), 1L);
            return;
        }

        if (!pdc.has(settingKey, PersistentDataType.STRING)) return;

        String settingId = pdc.get(settingKey, PersistentDataType.STRING);
        String[] opts = SETTING_OPTIONS.getOrDefault(settingId, new String[]{"Off", "On"});
        String current = getSetting(player, settingId);
        String next = nextOption(opts, current);

        playerSettings.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>()).put(settingId, next);
        player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);

        // Refresh the GUI
        player.closeInventory();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (title.contains("OP Settings")) openOpSettings(player);
            else openPlayerSettings(player);
        }, 1L);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String nextOption(String[] opts, String current) {
        for (int i = 0; i < opts.length; i++) {
            if (opts[i].equals(current)) return opts[(i + 1) % opts.length];
        }
        return opts[0];
    }

    private ItemStack settingItem(Material mat, String label, String settingId,
                                   String current, String next, String desc, int page) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        boolean isOn = current.equals("On") || current.equals("High") || current.equals("Medium");

        meta.displayName(Component.text("⚙ " + label)
                .color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(desc).color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("Current: ").color(NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(current).color(isOn ? NamedTextColor.GREEN : NamedTextColor.RED)
                        .decoration(TextDecoration.BOLD, true).decoration(TextDecoration.ITALIC, false)));
        lore.add(Component.text("Click to set: ").color(NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(next).color(NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false)));

        meta.lore(lore);
        meta.getPersistentDataContainer().set(settingKey, PersistentDataType.STRING, settingId);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack navItem(Material mat, String label, String action, int targetPage) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(label).decoration(TextDecoration.ITALIC, false));
        meta.getPersistentDataContainer().set(pageKey, PersistentDataType.INTEGER, targetPage);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack glass(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack closeItem() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("✖ Close").color(NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
        meta.getPersistentDataContainer().set(settingKey, PersistentDataType.STRING, "__close__");
        item.setItemMeta(meta);
        return item;
    }
    private void sendHelp(Player player) {
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("  ✦ RunicSMP Settings Help ✦")
                .color(NamedTextColor.DARK_PURPLE).decoration(TextDecoration.BOLD, true));
        player.sendMessage(Component.text("  ─────────────────────────────")
                .color(NamedTextColor.DARK_GRAY));
        player.sendMessage(Component.empty());

        sendHelpLine(player, "Particle Level",
                "Controls how many particles you see from rune abilities.",
                "Off = none, Low = minimal, Medium = reduced, High = full");
        sendHelpLine(player, "Rune Notifications",
                "Shows a chat message when your rune goes on cooldown.",
                "Turn off to reduce chat clutter.");
        sendHelpLine(player, "Ability Messages",
                "Shows '§5Dragon Rune activated!§7' style messages when you use abilities.",
                "Turn off if you find them annoying.");
        sendHelpLine(player, "Cooldown Display",
                "Shows your cooldown timer in the HUD action bar.",
                "Turn off to hide cooldown numbers.");
        sendHelpLine(player, "HUD Enabled",
                "Toggles the entire action bar HUD (rune icons + cooldowns).",
                "Turn off for a cleaner screen.");
        sendHelpLine(player, "Others\' Particles",
                "Whether you see particle effects from OTHER players\' runes.",
                "Turn off if nearby players are causing visual lag.");
        sendHelpLine(player, "Sound Effects",
                "Plays sounds when you or others use rune abilities.",
                "Turn off to mute all rune sounds.");
        sendHelpLine(player, "Combat Log",
                "Shows combat log messages when taking or dealing damage.",
                "Turn off to hide damage numbers in chat.");

        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("  Use §d/settings§7 to open the GUI, or §d/settings help§7 for this page.")
                .color(NamedTextColor.GRAY));
        player.sendMessage(Component.empty());
    }

    private void sendHelpLine(Player player, String setting, String desc, String detail) {
        player.sendMessage(Component.text("  § §e" + setting)
                .color(NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(" §e" + setting).color(NamedTextColor.YELLOW)
                        .decoration(TextDecoration.BOLD, true)));
        player.sendMessage(Component.text("    §7" + desc).color(NamedTextColor.GRAY));
        player.sendMessage(Component.text("    §8" + detail).color(NamedTextColor.DARK_GRAY));
        player.sendMessage(Component.empty());
    }

}
