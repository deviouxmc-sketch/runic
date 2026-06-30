package com.runicsmp.runes;

import com.runicsmp.RunicSMP;
import com.runicsmp.data.PlayerRuneData;
import com.runicsmp.data.RuneType;
import com.runicsmp.managers.RuneManager;
import com.runicsmp.utils.CooldownManager;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Handles activation of all rune abilities when a player right-clicks their rune.
 */
public class RuneAbilityHandler {

    private final RunicSMP plugin;
    private final RuneManager runeManager;
    private final CooldownManager cooldowns;
    private final PrimaryAbilityExtensions ext;

    public RuneAbilityHandler(RunicSMP plugin) {
        this.plugin = plugin;
        this.runeManager = plugin.getRuneManager();
        this.cooldowns = runeManager.getCooldowns();
        this.ext = new PrimaryAbilityExtensions(plugin);
    }

    /**
     * Called when a player activates their primary rune ability.
     */
    public void activatePrimary(Player player) { activatePrimary(player, false); }
    public void activatePrimary(Player player, boolean shifting) {
        PlayerRuneData data = runeManager.getData(player);
        if (!data.hasPrimary()) return;

        RuneType rune = data.getPrimaryRune();


        // Ritual mode blocks all abilities
        if (plugin.getRitualModeCmd().isRitualModeActive()) {
            if (!plugin.getNotifManager().isMuted(player.getUniqueId()))
                player.sendMessage("§6⚗ Ritual mode is active — abilities disabled!");
            return;
        }
        if (cooldowns.isOnCooldown(player.getUniqueId(), rune)) {
            int remaining = cooldowns.getRemainingSeconds(player.getUniqueId(), rune);
            if (!plugin.getNotifManager().isMuted(player.getUniqueId()))
                player.sendMessage("§cAbility on cooldown! §e" + remaining + "s remaining.");
            return;
        }

        switch (rune) {
            case WIND -> ext.activateWindDash(player);
            case STRENGTH -> activateStrength(player, data);
            case SHADOW -> ext.activateShadow(player);
            case LIFESTEAL -> {} // passive-only active handled via combat listener
            case LOCKDOWN -> ext.activateLockdown(player);
            // TIDAL is secondary - handled by SecondaryAbilityHandler
            case DEFENDER -> activateDefender(player, data);
            case ENCHANTMENT -> activateEnchantment(player, data);
            case DRAGON -> {
                if (shifting) activateDragonLeap(player, data);
                else activateDragon(player, data);
            }
            case CLONE -> activateClone(player, data);
            case TRACKER -> activateTracker(player, data);
            case THIEF -> activateThief(player, data);
            case FIRE -> activateFire(player, data);
            case HASTE -> activateHaste(player, data);
            case VITALITY -> activateVitality(player, data);
            case SWAP -> ext.activateSwap(player);
            case RESURRECTION -> ext.activateResurrection(player);
            case WARPED      -> ext.activateWarped(player, null);
            case GRAVITY     -> activateGravity(player, data);
            case LIGHTNING   -> activateLightning(player, data);
            case WARDEN      -> activateWardenBeam(player, data);
            // ARACHNID uses /ability command only
            case ARACHNID    -> plugin.getArachnidListener().activateWebPrison(player);
            case SHAPESHIFTER -> {
                plugin.getShapeshifterListener().activateShapeshift(player);
                cooldowns.setCooldown(player.getUniqueId(), RuneType.SHAPESHIFTER, 1200); // 20 min
            }
            case ENDER       -> activateEnder(player, data);
            case SPEED       -> activateSpeed(player, data);
            default -> player.sendMessage("§cThis rune has no active ability yet.");
        }
        // Always play active particle effect when ability fires
        plugin.getParticleManager().playActiveEffect(player, rune);
        // Notify HUD to flash + show cooldown timer
        int cd = plugin.getCooldowns().getRemainingSeconds(player.getUniqueId(), rune);
        plugin.getHudManager().notifyAbilityUsed(player, cd);
    }

    // ── WIND ─────────────────────────────────────────────────────────────────
    private void activateWind(Player player, PlayerRuneData data) {
        int cd = plugin.getConfig().getInt("wind-rune.cooldown-seconds", 12);
        double launchVel = plugin.getConfig().getDouble("wind-rune.launch-velocity", 1.8);

        // Launch upward
        Vector vel = player.getVelocity();
        vel.setY(launchVel);
        player.setVelocity(vel);

        // Trailing white particles on the way down
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks++ > 80 || player.isOnGround()) { cancel(); return; }
                player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 2, 0.3, 0.3, 0.3, 0.02);
            }
        }.runTaskTimer(plugin, 5L, 3L);

        player.playSound(player.getLocation(), Sound.ENTITY_BREEZE_SHOOT, 1f, 1.2f);
        cooldowns.setCooldown(player.getUniqueId(), RuneType.WIND, cd);
        if (!plugin.getNotifManager().isMuted(player.getUniqueId())) player.sendMessage("§b§lWind Rune activated!");
    }

    // ── STRENGTH ─────────────────────────────────────────────────────────────
    private void activateStrength(Player player, PlayerRuneData data) {
        int cd = 60; // 60 second cooldown

        // Strength III (amplifier 2) for exactly 5 seconds
        player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.STRENGTH, 200, 2, false, true, true)); // Strength III for 10s

        // Dramatic red particle explosion on activation
        Location sLoc = player.getLocation().add(0, 1, 0);
        World sWorld = player.getWorld();

        // Initial burst — shockwave ring + upward column
        for (int i = 0; i < 24; i++) {
            double a = Math.PI * 2 / 24 * i;
            Location ring = player.getLocation().add(Math.cos(a) * 1.5, 0.1, Math.sin(a) * 1.5);
            sWorld.spawnParticle(Particle.DUST, ring, 1, 0, 0, 0,
                    new Particle.DustOptions(Color.fromRGB(200, 0, 0), 2f));
            sWorld.spawnParticle(Particle.CRIT, ring, 1, 0, 0, 0, 0.05);
        }
        sWorld.spawnParticle(Particle.DUST, sLoc, 15, 0.4, 0.8, 0.4,
                new Particle.DustOptions(Color.fromRGB(220, 0, 0), 2.5f));
        sWorld.spawnParticle(Particle.CRIT, sLoc, 7, 0.3, 0.6, 0.3, 0.3);

        // Orbiting red spiral for 5 seconds duration
        new BukkitRunnable() {
            double angle = 0;
            int ticks = 0;
            @Override public void run() {
                if (ticks++ >= 100) { cancel(); return; } // 100 * 2 = 200 ticks = 10s
                // 2 orbiting particles spinning around player
                for (int i = 0; i < 2; i++) {
                    double a = angle + Math.PI * i;
                    double orbitR = 1.2 + Math.sin(ticks * 0.2) * 0.3;
                    Location orbit = player.getLocation().add(
                            Math.cos(a) * orbitR, 1.0 + Math.sin(ticks * 0.15) * 0.4, Math.sin(a) * orbitR);
                    player.getWorld().spawnParticle(Particle.DUST, orbit, 1, 0.05, 0.05, 0.05,
                            new Particle.DustOptions(Color.fromRGB(220, 0, 0), 2f));
                    player.getWorld().spawnParticle(Particle.CRIT, orbit, 1, 0.05, 0.05, 0.05, 0.05);
                }
                // Occasional upward burst
                if (ticks % 8 == 0) {
                    player.getWorld().spawnParticle(Particle.DUST,
                            player.getLocation().add(0, 0.5, 0), 10, 0.3, 0.5, 0.3,
                            new Particle.DustOptions(Color.fromRGB(180, 0, 0), 1.5f));
                }
                angle += 0.35;
            }
        }.runTaskTimer(plugin, 0L, 2L);

        player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SHOOT, 0.5f, 0.5f);
        player.playSound(player.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 0.6f, 1.2f);
        if (!plugin.getNotifManager().isMuted(player.getUniqueId()))
            player.sendMessage("§c§lStrength III for 5 seconds!");
        cooldowns.setCooldown(player.getUniqueId(), RuneType.STRENGTH, cd);
    }

    // ── SHADOW ────────────────────────────────────────────────────────────────
    private void activateShadow(Player player, PlayerRuneData data) {
        int cd = plugin.getConfig().getInt("shadow-rune.cooldown-seconds", 20);
        int blindRadius = plugin.getConfig().getInt("shadow-rune.blind-radius", 15);
        int blindDuration = plugin.getConfig().getInt("shadow-rune.blind-duration-ticks", 100);

        // Blind all nearby players
        for (Entity e : player.getNearbyEntities(blindRadius, blindRadius, blindRadius)) {
            if (e instanceof Player target && !target.equals(player)) {
                target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, blindDuration, 0));
                target.sendMessage("§8You have been blinded by " + player.getName() + "'s Shadow Rune!");
            }
        }

        // True invisibility (remove armor visibility)
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 200, 0, false, false));

        player.getWorld().spawnParticle(Particle.SMOKE, player.getLocation().add(0, 1, 0), 50, 0.5, 1, 0.5, 0.05);
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.5f);
        if (!plugin.getNotifManager().isMuted(player.getUniqueId())) player.sendMessage("§8§lShadow Rune activated!");
        cooldowns.setCooldown(player.getUniqueId(), RuneType.SHADOW, cd);
    }

    // LOCKDOWN now handled by ext.activateLockdown(player) — see PrimaryAbilityExtensions.java

    // ── TIDAL ─────────────────────────────────────────────────────────────────
    private void activateTidal(Player player, PlayerRuneData data) {
        int cd = plugin.getConfig().getInt("tidal-rune.cooldown-seconds", 18);
        int waveRadius = plugin.getConfig().getInt("tidal-rune.wave-radius", 10);

        Location center = player.getLocation();

        // Spawn water particles wave in all directions
        for (int angle = 0; angle < 360; angle += 10) {
            double rad = Math.toRadians(angle);
            for (double r = 0; r <= waveRadius; r += 0.5) {
                double x = center.getX() + r * Math.cos(rad);
                double z = center.getZ() + r * Math.sin(rad);
                Location particleLoc = new Location(center.getWorld(), x, center.getY() + 0.5, z);
                center.getWorld().spawnParticle(Particle.SPLASH, particleLoc, 1, 0, 0, 0, 0);
            }
        }

        // Knockback nearby entities
        for (Entity e : player.getNearbyEntities(waveRadius, 3, waveRadius)) {
            if (e instanceof LivingEntity && !e.equals(player)) {
                Vector dir = e.getLocation().subtract(center).toVector().normalize().multiply(1.5);
                dir.setY(0.5);
                e.setVelocity(dir);
            }
        }

        player.playSound(player.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_AMBIENT, 1f, 1.5f);
        if (!plugin.getNotifManager().isMuted(player.getUniqueId())) player.sendMessage("§1§lTidal Rune activated!");
        cooldowns.setCooldown(player.getUniqueId(), RuneType.TIDAL, cd);
    }

    // ── VITALITY ─────────────────────────────────────────────────────────────
    private void activateVitality(Player player, PlayerRuneData data) {
        int cd = plugin.getConfig().getInt("vitality-rune.cooldown-seconds", 30);

        // Set health to max (including gap hearts)
        double maxHealth = player.getAttribute(Attribute.MAX_HEALTH).getValue();
        player.setHealth(maxHealth);
        player.setSaturation(20f);
        player.setFoodLevel(20);

        // Heart particles
        player.getWorld().spawnParticle(Particle.HEART, player.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.1);

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);
        if (!plugin.getNotifManager().isMuted(player.getUniqueId())) player.sendMessage("§d§lVitality Rune activated! Health fully restored.");
        cooldowns.setCooldown(player.getUniqueId(), RuneType.VITALITY, cd);
    }

    // ── HASTE ─────────────────────────────────────────────────────────────────
    private void activateHaste(Player player, PlayerRuneData data) {
        int cd = plugin.getConfig().getInt("haste-rune.cooldown-seconds", 20);
        int duration = plugin.getConfig().getInt("haste-rune.active-duration-seconds", 10);
        Location loc = player.getLocation();
        World w = player.getWorld();
        int radius = 5;

        player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, duration * 20, 254, false, true, true));

        // Frost-style rings but white/yellow theme: DUST ring (30 pts) + DUST ring (60 pts) white/yellow
        for (int i = 0; i < 30; i++) {
            double a = Math.PI * 2 / 30 * i;
            Location pt = loc.clone().add(Math.cos(a)*radius, 0.1, Math.sin(a)*radius);
            w.spawnParticle(Particle.DUST, pt, 1, 0, 0, 0,
                    new Particle.DustOptions(Color.YELLOW, 1.0f));
        }
        for (int i = 0; i < 60; i++) {
            double a = Math.PI * 2 / 60 * i;
            Location pt = loc.clone().add(Math.cos(a)*radius, 0.1, Math.sin(a)*radius);
            w.spawnParticle(Particle.DUST, pt, 1, 0, 0.1, 0,
                    new Particle.DustOptions(Color.WHITE, 1.0f));
        }

        // Rising columns (box effect) white/yellow
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (t++ > 15) { cancel(); return; }
                double h = t * 0.2;
                for (int ci = 0; ci < 8; ci++) {
                    double ca = Math.PI * 2 / 8 * ci;
                    Location col = loc.clone().add(Math.cos(ca)*radius, h, Math.sin(ca)*radius);
                    w.spawnParticle(Particle.DUST, col, 1, 0, 0, 0,
                            new Particle.DustOptions(ci % 2 == 0 ? Color.YELLOW : Color.WHITE, 1.0f));
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);

        player.playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 2f);
        player.sendMessage("§e§lHaste Rune activated! Instant mining for §a" + duration + "s§e.");
        cooldowns.setCooldown(player.getUniqueId(), RuneType.HASTE, cd);
    }

    // ── DEFENDER ─────────────────────────────────────────────────────────────
    private void activateDefender(Player player, PlayerRuneData data) {
        int cd = plugin.getConfig().getInt("defender-rune.cooldown-seconds", 30);
        int duration = plugin.getConfig().getInt("defender-rune.shield-duration-seconds", 5);

        data.setDefenderShieldActive(true);

        // Resistance 255 = effectively impenetrable
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, duration * 20, 254, false, true, true));

        player.getWorld().spawnParticle(Particle.ENCHANT,
                player.getLocation().add(0, 1, 0), 30, 0.5, 1, 0.5, 0.5);

        new BukkitRunnable() {
            @Override
            public void run() { data.setDefenderShieldActive(false); }
        }.runTaskLater(plugin, duration * 20L);

        player.playSound(player.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1f, 0.8f);
        player.sendMessage("§6§lDefender Rune activated! Impenetrable shield for §e" + duration + "s§6.");
        cooldowns.setCooldown(player.getUniqueId(), RuneType.DEFENDER, cd);
    }

    // ── ENCHANTMENT ──────────────────────────────────────────────────────────
    private void activateEnchantment(Player player, PlayerRuneData data) {
        int cd = plugin.getConfig().getInt("enchantment-rune.cooldown-seconds", 25);
        int duration = plugin.getConfig().getInt("enchantment-rune.active-duration-seconds", 15);
        int sharpLevel = plugin.getConfig().getInt("enchantment-rune.active-sharpness-level", 5);

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType() == Material.AIR || !hand.getType().name().contains("SWORD")
                && !hand.getType().name().contains("AXE")) {
            if (!plugin.getNotifManager().isMuted(player.getUniqueId())) player.sendMessage("§cHold a sword or axe to use Enchantment Rune active!");
            return;
        }

        // Store original enchantments
        final var originalEnchants = new java.util.HashMap<>(hand.getEnchantments());
        final int originalSharp = hand.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.SHARPNESS);

        // Apply buffed sharpness
        hand.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.SHARPNESS, sharpLevel);

        player.getWorld().spawnParticle(Particle.ENCHANT,
                player.getLocation().add(0, 1, 0), 50, 0.5, 1, 0.5, 1.0);

        new BukkitRunnable() {
            @Override
            public void run() {
                // Revert sharpness
                if (originalSharp > 0) {
                    hand.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.SHARPNESS, originalSharp);
                } else {
                    hand.removeEnchantment(org.bukkit.enchantments.Enchantment.SHARPNESS);
                }
                if (!plugin.getNotifManager().isMuted(player.getUniqueId())) player.sendMessage("§5Enchantment buff has worn off.");
            }
        }.runTaskLater(plugin, duration * 20L);

        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1f);
        player.sendMessage("§5§lEnchantment Rune activated! Sharpness §d" + sharpLevel + "§5 for §e" + duration + "s§5.");
        cooldowns.setCooldown(player.getUniqueId(), RuneType.ENCHANTMENT, cd);
    }

    // ── DRAGON ────────────────────────────────────────────────────────────────
    private void activateDragon(Player player, PlayerRuneData data) {
        int cd = 120;

        Location eyeLoc = player.getEyeLocation();
        Vector dir = eyeLoc.getDirection().normalize();
        World w = player.getWorld();

        // Launch particles + deal damage to entities in path — no DragonFireball (causes flight bugs)
        player.playSound(eyeLoc, Sound.ENTITY_ENDER_DRAGON_SHOOT, 1f, 1f);

        // Particle beam travels forward, damages first entity hit
        new BukkitRunnable() {
            double dist = 0;
            boolean done = false;
            @Override public void run() {
                if (done || dist > 20) { cancel(); return; }
                dist += 0.6;
                Location pt = eyeLoc.clone().add(dir.clone().multiply(dist));

                // Stop at solid blocks
                if (pt.getBlock().getType().isSolid()) { cancel(); return; }

                // Dragon breath particles along path
                w.spawnParticle(Particle.DUST, pt, 2, 0.1, 0.1, 0.1, new Particle.DustOptions(Color.fromRGB(120, 0, 200), 1.5f));
                w.spawnParticle(Particle.DUST, pt, 1, 0.05, 0.05, 0.05,
                        new Particle.DustOptions(Color.fromRGB(150, 0, 255), 1.5f));

                // Hit detection
                for (org.bukkit.entity.Entity nearby : pt.getWorld().getNearbyEntities(pt, 1.2, 1.2, 1.2)) {
                    if (!(nearby instanceof LivingEntity le)) continue;
                    if (nearby.equals(player)) continue;
                    if (nearby instanceof Player tp &&
                            plugin.getTrustManager().isTrustedUUID(player.getUniqueId(), tp.getUniqueId())) continue;
                    done = true;
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        le.damage(8.0, player);
                        le.addPotionEffect(new PotionEffect(
                                PotionEffectType.WITHER, 60, 1, false, true, true));
                        w.spawnParticle(Particle.DUST, pt, 15, 0.3, 0.3, 0.3, new Particle.DustOptions(Color.fromRGB(120, 0, 200), 2f));
                    });
                    cancel();
                    return;
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);

        // Launch burst particles at player
        w.spawnParticle(Particle.DUST, eyeLoc, 15, 0.3, 0.3, 0.3, new Particle.DustOptions(Color.fromRGB(120, 0, 200), 2f));
        w.spawnParticle(Particle.PORTAL, eyeLoc, 10, 0.3, 0.3, 0.3, 0.1);

        if (!plugin.getNotifManager().isMuted(player.getUniqueId()))
            player.sendMessage("§5§lDragon Rune activated!");
        cooldowns.setCooldown(player.getUniqueId(), RuneType.DRAGON, cd);
    }

    // ── CLONE ─────────────────────────────────────────────────────────────────
    private void activateClone(Player player, PlayerRuneData data) {
        int cd = plugin.getConfig().getInt("clone-rune.cooldown-seconds", 20);
        int radius = plugin.getConfig().getInt("clone-rune.effect-copy-radius", 20);

        // Find nearest player with a rune and copy their effects
        Player nearest = null;
        double nearestDist = Double.MAX_VALUE;

        for (Entity e : player.getNearbyEntities(radius, radius, radius)) {
            if (e instanceof Player target && !target.equals(player)) {
                double dist = target.getLocation().distanceSquared(player.getLocation());
                if (dist < nearestDist) {
                    nearest = target;
                    nearestDist = dist;
                }
            }
        }

        if (nearest == null) {
            if (!plugin.getNotifManager().isMuted(player.getUniqueId())) player.sendMessage("§eNo players nearby to clone!");
            return;
        }

        // Copy all their active potion effects
        for (PotionEffect effect : nearest.getActivePotionEffects()) {
            player.addPotionEffect(new PotionEffect(effect.getType(), 200, effect.getAmplifier(), false, true));
        }

        data.setCloneTarget(nearest.getUniqueId());

        player.getWorld().spawnParticle(Particle.DUST,
                player.getLocation().add(0, 1, 0), 30, 0.5, 1, 0.5,
                new Particle.DustOptions(Color.YELLOW, 2f));

        player.playSound(player.getLocation(), Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1f, 1.5f);
        player.sendMessage("§e§lClone Rune activated! Copied effects from §a" + nearest.getName() + "§e.");
        cooldowns.setCooldown(player.getUniqueId(), RuneType.CLONE, cd);
    }

    // ── TRACKER ───────────────────────────────────────────────────────────────
    private void activateTracker(Player player, PlayerRuneData data) {
        int radius = plugin.getConfig().getInt("tracker-rune.glow-radius", 20);

        // Make all nearby players glow
        for (Entity e : player.getNearbyEntities(radius, radius, radius)) {
            if (e instanceof Player target) {
                target.setGlowing(true);
                new BukkitRunnable() {
                    @Override
                    public void run() { target.setGlowing(false); }
                }.runTaskLater(plugin, 100L);
            }
        }

        // Show particle line to tracked target
        UUID targetId = data.getTrackerTarget();
        if (targetId != null) {
            Player tracked = Bukkit.getPlayer(targetId);
            if (tracked != null && tracked.isOnline()) {
                drawParticleLine(player, tracked);
                player.sendMessage("§a§lTracker Rune: Tracking §e" + tracked.getName());
            }
        } else {
            if (!plugin.getNotifManager().isMuted(player.getUniqueId())) player.sendMessage("§aTracker Rune: Nearby players highlighted. Use §e/rune track <player>§a to set a target.");
        }
    }

    private void drawParticleLine(Player from, Player to) {
        Location start = from.getEyeLocation();
        Location end = to.getLocation();
        Vector dir = end.toVector().subtract(start.toVector()).normalize();
        double maxDist = plugin.getConfig().getInt("tracker-rune.particle-line-distance", 10);
        double dist = Math.min(start.distance(end), maxDist);

        for (double d = 0; d <= dist; d += 0.5) {
            Location point = start.clone().add(dir.clone().multiply(d));
            from.getWorld().spawnParticle(Particle.DUST, point, 1, 0, 0, 0,
                    new Particle.DustOptions(Color.GREEN, 1.5f));
        }
    }

    // ── THIEF ─────────────────────────────────────────────────────────────────
    private void activateThief(Player player, PlayerRuneData data) {
        int cd = plugin.getConfig().getInt("thief-rune.steal-cooldown-seconds", 30);

        // Find closest player within 5 blocks
        Player target = null;
        for (Entity e : player.getNearbyEntities(5, 5, 5)) {
            if (e instanceof Player p && !p.equals(player)) {
                target = p;
                break;
            }
        }

        if (target == null) {
            if (!plugin.getNotifManager().isMuted(player.getUniqueId())) player.sendMessage("§cNo player nearby to steal from!");
            return;
        }

        // Steal utility items (not weapons/potions)
        int stolen = 0;
        for (int i = 0; i < target.getInventory().getSize(); i++) {
            ItemStack item = target.getInventory().getItem(i);
            if (item == null || item.getType() == Material.AIR) continue;
            if (item.getType().name().contains("POTION")) continue;
            // Skip weapons by name matching (Material.SWORD/AXE don't exist as generic types in 1.21)
            String matName = item.getType().name();
            boolean isWeapon = matName.contains("SWORD") || matName.contains("_AXE")
                    || matName.equals("BOW") || matName.equals("CROSSBOW") || matName.equals("TRIDENT");
            if (isWeapon) continue;

            if (stolen >= 3) break; // steal up to 3 items

            if (player.getInventory().firstEmpty() != -1) {
                player.getInventory().addItem(item.clone());
                target.getInventory().setItem(i, null);
                stolen++;
            }
        }

        if (stolen > 0) {
            player.sendMessage("§7§lThief Rune activated! Stole §e" + stolen + " §7items from §e" + target.getName());
            target.sendMessage("§c" + player.getName() + " stole §e" + stolen + " §citems from you!");
        } else {
            if (!plugin.getNotifManager().isMuted(player.getUniqueId())) player.sendMessage("§cNothing to steal!");
        }

        cooldowns.setCooldown(player.getUniqueId(), RuneType.THIEF, cd);
    }

    // ── FIRE ─────────────────────────────────────────────────────────────────
    private void activateFire(Player player, PlayerRuneData data) {
        int cd = plugin.getConfig().getInt("fire-rune.cooldown-seconds", 10);
        double damage = plugin.getConfig().getDouble("fire-rune.fireball-damage", 8.0);

        Location eyeLoc = player.getEyeLocation();
        Vector dir = eyeLoc.getDirection();

        Fireball fireball = player.getWorld().spawn(
                eyeLoc.add(dir.multiply(2)), Fireball.class);
        fireball.setShooter(player);
        fireball.setVelocity(dir.multiply(2.0));
        fireball.setIsIncendiary(true);
        fireball.setYield(2.5f);

        // Tag fireball with custom damage
        fireball.setMetadata("rune_fireball_damage",
                new org.bukkit.metadata.FixedMetadataValue(plugin, damage));
        fireball.setMetadata("rune_fireball_owner",
                new org.bukkit.metadata.FixedMetadataValue(plugin, player.getUniqueId().toString()));

        // Orange trailing particles
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks++ > 60 || fireball.isDead()) { cancel(); return; }
                fireball.getWorld().spawnParticle(Particle.FLAME,
                        fireball.getLocation(), 5, 0.2, 0.2, 0.2, 0.02);
                fireball.getWorld().spawnParticle(Particle.DUST,
                        fireball.getLocation(), 3, 0.1, 0.1, 0.1,
                        new Particle.DustOptions(Color.ORANGE, 1.5f));
            }
        }.runTaskTimer(plugin, 0L, 2L);

        player.playSound(player.getLocation(), Sound.ENTITY_GHAST_SHOOT, 0.8f, 1.5f);
        if (!plugin.getNotifManager().isMuted(player.getUniqueId())) player.sendMessage("§c§lFire Rune activated!");
        cooldowns.setCooldown(player.getUniqueId(), RuneType.FIRE, cd);
    }
    // ── GRAVITY ───────────────────────────────────────────────────────────────
    private void activateGravity(Player player, PlayerRuneData data) {
        int cd = 30;
        double radius = 10.0;
        double pullStrength = 1.5;

        int count = 0;
        for (org.bukkit.entity.Entity nearby : player.getNearbyEntities(radius, radius, radius)) {
            if (!(nearby instanceof org.bukkit.entity.LivingEntity le)) continue;
            if (nearby.equals(player)) continue;
            if (nearby instanceof org.bukkit.entity.Player target) {
                if (plugin.getTrustManager().isTrustedUUID(player.getUniqueId(), target.getUniqueId())) continue;
            }
            // Pull toward player
            org.bukkit.util.Vector pull = player.getLocation().subtract(le.getLocation()).toVector();
            if (pull.lengthSquared() > 0) pull = pull.normalize().multiply(pullStrength);
            pull.setY(Math.max(pull.getY(), 0.2));
            le.setVelocity(pull);
            count++;
        }

        // Flashy gravity pull — spirals converging inward + shockwave + per-entity particles
        World w = player.getWorld();
        Location center = player.getLocation().add(0, 1, 0);

        // Initial shockwave ring expanding outward then collapsing
        new org.bukkit.scheduler.BukkitRunnable() {
            double angle = 0;
            int ticks = 0;
            @Override public void run() {
                if (ticks++ > 30) { cancel(); return; }
                double progress = ticks / 30.0;
                // Ring shrinks from radius 10 inward as it "pulls"
                double r = radius * (1.0 - progress);
                for (int i = 0; i < 16; i++) {
                    double a = Math.PI * 2 / 16 * i + angle;
                    Location ring = center.clone().add(Math.cos(a) * r, 0, Math.sin(a) * r);
                    w.spawnParticle(Particle.DUST, ring, 1, 0, 0, 0,
                            new Particle.DustOptions(org.bukkit.Color.fromRGB(80, 0, 150), 1.8f));
                    w.spawnParticle(Particle.PORTAL, ring, 1, 0.1, 0.2, 0.1, 0.05);
                }
                // Spiral arms converging to center
                for (int arm = 0; arm < 3; arm++) {
                    double a = angle * 2 + (Math.PI * 2 / 3 * arm);
                    double sr = r * 0.6;
                    Location spiral = center.clone().add(Math.cos(a) * sr, ticks * 0.03, Math.sin(a) * sr);
                    w.spawnParticle(Particle.DUST, spiral, 1, 0, 0, 0,
                            new Particle.DustOptions(org.bukkit.Color.fromRGB(140, 0, 200), 1.5f));
                }
                // Central vortex intensifies as ring collapses
                if (ticks > 15) {
                    w.spawnParticle(Particle.PORTAL, center, (int)(progress * 8), 0.2, 0.3, 0.2, 0.08);
                    w.spawnParticle(Particle.REVERSE_PORTAL, center, 1, 0.1, 0.2, 0.1, 0.02);
                }
                angle += 0.25;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        // Per-entity trail particles as they fly toward player
        for (org.bukkit.entity.Entity nearby : player.getNearbyEntities(radius, radius, radius)) {
            if (!(nearby instanceof org.bukkit.entity.LivingEntity)) continue;
            if (nearby.equals(player)) continue;
            final org.bukkit.entity.Entity ent = nearby;
            new org.bukkit.scheduler.BukkitRunnable() {
                int t = 0;
                @Override public void run() {
                    if (t++ > 10 || !ent.isValid()) { cancel(); return; }
                    w.spawnParticle(Particle.DUST, ent.getLocation().add(0,1,0),
                            4, 0.2, 0.3, 0.2,
                            new Particle.DustOptions(org.bukkit.Color.fromRGB(120, 0, 180), 1.2f));
                    w.spawnParticle(Particle.PORTAL, ent.getLocation().add(0,1,0), 3, 0.2,0.2,0.2, 0.05);
                }
            }.runTaskTimer(plugin, 0L, 2L);
        }

        // Sounds
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.8f, 1.5f);
        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_BEACON_ACTIVATE, 0.5f, 0.5f);
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 0.6f);
        if (!plugin.getNotifManager().isMuted(player.getUniqueId()))
            player.sendMessage("§5§lGravity Pull! §7Pulled §e" + count + " §7entities.");
        cooldowns.setCooldown(player.getUniqueId(), RuneType.GRAVITY, cd);
    }

    // ── LIGHTNING (Conductor Card) ────────────────────────────────────────────
    private void activateLightning(Player player, PlayerRuneData data) {
        int cd = 30;
        double radius = 5.0;
        World w = player.getWorld();
        Location center = player.getLocation();

        // Conductor card: FLASH + expanding dual ELECTRIC_SPARK rings at caster
        w.spawnParticle(Particle.FLASH, center.clone().add(0,1,0), 1, 0, 0, 0, 0);
        plugin.getLightningPassiveListener().spawnDualRings(w, center, radius * 0.7, 20);
        w.spawnParticle(Particle.WITCH, center.clone().add(0,1,0), 15, radius*0.4, 0.5, radius*0.4, 0.1);
        w.playSound(center, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1f, 0.7f);
        w.playSound(center, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.8f, 1.2f);

        for (org.bukkit.entity.Entity nearby : player.getNearbyEntities(radius, radius, radius)) {
            if (!(nearby instanceof org.bukkit.entity.LivingEntity le)) continue;
            if (nearby.equals(player)) continue;
            if (nearby instanceof Player tp &&
                plugin.getTrustManager().isTrustedUUID(player.getUniqueId(), tp.getUniqueId())) continue;

            Location tLoc = le.getLocation();
            // Dual rings on each target
            plugin.getLightningPassiveListener().spawnDualRings(w, tLoc, 1.2, 12);
            w.strikeLightningEffect(tLoc);
            w.spawnParticle(Particle.FLASH, tLoc, 1, 0, 0, 0, 0);
            w.spawnParticle(Particle.ELECTRIC_SPARK, tLoc.clone().add(0,1,0), 10, 0.3, 0.5, 0.3, 0.2);

            // 1.5 hearts every 2s for 10s
            new BukkitRunnable() {
                int ticks = 0;
                @Override public void run() {
                    if (ticks++ >= 5 || !le.isValid()) { cancel(); return; }
                    le.damage(3.0, player);
                    w.strikeLightningEffect(le.getLocation());
                    plugin.getLightningPassiveListener().spawnDualRings(w, le.getLocation(), 1.2, 8);
                    w.spawnParticle(Particle.ELECTRIC_SPARK, le.getLocation().clone().add(0,1,0),
                            5, 0.2, 0.3, 0.2, 0.1);
                }
            }.runTaskTimer(plugin, 40L, 40L);
        }

        if (!plugin.getNotifManager().isMuted(player.getUniqueId()))
            player.sendMessage("§e§l⚡ Lightning Rune — Storm unleashed!");
        cooldowns.setCooldown(player.getUniqueId(), RuneType.LIGHTNING, cd);
    }


    // ── WARDEN ────────────────────────────────────────────────────────────────
    private void activateWardenBeam(Player player, PlayerRuneData data) {
        int cd = 20;
        Location eyeLoc = player.getEyeLocation();
        Vector dir = eyeLoc.getDirection().normalize();
        World w = player.getWorld();

        // Set CD immediately
        cooldowns.setCooldown(player.getUniqueId(), RuneType.WARDEN, cd);
        if (!plugin.getNotifManager().isMuted(player.getUniqueId()))
            player.sendMessage("§2⚡ Warden Sonic Boom!");

        // Sonic Boom beam - SHRIEK + DUST dark green
        w.spawnParticle(Particle.SHRIEK, eyeLoc, 3, 0.2, 0.2, 0.2, 0);
        w.spawnParticle(Particle.DUST, eyeLoc, 10, 0.3, 0.3, 0.3,
                new Particle.DustOptions(Color.fromRGB(0, 80, 20), 2f));
        w.playSound(eyeLoc, Sound.ENTITY_WARDEN_SONIC_BOOM, 1f, 1f);
        w.playSound(eyeLoc, Sound.ENTITY_WARDEN_SONIC_CHARGE, 0.8f, 1f);

        new BukkitRunnable() {
            double dist = 0;
            boolean done = false;
            @Override public void run() {
                if (done || dist > 30) { cancel(); return; }
                dist += 0.8;
                Location pt = eyeLoc.clone().add(dir.clone().multiply(dist));
                if (pt.getBlock().getType().isSolid()) { cancel(); return; }

                w.spawnParticle(Particle.SHRIEK, pt, 1, 0.05, 0.05, 0.05, 0);
                w.spawnParticle(Particle.DUST, pt, 2, 0.05, 0.05, 0.05,
                        new Particle.DustOptions(Color.fromRGB(0, 100, 30), 1.5f));

                for (org.bukkit.entity.Entity nearby : pt.getWorld().getNearbyEntities(pt, 1.0, 1.0, 1.0)) {
                    if (!(nearby instanceof LivingEntity le)) continue;
                    if (nearby.equals(player)) continue;
                    if (nearby instanceof Player tp &&
                            plugin.getTrustManager().isTrustedUUID(player.getUniqueId(), tp.getUniqueId())) continue;
                    done = true;
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        le.damage(8.0, player);
                        w.spawnParticle(Particle.SHRIEK, pt, 10, 0.3, 0.3, 0.3, 0);
                        w.spawnParticle(Particle.DUST, pt, 15, 0.4, 0.4, 0.4,
                                new Particle.DustOptions(Color.fromRGB(0, 80, 20), 2f));
                        w.playSound(pt, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.6f, 1.5f);
                    });
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);

        cooldowns.setCooldown(player.getUniqueId(), RuneType.WARDEN, cd);
    }


    private void activateEnder(Player player, PlayerRuneData data) {
        int cd = 15;
        // Throw an ender pearl
        org.bukkit.inventory.ItemStack pearl = new org.bukkit.inventory.ItemStack(org.bukkit.Material.ENDER_PEARL);
        org.bukkit.entity.EnderPearl thrownPearl = player.launchProjectile(org.bukkit.entity.EnderPearl.class);
        thrownPearl.setVelocity(player.getEyeLocation().getDirection().normalize().multiply(1.5));
        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_PEARL_THROW, 1f, 1f);
        cooldowns.setCooldown(player.getUniqueId(), RuneType.ENDER, cd);
    }

    // ── SPEED ─────────────────────────────────────────────────────────────────
    private void activateSpeed(Player player, PlayerRuneData data) {
        int cd = 30;
        // Give a Wind Spear (custom item)
        org.bukkit.inventory.ItemStack spear = createWindSpear();
        if (player.getInventory().firstEmpty() != -1) {
            player.getInventory().addItem(spear);
        } else {
            player.getWorld().dropItemNaturally(player.getLocation(), spear);
        }
        player.sendMessage("§f✦ Wind Spear ready!");
        player.playSound(player.getLocation(), Sound.ENTITY_WIND_CHARGE_WIND_BURST, 1f, 1.3f);
        cooldowns.setCooldown(player.getUniqueId(), RuneType.SPEED, cd);
    }

    private org.bukkit.inventory.ItemStack createWindSpear() {
        org.bukkit.inventory.ItemStack spear = new org.bukkit.inventory.ItemStack(org.bukkit.Material.WIND_CHARGE);
        org.bukkit.inventory.meta.ItemMeta meta = spear.getItemMeta();
        if (meta != null) {
            meta.displayName(net.kyori.adventure.text.Component.text("✦ Wind Spear")
                    .color(net.kyori.adventure.text.format.NamedTextColor.WHITE)
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)
                    .decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, true));
            meta.lore(java.util.List.of(
                net.kyori.adventure.text.Component.text("Throw to launch yourself in the impact direction")
                    .color(net.kyori.adventure.text.format.NamedTextColor.GRAY)
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)
            ));
            meta.addEnchant(org.bukkit.enchantments.Enchantment.VANISHING_CURSE, 1, true);
            meta.getPersistentDataContainer().set(
                new org.bukkit.NamespacedKey("runicsmp", "wind_spear"),
                org.bukkit.persistence.PersistentDataType.BOOLEAN, true);
            spear.setItemMeta(meta);
        }
        return spear;
    }

    // ── DRAGON LEAP ───────────────────────────────────────────────────────────
    // Called via shift+ability for Dragon rune

    // ── DRAGON LEAP ───────────────────────────────────────────────────────────
    private void activateDragonLeap(Player player, PlayerRuneData data) {
        int cd = 45;
        World w = player.getWorld();
        Location loc = player.getLocation();

        // Launch upward ~10 blocks
        player.setVelocity(new Vector(0, 1.8, 0));
        player.playSound(loc, Sound.ENTITY_ENDER_DRAGON_FLAP, 1f, 0.8f);
        w.spawnParticle(Particle.TOTEM_OF_UNDYING, loc.clone().add(0,1,0), 20, 0.5, 0.5, 0.5, 0.5);
        w.spawnParticle(Particle.DUST, loc.clone().add(0,1,0), 15, 0.5, 0.5, 0.5,
                new Particle.DustOptions(Color.fromRGB(100, 0, 200), 2f));

        // Wait for landing then create explosion
        new BukkitRunnable() {
            boolean launched = false;
            int ticks = 0;
            @Override public void run() {
                if (ticks++ > 100) { cancel(); return; }
                if (ticks > 5) launched = true;
                if (!launched) return;
                if (player.isOnGround()) {
                    Location landLoc = player.getLocation();
                    // Explosion particles
                    w.spawnParticle(Particle.FLASH, landLoc, 1, 0, 0, 0, 0);
                    w.spawnParticle(Particle.TOTEM_OF_UNDYING, landLoc.clone().add(0,1,0), 40, 2.0, 1.0, 2.0, 0.8);
                    w.spawnParticle(Particle.DUST, landLoc.clone().add(0,1,0), 30, 2.0, 0.5, 2.0,
                            new Particle.DustOptions(Color.fromRGB(100, 0, 200), 2.5f));
                    w.playSound(landLoc, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.6f);
                    w.playSound(landLoc, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 1.5f);
                    // Damage nearby players
                    for (org.bukkit.entity.Entity nearby : landLoc.getWorld().getNearbyEntities(landLoc, 5, 5, 5)) {
                        if (!(nearby instanceof LivingEntity le)) continue;
                        if (nearby.equals(player)) continue;
                        if (nearby instanceof Player tp &&
                                plugin.getTrustManager().isTrustedUUID(player.getUniqueId(), tp.getUniqueId())) continue;
                        double dist = nearby.getLocation().distance(landLoc);
                        double dmg = Math.max(4.0, 16.0 - dist * 2); // scales with distance
                        le.damage(dmg, player);
                    }
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);

        player.sendMessage("§5§l✦ Dragon Leap!");
        cooldowns.setCooldown(player.getUniqueId(), RuneType.DRAGON, cd);
    }

}
