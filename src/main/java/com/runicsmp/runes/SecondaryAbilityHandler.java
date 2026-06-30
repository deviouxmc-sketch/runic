package com.runicsmp.runes;

import com.runicsmp.RunicSMP;
import com.runicsmp.data.PlayerRuneData;
import com.runicsmp.data.RuneType;
import com.runicsmp.managers.RuneManager;
import com.runicsmp.utils.CooldownManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

public class SecondaryAbilityHandler {

    private final RunicSMP plugin;
    private final RuneManager runeManager;
    private final CooldownManager cooldowns;

    public SecondaryAbilityHandler(RunicSMP plugin) {
        this.plugin = plugin;
        this.runeManager = plugin.getRuneManager();
        this.cooldowns = runeManager.getCooldowns();
    }

    public void activateSecondary(Player player) {
        PlayerRuneData data = runeManager.getData(player);
        if (!data.hasSecondary()) { player.sendMessage("§cNo secondary rune equipped!"); return; }
        RuneType rune = data.getSecondaryRune();
        // No energy gate - energy system removed
        if (cooldowns.isOnCooldown(player.getUniqueId(), rune)) {
            player.sendMessage("§cCooldown! §e" + cooldowns.getRemainingSeconds(player.getUniqueId(), rune) + "s left.");
            return;
        }
        switch (rune) {
            case FIRE     -> activateFire(player, data);
            case VITALITY -> activateVitality(player, data);
            case HASTE    -> activateHaste(player, data);
            case TRADER   -> activateTrader(player, data);
            case FROST    -> activateFrost(player, data);
            case DASH     -> activateDash(player, data);
            case TIDAL    -> activateTidal(player, data);
            default       -> { if (!plugin.getNotifManager().isMuted(player.getUniqueId())) player.sendMessage("§7No active ability for this secondary."); }
        }

        plugin.getParticleManager().playActiveEffect(player, rune);
        plugin.getHudManager().notifySecondaryUsed(player, cooldowns.getRemainingSeconds(player.getUniqueId(), rune));
    }

    // ── FIRE ──────────────────────────────────────────────────────────────────
    private void activateFire(Player player, PlayerRuneData data) {
        if (player.isSneaking()) {
            activateMagmaDomain(player, data);
        } else {
            activateBlazeArmy(player, data);
        }
    }

    private void activateBlazeArmy(Player player, PlayerRuneData data) {
        int cd = 60;
        // Summon 3 Blazes that attack enemies
        for (int i = 0; i < 3; i++) {
            double angle = Math.PI * 2 / 3 * i;
            Location spawnLoc = player.getLocation().add(Math.cos(angle) * 2, 0.5, Math.sin(angle) * 2);
            org.bukkit.entity.Blaze blaze = (org.bukkit.entity.Blaze)
                    player.getWorld().spawnEntity(spawnLoc, org.bukkit.entity.EntityType.BLAZE);
            blaze.setCustomName("§c✦ " + player.getName() + "'s Blaze");
            blaze.setCustomNameVisible(true);
            // Remove after 30s
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (blaze.isValid()) blaze.remove();
            }, 600L);
        }
        player.getWorld().spawnParticle(Particle.FLAME, player.getLocation().add(0,1,0), 30, 1.0, 0.5, 1.0, 0.1);
        player.playSound(player.getLocation(), Sound.ENTITY_BLAZE_AMBIENT, 1f, 0.8f);
        player.sendMessage("§6✦ Blaze Army summoned!");
        cooldowns.setCooldown(player.getUniqueId(), rune(data), cd);
    }

    private void activateMagmaDomain(Player player, PlayerRuneData data) {
        int cd = 90;
        Location center = player.getLocation();
        World w = player.getWorld();
        java.util.List<Location> magmaLocs = new java.util.ArrayList<>();

        // Place 9x9 magma
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                Location ml = center.clone().add(x, -1, z);
                if (ml.getBlock().getType().isSolid()) {
                    Location top = ml.clone().add(0, 1, 0);
                    if (top.getBlock().getType() == org.bukkit.Material.AIR) {
                        top.getBlock().setType(org.bukkit.Material.MAGMA_BLOCK);
                        magmaLocs.add(top.clone());
                    }
                }
            }
        }

        // Burn players inside + particles every 2s for 20s
        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                if (ticks++ >= 10) {
                    // Remove magma
                    for (Location ml : magmaLocs)
                        if (ml.getBlock().getType() == org.bukkit.Material.MAGMA_BLOCK)
                            ml.getBlock().setType(org.bukkit.Material.AIR);
                    cancel(); return;
                }
                for (org.bukkit.entity.Entity nearby : w.getNearbyEntities(center, 5, 3, 5)) {
                    if (!(nearby instanceof org.bukkit.entity.LivingEntity le)) continue;
                    if (nearby.equals(player)) continue;
                    if (nearby instanceof Player tp &&
                            plugin.getTrustManager().isTrustedUUID(player.getUniqueId(), tp.getUniqueId())) continue;
                    le.setFireTicks(80); // 4s on fire
                }
                w.spawnParticle(Particle.FLAME, center.clone().add(0, 0.5, 0), 20, 4.0, 0.2, 4.0, 0.05);
                w.spawnParticle(Particle.LAVA, center.clone().add(0, 0.3, 0), 10, 3.0, 0.1, 3.0, 0.02);
            }
        }.runTaskTimer(plugin, 0L, 40L);

        w.playSound(center, Sound.BLOCK_LAVA_AMBIENT, 1f, 0.7f);
        player.sendMessage("§c✦ Magma Domain activated! (20s)");
        cooldowns.setCooldown(player.getUniqueId(), rune(data), cd);
    }


    // ── VITALITY ──────────────────────────────────────────────────────────────
    private void activateVitality(Player player, PlayerRuneData data) {
        int cd = 60; // 1 minute
        double maxHp = player.getAttribute(Attribute.MAX_HEALTH).getValue();
        player.setHealth(maxHp);
        player.setSaturation(20f);
        player.setFoodLevel(20);
        // Heart spiral
        new BukkitRunnable() {
            double angle = 0; double h = 0;
            @Override public void run() {
                if (h > 3) { cancel(); return; }
                for (int i = 0; i < 3; i++) {
                    double a = angle + i * Math.PI * 2 / 3;
                    player.getWorld().spawnParticle(Particle.HEART,
                            player.getLocation().add(Math.cos(a), h, Math.sin(a)), 1, 0, 0, 0, 0);
                }
                player.getWorld().spawnParticle(Particle.DUST,
                        player.getLocation().add(0, h * 0.5, 0), 3, 0.4, 0.1, 0.4,
                        new Particle.DustOptions(Color.fromRGB(255, 105, 180), 2.5f));
                angle += 0.4; h += 0.1;
            }
        }.runTaskTimer(plugin, 0L, 1L);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);
        if (!plugin.getNotifManager().isMuted(player.getUniqueId())) player.sendMessage("§d§lVitality Rune — Fully healed!");
        cooldowns.setCooldown(player.getUniqueId(), rune(data), cd);
    }

    // ── HASTE ─────────────────────────────────────────────────────────────────
    private void activateHaste(Player player, PlayerRuneData data) {
        int cd = 40;
        int duration = 10; // 10 seconds hardcoded
        player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 200, 254, false, true, true)); // Haste 255 for 10s
        // Speed streaks burst
        for (int i = 0; i < 20; i++) {
            double angle = Math.random() * Math.PI * 2;
            player.getWorld().spawnParticle(Particle.DUST,
                    player.getLocation().add(Math.cos(angle) * 0.5, Math.random() * 2, Math.sin(angle) * 0.5),
                    1, 0, 0, 0, new Particle.DustOptions(Color.YELLOW, 2.5f));
        }
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 2f);
        player.sendMessage("§e§lHaste Rune — Instant mining for §a" + duration + "s!");
        // Restore permanent Haste I after ability ends (10s + buffer)
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            var d = plugin.getRuneManager().getData(player);
            if (d.hasSecondary() && d.getSecondaryRune() == com.runicsmp.data.RuneType.HASTE) {
                player.removePotionEffect(PotionEffectType.HASTE); // clear ability haste first
                player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE,
                        Integer.MAX_VALUE, 0, false, false, true)); // reapply Haste I perm
            }
        }, 205L);
        cooldowns.setCooldown(player.getUniqueId(), rune(data), cd);
    }

    // ── TRADER — Hero of the Village 255 for 20 seconds ───────────────────────
    private void activateTrader(Player player, PlayerRuneData data) {
        int cd = 120; // 2 minutes

        player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.HERO_OF_THE_VILLAGE,
                400, 254, false, true, true)); // 400 ticks = 20 seconds, amplifier 254 = level 255

        // Gold sparkle burst
        player.getWorld().spawnParticle(Particle.DUST, player.getLocation().add(0,1,0),
                30, 0.5, 0.8, 0.5,
                new Particle.DustOptions(org.bukkit.Color.fromRGB(255, 200, 0), 2f));
        player.getWorld().spawnParticle(Particle.ENCHANT, player.getLocation().add(0,1,0),
                20, 0.5, 0.5, 0.5, 0.3);
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_CELEBRATE, 1f, 1f);
        player.playSound(player.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.5f, 1.2f);
        if (!plugin.getNotifManager().isMuted(player.getUniqueId()))
            player.sendMessage("§6§lTrader Rune — Hero of the Village 255 for 20s!");
        cooldowns.setCooldown(player.getUniqueId(), rune(data), cd);
    }

    // ── TIDAL (Ocean Card — exact) ───────────────────────────────────────────
    private void activateTidal(Player player, PlayerRuneData data) {
        int cd = 40;
        int lockRadius = 5; // from config default
        int t = 0; // tick counter (used as loop variable in card)
        World w = player.getWorld();
        Location loc = player.getLocation().clone();
        Location playerLoc = loc.clone();

        // Ocean card exact: spawnRing(NAUTILUS, loc, radius, (t+3)*20, false)
        //                   spawnRing(BUBBLE, loc, radius-0.1, t*20)  (b method)
        // Then at delay 40: cc (NAUTILUS rings again at radius+1 and radius)
        // Then at delay 10: cd (BUBBLE + knockback/effects via ch)

        // Initial rings (t=0 in the card loop)
        spawnParticleRing(w, loc, Particle.NAUTILUS, lockRadius, (0+3)*20);
        // BUBBLE ring uses b method (different ring logic) - just spawn at radius-0.1
        spawnParticleRing(w, loc, Particle.BUBBLE, lockRadius - 0.1, 0*20 == 0 ? 1 : 0*20);

        // Rain particles surrounding effect
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (t++ > 40) { cancel(); return; }
                for (int i = 0; i < 8; i++) {
                    double rx = (Math.random()*2-1) * (lockRadius+2);
                    double rz = (Math.random()*2-1) * (lockRadius+2);
                    w.spawnParticle(Particle.DRIPPING_WATER, loc.clone().add(rx, 4, rz), 1, 0, 0, 0, 0);
                    w.spawnParticle(Particle.SPLASH, loc.clone().add(rx, 0.1, rz), 1, 0.1, 0, 0.1, 0.05);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);

        // At 40 ticks: cc effect - NAUTILUS ring at radius+1 and radius again
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            spawnParticleRing(w, loc, Particle.NAUTILUS, lockRadius+1, (t+1)*20);
            spawnParticleRing(w, loc, Particle.NAUTILUS, lockRadius, t*20);
        }, 40L);

        // At 10 ticks: cd effect - BUBBLE ring + knockback + Mining Fatigue + Slowness
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            spawnParticleRing(w, loc, Particle.BUBBLE, lockRadius - 0.1, t*20 == 0 ? 20 : t*20);
            // ch effect: getNearbyEntities(radius, radius, radius) -> Mining Fatigue + Slowness
            for (org.bukkit.entity.Entity nearby : loc.getWorld().getNearbyEntities(loc, lockRadius, lockRadius, lockRadius)) {
                if (!(nearby instanceof org.bukkit.entity.LivingEntity le)) continue;
                if (nearby.equals(player)) continue;
                if (nearby instanceof Player tp &&
                        plugin.getTrustManager().isTrustedUUID(player.getUniqueId(), tp.getUniqueId())) continue;
                le.addPotionEffect(new org.bukkit.potion.PotionEffect(
                        org.bukkit.potion.PotionEffectType.MINING_FATIGUE, 60, 1, false, false, true));
                le.addPotionEffect(new org.bukkit.potion.PotionEffect(
                        org.bukkit.potion.PotionEffectType.SLOWNESS, 60, 1, false, false, true));
                // Knockback
                org.bukkit.util.Vector kbDir = le.getLocation().subtract(loc).toVector();
                if (kbDir.lengthSquared() > 0) kbDir.normalize();
                kbDir.setY(0.6);
                le.setVelocity(kbDir.multiply(2.0));
            }
        }, 10L);

        w.playSound(loc, Sound.ENTITY_ELDER_GUARDIAN_AMBIENT, 1f, 0.7f);
        w.playSound(loc, Sound.ENTITY_GENERIC_SPLASH, 1f, 0.5f);

        if (!plugin.getNotifManager().isMuted(player.getUniqueId()))
            player.sendMessage("§1§lTidal Rune — Ocean's wrath!");
        cooldowns.setCooldown(player.getUniqueId(), rune(data), cd);
    }

    private void spawnParticleRing(World w, Location center, Particle particle, double radius, int points) {
        if (points <= 0) points = 20;
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 / points * i;
            Location pt = center.clone().add(Math.cos(a)*radius, 0.1, Math.sin(a)*radius);
            w.spawnParticle(particle, pt, 1, 0, 0, 0, 0);
        }
    }


    // ── FROST ─────────────────────────────────────────────────────────────────
    private void activateFrost(Player player, PlayerRuneData data) {
        int cd = 50; // 50 second cooldown
        double radius = 8.0;
        int freezeTicks = 60; // 3 seconds

        int count = 0;
        for (org.bukkit.entity.Entity nearby : player.getNearbyEntities(radius, radius, radius)) {
            if (!(nearby instanceof org.bukkit.entity.LivingEntity le)) continue;
            if (nearby.equals(player)) continue;
            if (nearby instanceof org.bukkit.entity.Player target) {
                if (plugin.getTrustManager().isTrustedUUID(player.getUniqueId(), target.getUniqueId())) continue;
            }
            // Slowness 255 — cant move
            le.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.SLOWNESS, freezeTicks, 254, false, true, true));
            // Max freeze ticks for visual
            le.setFreezeTicks(le.getMaxFreezeTicks());
            count++;

            // Lock completely in place — zero ALL velocity every tick for 3 seconds
            final org.bukkit.entity.LivingEntity frozen = le;
            final org.bukkit.Location frozenLoc = le.getLocation().clone();
            new org.bukkit.scheduler.BukkitRunnable() {
                int t = 0;
                @Override public void run() {
                    if (t++ >= freezeTicks || !frozen.isValid()) { cancel(); return; }
                    // Zero all velocity — no movement in any direction
                    frozen.setVelocity(new org.bukkit.util.Vector(0, -0.08, 0)); // tiny downward to stay grounded
                    // Teleport back to frozen spot if they somehow moved
                    if (frozen instanceof org.bukkit.entity.Player fp) {
                        org.bukkit.Location cur = fp.getLocation();
                        if (cur.distanceSquared(frozenLoc) > 0.1) {
                            frozenLoc.setYaw(cur.getYaw());
                            frozenLoc.setPitch(cur.getPitch());
                            fp.teleport(frozenLoc);
                        }
                    }
                }
            }.runTaskTimer(plugin, 0L, 1L);
        }

        // Freeze nearby water source blocks
        for (int x = -(int)radius; x <= (int)radius; x++) {
            for (int z = -(int)radius; z <= (int)radius; z++) {
                org.bukkit.Location check = player.getLocation().add(x, 0, z);
                if (check.getBlock().getType() == org.bukkit.Material.WATER) {
                    check.getBlock().setType(org.bukkit.Material.ICE);
                    // Melt back after 10 seconds
                    final org.bukkit.block.Block b = check.getBlock();
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                        if (b.getType() == org.bukkit.Material.ICE) b.setType(org.bukkit.Material.WATER);
                    }, 200L);
                }
            }
        }

        // Frost wave — expanding rings of ice like tidal but frozen
        org.bukkit.World fw = player.getWorld();
        org.bukkit.Location fc = player.getLocation().add(0, 0.1, 0);

        new org.bukkit.scheduler.BukkitRunnable() {
            double ringRadius = 0.5;
            int ticks = 0;
            @Override public void run() {
                if (ticks++ > 20) { cancel(); return; }
                ringRadius += 0.4;
                // Expanding ice ring
                for (int i = 0; i < 20; i++) {
                    double a = Math.PI * 2 / 20 * i;
                    org.bukkit.Location ring = fc.clone().add(
                            Math.cos(a) * ringRadius, 0.05, Math.sin(a) * ringRadius);
                    fw.spawnParticle(Particle.SNOWFLAKE, ring, 1, 0, 0, 0, 0.01);
                    fw.spawnParticle(Particle.DUST, ring, 1, 0, 0, 0,
                            new Particle.DustOptions(org.bukkit.Color.fromRGB(150, 220, 255), 1.5f));
                }
                // Inner burst upward
                fw.spawnParticle(Particle.SNOWFLAKE, fc.clone().add(0, ticks * 0.1, 0),
                        4, 0.3, 0.1, 0.3, 0.03);
                fw.spawnParticle(Particle.DUST, fc.clone().add(0, ticks * 0.05, 0),
                        3, 0.2, 0.1, 0.2,
                        new Particle.DustOptions(org.bukkit.Color.fromRGB(200, 240, 255), 1.8f));
            }
        }.runTaskTimer(plugin, 0L, 1L);

        // Freeze particles on each hit target
        for (org.bukkit.entity.Entity nearby : player.getNearbyEntities(radius, radius, radius)) {
            if (!(nearby instanceof org.bukkit.entity.LivingEntity le)) continue;
            if (nearby.equals(player)) continue;
            fw.spawnParticle(Particle.SNOWFLAKE, le.getLocation().add(0,1,0), 12, 0.3,0.5,0.3, 0.04);
            fw.spawnParticle(Particle.DUST, le.getLocation().add(0,1,0), 8, 0.2,0.4,0.2,
                    new Particle.DustOptions(org.bukkit.Color.fromRGB(150, 220, 255), 1.5f));
        }

        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_POWDER_SNOW_PLACE, 1f, 0.7f);
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_HURT_FREEZE, 1f, 1.0f);
        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_GLASS_BREAK, 0.6f, 1.5f);
        if (!plugin.getNotifManager().isMuted(player.getUniqueId()))
            player.sendMessage("§b§lFrost Rune — §7Slowed §e" + count + " §7nearby enemies!");
        cooldowns.setCooldown(player.getUniqueId(), rune(data), cd);
    }

    // ── DASH ──────────────────────────────────────────────────────────────────
    private void activateDash(Player player, PlayerRuneData data) {
        int cd = 20;

        // Dash in full look direction including vertical (looking up = goes up)
        org.bukkit.util.Vector dir = player.getLocation().getDirection().normalize();
        // Preserve Y direction — if looking up, boost goes upward
        player.setVelocity(dir.multiply(3.5));

        // Register dash for fall damage negate (3 seconds)
        if (plugin.getCombatListener() != null) plugin.getCombatListener().registerDash(player.getUniqueId());
        player.setFallDistance(0f);

        // Wind dash particles — same as wind rune
        org.bukkit.World w = player.getWorld();
        org.bukkit.Location loc = player.getLocation().add(0, 1, 0);

        // Burst at launch point
        w.spawnParticle(Particle.CLOUD, loc, 8, 0.3, 0.3, 0.3, 0.12);
        w.spawnParticle(Particle.DUST, loc, 6, 0.3, 0.4, 0.3,
                new Particle.DustOptions(Color.WHITE, 2f));
        w.spawnParticle(Particle.SMALL_GUST, loc, 4, 0.2, 0.2, 0.2, 0.04);

        // Trail of wind particles behind the player for 0.5 seconds
        new org.bukkit.scheduler.BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (t++ > 10 || !player.isOnline()) { cancel(); return; }
                org.bukkit.Location trail = player.getLocation().add(0, 0.8, 0);
                w.spawnParticle(Particle.CLOUD, trail, 4, 0.15, 0.15, 0.15, 0.06);
                w.spawnParticle(Particle.DUST, trail, 3, 0.1, 0.2, 0.1,
                        new Particle.DustOptions(Color.WHITE, 1.5f));
                w.spawnParticle(Particle.SMALL_GUST, trail, 2, 0.1, 0.1, 0.1, 0.02);
            }
        }.runTaskTimer(plugin, 0L, 1L);

        // Sound - just the wind burst, short
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_WIND_CHARGE_WIND_BURST, 0.8f, 1.2f);

        cooldowns.setCooldown(player.getUniqueId(), com.runicsmp.data.RuneType.DASH, 20);
    }

}
