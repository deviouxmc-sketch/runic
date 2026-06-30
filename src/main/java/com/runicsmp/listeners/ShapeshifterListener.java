package com.runicsmp.listeners;

import com.runicsmp.RunicSMP;
import com.runicsmp.data.RuneType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class ShapeshifterListener implements Listener {

    private final RunicSMP plugin;
    private final Map<UUID, String> disguisedAsName = new HashMap<>(); // uuid -> fake name

    public ShapeshifterListener(RunicSMP plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private boolean hasShapeshifter(Player player) {
        var data = plugin.getRuneManager().getData(player);
        return data.hasPrimary() && data.getPrimaryRune() == RuneType.SHAPESHIFTER;
    }

    public boolean isDisguised(Player player) {
        return disguisedAsName.containsKey(player.getUniqueId());
    }

    public String getDisguiseName(Player player) {
        return disguisedAsName.get(player.getUniqueId());
    }

    public void activateShapeshift(Player player) {
        if (isDisguised(player)) {
            removeDisguise(player, true);
            return;
        }

        List<Player> candidates = Bukkit.getOnlinePlayers().stream()
                .filter(p -> !p.equals(player))
                .collect(Collectors.toList());

        if (candidates.isEmpty()) {
            player.sendMessage("§cNo other players online to copy!");
            return;
        }

        Player target = candidates.get((int)(Math.random() * candidates.size()));
        String fakeName = target.getName();
        disguisedAsName.put(player.getUniqueId(), fakeName);

        // Change display name and tab list name
        player.displayName(Component.text(fakeName).color(NamedTextColor.WHITE));
        player.playerListName(Component.text(fakeName).color(NamedTextColor.WHITE));

        // Particles
        Location loc = player.getLocation().add(0, 1, 0);
        World w = player.getWorld();
        w.spawnParticle(Particle.TOTEM_OF_UNDYING, loc, 30, 0.5, 0.8, 0.5, 0.5);
        w.spawnParticle(Particle.ENCHANT, loc, 20, 0.5, 0.8, 0.5, 0.3);
        w.spawnParticle(Particle.DUST, loc, 15, 0.4, 0.6, 0.4,
                new Particle.DustOptions(Color.fromRGB(0, 200, 200), 2f));
        w.playSound(loc, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1f, 0.8f);

        player.sendMessage("§3✦ You are now disguised as §b§l" + fakeName + "§3 for 10 minutes!");
        player.sendMessage("§7Use /ability again to remove the disguise.");

        // Auto-remove after 10 minutes
        UUID aid = player.getUniqueId();
        new BukkitRunnable() {
            @Override public void run() {
                if (disguisedAsName.containsKey(aid)) removeDisguise(player, true);
            }
        }.runTaskLater(plugin, 12000L);
    }

    public void removeDisguise(Player player, boolean announce) {
        disguisedAsName.remove(player.getUniqueId());

        // Restore real name
        player.displayName(Component.text(player.getName()).color(NamedTextColor.WHITE));
        player.playerListName(Component.text(player.getName()).color(NamedTextColor.WHITE));

        Location loc = player.getLocation().add(0, 1, 0);
        player.getWorld().spawnParticle(Particle.CLOUD, loc, 20, 0.4, 0.6, 0.4, 0.05);
        player.getWorld().playSound(loc, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1f, 1.5f);

        if (announce) player.sendMessage("§7✦ Disguise removed.");
    }


    @EventHandler(priority = EventPriority.HIGH)
    public void onChat(io.papermc.paper.event.player.AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (!isDisguised(player)) return;
        String fakeName = getDisguiseName(player);
        // Override the renderer to show fake name
        event.renderer((source, sourceDisplayName, message, viewer) ->
            net.kyori.adventure.text.Component.text(fakeName + " » ").color(NamedTextColor.WHITE)
                .append(message));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (isDisguised(event.getPlayer())) removeDisguise(event.getPlayer(), false);
    }
}
