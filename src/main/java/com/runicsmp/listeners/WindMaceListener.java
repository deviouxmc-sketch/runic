package com.runicsmp.listeners;

import com.runicsmp.RunicSMP;
import com.runicsmp.data.RuneType;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

public class WindMaceListener implements Listener {

    private final RunicSMP plugin;
    private final NamespacedKey key = new NamespacedKey("runicsmp", "wind_mace");
    private final java.util.Map<java.util.UUID, Long> maceCooldowns = new java.util.HashMap<>();

    public WindMaceListener(RunicSMP plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        new BukkitRunnable() {
            @Override public void run() {
                for (Player p : plugin.getServer().getOnlinePlayers()) {
                    var data = plugin.getRuneManager().getData(p);
                    boolean hasWind = data.hasPrimary() && data.getPrimaryRune() == RuneType.WIND;
                    if (!hasWind) plugin.getRuneManager().removeWindMace(p);
                }
            }
        }.runTaskTimer(plugin, 40L, 40L);
    }

    private boolean isWindMace(ItemStack item) {
        if (item == null) return false;
        var meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(key, PersistentDataType.BOOLEAN);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMaceHit(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!isWindMace(attacker.getInventory().getItemInMainHand())) return;

        long now = System.currentTimeMillis();
        long lastHit = maceCooldowns.getOrDefault(attacker.getUniqueId(), 0L);
        long remaining = 45000 - (now - lastHit);

        if (remaining > 0) {
            event.setCancelled(true);
            attacker.sendMessage("§b✦ Wind Mace cooldown: §e" + (remaining / 1000 + 1) + "s");
            return;
        }

        maceCooldowns.put(attacker.getUniqueId(), now);
    }

    // Cap at 7 hearts (14 HP) max — let normal damage through, only clamp if over cap
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMaceDamageCap(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!isWindMace(attacker.getInventory().getItemInMainHand())) return;
        if (event.getFinalDamage() <= 14.0) return; // under 7 hearts — let it through untouched

        // Over cap — cancel and deal exactly 14 HP final damage (bypasses armor so it's truly 7 hearts)
        event.setCancelled(true);
        if (event.getEntity() instanceof org.bukkit.entity.LivingEntity target) {
            target.damage(14.0, attacker); // re-deals as new damage event so animations/sounds fire
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrop(PlayerDropItemEvent event) {
        if (!isWindMace(event.getItemDrop().getItemStack())) return;
        event.setCancelled(true);
        event.getPlayer().sendMessage("§cThe Wind Mace is soulbound!");
    }
}
