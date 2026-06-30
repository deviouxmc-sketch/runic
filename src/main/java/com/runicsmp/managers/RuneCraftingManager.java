package com.runicsmp.managers;

import com.runicsmp.RunicSMP;
import com.runicsmp.data.RuneType;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * Defines all crafting recipes for runes (shift-click smithing table).
 * Each recipe is a set of required materials (with counts).
 */
public class RuneCraftingManager {

    private final RunicSMP plugin;

    // Map<RuneType, List<required ItemStacks>>
    private final Map<RuneType, List<ItemStack>> recipes = new HashMap<>();

    public RuneCraftingManager(RunicSMP plugin) {
        this.plugin = plugin;
        registerRecipes();
    }

    private void registerRecipes() {

        // ── PRIMARY RECIPES ───────────────────────────────────────────────────

        // Wind: heavy core, 4 ominous trial keys, 2 breeze rods, 2 diamond blocks
        recipes.put(RuneType.WIND, List.of(
                item(Material.HEAVY_CORE, 1),
                item(Material.OMINOUS_TRIAL_KEY, 4),
                item(Material.BREEZE_ROD, 2),
                item(Material.DIAMOND_BLOCK, 2)
        ));

        // Shadow: 2 netherite scrap, 4 echo shards, 2 recovery compass, 1 silence armor trim
        recipes.put(RuneType.SHADOW, List.of(
                item(Material.NETHERITE_SCRAP, 2),
                item(Material.ECHO_SHARD, 4),
                item(Material.RECOVERY_COMPASS, 2),
                item(Material.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE, 1)
        ));

        // Swap: 2 shaper trim, 4 ender pearls, 1 netherite scrap, 1 diamond block
        recipes.put(RuneType.SWAP, List.of(
                item(Material.SHAPER_ARMOR_TRIM_SMITHING_TEMPLATE, 2),
                item(Material.ENDER_PEARL, 4),
                item(Material.NETHERITE_SCRAP, 1),
                item(Material.DIAMOND_BLOCK, 1)
        ));

        // Lockdown: 4 trial keys, 2 blocks of iron, 2 netherite scraps, 2 netherite upgrade templates
        recipes.put(RuneType.LOCKDOWN, List.of(
                item(Material.TRIAL_KEY, 4),
                item(Material.IRON_BLOCK, 2),
                item(Material.NETHERITE_SCRAP, 2),
                item(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 2)
        ));

        // Strength: netherite sword, 4 blaze rods, 2 netherite scraps, 2 wither skeleton skulls
        recipes.put(RuneType.STRENGTH, List.of(
                item(Material.NETHERITE_SWORD, 1),
                item(Material.BLAZE_ROD, 4),
                item(Material.NETHERITE_SCRAP, 2),
                item(Material.WITHER_SKELETON_SKULL, 2)
        ));

        // ── SECONDARY RECIPES ─────────────────────────────────────────────────

        // Tidal: 4 prismarine shards, 2 tridents, 2 nautilus shells, 1 coast armor trim
        recipes.put(RuneType.TIDAL, List.of(
                item(Material.PRISMARINE_SHARD, 4),
                item(Material.TRIDENT, 2),
                item(Material.NAUTILUS_SHELL, 2),
                item(Material.COAST_ARMOR_TRIM_SMITHING_TEMPLATE, 1)
        ));

        // Fire: 2 crying obsidian, 4 ghast tears, 2 lava buckets, 1 magma cream
        recipes.put(RuneType.FIRE, List.of(
                item(Material.CRYING_OBSIDIAN, 2),
                item(Material.GHAST_TEAR, 4),
                item(Material.LAVA_BUCKET, 2),
                item(Material.MAGMA_CREAM, 1)
        ));

        // Trader: 4 bells, 4 emerald blocks, 1 netherite scrap
        recipes.put(RuneType.TRADER, List.of(
                item(Material.BELL, 4),
                item(Material.EMERALD_BLOCK, 4),
                item(Material.NETHERITE_SCRAP, 1)
        ));

        // Haste: 1 dune trim, 4 gold blocks, 1 beacon, 1 netherite pickaxe, 2 diamond blocks
        // Dash: 8 gold blocks, 2 feathers, 2 phantom membrane, 1 netherite scrap
        recipes.put(RuneType.DASH, List.of(
                item(Material.GOLD_BLOCK, 8),
                item(Material.FEATHER, 2),
                item(Material.PHANTOM_MEMBRANE, 2),
                item(Material.NETHERITE_SCRAP, 1)
        ));

        // Frost: 4 packed ice, 2 powdered snow buckets, 1 diamond, 1 netherite scrap
        recipes.put(RuneType.FROST, List.of(
                item(Material.PACKED_ICE, 20),
                item(Material.POWDER_SNOW_BUCKET, 2),
                item(Material.DIAMOND_BLOCK, 1),
                item(Material.NETHERITE_SCRAP, 1)
        ));

        // Vitality: 10 golden apples, 1 gold block, 1 potion of healing, 1 netherite scrap
        recipes.put(RuneType.VITALITY, List.of(
                item(Material.GOLDEN_APPLE, 10),
                item(Material.GOLD_BLOCK, 1),
                item(Material.POTION, 1),        // potion of healing
                item(Material.NETHERITE_SCRAP, 1)
        ));

        recipes.put(RuneType.HASTE, List.of(
                item(Material.DUNE_ARMOR_TRIM_SMITHING_TEMPLATE, 1),
                item(Material.GOLD_BLOCK, 4),
                item(Material.BEACON, 1),
                item(Material.NETHERITE_PICKAXE, 1),
                item(Material.DIAMOND_BLOCK, 2)
        ));
    }

    private ItemStack item(Material mat, int amount) {
        return new ItemStack(mat, amount);
    }

    /**
     * Returns the recipe for a given rune, or null if no recipe.
     */
    public List<ItemStack> getRecipe(RuneType rune) {
        return recipes.get(rune);
    }

    /**
     * Returns all runes that have a recipe.
     */
    public Set<RuneType> getCraftableRunes() {
        return recipes.keySet();
    }

    /**
     * Checks if a player's inventory contains all the required items for a recipe.
     * Returns the RuneType if matched, null otherwise.
     */
    public RuneType matchRecipe(org.bukkit.entity.Player player) {
        for (Map.Entry<RuneType, List<ItemStack>> entry : recipes.entrySet()) {
            if (hasAllItems(player, entry.getValue())) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Check if the player has all the required items.
     */
    public boolean hasAllItems(org.bukkit.entity.Player player, List<ItemStack> required) {
        // Count what player has
        Map<Material, Integer> playerCounts = new HashMap<>();
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() != Material.AIR) {
                playerCounts.merge(item.getType(), item.getAmount(), Integer::sum);
            }
        }

        for (ItemStack req : required) {
            int have = playerCounts.getOrDefault(req.getType(), 0);
            if (have < req.getAmount()) return false;
        }
        return true;
    }

    /**
     * Consume the items from the player's inventory for the given recipe.
     */
    public void consumeItems(org.bukkit.entity.Player player, List<ItemStack> required) {
        for (ItemStack req : required) {
            int toRemove = req.getAmount();
            for (ItemStack item : player.getInventory().getContents()) {
                if (item != null && item.getType() == req.getType()) {
                    int canRemove = Math.min(toRemove, item.getAmount());
                    item.setAmount(item.getAmount() - canRemove);
                    toRemove -= canRemove;
                    if (toRemove <= 0) break;
                }
            }
        }
    }
}
