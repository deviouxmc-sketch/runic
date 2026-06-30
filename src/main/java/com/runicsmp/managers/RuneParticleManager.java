package com.runicsmp.managers;

import com.runicsmp.RunicSMP;
import com.runicsmp.data.PlayerRuneData;
import com.runicsmp.data.RuneType;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

/**
 * Full particle effects using the best Java Edition particles:
 * SONIC_BOOM, SCULK_SOUL, SCULK_CHARGE, SCULK_CHARGE_POP, SHRIEK,
 * GUST, SMALL_GUST, ELECTRIC_SPARK, TOTEM_OF_UNDYING, VIBRATION,
 * TRIAL_SPAWNER_DETECTION, VAULT_CONNECTION, DRAGON_BREATH,
 * ENCHANTED_HIT, CRIT, SWEEP_ATTACK, EXPLOSION, PORTAL, CLOUD, etc.
 */
public class RuneParticleManager {

    private final RunicSMP plugin;
    private BukkitTask passiveTask;

    public RuneParticleManager(RunicSMP plugin) { this.plugin = plugin; }

    public void start() {
        passiveTask = new BukkitRunnable() {
            int tick = 0;
            @Override public void run() {
                tick++;
                int onlineCount = plugin.getServer().getOnlinePlayers().size();
                // Skip every other cycle if server is busy (15+ players) to cut particle packet load in half
                if (onlineCount >= 15 && tick % 2 != 0) return;

                for (Player p : plugin.getServer().getOnlinePlayers()) {
                    // Respect particle level setting
                    var sc = plugin.getSettingsCmd();
                    if (sc != null && sc.getParticleLevel(p) == 0) continue;
                    PlayerRuneData data = plugin.getRuneManager().getData(p);
                    if (data.hasPrimary()) spawnPassive(p, data.getPrimaryRune(), tick);
                    if (data.hasSecondary()) spawnPassive(p, data.getSecondaryRune(), tick);
                }
            }
        }.runTaskTimer(plugin, 0L, 10L); // throttled to reduce packet spam
    }

    public void stop() { if (passiveTask != null) passiveTask.cancel(); }

    // ── PASSIVE — orbiting, visible, not screen-filling ───────────────────────

    private void spawnPassive(Player p, RuneType rune, int tick) {
        Location loc = p.getLocation().add(0, 0.1, 0);
        World w = p.getWorld();

        switch (rune) {
            case WIND -> {
                // White orbiting ring + cloud wisps
                for (int i = 0; i < 10; i++) {
                    double a = Math.PI*2/10*i + tick*0.18;
                    w.spawnParticle(Particle.DUST, loc.clone().add(Math.cos(a)*0.8,0.3+Math.sin(tick*0.1+i)*0.1,Math.sin(a)*0.8),
                            1,0,0,0, new Particle.DustOptions(Color.WHITE, 2f));
                }
                if (tick%2==0) w.spawnParticle(Particle.CLOUD, loc.clone().add(0,0.5,0), 2, 0.3,0.1,0.3, 0.01);
                if (tick%4==0) w.spawnParticle(Particle.SMALL_GUST, loc.clone().add(0,0.5,0), 1, 0.2,0.2,0.2, 0.01);
            }
            case STRENGTH -> {
                // Red orbiting ring + crit sparks + electric spark accent
                for (int i = 0; i < 10; i++) {
                    double a = Math.PI*2/10*i + tick*0.2;
                    w.spawnParticle(Particle.DUST, loc.clone().add(Math.cos(a)*0.7,1.0,Math.sin(a)*0.7),
                            1,0,0,0, new Particle.DustOptions(Color.RED, 2.2f));
                }
                if (tick%2==0) w.spawnParticle(Particle.CRIT, loc.clone().add(0,1,0), 2, 0.3,0.3,0.3, 0.04);
                if (tick%4==0) w.spawnParticle(Particle.ELECTRIC_SPARK, loc.clone().add(0,0.8,0), 1, 0.3,0.3,0.3, 0.02);
            }
            case SHADOW -> {
                // Dark sculk wisps + smoke
                w.spawnParticle(Particle.SMOKE, loc, 1, 0.25,0.05,0.25, 0.004);
                if (tick%2==0) w.spawnParticle(Particle.SCULK_CHARGE_POP, loc.clone().add(0,0.5,0), 1, 0.2,0.2,0.2, 0);
                if (tick%3==0) w.spawnParticle(Particle.SCULK_SOUL, loc.clone().add(0,0.3,0), 1, 0.2,0.1,0.2, 0.01);
                w.spawnParticle(Particle.DUST, loc.clone().add(0,0.5,0), 2, 0.2,0.3,0.2,
                        new Particle.DustOptions(Color.fromRGB(5,0,5), 1.5f));
            }
            case LIFESTEAL -> {
                if (tick%2==0) {
                    w.spawnParticle(Particle.DUST, loc.clone().add(0,0.5,0), 3, 0.2,0.3,0.2,
                            new Particle.DustOptions(Color.fromRGB(139,0,0), 2f));
                    w.spawnParticle(Particle.DRIPPING_LAVA, loc.clone().add(0,1.5,0), 1, 0.2,0.1,0.2, 0);
                }
                if (tick%5==0) w.spawnParticle(Particle.HEART, loc.clone().add(0,2,0), 1, 0.2,0.1,0.2, 0);
            }
            case LOCKDOWN -> {
                // Sculk + purple orbiting
                for (int i = 0; i < 6; i++) {
                    double a = Math.PI*2/6*i + tick*0.18;
                    w.spawnParticle(Particle.DUST, loc.clone().add(Math.cos(a)*0.8,0.5,Math.sin(a)*0.8),
                            1,0,0,0, new Particle.DustOptions(Color.fromRGB(80,0,120), 2f));
                }
                if (tick%3==0) w.spawnParticle(Particle.SCULK_CHARGE_POP, loc.clone().add(0,0.5,0), 1, 0.2,0.2,0.2, 0);
                if (tick%4==0) w.spawnParticle(Particle.WITCH, loc.clone().add(0,1,0), 1, 0.1,0.2,0.1, 0);
            }
            case TIDAL -> {
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI*2/8*i - tick*0.15;
                    w.spawnParticle(Particle.DUST, loc.clone().add(Math.cos(a)*0.7,0.3,Math.sin(a)*0.7),
                            1,0,0,0, new Particle.DustOptions(Color.fromRGB(30,80,255), 2f));
                }
                if (tick%2==0) w.spawnParticle(Particle.DRIPPING_WATER, loc.clone().add(0,1.5,0), 2, 0.3,0.1,0.3, 0);
            }
            case DEFENDER -> {
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI*2/8*i + tick*0.12;
                    w.spawnParticle(Particle.DUST, loc.clone().add(Math.cos(a)*0.9,1.0+Math.sin(a+tick*0.1)*0.3,Math.sin(a)*0.9),
                            1,0,0,0, new Particle.DustOptions(Color.YELLOW, 2f));
                }
                if (tick%3==0) w.spawnParticle(Particle.ENCHANT, loc.clone().add(0,1,0), 2, 0.3,0.5,0.3, 0.02);
                if (tick%5==0) w.spawnParticle(Particle.VAULT_CONNECTION, loc.clone().add(0,1,0), 1, 0.2,0.3,0.2, 0.01);
            }
            case ENCHANTMENT -> {
                if (tick%2==0) w.spawnParticle(Particle.ENCHANT, loc.clone().add(0,1,0), 4, 0.3,0.5,0.3, 0.04);
                if (tick%3==0) w.spawnParticle(Particle.DUST, loc.clone().add(0,1,0), 2, 0.3,0.4,0.3,
                        new Particle.DustOptions(Color.fromRGB(160,0,255), 1.8f));
                if (tick%4==0) w.spawnParticle(Particle.TRIAL_SPAWNER_DETECTION, loc.clone().add(0,1,0), 1, 0.2,0.3,0.2, 0.01);
            }
            case DRAGON -> {
                if (tick%2==0) w.spawnParticle(Particle.DUST, loc, 1, 0.2,0.05,0.2, new Particle.DustOptions(Color.fromRGB(120,0,200), 1.5f));
                w.spawnParticle(Particle.DUST, loc.clone().add(0,0.5,0), 2, 0.2,0.2,0.2,
                        new Particle.DustOptions(Color.fromRGB(120,0,255), 1.8f));
                if (tick%4==0) w.spawnParticle(Particle.DUST, loc.clone().add(0,1,0), 1, 0.1,0.2,0.1,
                        new Particle.DustOptions(Color.fromRGB(0,80,255), 1.5f));
            }
            case CLONE -> {
                Color c = (tick%4<2) ? Color.YELLOW : Color.fromRGB(0,120,255);
                if (tick%2==0) w.spawnParticle(Particle.DUST, loc.clone().add(0,0.5,0), 3, 0.2,0.3,0.2,
                        new Particle.DustOptions(c, 1.8f));
                if (tick%4==0) w.spawnParticle(Particle.ENCHANTED_HIT, loc.clone().add(0,1,0), 1, 0.2,0.3,0.2, 0.02);
            }
            case TRACKER -> {
                if (tick%3==0) {
                    Location fwd = loc.clone().add(p.getLocation().getDirection().multiply(0.8)).add(0,1,0);
                    w.spawnParticle(Particle.DUST, fwd, 1, 0.1,0.1,0.1, new Particle.DustOptions(Color.GREEN, 1.5f));
                }
                if (tick%4==0) w.spawnParticle(Particle.SCULK_CHARGE_POP, loc.clone().add(0,0.5,0), 1, 0.2,0.2,0.2, 0);
                if (tick%6==0) w.spawnParticle(Particle.TRIAL_SPAWNER_DETECTION, loc.clone().add(0,1,0), 1, 0.2,0.3,0.2, 0.01);
            }
            case THIEF -> {
                if (tick%3==0) w.spawnParticle(Particle.CRIT, loc.clone().add(0,0.5,0), 2, 0.2,0.2,0.2, 0.01);
                if (tick%5==0) w.spawnParticle(Particle.DUST, loc.clone().add(0,1,0), 1, 0.1,0.2,0.1,
                        new Particle.DustOptions(Color.SILVER, 1.5f));
            }
            case TRADER -> {
                if (tick%4==0) w.spawnParticle(Particle.DUST, loc.clone().add(0,1,0), 2, 0.2,0.3,0.2,
                        new Particle.DustOptions(Color.fromRGB(0,200,60), 1.8f));
                if (tick%6==0) w.spawnParticle(Particle.VAULT_CONNECTION, loc.clone().add(0,1,0), 1, 0.2,0.3,0.2, 0.01);
            }
            case FIRE -> {
                w.spawnParticle(Particle.SMALL_FLAME, loc, 1, 0.2,0.05,0.2, 0.005);
                if (tick%2==0) w.spawnParticle(Particle.DUST, loc.clone().add(0,0.5,0), 2, 0.2,0.2,0.2,
                        new Particle.DustOptions(Color.ORANGE, 1.8f));
                if (tick%4==0) w.spawnParticle(Particle.LAVA, loc.clone().add(0,0.3,0), 1, 0.2,0.1,0.2, 0);
            }
            case VITALITY -> {
                if (tick%3==0) {
                    w.spawnParticle(Particle.DUST, loc.clone().add(0,0.5,0), 3, 0.2,0.3,0.2,
                            new Particle.DustOptions(Color.fromRGB(255,105,180), 1.8f));
                    w.spawnParticle(Particle.HEART, loc.clone().add(0,1.8,0), 1, 0.3,0.1,0.3, 0);
                }
            }
            case DASH -> {
                // Subtle wind wisps orbiting
                w.spawnParticle(Particle.CLOUD, loc.clone().add(0, 1, 0), 1, 0.3, 0.3, 0.3, 0.02);
            }
            case FROST -> {
                // Light blue mist + snowflakes
                w.spawnParticle(Particle.SNOWFLAKE, loc.clone().add(0, 1, 0), 1, 0.3, 0.5, 0.3, 0.01);
                w.spawnParticle(Particle.DUST, loc.clone().add(0, 0.5, 0), 1, 0.3, 0.3, 0.3,
                        new Particle.DustOptions(org.bukkit.Color.fromRGB(150, 220, 255), 1.2f));
            }
            case HASTE -> {
                w.spawnParticle(Particle.DUST, loc, 1, 0.3,0.05,0.3, new Particle.DustOptions(Color.YELLOW, 1.8f));
                if (tick%2==0) w.spawnParticle(Particle.CRIT, loc.clone().add(0,0.5,0), 1, 0.2,0.2,0.2, 0.02);
                if (tick%4==0) w.spawnParticle(Particle.ELECTRIC_SPARK, loc.clone().add(0,0.5,0), 1, 0.2,0.2,0.2, 0.01);
            }
            case GRAVITY -> {
                // Black/purple swirling particles
                w.spawnParticle(Particle.DUST, loc.clone().add(0, 1, 0),
                        1, 0.4, 0.4, 0.4, new Particle.DustOptions(org.bukkit.Color.fromRGB(80, 0, 120), 1.5f));
                w.spawnParticle(Particle.PORTAL, loc.clone().add(0, 1, 0), 1, 0.3, 0.3, 0.3, 0.02);
            }
            case WARPED -> {
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI*2/8*i + tick*0.2;
                    w.spawnParticle(Particle.DUST, loc.clone().add(Math.cos(a)*0.7,0.5,Math.sin(a)*0.7),
                            1,0,0,0, new Particle.DustOptions(Color.fromRGB(0,180,160), 2f));
                }
                if (tick%3==0) w.spawnParticle(Particle.PORTAL, loc, 1, 0.2,0.1,0.2, 0.01);
                if (tick%5==0) w.spawnParticle(Particle.SCULK_SOUL, loc.clone().add(0,0.5,0), 1, 0.2,0.2,0.2, 0.01);
            }
            case SWAP -> {
                if (tick%3==0) {
                    w.spawnParticle(Particle.DUST, loc.clone().add(0,1,0), 2, 0.2,0.3,0.2,
                            new Particle.DustOptions(Color.WHITE, 1.8f));
                    w.spawnParticle(Particle.ENCHANT, loc.clone().add(0,0.5,0), 1, 0.2,0.2,0.2, 0.02);
                }
                if (tick%5==0) w.spawnParticle(Particle.PORTAL, loc.clone().add(0,0.5,0), 1, 0.2,0.1,0.2, 0.01);
            }
            case RESURRECTION -> {
                if (tick%3==0) {
                    w.spawnParticle(Particle.DUST, loc.clone().add(0,0.5,0), 2, 0.2,0.3,0.2,
                            new Particle.DustOptions(Color.fromRGB(255,255,200), 1.8f));
                    w.spawnParticle(Particle.ENCHANT, loc.clone().add(0,1,0), 1, 0.2,0.5,0.2, 0.01);
                }
                if (tick%6==0) w.spawnParticle(Particle.TRIAL_SPAWNER_DETECTION, loc.clone().add(0,1,0), 1, 0.2,0.3,0.2, 0.01);
            }
            case HOARDER -> {
                if (tick%4==0) w.spawnParticle(Particle.DUST, loc.clone().add(0,0.3,0), 2, 0.3,0.1,0.3,
                        new Particle.DustOptions(Color.ORANGE, 1.8f));
                if (tick%6==0) w.spawnParticle(Particle.VAULT_CONNECTION, loc.clone().add(0,0.5,0), 1, 0.2,0.2,0.2, 0.01);
            }
            default -> {}
        }
    }

    // ── ACTIVE — full cinematic effects ──────────────────────────────────────

    public void playActiveEffect(Player player, RuneType rune) {
        Location loc = player.getLocation();
        World w = player.getWorld();

        switch (rune) {
            // WIND particles handled entirely in activateWindDash() (Feather card exact) — no override here

            case STRENGTH -> {
                // Star burst + helix + sonic boom
                spawnStar(w, loc, Particle.CRIT, 8);
                spawnHelix(w, loc, Color.RED, Color.fromRGB(180,0,0), 4);
                spawnCross(w, loc, Color.RED, 4.0);
                w.spawnParticle(Particle.DUST, loc.clone().add(0,1,0), 200, 0.8,1.8,0.8,
                        new Particle.DustOptions(Color.RED, 4.5f));
                w.spawnParticle(Particle.ELECTRIC_SPARK, loc.clone().add(0,1,0), 50, 0.6,0.8,0.6, 0.12);
                w.spawnParticle(Particle.SONIC_BOOM, loc.clone().add(0,1,0), 1, 0,0,0, 0);
                w.spawnParticle(Particle.EXPLOSION, loc.clone().add(0,1,0), 5, 0.4,0.4,0.4, 0);
                w.spawnParticle(Particle.CRIT, loc.clone().add(0,1,0), 120, 0.7,1.2,0.7, 0.8);
            }
            case SHADOW -> {
                // Tornado of darkness + sculk cross
                spawnTornado(w, loc, Color.fromRGB(5,0,5), 5);
                spawnCross(w, loc, Color.fromRGB(20,0,40), 4.5);
                w.spawnParticle(Particle.SMOKE, loc.clone().add(0,1,0), 250, 1.2,2.0,1.2, 0.2);
                w.spawnParticle(Particle.ASH, loc.clone().add(0,1,0), 200, 1.5,2.0,1.5, 0.22);
                w.spawnParticle(Particle.SCULK_SOUL, loc.clone().add(0,1,0), 60, 0.7,1.2,0.7, 0.1);
                w.spawnParticle(Particle.SCULK_CHARGE_POP, loc.clone().add(0,1,0), 80, 0.6,1.0,0.6, 0.18);
                w.spawnParticle(Particle.PORTAL, loc.clone().add(0,1,0), 80, 0.5,1.0,0.5, 0.5);
            }
            case LIFESTEAL -> {
                w.spawnParticle(Particle.HEART, loc.clone().add(0,2,0), 35, 1.0,1.0,1.0, 0.35);
                w.spawnParticle(Particle.DUST, loc.clone().add(0,1,0), 200, 0.7,1.5,0.7,
                        new Particle.DustOptions(Color.fromRGB(139,0,0), 4f));
                w.spawnParticle(Particle.DRIPPING_LAVA, loc.clone().add(0,2,0), 35, 0.6,0.4,0.6, 0.12);
                w.spawnParticle(Particle.DUST, loc.clone().add(0,1,0), 80, 0.5,1.0,0.5,
                        new Particle.DustOptions(Color.fromRGB(0,0,180), 3f));
                w.spawnParticle(Particle.SONIC_BOOM, loc.clone().add(0,1,0), 1, 0,0,0, 0);
                ringBurst(w, loc, Color.fromRGB(180,0,0), 3, 3.0);
            }
            // LOCKDOWN particles handled entirely in ext.activateLockdown() — no override here

            // TIDAL particles handled entirely in activateTidal() (secondary) — no override here

            case VITALITY -> {
                w.spawnParticle(Particle.HEART, loc.clone().add(0,2,0), 60, 1.2,1.2,1.2, 0.45);
                w.spawnParticle(Particle.DUST, loc.clone().add(0,1,0), 200, 0.8,1.5,0.8,
                        new Particle.DustOptions(Color.fromRGB(255,105,180), 4f));
                w.spawnParticle(Particle.TOTEM_OF_UNDYING, loc.clone().add(0,1,0), 80, 0.5,1.0,0.5, 0.4);
                w.spawnParticle(Particle.DUST, loc.clone().add(0,1,0), 80, 0.5,1.0,0.5,
                        new Particle.DustOptions(Color.fromRGB(0,100,255), 3f));
                ringBurst(w, loc, Color.fromRGB(255,182,193), 3, 3.0);
                new BukkitRunnable() { double a=0; double h=0;
                    @Override public void run() { if (h>5){cancel();return;}
                        for (int i=0;i<4;i++) { double ang=a+i*Math.PI/2;
                            w.spawnParticle(Particle.HEART,loc.clone().add(Math.cos(ang),h,Math.sin(ang)),1,0,0,0,0); }
                        a+=0.4; h+=0.1; }
                }.runTaskTimer(plugin, 0L, 1L);
            }
            case DASH -> {
                // Subtle wind wisps orbiting
                w.spawnParticle(Particle.CLOUD, loc.clone().add(0, 1, 0), 1, 0.3, 0.3, 0.3, 0.02);
            }
            case FROST -> {
                // Light blue mist + snowflakes
                w.spawnParticle(Particle.SNOWFLAKE, loc.clone().add(0, 1, 0), 1, 0.3, 0.5, 0.3, 0.01);
                w.spawnParticle(Particle.DUST, loc.clone().add(0, 0.5, 0), 1, 0.3, 0.3, 0.3,
                        new Particle.DustOptions(org.bukkit.Color.fromRGB(150, 220, 255), 1.2f));
            }
            // HASTE particles handled entirely in activateHaste() — no override here

            case DEFENDER -> {
                // VAULT_CONNECTION + gold sphere
                for (int lat=0;lat<=12;lat++) { double phi=Math.PI*lat/12;
                    double r=Math.sin(phi)*2.5+0.2; double y=Math.cos(phi)*2.5+1.2;
                    for (int i=0;i<28;i++) { double a=Math.PI*2/28*i;
                        w.spawnParticle(Particle.DUST,loc.clone().add(Math.cos(a)*r,y,Math.sin(a)*r),1,0,0,0,
                                new Particle.DustOptions(Color.YELLOW,2.8f));
                    }
                }
                w.spawnParticle(Particle.ENCHANT, loc.clone().add(0,1,0), 250, 1.2,1.5,1.2, 0.7);
                w.spawnParticle(Particle.ENCHANTED_HIT, loc.clone().add(0,1,0), 120, 0.8,1.0,0.8, 0.5);
                w.spawnParticle(Particle.VAULT_CONNECTION, loc.clone().add(0,1,0), 20, 0.5,0.8,0.5, 0.05);
            }
            case ENCHANTMENT -> {
                // TRIAL_SPAWNER_DETECTION + enchant tornado
                w.spawnParticle(Particle.ENCHANT, loc.clone().add(0,1,0), 300, 1.2,2.0,1.2, 1.0);
                w.spawnParticle(Particle.ENCHANTED_HIT, loc.clone().add(0,1,0), 180, 0.8,1.5,0.8, 0.8);
                w.spawnParticle(Particle.DUST, loc.clone().add(0,1,0), 120, 0.7,1.2,0.7,
                        new Particle.DustOptions(Color.fromRGB(160,0,255), 4f));
                w.spawnParticle(Particle.TRIAL_SPAWNER_DETECTION, loc.clone().add(0,1,0), 20, 0.5,0.8,0.5, 0.05);
                w.spawnParticle(Particle.DUST, loc.clone().add(0,1,0), 80, 0.5,1.0,0.5,
                        new Particle.DustOptions(Color.fromRGB(0,0,255), 3.5f));
                new BukkitRunnable() { double a=0; double h=0;
                    @Override public void run() { if (h>6){cancel();return;}
                        for (int i=0;i<5;i++) { double ang=a+i*Math.PI*2/5;
                            w.spawnParticle(Particle.ENCHANT,loc.clone().add(Math.cos(ang)*0.9,h,Math.sin(ang)*0.9),4,0,0,0,0.07);
                            w.spawnParticle(Particle.DUST,loc.clone().add(Math.cos(ang)*0.9,h,Math.sin(ang)*0.9),2,0,0,0,new Particle.DustOptions(Color.fromRGB(180,80,255),2.5f));
                        }
                        a+=0.28; h+=0.055; }
                }.runTaskTimer(plugin, 0L, 1L);
            }
            case DRAGON -> {
                // Helix + star + beam
                spawnHelix(w, loc, Color.fromRGB(120,0,255), Color.fromRGB(0,80,255), 5);
                spawnStar(w, loc, Particle.PORTAL, 8);
                spawnCross(w, loc, Color.fromRGB(120,0,255), 4.0);
                w.spawnParticle(Particle.DUST, loc.clone().add(0,1,0), 60, 0.7,1.2,0.7, new Particle.DustOptions(Color.fromRGB(120,0,200), 1.5f));
                w.spawnParticle(Particle.SONIC_BOOM, loc.clone().add(0,1,0), 1, 0,0,0, 0);
                w.spawnParticle(Particle.PORTAL, loc.clone().add(0,1,0), 100, 0.6,1.2,0.6, 0.65);
                new BukkitRunnable() { double d=0;
                    @Override public void run() { if (d>12){cancel();return;}
                        Location pt=loc.clone().add(player.getLocation().getDirection().multiply(d)).add(0,1.5,0);
                        w.spawnParticle(Particle.DUST,pt,12,0.3,0.3,0.3, new Particle.DustOptions(Color.fromRGB(120,0,200), 1.5f));
                        w.spawnParticle(Particle.DUST,pt,6,0.2,0.2,0.2,new Particle.DustOptions(Color.fromRGB(150,0,255),2.8f));
                        d+=0.38; }
                }.runTaskTimer(plugin, 0L, 1L);
            }
            case CLONE -> {
                w.spawnParticle(Particle.DUST, loc.clone().add(0,1,0), 150, 0.6,1.2,0.6,
                        new Particle.DustOptions(Color.YELLOW, 4f));
                w.spawnParticle(Particle.DUST, loc.clone().add(0,1,0), 150, 0.6,1.2,0.6,
                        new Particle.DustOptions(Color.fromRGB(0,120,255), 4f));
                w.spawnParticle(Particle.ENCHANTED_HIT, loc.clone().add(0,1,0), 100, 0.6,1.0,0.6, 0.5);
                w.spawnParticle(Particle.TRIAL_SPAWNER_DETECTION, loc.clone().add(0,1,0), 15, 0.4,0.8,0.4, 0.04);
                for (int i=1;i<=8;i++) { final int fi=i;
                    Color c=(fi%2==0)?Color.YELLOW:Color.fromRGB(0,120,255);
                    new BukkitRunnable() { @Override public void run() { ringBurst(w,loc,c,1,fi*0.45+0.3); }}.runTaskLater(plugin,fi*3L);
                }
            }
            case TRACKER -> {
                for (int i=1;i<=15;i++) { final int fi=i;
                    new BukkitRunnable() { @Override public void run() { ringBurst(w,loc,Color.GREEN,1,fi*1.8); }}.runTaskLater(plugin,fi*3L);
                }
                w.spawnParticle(Particle.DUST, loc.clone().add(0,1,0), 100, 0.5,1.2,0.5,
                        new Particle.DustOptions(Color.fromRGB(0,255,80), 3.5f));
                w.spawnParticle(Particle.SCULK_CHARGE_POP, loc.clone().add(0,1,0), 40, 0.4,0.8,0.4, 0.15);
                w.spawnParticle(Particle.TRIAL_SPAWNER_DETECTION, loc.clone().add(0,1,0), 15, 0.4,0.8,0.4, 0.04);
            }
            case THIEF -> {
                w.spawnParticle(Particle.CRIT, loc.clone().add(0,1,0), 150, 0.7,1.2,0.7, 0.9);
                w.spawnParticle(Particle.SMOKE, loc.clone().add(0,1,0), 100, 0.6,1.0,0.6, 0.12);
                w.spawnParticle(Particle.DUST, loc.clone().add(0,1,0), 100, 0.5,1.0,0.5,
                        new Particle.DustOptions(Color.SILVER, 3.5f));
                w.spawnParticle(Particle.ENCHANTED_HIT, loc.clone().add(0,1,0), 60, 0.4,0.8,0.4, 0.4);
                ringBurst(w, loc, Color.SILVER, 2, 3.0);
            }
            case TRADER -> {
                w.spawnParticle(Particle.DUST, loc.clone().add(0,1,0), 180, 0.7,1.5,0.7,
                        new Particle.DustOptions(Color.fromRGB(0,200,60), 4f));
                w.spawnParticle(Particle.ENCHANTED_HIT, loc.clone().add(0,1,0), 100, 0.6,1.0,0.6, 0.5);
                w.spawnParticle(Particle.DUST, loc.clone().add(0,1,0), 80, 0.5,1.0,0.5,
                        new Particle.DustOptions(Color.YELLOW, 3.5f));
                w.spawnParticle(Particle.VAULT_CONNECTION, loc.clone().add(0,1,0), 20, 0.5,0.8,0.5, 0.05);
                ringBurst(w, loc, Color.YELLOW, 3, 3.0);
            }
            case FIRE -> {
                w.spawnParticle(Particle.FLAME, loc.clone().add(0,1,0), 250, 0.8,1.2,0.8, 0.4);
                w.spawnParticle(Particle.LAVA, loc.clone().add(0,0.5,0), 100, 0.7,0.5,0.7, 0.25);
                w.spawnParticle(Particle.DUST, loc.clone().add(0,1,0), 150, 0.7,1.2,0.7,
                        new Particle.DustOptions(Color.ORANGE, 4f));
                w.spawnParticle(Particle.DUST, loc.clone().add(0,1,0), 80, 0.5,1.0,0.5,
                        new Particle.DustOptions(Color.RED, 3.5f));
                w.spawnParticle(Particle.LARGE_SMOKE, loc.clone().add(0,1,0), 50, 0.6,1.0,0.6, 0.07);
                w.spawnParticle(Particle.SONIC_BOOM, loc.clone().add(0,1,0), 1, 0,0,0, 0);
                for (int i=1;i<=10;i++) { final int fi=i;
                    new BukkitRunnable() { @Override public void run() { ringBurst(w,loc,Color.ORANGE,1,fi*0.65); }}.runTaskLater(plugin,fi*2L);
                }
            }
            case GRAVITY -> {
                // Black/purple swirling particles
                w.spawnParticle(Particle.DUST, loc.clone().add(0, 1, 0),
                        1, 0.4, 0.4, 0.4, new Particle.DustOptions(org.bukkit.Color.fromRGB(80, 0, 120), 1.5f));
                w.spawnParticle(Particle.PORTAL, loc.clone().add(0, 1, 0), 1, 0.3, 0.3, 0.3, 0.02);
            }
            case WARPED -> {
                w.spawnParticle(Particle.PORTAL, loc.clone().add(0,1,0), 200, 0.6,1.2,0.6, 0.8);
                w.spawnParticle(Particle.DUST, loc.clone().add(0,1,0), 150, 0.6,1.2,0.6,
                        new Particle.DustOptions(Color.fromRGB(0,180,160), 4f));
                w.spawnParticle(Particle.SCULK_SOUL, loc.clone().add(0,1,0), 40, 0.5,1.0,0.5, 0.1);
                w.spawnParticle(Particle.DUST, loc.clone().add(0,1,0), 80, 0.5,1.0,0.5,
                        new Particle.DustOptions(Color.fromRGB(0,80,255), 3.5f));
                ringBurst(w, loc, Color.fromRGB(0,180,160), 4, 3.5);
            }
            case SWAP -> {
                w.spawnParticle(Particle.DUST, loc.clone().add(0,1,0), 180, 0.6,1.2,0.6,
                        new Particle.DustOptions(Color.WHITE, 4f));
                w.spawnParticle(Particle.ENCHANT, loc.clone().add(0,1,0), 150, 0.6,1.0,0.6, 0.6);
                w.spawnParticle(Particle.PORTAL, loc.clone().add(0,1,0), 80, 0.5,1.0,0.5, 0.5);
                w.spawnParticle(Particle.DUST, loc.clone().add(0,1,0), 80, 0.5,1.0,0.5,
                        new Particle.DustOptions(Color.fromRGB(0,150,255), 3.5f));
                w.spawnParticle(Particle.SONIC_BOOM, loc.clone().add(0,1,0), 1, 0,0,0, 0);
                ringBurst(w, loc, Color.WHITE, 4, 3.5);
            }
            case RESURRECTION -> {
                w.spawnParticle(Particle.ENCHANT, loc.clone().add(0,1,0), 200, 0.7,1.2,0.7, 0.7);
                w.spawnParticle(Particle.DUST, loc.clone().add(0,1,0), 150, 0.6,1.2,0.6,
                        new Particle.DustOptions(Color.fromRGB(255,255,200), 4f));
                w.spawnParticle(Particle.TOTEM_OF_UNDYING, loc.clone().add(0,1,0), 100, 0.6,1.0,0.6, 0.5);
                w.spawnParticle(Particle.ENCHANTED_HIT, loc.clone().add(0,1,0), 80, 0.5,0.8,0.5, 0.4);
                w.spawnParticle(Particle.TRIAL_SPAWNER_DETECTION, loc.clone().add(0,1,0), 20, 0.5,0.8,0.5, 0.05);
                ringBurst(w, loc, Color.WHITE, 4, 3.5);
            }
            default -> w.spawnParticle(Particle.ENCHANTED_HIT, loc.clone().add(0,1,0), 80, 0.6,1.0,0.6, 0.4);
        }
    }

    // ── Helpers — unique shapes ──────────────────────────────────────────────

    private void ringBurst(World w, Location center, Color color, int rings, double maxR) {
        for (int ring = 1; ring <= rings; ring++) {
            double r = maxR / rings * ring;
            for (int i = 0; i < 32; i++) {
                double a = Math.PI*2/32*i;
                w.spawnParticle(Particle.DUST, center.clone().add(Math.cos(a)*r,0.1,Math.sin(a)*r),
                        1,0,0,0, new Particle.DustOptions(color, 2.2f));
            }
        }
    }

    /** Double helix spiraling upward */
    private void spawnHelix(World w, Location base, Color c1, Color c2, int height) {
        for (int t = 0; t < height * 10; t++) {
            double h = t * 0.1;
            double a1 = t * 0.4;
            double a2 = a1 + Math.PI;
            w.spawnParticle(Particle.DUST, base.clone().add(Math.cos(a1)*0.7, h, Math.sin(a1)*0.7),
                    1,0,0,0, new Particle.DustOptions(c1, 2f));
            w.spawnParticle(Particle.DUST, base.clone().add(Math.cos(a2)*0.7, h, Math.sin(a2)*0.7),
                    1,0,0,0, new Particle.DustOptions(c2, 2f));
        }
    }

    /** Star burst — particles shooting out diagonally */
    private void spawnStar(World w, Location center, Particle p, int points) {
        for (int i = 0; i < points; i++) {
            double a = Math.PI*2/points*i;
            double[] ys = {0.3, 1.0, 1.7};
            for (double y : ys) {
                w.spawnParticle(p, center.clone().add(Math.cos(a)*1.5, y, Math.sin(a)*1.5),
                        2, 0.1,0.1,0.1, 0.05);
            }
        }
    }

    /** Cross explosion — 4 lines outward */
    private void spawnCross(World w, Location center, Color color, double len) {
        double[] dirs = {0, Math.PI/2, Math.PI, Math.PI*3/2};
        for (double dir : dirs) {
            for (double d = 0.3; d <= len; d += 0.35) {
                w.spawnParticle(Particle.DUST,
                        center.clone().add(Math.cos(dir)*d, 0.5, Math.sin(dir)*d),
                        1,0,0,0, new Particle.DustOptions(color, 2.5f));
            }
        }
    }

    /** Tornado — particles spiraling inward from large radius */
    private void spawnTornado(World w, Location base, Color color, int height) {
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (t >= height*8) { cancel(); return; }
                double h = t * 0.15;
                double r = Math.max(0.3, 2.5 - h*0.3);
                double a = t * 0.5;
                w.spawnParticle(Particle.DUST, base.clone().add(Math.cos(a)*r, h, Math.sin(a)*r),
                        2,0,0,0, new Particle.DustOptions(color, 2.5f));
                t++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
