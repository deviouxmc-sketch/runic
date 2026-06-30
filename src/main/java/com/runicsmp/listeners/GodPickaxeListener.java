package com.runicsmp.listeners;

import com.runicsmp.RunicSMP;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * God Pickaxe — auto-smelts ores, gives permanent Haste I while held.
 * Crafted by shift-right-clicking a smithing table with the ingredients.
 * Admin command: /godpickaxe give <player>
 */
public class GodPickaxeListener implements Listener, CommandExecutor, TabCompleter {

    private final RunicSMP plugin;
    private static final NamespacedKey PICK_KEY = new NamespacedKey("runicsmp", "god_pickaxe");

    // Auto-smelt map: ore block -> smelted item
    private static final Map<Material, Material> SMELT_MAP = new HashMap<>();
    static {
        SMELT_MAP.put(Material.IRON_ORE,           Material.IRON_INGOT);
        SMELT_MAP.put(Material.DEEPSLATE_IRON_ORE, Material.IRON_INGOT);
        SMELT_MAP.put(Material.GOLD_ORE,           Material.GOLD_INGOT);
        SMELT_MAP.put(Material.DEEPSLATE_GOLD_ORE, Material.GOLD_INGOT);
        SMELT_MAP.put(Material.NETHER_GOLD_ORE,    Material.GOLD_INGOT);
        SMELT_MAP.put(Material.COPPER_ORE,         Material.COPPER_INGOT);
        SMELT_MAP.put(Material.DEEPSLATE_COPPER_ORE, Material.COPPER_INGOT);
        SMELT_MAP.put(Material.ANCIENT_DEBRIS,     Material.NETHERITE_SCRAP);
        SMELT_MAP.put(Material.NETHER_QUARTZ_ORE,  Material.QUARTZ);
        SMELT_MAP.put(Material.COAL_ORE,           Material.COAL);
        SMELT_MAP.put(Material.DEEPSLATE_COAL_ORE, Material.COAL);
    }

    public GodPickaxeListener(RunicSMP plugin) { this.plugin = plugin; }

    // ── Create the pickaxe item ───────────────────────────────────────────────

    public static ItemStack createGodPickaxe() {
        ItemStack pick = new ItemStack(Material.NETHERITE_PICKAXE);
        ItemMeta meta = pick.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("✦ God Pickaxe ✦")
                    .color(NamedTextColor.LIGHT_PURPLE)
                    .decoration(TextDecoration.ITALIC, false)
                    .decoration(TextDecoration.BOLD, true));
            meta.lore(List.of(
                    Component.text("Auto-smelts ores on mine").color(NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("Grants permanent Haste I while held").color(NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("").decoration(TextDecoration.ITALIC, false),
                    Component.text("Crafted at a Smithing Table").color(NamedTextColor.DARK_GRAY)
                            .decoration(TextDecoration.ITALIC, false)
            ));
            meta.addEnchant(Enchantment.EFFICIENCY, 5, true);
            meta.addEnchant(Enchantment.UNBREAKING, 3, true);
            meta.addEnchant(Enchantment.FORTUNE, 3, true);
            meta.setUnbreakable(true);
            meta.getPersistentDataContainer().set(
                    new NamespacedKey("runicsmp", "god_pickaxe"),
                    PersistentDataType.BOOLEAN, true);
            pick.setItemMeta(meta);
        }
        return pick;
    }

    public static boolean isGodPickaxe(ItemStack item) {
        if (item == null || item.getType() != Material.NETHERITE_PICKAXE) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(
                new NamespacedKey("runicsmp", "god_pickaxe"), PersistentDataType.BOOLEAN);
    }

    // ── Haste I while held ────────────────────────────────────────────────────

    @EventHandler
    public void onHotbarSwitch(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> updateHaste(player), 1L);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () ->
                updateHaste(event.getPlayer()), 2L);
    }

    private final java.util.Set<java.util.UUID> updatingHaste = new java.util.HashSet<>();

    private void updateHaste(Player player) {
        if (!updatingHaste.add(player.getUniqueId())) return; // prevent re-entry
        try {
            ItemStack held = player.getInventory().getItemInMainHand();
            boolean holdingPick = isGodPickaxe(held);
            var data = plugin.getRuneManager().getData(player);
            boolean hasHasteRune = data.hasSecondary()
                    && data.getSecondaryRune() == com.runicsmp.data.RuneType.HASTE;

            if (holdingPick && hasHasteRune) {
                // Both — Haste II
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.HASTE, Integer.MAX_VALUE, 1, false, false, true));
            } else if (holdingPick || hasHasteRune) {
                // Either one — Haste I
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.HASTE, Integer.MAX_VALUE, 0, false, false, true));
            } else {
                // Neither — remove if it was ours (no particles)
                PotionEffect current = player.getPotionEffect(PotionEffectType.HASTE);
                if (current != null && !current.hasParticles()) {
                    player.removePotionEffect(PotionEffectType.HASTE);
                }
            }
        } finally {
            updatingHaste.remove(player.getUniqueId());
        }
    }

    // ── Auto-smelt on block break ─────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!isGodPickaxe(player.getInventory().getItemInMainHand())) return;

        Block block = event.getBlock();
        Material smelted = SMELT_MAP.get(block.getType());
        if (smelted == null) return;

        // Cancel normal drops, give smelted item directly
        event.setDropItems(false);
        ItemStack result = new ItemStack(smelted, 1);
        if (player.getInventory().firstEmpty() != -1) {
            player.getInventory().addItem(result);
        } else {
            player.getWorld().dropItemNaturally(player.getLocation(), result);
        }

        // Particles
        block.getWorld().spawnParticle(Particle.FLAME,
                block.getLocation().add(0.5, 0.5, 0.5), 6, 0.2, 0.2, 0.2, 0.05);
    }

    // ── Crafting: shift-right-click smithing table ────────────────────────────
    // Ingredients: 3 netherite scrap + 3 diamonds + 1 stick (in any slots)
    // Handled in SmithingTableListener — we expose the check here

    public static boolean hasIngredients(Player player) {
        int scraps = 0, diamonds = 0, sticks = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null) continue;
            switch (item.getType()) {
                case NETHERITE_SCRAP -> scraps += item.getAmount();
                case DIAMOND -> diamonds += item.getAmount();
                case STICK -> sticks += item.getAmount();
            }
        }
        return scraps >= 3 && diamonds >= 3 && sticks >= 1;
    }

    public static void consumeIngredients(Player player) {
        removeItems(player, Material.NETHERITE_SCRAP, 3);
        removeItems(player, Material.DIAMOND, 3);
        removeItems(player, Material.STICK, 1);
    }

    private static void removeItems(Player player, Material mat, int amount) {
        int left = amount;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length && left > 0; i++) {
            if (contents[i] != null && contents[i].getType() == mat) {
                int take = Math.min(left, contents[i].getAmount());
                contents[i].setAmount(contents[i].getAmount() - take);
                if (contents[i].getAmount() <= 0) player.getInventory().setItem(i, null);
                left -= take;
            }
        }
    }

    // ── /godpickaxe give <player> ─────────────────────────────────────────────

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("runicsmp.admin") && !(sender instanceof Player p && p.isOp())) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length < 2 || !args[0].equalsIgnoreCase("give")) {
            sender.sendMessage("§cUsage: /godpickaxe give <player>");
            return true;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) { sender.sendMessage("§cPlayer not found."); return true; }

        if (target.getInventory().firstEmpty() != -1) {
            target.getInventory().addItem(createGodPickaxe());
        } else {
            target.getWorld().dropItemNaturally(target.getLocation(), createGodPickaxe());
            sender.sendMessage("§eInventory full — dropped at their location.");
        }
        sender.sendMessage("§a✦ Gave God Pickaxe to §e" + target.getName() + "§a.");
        target.sendMessage("§d✦ You received the §5§lGod Pickaxe§d!");
        target.playSound(target.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command c,
                                                 @NotNull String l, @NotNull String[] args) {
        if (args.length == 1) return List.of("give");
        if (args.length == 2) return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        return List.of();
    }
}
