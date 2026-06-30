package com.runicsmp.listeners;

import com.runicsmp.RunicSMP;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.entity.EntityType;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.command.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Handles altar crafting for God-tier items (God Boots, etc.)
 * /altar <item> <player> — OP only, spawns altar on player, instant craft, no location reveal
 */
public class AltarListener implements Listener, CommandExecutor, TabCompleter {

    private final RunicSMP plugin;
    private static final NamespacedKey BOOTS_KEY = new NamespacedKey("runicsmp", "god_boots");
    private static final NamespacedKey LEGS_KEY = new NamespacedKey("runicsmp", "god_leggings");
    private static final NamespacedKey CHEST_KEY = new NamespacedKey("runicsmp", "god_chestplate");

    // Track active altar center locations
    private final java.util.Set<String> activeAltarLocations = new java.util.HashSet<>();

    public AltarListener(RunicSMP plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private String locKey(Location loc) {
        return loc.getWorld().getName() + "," + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

    // Right-click the enchanting table top slab or any altar block to craft
    @EventHandler(priority = EventPriority.HIGH)
    public void onAltarClick(org.bukkit.event.player.PlayerInteractEvent event) {
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;
        if (!(event.getPlayer() instanceof Player player)) return;

        Location clicked = event.getClickedBlock().getLocation();

        // Check if this block is part of any active altar (within 2 blocks of a registered center)
        String center = findNearbyAltar(clicked);
        if (center == null) return;

        event.setCancelled(true); // don't open enchant table etc

        if (!hasBootsIngredients(player)) {
            player.sendMessage("§c⚗ The altar rejects you. Missing ingredients:");
            player.sendMessage("§74x Netherite Ingot, 1 Diamond Boots, 10 Gold Block, 2 Feather, 1 Recovery Compass");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        // Consume and give boots
        consumeBootsIngredients(player);
        ItemStack boots = createGodBoots();
        if (player.getInventory().firstEmpty() != -1) {
            player.getInventory().addItem(boots);
        } else {
            player.getWorld().dropItemNaturally(player.getLocation(), boots);
        }

        // Broadcast
        net.kyori.adventure.text.Component bc = net.kyori.adventure.text.Component.text("⚗ ALTAR » ")
                .color(NamedTextColor.GOLD)
                .append(net.kyori.adventure.text.Component.text(player.getName())
                        .color(net.kyori.adventure.text.format.NamedTextColor.YELLOW)
                        .decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, true))
                .append(net.kyori.adventure.text.Component.text(" has forged the ")
                        .color(net.kyori.adventure.text.format.NamedTextColor.GRAY))
                .append(net.kyori.adventure.text.Component.text("✦ God Boots ✦")
                        .color(NamedTextColor.GOLD)
                        .decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, true))
                .append(net.kyori.adventure.text.Component.text(" at the altar!")
                        .color(net.kyori.adventure.text.format.NamedTextColor.GRAY));
        org.bukkit.Bukkit.broadcast(bc);

        player.sendMessage("§6✦ The altar has accepted your offering! §eGod Boots forged!");
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.2f);
        applyBootsPassive(player);
    }

    private String findNearbyAltar(Location loc) {
        for (String key : activeAltarLocations) {
            String[] parts = key.split(",");
            if (!parts[0].equals(loc.getWorld().getName())) continue;
            int cx = Integer.parseInt(parts[1]);
            int cy = Integer.parseInt(parts[2]);
            int cz = Integer.parseInt(parts[3]);
            if (Math.abs(loc.getBlockX() - cx) <= 2 &&
                Math.abs(loc.getBlockY() - cy) <= 4 &&
                Math.abs(loc.getBlockZ() - cz) <= 2) {
                return key;
            }
        }
        return null;
    }

    // ── Create God Boots ─────────────────────────────────────────────────────

    public static ItemStack createGodBoots() {
        ItemStack boots = new ItemStack(Material.NETHERITE_BOOTS);
        ItemMeta meta = boots.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("✦ God Boots ✦")
                    .color(NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false)
                    .decoration(TextDecoration.BOLD, true));
            meta.lore(List.of(
                    Component.text("Depth Strider III · Feather Falling X").color(NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("Grants permanent Speed I").color(NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("460 durability").color(NamedTextColor.DARK_GRAY)
                            .decoration(TextDecoration.ITALIC, false)
            ));
            meta.addEnchant(Enchantment.DEPTH_STRIDER, 3, true);
            meta.addEnchant(Enchantment.FEATHER_FALLING, 10, true);
            // Set custom durability 460 (netherite boots max is 481)
            meta.setUnbreakable(false);
            // Set durability via Damageable interface
            if (meta instanceof org.bukkit.inventory.meta.Damageable damageable) {
                damageable.setMaxDamage(460);
            }
            meta.getPersistentDataContainer().set(BOOTS_KEY, PersistentDataType.BOOLEAN, true);
            boots.setItemMeta(meta);
        }
        return boots;
    }

    public static ItemStack createGodChestplate() {
        ItemStack chest = new ItemStack(Material.NETHERITE_CHESTPLATE);
        ItemMeta meta = chest.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("✦ God Chestplate ✦")
                    .color(NamedTextColor.DARK_AQUA)
                    .decoration(TextDecoration.ITALIC, false)
                    .decoration(TextDecoration.BOLD, true));
            meta.lore(java.util.List.of(
                    Component.text("Protection III · Unbreaking III · Mending")
                            .color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                    Component.text("Grants permanent Resistance I").color(NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("600 durability").color(NamedTextColor.DARK_GRAY)
                            .decoration(TextDecoration.ITALIC, false)
            ));
            meta.addEnchant(Enchantment.PROTECTION, 3, true);
            meta.addEnchant(Enchantment.UNBREAKING, 3, true);
            meta.addEnchant(Enchantment.MENDING, 1, true);
            meta.setUnbreakable(false);
            if (meta instanceof org.bukkit.inventory.meta.Damageable d) d.setMaxDamage(600);
            meta.getPersistentDataContainer().set(CHEST_KEY, PersistentDataType.BOOLEAN, true);
            chest.setItemMeta(meta);
        }
        return chest;
    }

    public static boolean isGodChestplate(ItemStack item) {
        if (item == null || item.getType() != Material.NETHERITE_CHESTPLATE) return false;
        var meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(CHEST_KEY, PersistentDataType.BOOLEAN);
    }

    public static void applyChestplatePassive(Player player) {
        if (isGodChestplate(player.getInventory().getChestplate())) {
            player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 0, false, false, true));
        } else {
            var cur = player.getPotionEffect(org.bukkit.potion.PotionEffectType.RESISTANCE);
            if (cur != null && !cur.hasParticles()) player.removePotionEffect(org.bukkit.potion.PotionEffectType.RESISTANCE);
        }
    }

    public static ItemStack createGodLeggings() {
        ItemStack legs = new ItemStack(Material.NETHERITE_LEGGINGS);
        ItemMeta meta = legs.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("✦ God Leggings ✦")
                    .color(NamedTextColor.LIGHT_PURPLE)
                    .decoration(TextDecoration.ITALIC, false)
                    .decoration(TextDecoration.BOLD, true));
            meta.lore(java.util.List.of(
                    Component.text("Swift Sneak III · Protection III · Unbreaking III · Mending")
                            .color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                    Component.text("Grants permanent Weaving effect").color(NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("500 durability").color(NamedTextColor.DARK_GRAY)
                            .decoration(TextDecoration.ITALIC, false)
            ));
            meta.addEnchant(Enchantment.SWIFT_SNEAK, 3, true);
            meta.addEnchant(Enchantment.PROTECTION, 3, true);
            meta.addEnchant(Enchantment.UNBREAKING, 3, true);
            meta.addEnchant(Enchantment.MENDING, 1, true);
            meta.setUnbreakable(false);
            if (meta instanceof org.bukkit.inventory.meta.Damageable d) d.setMaxDamage(600);
            meta.getPersistentDataContainer().set(LEGS_KEY, PersistentDataType.BOOLEAN, true);
            legs.setItemMeta(meta);
        }
        return legs;
    }

    public static boolean isGodLeggings(ItemStack item) {
        if (item == null || item.getType() != Material.NETHERITE_LEGGINGS) return false;
        var meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(LEGS_KEY, PersistentDataType.BOOLEAN);
    }

    public static void applyLeggingsPassive(Player player) {
        if (isGodLeggings(player.getInventory().getLeggings())) {
            player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.WEAVING, Integer.MAX_VALUE, 0, false, false, true));
        } else {
            var cur = player.getPotionEffect(org.bukkit.potion.PotionEffectType.WEAVING);
            if (cur != null && !cur.hasParticles()) player.removePotionEffect(org.bukkit.potion.PotionEffectType.WEAVING);
        }
    }

    public static boolean isGodBoots(ItemStack item) {
        if (item == null || item.getType() != Material.NETHERITE_BOOTS) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(BOOTS_KEY, PersistentDataType.BOOLEAN);
    }

    // ── Passive Speed II while wearing, removed when taken off ────────────────

    public static void applyBootsPassive(Player player) {
        boolean wearing = isGodBoots(player.getInventory().getBoots());
        if (wearing) {
            player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.SPEED, Integer.MAX_VALUE, 1, false, false, true)); // icon visible
        } else {
            // Remove speed if it's a silent (non-particle) effect — i.e. from us
            var current = player.getPotionEffect(org.bukkit.potion.PotionEffectType.SPEED);
            if (current != null && !current.hasParticles()) {
                player.removePotionEffect(org.bukkit.potion.PotionEffectType.SPEED);
            }
        }
    }

    @EventHandler
    public void onJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () ->
                applyBootsPassive(event.getPlayer()), 2L);
    }

    // Cancel ALL fall damage if wearing god boots
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFallDamage(org.bukkit.event.entity.EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getCause() != org.bukkit.event.entity.EntityDamageEvent.DamageCause.FALL) return;
        if (isGodBoots(player.getInventory().getBoots())) {
            event.setCancelled(true);
        }
    }

    // ── /altar command ───────────────────────────────────────────────────────

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("runicsmp.admin") && !(sender instanceof Player p && p.isOp())) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /altar <GodBoots> <player>");
            return true;
        }

        if (!args[0].equalsIgnoreCase("GodBoots")) {
            sender.sendMessage("§cUnknown altar item: §e" + args[0]);
            sender.sendMessage("§7Available: §eGodBoots");
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§cPlayer not found or not online.");
            return true;
        }

        // Spawn altar structure at target location
        spawnAltarEffect(target);

        // Check ingredients and give boots if they have them
        if (hasBootsIngredients(target)) {
            consumeBootsIngredients(target);
            ItemStack boots = createGodBoots();
            if (target.getInventory().firstEmpty() != -1) {
                target.getInventory().addItem(boots);
            } else {
                target.getWorld().dropItemNaturally(target.getLocation(), boots);
            }
            Component broadcast = Component.text("⚗ ALTAR » ", NamedTextColor.GOLD)
                    .append(Component.text(target.getName(), NamedTextColor.YELLOW, TextDecoration.BOLD))
                    .append(Component.text(" has forged the ", NamedTextColor.GRAY))
                    .append(Component.text("✦ God Boots ✦", NamedTextColor.GOLD, TextDecoration.BOLD))
                    .append(Component.text(" at the altar!", NamedTextColor.GRAY));
            Bukkit.broadcast(broadcast);
            target.sendMessage("§6✦ The altar has accepted your offering!");
            applyBootsPassive(target);
        } else {
            sender.sendMessage("§eAltar spawned! §c" + target.getName() + " is missing ingredients.");
            sender.sendMessage("§7Needs: 4 Netherite Ingot, 1 Diamond Boots, 10 Gold Block, 2 Feather, 1 Recovery Compass");
            target.sendMessage("§6⚗ The altar awaits your offering...");
        }

        sender.sendMessage("§a✦ Altar spawned at §e" + target.getName() + "§a's location!");
        return true;
    }

    private boolean hasBootsIngredients(Player player) {
        int nethInts = 0, goldBlocks = 0, feathers = 0;
        boolean hasDiamondBoots = false, hasCompass = false;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null) continue;
            switch (item.getType()) {
                case NETHERITE_INGOT -> nethInts += item.getAmount();
                case GOLD_BLOCK -> goldBlocks += item.getAmount();
                case FEATHER -> feathers += item.getAmount();
                case DIAMOND_BOOTS -> hasDiamondBoots = true;
                case RECOVERY_COMPASS -> hasCompass = true;
            }
        }
        return nethInts >= 4 && goldBlocks >= 10 && feathers >= 2 && hasDiamondBoots && hasCompass;
    }

    private void consumeBootsIngredients(Player player) {
        removeItems(player, Material.NETHERITE_INGOT, 4);
        removeItems(player, Material.GOLD_BLOCK, 10);
        removeItems(player, Material.FEATHER, 2);
        removeItems(player, Material.DIAMOND_BOOTS, 1);
        removeItems(player, Material.RECOVERY_COMPASS, 1);
    }

    private void removeItems(Player player, Material mat, int amount) {
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

    public void spawnAltarEffect(Player player) {
        Location center = player.getLocation().clone();
        World w = center.getWorld();

        // Register altar location for right-click detection
        String altarKey = locKey(center);
        activeAltarLocations.add(altarKey);

        // ── Pedestal structure (like the image) ──────────────────────────────
        // Base layer: 3x3 stone bricks
        for (int x = -1; x <= 1; x++)
            for (int z = -1; z <= 1; z++)
                w.getBlockAt(center.clone().add(x, -1, z)).setType(Material.STONE_BRICKS);
        // Middle: 1x1 pillar (chiseled)
        w.getBlockAt(center.clone().add(0, 0, 0)).setType(Material.CHISELED_STONE_BRICKS);
        w.getBlockAt(center.clone().add(0, 1, 0)).setType(Material.CHISELED_STONE_BRICKS);
        // Top platform: 3x3 smooth stone slab
        for (int x = -1; x <= 1; x++)
            for (int z = -1; z <= 1; z++)
                w.getBlockAt(center.clone().add(x, 2, z)).setType(Material.SMOOTH_STONE_SLAB);

        java.util.List<org.bukkit.entity.Entity> entities = new java.util.ArrayList<>();

        // ── Boots floating above pedestal (centered) ──────────────────────────
        Location bootsLoc = center.clone().add(0.0, 4.0, 0.0);
        org.bukkit.entity.ItemDisplay bootsDisplay = w.spawn(bootsLoc, org.bukkit.entity.ItemDisplay.class, d -> {
            d.setItemStack(createGodBoots());
            d.setTransformation(new org.bukkit.util.Transformation(
                new org.joml.Vector3f(0,0,0), new org.joml.Quaternionf(),
                new org.joml.Vector3f(1.4f,1.4f,1.4f), new org.joml.Quaternionf()));
            d.setBillboard(org.bukkit.entity.Display.Billboard.FIXED);
        });
        entities.add(bootsDisplay);

        // ── Material list as text holograms above boots ───────────────────────
        java.util.List<String> matLines = java.util.List.of(
            "§a4x §fNetherite Ingot",
            "§a1x §fDiamond Boots",
            "§a10x §fGold Block",
            "§a2x §fFeather",
            "§a1x §fRecovery Compass",
            "§6§l✦ God Boots ✦"
        );

        for (int i = 0; i < matLines.size(); i++) {
            final String line = matLines.get(matLines.size() - 1 - i); // bottom to top
            Location textLoc = bootsLoc.clone().add(0, 0.8 + i * 0.28, 0);
            org.bukkit.entity.TextDisplay td = w.spawn(textLoc, org.bukkit.entity.TextDisplay.class, d -> {
                d.setText(line);
                d.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
                d.setAlignment(org.bukkit.entity.TextDisplay.TextAlignment.CENTER);
                d.setSeeThrough(false);
                d.setTextOpacity((byte)255);
            });
            entities.add(td);
        }

        // ── Sounds ───────────────────────────────────────────────────────────
        w.playSound(center, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 0.8f);
        w.playSound(center, Sound.BLOCK_BEACON_ACTIVATE, 1f, 0.7f);
        w.playSound(center, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 0.5f);

        // ── Particle glow + auto-remove after 5 minutes ───────────────────────
        new org.bukkit.scheduler.BukkitRunnable() {
            double angle = 0;
            int ticks = 0;
            @Override public void run() {
                if (ticks++ > 6000) { // 5 minutes
                    // Remove blocks
                    for (int x = -1; x <= 1; x++)
                        for (int z = -1; z <= 1; z++) {
                            w.getBlockAt(center.clone().add(x, -1, z)).setType(Material.AIR);
                            w.getBlockAt(center.clone().add(x, 2, z)).setType(Material.AIR);
                        }
                    w.getBlockAt(center.clone().add(0, 0, 0)).setType(Material.AIR);
                    w.getBlockAt(center.clone().add(0, 1, 0)).setType(Material.AIR);
                    entities.forEach(org.bukkit.entity.Entity::remove);
                    activeAltarLocations.remove(altarKey);
                    cancel(); return;
                }
                // Spinning gold particles around boots
                for (int i = 0; i < 3; i++) {
                    double a = angle + Math.PI * 2 / 3 * i;
                    w.spawnParticle(Particle.DUST, bootsLoc.clone().add(
                            Math.cos(a)*0.8, 0, Math.sin(a)*0.8), 1, 0, 0, 0,
                            new Particle.DustOptions(Color.fromRGB(255,200,0), 1.2f));
                }
                w.spawnParticle(Particle.ENCHANT, bootsLoc, 2, 0.3, 0.5, 0.3, 0.2);
                angle += 0.15;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command c,
                                                 @NotNull String l, @NotNull String[] args) {
        if (args.length == 1) return List.of("GodBoots");
        if (args.length == 2) return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        return List.of();
    }
    public void spawnAltarForPlayer(org.bukkit.entity.Player op, org.bukkit.entity.Player target) {
        spawnAltarEffect(target);
        op.sendMessage("§a✦ Altar summoned at §e" + target.getName() + "§a's location!");
        target.sendMessage("§6⚗ An altar has been summoned at your location!");

        if (hasBootsIngredients(target)) {
            consumeBootsIngredients(target);
            ItemStack boots = createGodBoots();
            if (target.getInventory().firstEmpty() != -1) target.getInventory().addItem(boots);
            else target.getWorld().dropItemNaturally(target.getLocation(), boots);
            net.kyori.adventure.text.Component bc = net.kyori.adventure.text.Component.text("⚗ ALTAR » ")
                    .color(NamedTextColor.GOLD)
                    .append(net.kyori.adventure.text.Component.text(target.getName()).color(NamedTextColor.YELLOW))
                    .append(net.kyori.adventure.text.Component.text(" has forged ").color(NamedTextColor.GRAY))
                    .append(net.kyori.adventure.text.Component.text("✦ God Boots ✦").color(NamedTextColor.GOLD));
            org.bukkit.Bukkit.broadcast(bc);
            target.sendMessage("§6✦ The altar has accepted your offering!");
            applyBootsPassive(target);
        } else {
            op.sendMessage("§eTarget missing ingredients — altar awaits their offering.");
        }
    }

}
