package com.runicsmp.runes;

import com.runicsmp.RunicSMP;
import com.runicsmp.data.PlayerRuneData;
import com.runicsmp.data.RuneType;
import com.runicsmp.managers.ResurrectionManager;
import com.runicsmp.managers.TrustManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * Handles the more complex primary ability implementations:
 * - Resurrection (pick-a-player UI)
 * - Warped (teleport target)
 * - Shadow (trust-aware blind + sculk particles)
 * - Lockdown (full stun)
 * - Swap (nearest mob/player swap)
 * - Wind (mace-like launch)
 */
public class PrimaryAbilityExtensions {

    private final RunicSMP plugin;

    // Warped rune recent targets per player
    private final Map<UUID, UUID> warpedTargets = new HashMap<>();

    public PrimaryAbilityExtensions(RunicSMP plugin) { this.plugin = plugin; }

    // ── WIND PRIMARY — dash in look direction (jar logic + our particles) ──────
    public void activateWindDash(Player player) {
        // Feather card exact dash: getDirection(), multiply(2.5), setY(1), dc trail every 2t, ENTITY_BREEZE_SHOOT vol=2.5
        org.bukkit.util.Vector dir = player.getLocation().getDirection();
        dir.multiply(2.5);
        dir.setY(1);

        // dc trail: CLOUD x2 at player location every 2 ticks until grounded
        new org.bukkit.scheduler.BukkitRunnable() {
            @Override public void run() {
                if (player.isOnGround() || !player.isOnline()) { cancel(); return; }
                player.spawnParticle(org.bukkit.Particle.CLOUD, player.getLocation(), 2, 0, 0, 0, 0);
            }
        }.runTaskTimer(plugin, 0L, 2L);

        player.setVelocity(dir);
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_BREEZE_SHOOT, 2.5f, 1f);
    }


    public void startWindPassive(Player player) {
        // The actual tracking is done in WindLandingListener
    }

    /**
     * Called when wind rune player lands after being airborne.
     * Activates mace-like hit for next attack. 30s cooldown.
     */
    public void onWindLanding(Player player, double fallHeight) {
        if (fallHeight < 2.0) return; // need at least 2 blocks of fall
        if (plugin.getRuneManager().getCooldowns().isOnCooldown(player.getUniqueId(), RuneType.WIND)) return;

        // Enable mace hit for next attack
        double launchY = player.getLocation().getY() + fallHeight;
        player.setMetadata("wind_launch_y", new org.bukkit.metadata.FixedMetadataValue(plugin, launchY));
        player.setMetadata("wind_mace_active", new org.bukkit.metadata.FixedMetadataValue(plugin, true));
        player.setFallDistance(0f); // cancel fall damage

        // Landing burst particles
        Location loc = player.getLocation();
        World w = loc.getWorld();
        w.spawnParticle(Particle.CLOUD, loc.clone().add(0,0.5,0), 30, 0.5,0.3,0.5, 0.08);
        w.spawnParticle(Particle.DUST, loc.clone().add(0,0.3,0), 20, 0.6,0.1,0.6,
                new Particle.DustOptions(Color.WHITE, 3f));
        w.spawnParticle(Particle.SWEEP_ATTACK, loc.clone().add(0,0.3,0), 8, 0.4,0.1,0.4, 0.05);
        w.spawnParticle(Particle.SMALL_GUST, loc.clone().add(0,0.5,0), 5, 0.4,0.3,0.4, 0.03);
        player.playSound(loc, Sound.ENTITY_BREEZE_WIND_BURST, 1f, 1.2f);
        player.sendMessage("§b⚡ Wind Rune — Mace hit ready! Strike someone within 5 seconds.");

        // Auto-expire mace hit after 5 seconds if not used
        new BukkitRunnable() {
            @Override public void run() {
                if (player.hasMetadata("wind_mace_active")) {
                    player.removeMetadata("wind_mace_active", plugin);
                    player.removeMetadata("wind_launch_y", plugin);
                }
            }
        }.runTaskLater(plugin, 100L);
    }

    /**
     * Called from CombatListener when a Wind rune player hits someone while airborne/just landed.
     * Calculates density-2 mace damage from height above ground.
     */
    public double calculateWindMaceDamage(Player attacker, double baseDamage) {
        if (!attacker.hasMetadata("wind_mace_active")) return 0;

        // Get stored airborne Y, or fallback to current Y + fallDistance
        double heightBonus;
        if (attacker.hasMetadata("wind_airborne")) {
            double airborneY = attacker.getMetadata("wind_airborne").get(0).asDouble();
            heightBonus = Math.max(0, airborneY - attacker.getLocation().getY());
        } else {
            heightBonus = Math.max(0, attacker.getFallDistance());
        }

        // Density 2 mace: each block = 1 extra damage, capped at 100
        double bonus = Math.min(heightBonus, 100) * 1.0;

        attacker.removeMetadata("wind_mace_active", plugin);
        attacker.removeMetadata("wind_airborne", plugin);
        if (attacker.hasMetadata("wind_launch_y")) attacker.removeMetadata("wind_launch_y", plugin);

        // 30s cooldown
        plugin.getRuneManager().getCooldowns().setCooldown(attacker.getUniqueId(), RuneType.WIND, 30);
        return baseDamage + bonus;
    }

    // ── SHADOW — blind radius, trust-aware, sculk + black smoke ───────────────
    public void activateShadow(Player player) {
        int cd = 90;
        int radius = 15;
        TrustManager trust = plugin.getTrustManager();

        // True invis
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 200, 0, false, false));

        // Blind all nearby non-trusted players
        for (Entity e : player.getNearbyEntities(radius, radius, radius)) {
            if (!(e instanceof Player target)) continue;
            if (trust.isTrusted(player, target)) continue;
            target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 100, 0));
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));
            target.sendMessage("§8You've been blinded by " + player.getName() + "'s Shadow Rune!");
        }

        // Black smoke + sculk + void particles explosion
        Location loc = player.getLocation();
        World world = player.getWorld();
        world.spawnParticle(Particle.SMOKE, loc.clone().add(0,1,0), 80, 0.8, 1.2, 0.8, 0.12);
        world.spawnParticle(Particle.SCULK_SOUL, loc.clone().add(0,1,0), 20, 0.5, 0.8, 0.5, 0.05);
        world.spawnParticle(Particle.SCULK_CHARGE_POP, loc.clone().add(0,1,0), 30, 0.4, 0.6, 0.4, 0.1);
        world.spawnParticle(Particle.ASH, loc.clone().add(0,1,0), 60, 1.0, 1.0, 1.0, 0.15);
        world.spawnParticle(Particle.DUST, loc.clone().add(0,1,0), 40, 0.5, 1.0, 0.5,
                new Particle.DustOptions(Color.fromRGB(5, 0, 5), 3f));
        // Expanding dark rings
        new BukkitRunnable() {
            double r = 0.5; int t = 0;
            @Override public void run() {
                if (t++ > 15) { cancel(); return; }
                for (int i = 0; i < 20; i++) {
                    double a = Math.PI * 2 / 20 * i;
                    Location pt = loc.clone().add(Math.cos(a) * r, 0.1, Math.sin(a) * r);
                    world.spawnParticle(Particle.SMOKE, pt, 1, 0, 0, 0, 0);
                    world.spawnParticle(Particle.SCULK_CHARGE_POP, pt, 1, 0, 0, 0, 0);
                }
                r += 0.7;
            }
        }.runTaskTimer(plugin, 0L, 2L);

        player.playSound(loc, Sound.BLOCK_SCULK_SENSOR_CLICKING, 1f, 0.5f);
        player.playSound(loc, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.6f);
        player.sendMessage("§8§lShadow Rune — Darkness falls!");
        plugin.getRuneManager().getCooldowns().setCooldown(player.getUniqueId(), RuneType.SHADOW, cd);
    }

    // ── LOCKDOWN — complete action lockdown 2s, trust-aware ─────────────────
    public void activateLockdown(Player player) {
        org.bukkit.Location loc = player.getLocation();
        org.bukkit.World w = player.getWorld();
        int lockRadius = 5;

        // Frost card exact:
        // 1. DUST ring AQUA, 30 pts at radius
        spawnDustRing(w, loc, lockRadius, 30, org.bukkit.Color.AQUA);
        // 2. SNOWFLAKE ring, 60 pts at radius
        spawnParticleRing(w, loc, org.bukkit.Particle.SNOWFLAKE, lockRadius, 60);
        // 3. ENTITY_PLAYER_HURT_FREEZE vol=25 pitch=0.5
        player.playSound(loc, org.bukkit.Sound.ENTITY_PLAYER_HURT_FREEZE, 25f, 0.5f);

        // 4. dm at 15 ticks: ENTITY_PLAYER_HURT_FREEZE vol=25 pitch=0.6
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            player.playSound(loc, org.bukkit.Sound.ENTITY_PLAYER_HURT_FREEZE, 25f, 0.6f);
        }, 15L);

        // Vertical columns rising up for box effect
        new org.bukkit.scheduler.BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (t++ > 15) { cancel(); return; }
                double h = t * 0.2;
                for (int ci = 0; ci < 8; ci++) {
                    double ca = Math.PI * 2 / 8 * ci;
                    org.bukkit.Location col = loc.clone().add(Math.cos(ca)*lockRadius, h, Math.sin(ca)*lockRadius);
                    w.spawnParticle(org.bukkit.Particle.DUST, col, 1, 0, 0, 0,
                            new org.bukkit.Particle.DustOptions(org.bukkit.Color.AQUA, 1.0f));
                    w.spawnParticle(org.bukkit.Particle.SNOWFLAKE, col, 1, 0.05, 0.1, 0.05, 0);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);

        // 5. getNearbyEntities -> DARKNESS + SLOWNESS + freeze ticks (dn every 30t x3)
        for (org.bukkit.entity.Entity nearby : player.getNearbyEntities(lockRadius, lockRadius, lockRadius)) {
            if (!(nearby instanceof org.bukkit.entity.Player target)) continue;
            if (plugin.getTrustManager().isTrustedUUID(player.getUniqueId(), target.getUniqueId())) continue;

            // DARKNESS 200t amplifier 4
            target.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.DARKNESS, 200, 4, true, true, true));
            // SLOWNESS 200t amplifier 4
            target.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.SLOWNESS, 200, 4, true, true, true));
            // dn: setFreezeTicks(300) every 30 ticks, 3 times
            new org.bukkit.scheduler.BukkitRunnable() {
                int count = 0;
                @Override public void run() {
                    if (count++ >= 3 || !target.isOnline()) { cancel(); return; }
                    target.setFreezeTicks(300);
                }
            }.runTaskTimer(plugin, 0L, 30L);

            target.sendMessage("§b❄ You have been frozen by the Lockdown Rune!");
        }

        player.sendMessage("§b❄ Lockdown activated!");
        plugin.getRuneManager().getCooldowns().setCooldown(player.getUniqueId(), com.runicsmp.data.RuneType.LOCKDOWN, 45);
    }

    private void spawnDustRing(org.bukkit.World w, org.bukkit.Location center, double radius, int points, org.bukkit.Color color) {
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 / points * i;
            org.bukkit.Location pt = center.clone().add(Math.cos(a)*radius, 0.1, Math.sin(a)*radius);
            w.spawnParticle(org.bukkit.Particle.DUST, pt, 1, 0, 0, 0,
                    new org.bukkit.Particle.DustOptions(color, 1.0f));
        }
    }

    private void spawnParticleRing(org.bukkit.World w, org.bukkit.Location center, org.bukkit.Particle particle, double radius, int points) {
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 / points * i;
            org.bukkit.Location pt = center.clone().add(Math.cos(a)*radius, 0.1, Math.sin(a)*radius);
            w.spawnParticle(particle, pt, 1, 0, 0.1, 0, 0);
        }
    }


    public void activateSwap(Player player) {
        int cd = 90;
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize();

        Entity target = null;
        double closest = Double.MAX_VALUE;

        for (Entity e : player.getNearbyEntities(20, 20, 20)) {
            if (e.equals(player)) continue;
            if (!(e instanceof LivingEntity)) continue;
            // Check if entity is roughly in look direction
            Vector toEntity = e.getLocation().subtract(eye).toVector().normalize();
            double dot = toEntity.dot(dir);
            if (dot > 0.85) { // within ~30 degrees of look direction
                double dist = e.getLocation().distanceSquared(eye);
                if (dist < closest) { closest = dist; target = e; }
            }
        }

        if (target == null) {
            player.sendMessage("§cNo target in range! Look toward a mob or player.");
            return;
        }

        Location playerLoc = player.getLocation().clone();
        Location targetLoc = target.getLocation().clone();

        // Particles at both locations
        spawnSwapBurst(player.getWorld(), playerLoc);
        spawnSwapBurst(player.getWorld(), targetLoc);

        // Teleport both
        final Entity finalTarget = target;
        player.teleport(targetLoc);
        finalTarget.teleport(playerLoc);

        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.3f);
        player.sendMessage("§f§lSwap Rune — Swapped with §e" + (target instanceof Player p ? p.getName() : target.getType().name()) + "§f!");
        if (target instanceof Player tp) tp.sendMessage("§fYou were swapped by §e" + player.getName() + "§f!");
        plugin.getRuneManager().getCooldowns().setCooldown(player.getUniqueId(), RuneType.SWAP, cd);
    }

    private void spawnSwapBurst(World world, Location loc) {
        world.spawnParticle(Particle.DUST, loc.clone().add(0,1,0), 40, 0.4, 0.8, 0.4,
                new Particle.DustOptions(Color.WHITE, 3f));
        world.spawnParticle(Particle.ENCHANT, loc.clone().add(0,1,0), 30, 0.4, 0.8, 0.4, 0.3);
        world.spawnParticle(Particle.PORTAL, loc.clone().add(0,1,0), 20, 0.3, 0.5, 0.3, 0.2);
    }

    // ── RESURRECTION — open UI to pick a player for protection ───────────────
    public void activateResurrection(Player caster) {
        int cd = 600; // 10 minutes
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        // Don't remove caster — allow self-targeting

        if (online.isEmpty()) {
            caster.sendMessage("§cNo players online!");
            return;
        }

        // Build inventory UI
        int size = Math.min(9 * ((online.size() / 9) + 1), 54);
        Inventory gui = Bukkit.createInventory(null, size,
                Component.text("☩ Resurrection — Choose Target").color(NamedTextColor.LIGHT_PURPLE));

        for (Player p : online) {
            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            ItemMeta meta = skull.getItemMeta();
            if (meta instanceof org.bukkit.inventory.meta.SkullMeta skullMeta) {
                skullMeta.setOwningPlayer(p);
                skullMeta.displayName(Component.text(p.getName())
                        .color(NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false));
                skullMeta.lore(List.of(
                        Component.text("Click to protect " + p.getName()).color(NamedTextColor.GRAY)
                                .decoration(TextDecoration.ITALIC, false),
                        Component.text("They get 1 totem pop within 10 minutes.").color(NamedTextColor.DARK_GRAY)
                                .decoration(TextDecoration.ITALIC, false)
                ));
                skullMeta.getPersistentDataContainer().set(
                        new org.bukkit.NamespacedKey(plugin, "res_target"),
                        org.bukkit.persistence.PersistentDataType.STRING, p.getUniqueId().toString());
                skull.setItemMeta(skullMeta);
            }
            gui.addItem(skull);
        }

        caster.openInventory(gui);
        caster.setMetadata("res_gui_open", new org.bukkit.metadata.FixedMetadataValue(plugin, true));
        plugin.getRuneManager().getCooldowns().setCooldown(caster.getUniqueId(), RuneType.RESURRECTION, cd);
    }

    // Called from inventory click event handler
    public void handleResurrectionClick(Player caster, ItemStack clicked) {
        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;
        String uuidStr = meta.getPersistentDataContainer().get(
                new org.bukkit.NamespacedKey(plugin, "res_target"),
                org.bukkit.persistence.PersistentDataType.STRING);
        if (uuidStr == null) return;

        Player target = Bukkit.getPlayer(UUID.fromString(uuidStr));
        if (target == null) { caster.sendMessage("§cThat player went offline!"); caster.closeInventory(); return; }

        plugin.getResurrectionManager().protect(caster, target, 10 * 60 * 1000L);
        caster.closeInventory();
        caster.removeMetadata("res_gui_open", plugin);

        // Particles on target
        target.getWorld().spawnParticle(Particle.ENCHANT, target.getLocation().add(0,1,0),
                60, 0.5, 1.0, 0.5, 0.4);
        target.getWorld().spawnParticle(Particle.DUST, target.getLocation().add(0,1,0),
                40, 0.4, 0.8, 0.4, new Particle.DustOptions(Color.WHITE, 3f));

        caster.sendMessage("§f§l☩ Resurrection — §e" + target.getName() + "§f is now protected for 10 minutes!");
        target.sendMessage("§f§l☩ " + caster.getName() + " has granted you Resurrection protection!");
        target.playSound(target.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.3f);
    }

    // ── WARPED — teleport whatever you're looking at up to 5000 blocks ─────
    public void activateWarped(Player player, String[] args) {
        int cd = 600; // 10 minutes

        // Find entity in crosshair within 5000 blocks
        org.bukkit.util.RayTraceResult ray = player.getWorld().rayTraceEntities(
                player.getEyeLocation(),
                player.getEyeLocation().getDirection(),
                5000,
                0.5,
                e -> !e.equals(player) && e instanceof org.bukkit.entity.LivingEntity
        );

        // Parse distance from args or default 200
        int distance = 200;
        if (args != null && args.length > 0) {
            try { distance = Math.max(15, Math.min(5000, Integer.parseInt(args[0]))); }
            catch (NumberFormatException ignored) {}
        }

        if (ray == null || ray.getHitEntity() == null) {
            player.sendMessage("§cNo target in your crosshair! Look at a player or mob within 5000 blocks.");
            return;
        }

        org.bukkit.entity.Entity target = ray.getHitEntity();

        // Teleport in player look direction at distance
        Vector dir = player.getLocation().getDirection().normalize();
        Location dest = player.getLocation().clone().add(dir.multiply(distance));
        dest.setY(dest.getWorld().getHighestBlockYAt(dest) + 1);

        Location from = target.getLocation().clone();
        spawnWarpBurst(from.getWorld(), from);
        target.teleport(dest);
        spawnWarpBurst(dest.getWorld(), dest);

        target.getWorld().playSound(dest, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.7f);
        String targetName = target instanceof Player p ? p.getName() : target.getType().name();
        player.sendMessage("§3§lWarped Rune — Teleported §e" + targetName + "§3 §e" + distance + "§3 blocks away!");
        if (target instanceof Player tp) tp.sendMessage("§3You were warped by §e" + player.getName() + "§3!");
        plugin.getRuneManager().getCooldowns().setCooldown(player.getUniqueId(), RuneType.WARPED, cd);
    }

    private void spawnWarpBurst(World world, Location loc) {
        world.spawnParticle(Particle.PORTAL, loc.clone().add(0,1,0), 60, 0.4, 1.0, 0.4, 0.5);
        world.spawnParticle(Particle.DUST, loc.clone().add(0,1,0), 40, 0.4, 0.8, 0.4,
                new Particle.DustOptions(Color.fromRGB(0, 180, 160), 3f));
        world.spawnParticle(Particle.SCULK_SOUL, loc.clone().add(0,1,0), 10, 0.3, 0.5, 0.3, 0.05);
    }

    public Map<UUID, UUID> getWarpedTargets() { return warpedTargets; }
}
