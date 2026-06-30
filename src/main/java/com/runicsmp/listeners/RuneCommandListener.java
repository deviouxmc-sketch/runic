package com.runicsmp.listeners;

import com.runicsmp.RunicSMP;
import com.runicsmp.data.PlayerRuneData;
import com.runicsmp.data.RuneType;
import com.runicsmp.managers.RuneCraftingManager;
import com.runicsmp.utils.RuneItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

public class RuneCommandListener implements CommandExecutor, TabCompleter {

    private final RunicSMP plugin;

    public RuneCommandListener(RunicSMP plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        if (command.getName().equalsIgnoreCase("runeadmin")) return handleAdmin(sender, args);
        if (command.getName().equalsIgnoreCase("withdraw")) return handleWithdraw(sender, args);
        if (command.getName().equalsIgnoreCase("ritualtpto")) return handleRitualTp(sender, args);
        if (command.getName().equalsIgnoreCase("rune")) return handleRune(sender, args);
        return false;
    }

    // ── /rune ─────────────────────────────────────────────────────────────────

    private boolean handleRune(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("§cOnly players can use this."); return true; }

        if (args.length == 0) { sendRuneInfo(player); return true; }

        switch (args[0].toLowerCase()) {
            case "info" -> sendRuneInfo(player);
            case "track" -> {
                if (args.length < 2) { player.sendMessage("§cUsage: /rune track <player>"); return true; }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) { player.sendMessage("§cPlayer not found."); return true; }
                PlayerRuneData data = plugin.getRuneManager().getData(player);
                if (!data.hasPrimary() || data.getPrimaryRune() != RuneType.TRACKER) {
                    player.sendMessage("§cYou need the Tracker Rune to track players."); return true;
                }
                data.setTrackerTarget(target.getUniqueId());
                player.sendMessage("§aNow tracking §e" + target.getName() + "§a.");
            }
            case "reload" -> {
                if (!sender.hasPermission("runicsmp.admin")) { player.sendMessage("§cNo permission."); return true; }
                plugin.reloadConfig();
                player.sendMessage("§aConfig reloaded.");
            }
            case "history" -> handleHistory(player);
            case "ritual"  -> handleForcedRitual(player, args);
            case "altar"   -> handleAltar(player, args);
            default -> {
                // /rune <runename> — start a crafting ritual
                String runeName = args[0].toUpperCase();
                RuneType rune;
                try {
                    rune = RuneType.valueOf(runeName);
                } catch (IllegalArgumentException e) {
                    player.sendMessage("§cUnknown rune or subcommand: §e" + args[0]);
                    player.sendMessage("§7Use §e/rune info§7, §e/rune track <player>§7, or §e/rune <runename>§7 to start a ritual.");
                    return true;
                }

                // Check they have the recipe items
                RuneCraftingManager cm = plugin.getCraftingManager();
                if (cm.getRecipe(rune) == null) {
                    player.sendMessage("§c" + rune.getDisplayName() + " has no craftable recipe.");
                    return true;
                }
                if (!cm.hasAllItems(player, cm.getRecipe(rune))) {
                    player.sendMessage("§cYou don't have the required items to craft §e" + rune.getDisplayName() + "§c!");
                    player.sendMessage("§7Use §e/recipes " + rune.name().toLowerCase() + "§7 to see what you need.");
                    return true;
                }

                if (rune.isPrimary()) {
                    boolean started = plugin.getRitualManager().startRitual(player, rune);
                    if (started) cm.consumeItems(player, cm.getRecipe(rune));
                } else {
                    // Instant craft for secondary
                    cm.consumeItems(player, cm.getRecipe(rune));
                    PlayerRuneData data = plugin.getRuneManager().getData(player);
                    if (data.hasSecondary()) {
                        plugin.getRuneManager().unequipRune(player, data.getSecondaryRune());
                        player.sendMessage("§eYour old secondary was returned to your inventory.");
                    }
                    player.getInventory().addItem(RuneItemBuilder.createRune(rune));
                    player.sendMessage("§a§lCrafted: §e" + rune.getDisplayName() + "§a! Right-click to equip.");
                }
            }
        }
        return true;
    }

    // ── /withdraw ─────────────────────────────────────────────────────────────

    private boolean handleWithdraw(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("§cOnly players can use this."); return true; }
        if (args.length < 1) {
            player.sendMessage("§cUsage: /withdraw <left|right|primary|secondary|<runename>>");
            return true;
        }

        String slot = args[0].toLowerCase();

        // ── OP: /withdraw <runename> — finds whoever has it online or offline ──
        if (player.isOp()) {
            RuneType targetRune = null;
            try { targetRune = RuneType.valueOf(slot.toUpperCase()); } catch (IllegalArgumentException ignored) {}

            if (targetRune != null && targetRune.isPrimary()) {
                // Search online players first
                for (org.bukkit.entity.Player online : org.bukkit.Bukkit.getOnlinePlayers()) {
                    PlayerRuneData d = plugin.getRuneManager().getData(online);
                    if (d.hasPrimary() && d.getPrimaryRune() == targetRune) {
                        plugin.getRuneManager().unequipRune(online, targetRune);
                        org.bukkit.inventory.ItemStack[] inv = online.getInventory().getContents();
                        for (int i = 0; i < inv.length; i++) {
                            if (com.runicsmp.utils.RuneItemBuilder.isRune(inv[i])
                                    && targetRune.equals(com.runicsmp.utils.RuneItemBuilder.getRuneType(inv[i]))) {
                                online.getInventory().setItem(i, null); break;
                            }
                        }
                        giveOrDrop(player, com.runicsmp.utils.RuneItemBuilder.createRune(targetRune));
                        player.sendMessage("§aWithdrew §e" + online.getName() + "§a's §e" + targetRune.getDisplayName() + "§a.");
                        online.sendMessage("§cAn operator withdrew your §e" + targetRune.getDisplayName() + "§c.");
                        return true;
                    }
                }
                // Search offline players
                java.io.File dataFolder = new java.io.File(plugin.getDataFolder(), "playerdata");
                if (dataFolder.exists()) {
                    for (java.io.File f : dataFolder.listFiles()) {
                        if (f == null || !f.getName().endsWith(".yml")) continue;
                        org.bukkit.configuration.file.YamlConfiguration cfg =
                                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(f);
                        if (targetRune.name().equals(cfg.getString("primary"))) {
                            String uuidStr = f.getName().replace(".yml", "");
                            org.bukkit.OfflinePlayer op = org.bukkit.Bukkit.getOfflinePlayer(java.util.UUID.fromString(uuidStr));
                            cfg.set("primary", null);
                            try { cfg.save(f); } catch (java.io.IOException e) { player.sendMessage("§cFailed to save data."); return true; }
                            giveOrDrop(player, com.runicsmp.utils.RuneItemBuilder.createRune(targetRune));
                            player.sendMessage("§aWithdrew " + (op.getName() != null ? "§e" + op.getName() + "§a's " : "offline player's ") + "§e" + targetRune.getDisplayName() + "§a.");
                            return true;
                        }
                    }
                }
                player.sendMessage("§cNo one currently has the §e" + targetRune.getDisplayName() + "§c equipped.");
                return true;
            }
        }

        // ── OP: /withdraw primary <player> — works for online AND offline players ──
        if ((slot.equals("primary") || slot.equals("left")) && args.length >= 2) {
            if (!player.isOp()) {
                player.sendMessage("§cYou must be an operator to withdraw another player's rune!");
                return true;
            }

            org.bukkit.entity.Player onlineTarget = org.bukkit.Bukkit.getPlayer(args[1]);

            if (onlineTarget != null) {
                // ── ONLINE PATH ──
                PlayerRuneData targetData = plugin.getRuneManager().getData(onlineTarget);
                if (!targetData.hasPrimary()) {
                    player.sendMessage("§e" + onlineTarget.getName() + "§c has no primary rune equipped.");
                    return true;
                }
                RuneType stolen = targetData.getPrimaryRune();
                plugin.getRuneManager().unequipRune(onlineTarget, stolen);
                org.bukkit.inventory.ItemStack[] inv = onlineTarget.getInventory().getContents();
                for (int i = 0; i < inv.length; i++) {
                    if (com.runicsmp.utils.RuneItemBuilder.isRune(inv[i])
                            && stolen.equals(com.runicsmp.utils.RuneItemBuilder.getRuneType(inv[i]))) {
                        onlineTarget.getInventory().setItem(i, null);
                        break;
                    }
                }
                giveOrDrop(player, com.runicsmp.utils.RuneItemBuilder.createRune(stolen));
                player.sendMessage("§aWithdrew §e" + onlineTarget.getName() + "§a's §e"
                        + stolen.getDisplayName() + "§a into your inventory.");
                onlineTarget.sendMessage("§cAn operator has withdrawn your §e" + stolen.getDisplayName() + "§c.");
                return true;
            }

            // ── OFFLINE PATH — read/write their saved data file directly ──
            org.bukkit.OfflinePlayer offlineTarget = org.bukkit.Bukkit.getOfflinePlayer(args[1]);
            if (!offlineTarget.hasPlayedBefore()) {
                player.sendMessage("§cPlayer §e" + args[1] + "§c not found (never joined).");
                return true;
            }

            java.io.File dataFile = new java.io.File(plugin.getDataFolder(),
                    "playerdata/" + offlineTarget.getUniqueId() + ".yml");
            if (!dataFile.exists()) {
                player.sendMessage("§e" + args[1] + "§c has no saved rune data.");
                return true;
            }

            org.bukkit.configuration.file.YamlConfiguration cfg =
                    org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(dataFile);
            String primaryStr = cfg.getString("primary");
            if (primaryStr == null) {
                player.sendMessage("§e" + args[1] + "§c has no primary rune equipped.");
                return true;
            }

            RuneType stolen;
            try { stolen = RuneType.valueOf(primaryStr); }
            catch (IllegalArgumentException e) {
                player.sendMessage("§cCorrupted rune data for " + args[1]);
                return true;
            }

            // Clear their saved primary
            cfg.set("primary", null);
            try { cfg.save(dataFile); }
            catch (java.io.IOException e) { player.sendMessage("§cFailed to update offline player data."); return true; }

            // Also try to remove the physical item from their offline inventory if it exists in their ender chest/inv NBT — Bukkit can't directly edit offline player inventories without NBT libs, so we just clear the rune-data flag (the item itself, if duped in their inv, becomes a regular cosmetic item with no function since data flag is gone)

            giveOrDrop(player, com.runicsmp.utils.RuneItemBuilder.createRune(stolen));
            player.sendMessage("§aWithdrew §e" + args[1] + "§a's (offline) §e"
                    + stolen.getDisplayName() + "§a into your inventory.");
            return true;
        }

        // ── OP with target: /withdraw <slot> <player> ──────────────────────────
        if (player.isOp() && args.length >= 2) {
            String targetName = args[1];
            org.bukkit.entity.Player onlineTarget = org.bukkit.Bukkit.getPlayer(targetName);

            if (onlineTarget != null) {
                // Online player
                PlayerRuneData targetData = plugin.getRuneManager().getData(onlineTarget);
                RuneType stolen = switch (slot) {
                    case "left", "primary" -> targetData.getPrimaryRune();
                    case "right", "secondary" -> targetData.getSecondaryRune();
                    default -> null;
                };
                if (stolen == null) { player.sendMessage("§e" + onlineTarget.getName() + "§c has no rune in that slot."); return true; }
                plugin.getRuneManager().unequipRune(onlineTarget, stolen);
                org.bukkit.inventory.ItemStack[] inv = onlineTarget.getInventory().getContents();
                for (int i = 0; i < inv.length; i++) {
                    if (com.runicsmp.utils.RuneItemBuilder.isRune(inv[i]) && stolen.equals(com.runicsmp.utils.RuneItemBuilder.getRuneType(inv[i]))) {
                        onlineTarget.getInventory().setItem(i, null); break;
                    }
                }
                giveOrDrop(player, com.runicsmp.utils.RuneItemBuilder.createRune(stolen));
                player.sendMessage("§aWithdrew §e" + onlineTarget.getName() + "§a's §e" + stolen.getDisplayName() + "§a.");
                onlineTarget.sendMessage("§cAn operator withdrew your §e" + stolen.getDisplayName() + "§c.");
                return true;
            }

            // Offline player — read from file
            org.bukkit.OfflinePlayer offlineTarget = org.bukkit.Bukkit.getOfflinePlayer(targetName);
            if (!offlineTarget.hasPlayedBefore()) { player.sendMessage("§cPlayer §e" + targetName + "§c not found."); return true; }
            java.io.File dataFile = new java.io.File(plugin.getDataFolder(), "playerdata/" + offlineTarget.getUniqueId() + ".yml");
            if (!dataFile.exists()) { player.sendMessage("§e" + targetName + "§c has no saved rune data."); return true; }
            org.bukkit.configuration.file.YamlConfiguration cfg = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(dataFile);
            String key = (slot.equals("primary") || slot.equals("left")) ? "primary" : "secondary";
            String runeStr = cfg.getString(key);
            if (runeStr == null) { player.sendMessage("§e" + targetName + "§c has no " + key + " rune saved."); return true; }
            RuneType stolen;
            try { stolen = RuneType.valueOf(runeStr); } catch (IllegalArgumentException e) { player.sendMessage("§cCorrupted data."); return true; }
            cfg.set(key, null);
            try { cfg.save(dataFile); } catch (java.io.IOException e) { player.sendMessage("§cFailed to save offline data."); return true; }
            giveOrDrop(player, com.runicsmp.utils.RuneItemBuilder.createRune(stolen));
            player.sendMessage("§aWithdrew §e" + targetName + "§a's (offline) §e" + stolen.getDisplayName() + "§a.");
            return true;
        }

        // ── Self-withdraw ─────────────────────────────────────────────────────
        PlayerRuneData data = plugin.getRuneManager().getData(player);

        RuneType toWithdraw = switch (slot) {
            case "left", "primary" -> data.getPrimaryRune();
            case "right", "secondary" -> data.getSecondaryRune();
            default -> null;
        };

        if (toWithdraw == null) {
            if (slot.equals("left") || slot.equals("primary")) {
                player.sendMessage("§cYou don't have a primary rune equipped.");
            } else if (slot.equals("right") || slot.equals("secondary")) {
                player.sendMessage("§cYou don't have a secondary rune equipped.");
            } else {
                player.sendMessage("§cUsage: /withdraw <left|right|primary|secondary>");
            }
            return true;
        }

        plugin.getRuneManager().unequipRune(player, toWithdraw);
        return true;
    }

    // ── /ritualtpto ───────────────────────────────────────────────────────────

    private boolean handleRitualTp(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("§cOnly players can use this."); return true; }
        if (args.length < 1) { player.sendMessage("§cUsage: /ritualtpto <player>"); return true; }
        plugin.getRitualTpManager().tpToRitual(player, args[0]);
        return true;
    }

    // ── /runeadmin ────────────────────────────────────────────────────────────

    private boolean handleAdmin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("runicsmp.admin")) { sender.sendMessage("§cNo permission."); return true; }
        if (args.length < 3) { sender.sendMessage("§cUsage: /runeadmin <give|remove|set> <player> <rune>"); return true; }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) { sender.sendMessage("§cPlayer not found: " + args[1]); return true; }

        RuneType rune;
        try { rune = RuneType.valueOf(args[2].toUpperCase()); }
        catch (IllegalArgumentException e) { sender.sendMessage("§cUnknown rune: " + args[2]); return true; }

        switch (args[0].toLowerCase()) {
            case "give" -> {
                target.getInventory().addItem(RuneItemBuilder.createRune(rune));
                sender.sendMessage("§aGave §e" + rune.getDisplayName() + " §ato §e" + target.getName());
                target.sendMessage("§aAn admin gave you a §e" + rune.getDisplayName() + "§a.");
            }
            case "givesecondary" -> {
                // Only allow secondary runes
                if (rune.isPrimary()) {
                    sender.sendMessage("§c" + rune.getDisplayName() + " is a primary rune. Use /runeadmin give for primaries.");
                    return true;
                }
                target.getInventory().addItem(RuneItemBuilder.createRune(rune));
                sender.sendMessage("§aGave secondary §e" + rune.getDisplayName() + " §ato §e" + target.getName());
                target.sendMessage("§aAn admin gave you the secondary §e" + rune.getDisplayName() + "§a.");
            }
            case "remove" -> {
                PlayerRuneData data = plugin.getRuneManager().getData(target);
                if ((rune.isPrimary() && rune.equals(data.getPrimaryRune()))
                        || (rune.isSecondary() && rune.equals(data.getSecondaryRune()))) {
                    plugin.getRuneManager().unequipRune(target, rune);
                    sender.sendMessage("§aRemoved §e" + rune.getDisplayName() + " §afrom §e" + target.getName());
                } else {
                    sender.sendMessage("§c" + target.getName() + " doesn't have that rune equipped.");
                }
            }
            case "set" -> {
                plugin.getRuneManager().equipRune(target, rune);
                sender.sendMessage("§aSet §e" + target.getName() + "§a's rune to §e" + rune.getDisplayName());
                target.sendMessage("§aAn admin set your rune to §e" + rune.getDisplayName() + "§a.");
            }
            default -> sender.sendMessage("§cUnknown subcommand.");
        }
        return true;
    }

    private void sendRuneInfo(Player player) {
        PlayerRuneData data = plugin.getRuneManager().getData(player);
        player.sendMessage("§6§l--- Your Rune Info ---");
        player.sendMessage("§ePrimary: §f" + (data.hasPrimary() ? data.getPrimaryRune().getDisplayName() : "None"));
        player.sendMessage("§bSecondary: §f" + (data.hasSecondary() ? data.getSecondaryRune().getDisplayName() : "None"));
        player.sendMessage("§aEnergy Level: §f" + data.getEnergyLevel() + "/5");
        if (data.hasPrimary() && data.getPrimaryRune() == RuneType.LIFESTEAL)
            player.sendMessage("§cLifesteal Bonus Hearts: §f" + data.getLifeStealBonusHearts());
        player.sendMessage("§7Use §e/withdraw left§7 or §e/withdraw right§7 to unequip a rune slot.");
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                  @NotNull String label, @NotNull String[] args) {
        if (command.getName().equalsIgnoreCase("runeadmin")) {
            if (args.length == 1) return List.of("give", "givesecondary", "remove", "set");
            if (args.length == 2) return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
            if (args.length == 3) {
                if (args[0].equalsIgnoreCase("givesecondary")) {
                    return Arrays.stream(RuneType.values())
                            .filter(RuneType::isSecondary)
                            .map(r -> r.name().toLowerCase()).toList();
                }
                return Arrays.stream(RuneType.values()).map(r -> r.name().toLowerCase()).toList();
            }
        }
        if (command.getName().equalsIgnoreCase("rune")) {
            if (args.length == 1) {
                List<String> opts = new ArrayList<>(List.of("info", "track", "reload"));
                Arrays.stream(RuneType.values()).map(r -> r.name().toLowerCase()).forEach(opts::add);
                return opts.stream().filter(s -> s.startsWith(args[0].toLowerCase())).toList();
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("track"))
                return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        }
        if (command.getName().equalsIgnoreCase("withdraw")) {
            if (args.length == 1) return List.of("left", "right", "primary", "secondary");
        }
        if (command.getName().equalsIgnoreCase("ritualtpto")) {
            if (args.length == 1) return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        }
        return List.of();
    }
    private void handleHistory(Player player) {
        // OP only
        if (!player.isOp()) {
            player.sendMessage("§cOnly operators can use /rune history.");
            return;
        }

        // Open a GUI chest menu with all rune types to pick from
        int size = 54; // 6 rows
        org.bukkit.inventory.Inventory gui = plugin.getServer().createInventory(null, size,
                net.kyori.adventure.text.Component.text("§5§lRune History — Pick a Rune"));

        // Fill with rune items
        int slot = 0;
        for (com.runicsmp.data.RuneType rune : com.runicsmp.data.RuneType.values()) {
            if (slot >= size) break;
            org.bukkit.inventory.ItemStack icon = com.runicsmp.utils.RuneItemBuilder.createRune(rune);
            org.bukkit.inventory.meta.ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                // Add lore showing who currently has it
                java.util.List<net.kyori.adventure.text.Component> lore = new java.util.ArrayList<>();
                lore.add(net.kyori.adventure.text.Component.text("§8─────────────────"));
                boolean found = false;
                for (org.bukkit.entity.Player online : plugin.getServer().getOnlinePlayers()) {
                    var data = plugin.getRuneManager().getData(online);
                    boolean hasPrimary = data.hasPrimary() && data.getPrimaryRune() == rune;
                    boolean hasSecondary = data.hasSecondary() && data.getSecondaryRune() == rune;
                    if (hasPrimary) {
                        lore.add(net.kyori.adventure.text.Component.text("§e" + online.getName() + " §a[Primary]"));
                        found = true;
                    } else if (hasSecondary) {
                        lore.add(net.kyori.adventure.text.Component.text("§e" + online.getName() + " §b[Secondary]"));
                        found = true;
                    } else {
                        // Check inventory
                        for (org.bukkit.inventory.ItemStack item : online.getInventory().getContents()) {
                            if (com.runicsmp.utils.RuneItemBuilder.isRune(item)
                                    && com.runicsmp.utils.RuneItemBuilder.getRuneType(item) == rune) {
                                lore.add(net.kyori.adventure.text.Component.text("§e" + online.getName() + " §7[Inventory]"));
                                found = true;
                                break;
                            }
                        }
                        // Check ender chest
                        for (org.bukkit.inventory.ItemStack item : online.getEnderChest().getContents()) {
                            if (com.runicsmp.utils.RuneItemBuilder.isRune(item)
                                    && com.runicsmp.utils.RuneItemBuilder.getRuneType(item) == rune) {
                                lore.add(net.kyori.adventure.text.Component.text("§e" + online.getName() + " §d[Ender Chest]"));
                                found = true;
                                break;
                            }
                        }
                    }
                }
                if (!found) {
                    lore.add(net.kyori.adventure.text.Component.text("§7No online players have this rune"));
                }
                lore.add(net.kyori.adventure.text.Component.text("§8─────────────────"));
                meta.lore(lore);
                icon.setItemMeta(meta);
            }
            gui.setItem(slot++, icon);
        }

        player.openInventory(gui);
    }
    private void handleForcedRitual(Player player, String[] args) {
        if (!player.isOp()) {
            player.sendMessage("§cOnly operators can force-start rituals.");
            return;
        }
        if (args.length < 2) {
            player.sendMessage("§cUsage: /rune ritual <rune_name>");
            player.sendMessage("§7Example: §e/rune ritual resurrection");
            return;
        }

        String runeName = String.join("_", java.util.Arrays.copyOfRange(args, 1, args.length)).toUpperCase();
        com.runicsmp.data.RuneType rune;
        try {
            rune = com.runicsmp.data.RuneType.valueOf(runeName);
        } catch (IllegalArgumentException e) {
            player.sendMessage("§cUnknown rune: §e" + runeName);
            return;
        }

        if (!rune.isPrimary()) {
            player.sendMessage("§cOnly primary runes can have rituals. Use /runeadmin givesecondary for secondaries.");
            return;
        }

        // Start ritual at player's current location
        plugin.getRitualManager().startRitual(player, rune);
        player.sendMessage("§6⚗ Ritual started for §e" + rune.getDisplayName() + "§6 at your location!");
    }

    private void handleAltar(Player player, String[] args) {
        if (!player.isOp()) {
            player.sendMessage("§cOnly operators can summon altars.");
            return;
        }
        if (args.length < 3) {
            player.sendMessage("§cUsage: /rune altar <item> <player>");
            player.sendMessage("§7Example: §e/rune altar GodBoots Stalari");
            return;
        }
        if (!args[1].equalsIgnoreCase("GodBoots")) {
            player.sendMessage("§cUnknown altar item: §e" + args[1]);
            return;
        }
        org.bukkit.entity.Player target = org.bukkit.Bukkit.getPlayer(args[2]);
        if (target == null) { player.sendMessage("§cPlayer not found."); return; }

        // Delegate to AltarListener
        plugin.getAltarListener().spawnAltarForPlayer(player, target);
    }

    private void giveOrDrop(Player player, org.bukkit.inventory.ItemStack item) {
        if (player.getInventory().firstEmpty() != -1) {
            player.getInventory().addItem(item);
        } else {
            player.getWorld().dropItemNaturally(player.getLocation(), item);
        }
    }

}
