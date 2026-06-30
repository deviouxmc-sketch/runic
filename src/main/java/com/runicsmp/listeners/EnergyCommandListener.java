package com.runicsmp.listeners;

import com.runicsmp.RunicSMP;
import com.runicsmp.data.PlayerRuneData;
import com.runicsmp.managers.EnergyManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * /energy                         — show your energy level + stage name
 * /energy bank                    — show your banked energy with visual bar
 * /energy set <player> <0-5>      — OP: force-set energy level
 * /energy give <player> <amount>  — give energy levels to a player
 * /energy withdraw <amount>       — drop a physical energy item (withdraws from bank)
 */
public class EnergyCommandListener implements CommandExecutor, TabCompleter {

    private final RunicSMP plugin;

    public EnergyCommandListener(RunicSMP plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use /energy.");
            return true;
        }

        PlayerRuneData data = plugin.getRuneManager().getData(player);

        // /energy with no args — show status
        if (args.length == 0) {
            sendEnergyStatus(player, data);
            return true;
        }

        switch (args[0].toLowerCase()) {

            case "bank" -> sendBankStatus(player, data);

            case "set" -> {
                if (!player.hasPermission("runicsmp.admin")) {
                    player.sendMessage("§cNo permission.");
                    return true;
                }
                if (args.length < 3) {
                    player.sendMessage("§cUsage: /energy set <player> <0-5>");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) { player.sendMessage("§cPlayer not found: " + args[1]); return true; }

                int level;
                try { level = Integer.parseInt(args[2]); }
                catch (NumberFormatException e) { player.sendMessage("§cInvalid number."); return true; }

                if (level < 0 || level > 5) {
                    player.sendMessage("§cEnergy level must be between §e0§c and §e5§c. No negatives!");
                    return true;
                }

                // Level 0 = completely empty — set to 1 (minimum functional)
                // We allow "0" as input but treat it as clearing to floor of 1
                int clamped = Math.max(1, level);
                plugin.getEnergyManager().setEnergy(target, clamped);
                player.sendMessage("§aSet §e" + target.getName() + "§a's energy to §e" + clamped + "/5 §a("
                        + getStageName(clamped) + "§a).");
                target.sendMessage("§aYour energy was set to §e" + clamped + "/5 §aby an admin.");
            }

            case "give" -> {
                if (!player.hasPermission("runicsmp.admin")) {
                    player.sendMessage("§cNo permission.");
                    return true;
                }
                if (args.length < 3) {
                    player.sendMessage("§cUsage: /energy give <player> <amount>");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) { player.sendMessage("§cPlayer not found: " + args[1]); return true; }

                int amount;
                try { amount = Integer.parseInt(args[2]); }
                catch (NumberFormatException e) { player.sendMessage("§cInvalid number."); return true; }

                if (amount <= 0) { player.sendMessage("§cAmount must be positive."); return true; }

                PlayerRuneData targetData = plugin.getRuneManager().getData(target);
                int before = targetData.getEnergyLevel();
                int overflow = plugin.getEnergyManager().addEnergy(target, amount);

                int after = targetData.getEnergyLevel();
                player.sendMessage("§aGave §e" + amount + " §aenergy to §e" + target.getName()
                        + "§a. (§e" + before + "§a → §e" + after + "/5§a)"
                        + (overflow > 0 ? " §7+" + overflow + " banked" : ""));
                target.sendMessage("§a+§e" + amount + " §aenergy from §e" + player.getName()
                        + "§a! Now at §e" + after + "/5 §a(" + getStageName(after) + "§a).");
            }

            case "withdraw" -> {
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /energy withdraw <amount>");
                    return true;
                }
                int amount;
                try { amount = Integer.parseInt(args[1]); }
                catch (NumberFormatException e) { player.sendMessage("§cInvalid number."); return true; }

                if (amount <= 0) { player.sendMessage("§cAmount must be positive."); return true; }

                // Can withdraw from bank OR from current energy level (above 1)
                int totalAvailable = data.getEnergyBank() + Math.max(0, data.getEnergyLevel() - 1);
                if (amount > totalAvailable) {
                    player.sendMessage("§cNot enough energy. You have §e" + data.getEnergyBank()
                            + "§c banked and §e" + Math.max(0, data.getEnergyLevel() - 1)
                            + "§c withdrawable energy (level stays at minimum 1).");
                    return true;
                }

                // Drain from bank first, then from energy level
                int fromBank = Math.min(amount, data.getEnergyBank());
                int fromLevel = amount - fromBank;
                if (fromBank > 0) data.addEnergyBank(-fromBank);
                if (fromLevel > 0) plugin.getEnergyManager().setEnergy(player, data.getEnergyLevel() - fromLevel);

                if (player.getInventory().firstEmpty() == -1) {
                    player.sendMessage("§cYou need a free inventory slot to withdraw energy!");
                    return true;
                }

                data.addEnergyBank(-amount);
                org.bukkit.inventory.ItemStack energyItem = buildEnergyItem(amount);
                player.getInventory().addItem(energyItem);
                player.sendMessage("§aWithdrew §e" + amount + " §aenergy from your bank as a physical item.");
                player.sendMessage("§7Drop it near a player or right-click to consume it.");
            }

            default -> {
                player.sendMessage("§cUnknown subcommand. Use: §e/energy§c, §e/energy bank§c, §e/energy set§c, §e/energy give§c, §e/energy withdraw§c.");
            }
        }

        return true;
    }

    // ── Status displays ────────────────────────────────────────────────────────

    private void sendEnergyStatus(Player player, PlayerRuneData data) {
        int level = data.getEnergyLevel();
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("  ⚡ Energy Status")
                .color(NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true));
        player.sendMessage(Component.text("  Level: ")
                .color(NamedTextColor.GRAY)
                .append(Component.text(level + " / 5")
                        .color(energyColor(level))));
        player.sendMessage(Component.text("  Stage: ")
                .color(NamedTextColor.GRAY)
                .append(getStageName(level)));
        player.sendMessage(Component.text("  Bar:   ")
                .color(NamedTextColor.GRAY)
                .append(buildEnergyBar(level)));
        if (data.hasEnergyBanked()) {
            player.sendMessage(Component.text("  Bank:  ")
                    .color(NamedTextColor.GRAY)
                    .append(Component.text("⚡ " + data.getEnergyBank() + " stored")
                            .color(NamedTextColor.AQUA)));
        }
        player.sendMessage(Component.text(""));
    }

    private void sendBankStatus(Player player, PlayerRuneData data) {
        int bank = data.getEnergyBank();
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("  ⚡ Energy Bank")
                .color(NamedTextColor.AQUA).decoration(TextDecoration.BOLD, true));

        if (bank == 0) {
            player.sendMessage(Component.text("  Your bank is empty.")
                    .color(NamedTextColor.GRAY));
            player.sendMessage(Component.text("  Kill players while at Energy Level 5 to bank energy.")
                    .color(NamedTextColor.DARK_GRAY));
        } else {
            player.sendMessage(Component.text("  Stored: ")
                    .color(NamedTextColor.GRAY)
                    .append(Component.text("⚡ " + bank)
                            .color(NamedTextColor.AQUA)
                            .decoration(TextDecoration.BOLD, true)));
            player.sendMessage(Component.text("  ").append(buildBankBar(bank)));
            player.sendMessage(Component.text(""));
            player.sendMessage(Component.text("  Auto-refills your energy on death or when below 5.")
                    .color(NamedTextColor.DARK_GRAY));
            player.sendMessage(Component.text("  Use /energy withdraw <amount> to take it out as an item.")
                    .color(NamedTextColor.DARK_GRAY));
        }
        player.sendMessage(Component.text(""));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Component getStageName(int level) {
        return switch (level) {
            case 1 -> Component.text("Dormant").color(NamedTextColor.DARK_GRAY);
            case 2 -> Component.text("Awakening").color(NamedTextColor.GRAY);
            case 3 -> Component.text("Passive").color(NamedTextColor.GREEN);
            case 4 -> Component.text("Charged").color(NamedTextColor.YELLOW);
            case 5 -> Component.text("Maximum").color(NamedTextColor.GOLD)
                    .decoration(TextDecoration.BOLD, true);
            default -> Component.text("Unknown").color(NamedTextColor.WHITE);
        };
    }

    private NamedTextColor energyColor(int level) {
        return switch (level) {
            case 1 -> NamedTextColor.DARK_GRAY;
            case 2 -> NamedTextColor.GRAY;
            case 3 -> NamedTextColor.GREEN;
            case 4 -> NamedTextColor.YELLOW;
            case 5 -> NamedTextColor.GOLD;
            default -> NamedTextColor.WHITE;
        };
    }

    private Component buildEnergyBar(int level) {
        Component bar = Component.empty();
        for (int i = 1; i <= 5; i++) {
            bar = bar.append(Component.text(i <= level ? "⬛ " : "⬜ ")
                    .color(i <= level ? energyColor(level) : NamedTextColor.DARK_GRAY));
        }
        return bar;
    }

    private Component buildBankBar(int bank) {
        // Show up to 10 banked energy as blocks
        Component bar = Component.empty();
        for (int i = 1; i <= 10; i++) {
            bar = bar.append(Component.text(i <= bank ? "⚡" : "·")
                    .color(i <= bank ? NamedTextColor.AQUA : NamedTextColor.DARK_GRAY));
        }
        return bar;
    }

    /**
     * Creates a physical energy item that can be dropped/given to players.
     * When right-clicked it restores energy.
     */
    public static org.bukkit.inventory.ItemStack buildEnergyItem(int amount) {
        org.bukkit.inventory.ItemStack item = new org.bukkit.inventory.ItemStack(org.bukkit.Material.BLAZE_POWDER, amount);
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.displayName(Component.text("⚡ Energy Charge")
                .color(NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true));
        meta.lore(java.util.List.of(
                Component.text("").decoration(TextDecoration.ITALIC, false),
                Component.text("Contains " + amount + " energy charge" + (amount != 1 ? "s" : "") + ".")
                        .color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false),
                Component.text("").decoration(TextDecoration.ITALIC, false),
                Component.text("Right-click to absorb energy.").color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        meta.getPersistentDataContainer().set(
                new org.bukkit.NamespacedKey("runicsmp", "energy_charge"),
                org.bukkit.persistence.PersistentDataType.INTEGER, amount
        );
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Returns the energy charge amount from an item, or 0 if not an energy item.
     */
    public static int getEnergyCharge(org.bukkit.inventory.ItemStack item) {
        if (item == null || item.getType() != org.bukkit.Material.BLAZE_POWDER) return 0;
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (meta == null) return 0;
        Integer val = meta.getPersistentDataContainer().get(
                new org.bukkit.NamespacedKey("runicsmp", "energy_charge"),
                org.bukkit.persistence.PersistentDataType.INTEGER
        );
        return val != null ? val : 0;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) return List.of("bank", "set", "give", "withdraw");
        if (args.length == 2 && (args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("give")))
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        if (args.length == 3 && args[0].equalsIgnoreCase("set"))
            return List.of("1", "2", "3", "4", "5");
        return List.of();
    }
}
