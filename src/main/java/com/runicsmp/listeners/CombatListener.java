package com.runicsmp.listeners;

import com.runicsmp.RunicSMP;
import com.runicsmp.runes.PrimaryAbilityExtensions;
import com.runicsmp.data.PlayerRuneData;
import com.runicsmp.data.RuneType;
import com.runicsmp.managers.RuneManager;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

/**
 * Handles all combat-related rune effects:
 * - Wind rune mace
 * - Strength true damage
 * - Lifesteal (drain enemy hearts, gain on kill)
 * - Lockdown (block utility items)
 * - Fire rune fireball custom damage
 * - Defender shield (cancel damage)
 */
public class CombatListener implements Listener {

    private final java.util.Set<java.util.UUID> dashedRecently = new java.util.HashSet<>();

    public void registerDash(java.util.UUID uuid) {
        dashedRecently.add(uuid);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> dashedRecently.remove(uuid), 80L);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDashFallDamage(org.bukkit.event.entity.EntityDamageEvent event) {
        if (event.getCause() != org.bukkit.event.entity.EntityDamageEvent.DamageCause.FALL) return;
        if (!(event.getEntity() instanceof Player p)) return;
        if (!dashedRecently.contains(p.getUniqueId())) return;
        event.setCancelled(true);
    }

    private final RunicSMP plugin;
    private final RuneManager runeManager;

    // Items that lockdown prevents using
    private static final List<Material> LOCKDOWN_BLOCKED = List.of(
            Material.WATER_BUCKET, Material.SPLASH_POTION, Material.LINGERING_POTION,
            Material.ENDER_PEARL, Material.CHORUS_FRUIT, Material.COBWEB,
            Material.EXPERIENCE_BOTTLE
    );

    public CombatListener(RunicSMP plugin) {
        this.plugin = plugin;
        this.runeManager = plugin.getRuneManager();
    }

    // ── Defender Shield ───────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        PlayerRuneData data = runeManager.getData(player);

        if (data.isDefenderShieldActive()) {
            event.setCancelled(true);
            player.getWorld().spawnParticle(Particle.ENCHANT,
                    player.getLocation().add(0, 1, 0), 10, 0.3, 0.5, 0.3, 0.3);
        }
    }

    // ── Combat hit processing ─────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        // ── ATTACKER is a Player ──────────────────────────────────────────────
        Player attacker = getAttacker(event);
        if (attacker != null) {
            // Trust check: if attacker has trusted the victim, cancel rune bonus effects
            if (event.getEntity() instanceof Player victim) {
                if (plugin.getTrustManager().isTrustedUUID(attacker.getUniqueId(), victim.getUniqueId())) {
                    // Still allow normal damage but skip all rune extras
                    return;
                }
            }
            PlayerRuneData attackerData = runeManager.getData(attacker);

            // ── Wind rune mace damage/kb ──────────────────────────────────────
            if (attackerData.hasPrimary() && attackerData.getPrimaryRune() == RuneType.WIND) {
                ItemStack weapon = attacker.getInventory().getItemInMainHand();
                if (weapon.getType() == Material.MACE) {
                    double bonusDamage = plugin.getConfig().getDouble("wind-rune.mace-damage-bonus", 8.0);
                    double kbReduction = plugin.getConfig().getDouble("wind-rune.mace-kb-reduction", 0.4);
                    event.setDamage(event.getDamage() + bonusDamage);
                    // Reduce knockback by scaling velocity post-hit
                    if (event.getEntity() instanceof LivingEntity target) {
                        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                            org.bukkit.util.Vector vel = target.getVelocity();
                            vel.setX(vel.getX() * kbReduction);
                            vel.setZ(vel.getZ() * kbReduction);
                            target.setVelocity(vel);
                        }, 1L);
                    }
                    // Mace particle burst
                    if (event.getEntity() instanceof LivingEntity) {
                        event.getEntity().getWorld().spawnParticle(
                                Particle.CLOUD, event.getEntity().getLocation().add(0, 1, 0),
                                20, 0.5, 0.5, 0.5, 0.1);
                    }
                }
            }

            // Wind mace is handled by WindLandingListener (real Density V mace swap)

            // Strength now gives Strength III buff (handled in ability handler, not here)

            // ── Lifesteal: +2 hearts bonus damage vs PLAYERS only ───────────
            if (attackerData.hasPrimary() && attackerData.getPrimaryRune() == RuneType.LIFESTEAL) {
                if (event.getEntity() instanceof Player victim) {
                    // Skip if attacker has trusted victim
                    if (plugin.getTrustManager().isTrustedUUID(attacker.getUniqueId(), victim.getUniqueId())) return;
                    boolean victimHasLifesteal = runeManager.getData(victim).hasPrimary()
                            && runeManager.getData(victim).getPrimaryRune() == RuneType.LIFESTEAL;
                    if (!victimHasLifesteal) {
                        event.setDamage(event.getDamage() + 4.0); // +2 hearts
                        victim.getWorld().spawnParticle(Particle.HEART,
                                victim.getLocation().add(0, 2, 0), 3, 0.3, 0.3, 0.3, 0);
                    }
                }
            }
        }

        // ── FIREBALL custom damage ────────────────────────────────────────────
        if (event.getDamager() instanceof Fireball fireball) {
            if (fireball.hasMetadata("rune_fireball_damage")) {
                double damage = fireball.getMetadata("rune_fireball_damage").get(0).asDouble();
                event.setDamage(damage);
            }
        }
    }

    // ── Lockdown: block item use ──────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerItemConsume(org.bukkit.event.player.PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        if (player.hasMetadata("rune_lockdown")) {
            event.setCancelled(true);
            player.sendMessage("§5You are locked down and cannot use this item!");
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteractLockdown(org.bukkit.event.player.PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!player.hasMetadata("rune_lockdown")) return;

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null) return;

        if (LOCKDOWN_BLOCKED.contains(item.getType())) {
            event.setCancelled(true);
            player.sendMessage("§5You are locked down and cannot use this item!");
        }
    }

    // Fire rune water deletion removed — was too glitchy

    // ── Lifesteal on kill ─────────────────────────────────────────────────────

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        // PLAYERS ONLY
        if (!(event.getEntity() instanceof Player victim)) return;
        Player killer = victim.getKiller();
        // No killer or self-kill — no lifesteal
        if (killer == null || killer.equals(victim)) return;

        PlayerRuneData data = runeManager.getData(killer);
        if (!data.hasPrimary() || data.getPrimaryRune() != RuneType.LIFESTEAL) return;

        int heartsPerKill = plugin.getConfig().getInt("lifesteal-rune.hearts-per-kill", 2);
        double hpGain = heartsPerKill * 2.0;

        double currentMax = killer.getAttribute(Attribute.MAX_HEALTH).getValue();
        double newMax = currentMax + hpGain;
        killer.getAttribute(Attribute.MAX_HEALTH).setBaseValue(newMax);
        killer.setHealth(Math.min(killer.getHealth() + hpGain, newMax));
        data.addLifeStealBonusHearts(heartsPerKill);

        killer.getWorld().spawnParticle(Particle.HEART,
                killer.getLocation().add(0, 2, 0), 5, 0.3, 0.3, 0.3, 0);
        if (!plugin.getNotifManager().isMuted(killer.getUniqueId()))
            killer.sendMessage("§cLifesteal: +§e" + heartsPerKill + " §chearts! Total: §e"
                    + data.getLifeStealBonusHearts() + " §cbonus hearts.");
    }



    // ── Respawn — reapply passives and particles ─────────────────────────────

    @EventHandler
    public void onPlayerRespawn(org.bukkit.event.player.PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        // Delay 2 ticks so player is fully loaded before applying effects
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            PlayerRuneData data = runeManager.getData(player);

            // Reapply secondary passive — secondary always survives death
            if (data.hasSecondary() && data.hasPassive()) {
                runeManager.applyPassiveEffects(player, data.getSecondaryRune());
            }

            // Reapply primary passive if they somehow still have it equipped
            if (data.hasPrimary() && data.hasPassive()) {
                runeManager.applyPassiveEffects(player, data.getPrimaryRune());
            }

            // Update HUD so particles restart properly
            plugin.getHudManager().updateHud(player);
        }, 2L);
    }

    // ── Lifesteal on death: lose all bonus hearts ─────────────────────────────

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        PlayerRuneData data = runeManager.getData(player);

        if (data.hasPrimary() && data.getPrimaryRune() == RuneType.LIFESTEAL) {
            // Reset bonus hearts on death
            int bonusHearts = data.getLifeStealBonusHearts();
            if (bonusHearts > 0) {
                double baseMax = 20.0 + (data.hasSecondary() && data.getSecondaryRune() == RuneType.VITALITY
                        ? plugin.getConfig().getInt("vitality-rune.bonus-hearts", 15) : 0);
                player.getAttribute(Attribute.MAX_HEALTH).setBaseValue(baseMax);
                data.resetLifeStealBonusHearts();
                player.sendMessage("§cYou lost all §e" + bonusHearts + " §clifesteal bonus hearts on death!");
            }
        }

        // Reset strength active
        if (data.isStrengthActive()) {
            data.setStrengthActive(false);
            data.setStrengthHitCount(0);
        }

        // Drop primary rune on death (if equipped)
        if (data.hasPrimary()) {
            RuneType dropped = data.getPrimaryRune();
            runeManager.removePassiveEffects(player, dropped);
            data.setPrimaryRune(null);
            plugin.getRuneManager().getActivePrimariesSet().remove(dropped);
            // Drop at death location — scheduled 1 tick after death
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                player.getWorld().dropItemNaturally(
                    player.getLocation(),
                    com.runicsmp.utils.RuneItemBuilder.createRune(dropped));
            }, 1L);
        }

        // Drain bank into energy on death
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            plugin.getEnergyManager().drainBankIntoEnergy(player);
        }, 1L);
    }

    // ── Resurrection — cancel first death, allow second ─────────────────────

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onResurrectionDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        if (!plugin.getResurrectionManager().isProtected(victim)) return;
        boolean saved = plugin.getResurrectionManager().consumeProtection(victim);
        if (!saved) return;

        // Cancel death + clear all drops so nothing falls on the ground
        event.setCancelled(true);
        event.getDrops().clear();
        event.setDroppedExp(0);
        event.setKeepInventory(true);
        event.setKeepLevel(true);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!victim.isOnline()) return;
            // Restore to 4 hearts (8 HP), clear bad effects
            double maxHp = victim.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
            victim.setHealth(Math.min(8.0, maxHp));
            victim.setFireTicks(0);
            victim.setFallDistance(0f);
            victim.removePotionEffect(org.bukkit.potion.PotionEffectType.POISON);
            victim.removePotionEffect(org.bukkit.potion.PotionEffectType.WITHER);
            victim.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.ABSORPTION, 200, 1, false, false));
            victim.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.REGENERATION, 60, 1, false, false));
            victim.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.RESISTANCE, 60, 1, false, false));
            victim.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING,
                    victim.getLocation().add(0,1,0), 100, 0.5,1.0,0.5, 0.5);
            victim.getWorld().spawnParticle(Particle.ENCHANT,
                    victim.getLocation().add(0,1,0), 60, 0.5,1.0,0.5, 0.6);
            victim.playSound(victim.getLocation(), Sound.ITEM_TOTEM_USE, 1f, 1f);
            victim.playSound(victim.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);
            if (!plugin.getNotifManager().isMuted(victim.getUniqueId()))
                victim.sendMessage("§6§l☩ Resurrection saved your life! One more chance.");
            org.bukkit.Bukkit.broadcast(net.kyori.adventure.text.Component.text(
                    "§6§l☩ " + victim.getName() + " was saved by Resurrection!"));
        }, 1L);
    }

    // ── Fireball explosion — BlissGems FireAbilities (b$4) accurate ─────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFireballHit(org.bukkit.event.entity.ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Fireball fireball)) return;
        if (!fireball.hasMetadata("rune_fireball_tnt")) return;

        Location loc = fireball.getLocation();
        World world = loc.getWorld();
        Player shooter = fireball.getShooter() instanceof Player p ? p : null;

        // BlissGems b$4: createExplosion for visual + manual damage on nearby entities
        // No block damage, no fire (we rely on isIncendiary for fire spread on hit)
        world.createExplosion(loc.getX(), loc.getY(), loc.getZ(), 3.0f, false, false);

        // BlissGems: manual damage loop with trust check and setFireTicks
        double blastRadius = 5.0;
        double configDamage = 8.0; // 4 hearts at point blank, scales with falloff

        for (org.bukkit.entity.Entity nearby : world.getNearbyEntities(loc, blastRadius, blastRadius, blastRadius)) {
            if (!(nearby instanceof LivingEntity le)) continue;
            if (nearby.equals(shooter)) continue;

            // BlissGems: getTrustedPlayersManager check — we use trust system
            if (nearby instanceof Player victim && shooter != null) {
                if (plugin.getTrustManager().isTrustedUUID(shooter.getUniqueId(), victim.getUniqueId())) continue;
            }

            double dist = nearby.getLocation().distance(loc);
            if (dist > blastRadius) continue;

            // BlissGems damage formula: configDamage * falloff
            double falloff = 1.0 - (dist / blastRadius);
            le.damage(configDamage * falloff, fireball);
            // BlissGems: setFireTicks on hit
            le.setFireTicks(100);
        }

        // BlissGems b$4 explosion particles: FLAME + LAVA + SOUL_FIRE_FLAME + FIRE_ORANGE DUST
        world.spawnParticle(Particle.FLAME, loc, 80, 1.5, 1.5, 1.5, 0.18);
        world.spawnParticle(Particle.LAVA, loc, 30, 1.0, 1.0, 1.0, 0.08);
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, loc, 25, 1.2, 1.2, 1.2, 0.08);
        world.spawnParticle(Particle.DUST, loc, 60, 1.5, 1.5, 1.5,
                new Particle.DustOptions(Color.fromRGB(255, 80, 0), 3f)); // FIRE_ORANGE
        world.spawnParticle(Particle.DUST, loc, 30, 1.0, 1.0, 1.0,
                new Particle.DustOptions(Color.fromRGB(255, 30, 0), 2.5f));
        world.spawnParticle(Particle.CLOUD, loc, 20, 0.8, 0.8, 0.8, 0.06);

        // BlissGems b$4 sounds: ENTITY_GENERIC_EXPLODE + BLOCK_LAVA_POP
        world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1f);
        world.playSound(loc, Sound.BLOCK_LAVA_POP, 1f, 0.8f);
        world.playSound(loc, Sound.ENTITY_BLAZE_SHOOT, 0.8f, 0.7f);
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private Player getAttacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player p) return p;
        if (event.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p) return p;
        return null;
    }
}
