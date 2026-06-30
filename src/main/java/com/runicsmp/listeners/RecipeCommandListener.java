package com.runicsmp.listeners;

import com.runicsmp.RunicSMP;
import com.runicsmp.data.RuneType;
import com.runicsmp.managers.RuneCraftingManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * /recipes — shows all craftable rune recipes in chat.
 * /recipes <rune> — shows the specific recipe for that rune.
 *
 * Clickable rune names expand the recipe inline.
 * Primaries show the ritual warning.
 */
public class RecipeCommandListener implements CommandExecutor, TabCompleter {

    private final RunicSMP plugin;
    private final RuneCraftingManager craftingManager;

    public RecipeCommandListener(RunicSMP plugin) {
        this.plugin = plugin;
        this.craftingManager = plugin.getCraftingManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use /recipes.");
            return true;
        }

        if (args.length == 0) {
            showRecipeBook(player);
        } else {
            // /recipes <rune name>
            // God Pickaxe special case
            if (String.join("_", args).equalsIgnoreCase("godpickaxe")) {
                player.sendMessage(Component.text("✦ God Pickaxe Recipe").color(net.kyori.adventure.text.format.NamedTextColor.LIGHT_PURPLE).decorate(net.kyori.adventure.text.format.TextDecoration.BOLD));
                player.sendMessage(Component.text("§8─────────────────────────"));
                player.sendMessage(Component.text("§7Craft at a §fSmithing Table §7(Shift+Right-Click)"));
                player.sendMessage(Component.text(""));
                player.sendMessage(Component.text("§e3x §fNetherite Scrap"));
                player.sendMessage(Component.text("§e3x §fDiamond"));
                player.sendMessage(Component.text("§e1x §fStick"));
                player.sendMessage(Component.text(""));
                player.sendMessage(Component.text("§7• Auto-smelts all ores into ingots"));
                player.sendMessage(Component.text("§7• Permanent Haste I while held"));
                player.sendMessage(Component.text("§7• Efficiency V, Fortune III, Unbreakable"));
                player.sendMessage(Component.text("§8─────────────────────────"));
                return true;
            }

            String input = String.join("_", args).toUpperCase();
            try {
                RuneType rune = RuneType.valueOf(input);
                if (craftingManager.getRecipe(rune) == null) {
                    player.sendMessage(Component.text("No recipe found for " + rune.getDisplayName() + ".")
                            .color(NamedTextColor.RED));
                } else {
                    showSingleRecipe(player, rune);
                }
            } catch (IllegalArgumentException e) {
                player.sendMessage(Component.text("Unknown rune: " + args[0] + ". Use /recipes to see all.")
                        .color(NamedTextColor.RED));
            }
        }

        return true;
    }

    // ── Full recipe book ──────────────────────────────────────────────────────

    private void showRecipeBook(Player player) {
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("  ⚗ Rune Recipe Book")
                .color(NamedTextColor.GOLD)
                .decoration(TextDecoration.BOLD, true));
        player.sendMessage(Component.text("  Shift + click a Smithing Table with the required items.")
                .color(NamedTextColor.GRAY));
        player.sendMessage(Component.text(""));

        // ── Primaries ─────────────────────────────────────────────────────────
        player.sendMessage(Component.text("  ─── Primaries (15 min ritual) ───")
                .color(NamedTextColor.RED));
        player.sendMessage(Component.text("  ⚠ Your location is broadcast while crafting a primary!")
                .color(NamedTextColor.DARK_RED));
        player.sendMessage(Component.text(""));

        for (RuneType rune : craftingManager.getCraftableRunes()) {
            if (!rune.isPrimary()) continue;
            player.sendMessage(buildRecipeLine(rune));
        }

        player.sendMessage(Component.text(""));

        // ── Secondaries ───────────────────────────────────────────────────────
        player.sendMessage(Component.text("  ─── Secondaries (instant craft) ───")
                .color(NamedTextColor.AQUA));
        player.sendMessage(Component.text("  Crafting a new secondary replaces your current one.")
                .color(NamedTextColor.GRAY));
        player.sendMessage(Component.text(""));

        for (RuneType rune : craftingManager.getCraftableRunes()) {
            if (!rune.isSecondary()) continue;
            player.sendMessage(buildRecipeLine(rune));
        }

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("  Click a rune name for full details, or use /recipes <name>.")
                .color(NamedTextColor.DARK_GRAY));
        player.sendMessage(Component.text(""));
    }

    // ── Single recipe detail ─────────────────────────────────────────────────

    private void showSingleRecipe(Player player, RuneType rune) {
        List<ItemStack> recipe = craftingManager.getRecipe(rune);

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("  ⚗ " + rune.getDisplayName())
                .color(rune.isPrimary() ? NamedTextColor.RED : NamedTextColor.AQUA)
                .decoration(TextDecoration.BOLD, true));

        String kind = rune.isPrimary()
                ? "§c[Primary] §715-minute ritual — location will be broadcast!"
                : "§b[Secondary] §7Instant craft — replaces your current secondary.";
        player.sendMessage(Component.text("  " + kind));
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("  Required items (shift + Smithing Table):")
                .color(NamedTextColor.YELLOW));

        for (ItemStack item : recipe) {
            String matName = formatMaterial(item.getType());
            player.sendMessage(Component.text("    • " + item.getAmount() + "x " + matName)
                    .color(NamedTextColor.WHITE));
        }

        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("  ← Back to all recipes")
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.UNDERLINED, true)
                .clickEvent(ClickEvent.runCommand("/recipes"))
                .hoverEvent(HoverEvent.showText(Component.text("View all recipes"))));
        player.sendMessage(Component.text(""));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Builds a single clickable line for a rune in the recipe list.
     * Hovering shows all required materials. Clicking runs /recipes <rune>.
     */
    private Component buildRecipeLine(RuneType rune) {
        List<ItemStack> recipe = craftingManager.getRecipe(rune);

        // Build hover text showing all required materials
        Component hoverText = Component.text("Recipe for " + rune.getDisplayName() + "\n")
                .color(NamedTextColor.YELLOW);
        for (ItemStack item : recipe) {
            hoverText = hoverText.append(
                    Component.text("• " + item.getAmount() + "x " + formatMaterial(item.getType()) + "\n")
                            .color(NamedTextColor.WHITE));
        }
        hoverText = hoverText.append(
                Component.text("Click for full details").color(NamedTextColor.GRAY));

        NamedTextColor runeColor = rune.isPrimary() ? NamedTextColor.RED : NamedTextColor.AQUA;
        String prefix = rune.isPrimary() ? "  🔴 " : "  🔵 ";

        return Component.text(prefix)
                .append(Component.text(rune.getDisplayName())
                        .color(runeColor)
                        .decoration(TextDecoration.UNDERLINED, true)
                        .clickEvent(ClickEvent.runCommand("/recipes " + rune.name().toLowerCase()))
                        .hoverEvent(HoverEvent.showText(hoverText)))
                .append(Component.text("  — ")
                        .color(NamedTextColor.DARK_GRAY))
                .append(Component.text(summariseRecipe(recipe))
                        .color(NamedTextColor.GRAY));
    }

    /**
     * Short summary of a recipe for the list view, e.g. "Heavy Core, 4x Ominous Trial Key..."
     */
    private String summariseRecipe(List<ItemStack> recipe) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(recipe.size(), 3); i++) {
            if (i > 0) sb.append(", ");
            ItemStack item = recipe.get(i);
            if (item.getAmount() > 1) sb.append(item.getAmount()).append("x ");
            sb.append(formatMaterial(item.getType()));
        }
        if (recipe.size() > 3) sb.append("...");
        return sb.toString();
    }

    private String formatMaterial(Material mat) {
        String raw = mat.name().replace("_", " ").toLowerCase();
        // Capitalise first letter of each word
        String[] words = raw.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0)))
                  .append(word.substring(1)).append(" ");
            }
        }
        return sb.toString().trim();
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return craftingManager.getCraftableRunes().stream()
                    .map(r -> r.name().toLowerCase())
                    .filter(n -> n.startsWith(args[0].toLowerCase()))
                    .toList();
        }
        return List.of();
    }
}
