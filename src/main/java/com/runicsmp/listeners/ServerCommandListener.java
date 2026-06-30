package com.runicsmp.listeners;

import com.runicsmp.RunicSMP;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class ServerCommandListener implements CommandExecutor, TabCompleter, Listener {

    private final RunicSMP plugin;
    private boolean endDisabled = false;
    private boolean netherDisabled = false;
    private final Map<String, Boolean> pvpWorlds = new HashMap<>();
    private boolean globalPvp = true;
    private final Set<UUID> frozenPlayers = new HashSet<>();

    public ServerCommandListener(RunicSMP plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPortal(PlayerPortalEvent event) {
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.END_PORTAL && endDisabled) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cThe End is currently disabled.");
        }
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL && netherDisabled) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cThe Nether is currently disabled.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPvP(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        Player attacker = null;
        if (event.getDamager() instanceof Player p) attacker = p;
        else if (event.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p) attacker = p;
        if (attacker == null) return;
        String worldName = victim.getWorld().getName();
        boolean worldPvp = pvpWorlds.getOrDefault(worldName, globalPvp);
        if (!worldPvp) {
            event.setCancelled(true);
            attacker.sendMessage("§cPvP is disabled" + (pvpWorlds.containsKey(worldName) ? " in this world." : "."));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onFrozenMove(PlayerMoveEvent event) {
        if (!frozenPlayers.contains(event.getPlayer().getUniqueId())) return;
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;
        if (from.getBlockX() != to.getBlockX() || from.getBlockY() != to.getBlockY() || from.getBlockZ() != to.getBlockZ()) {
            event.setTo(from.clone().setDirection(to.getDirection()));
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        switch (label.toLowerCase()) {
            case "endportal" -> {
                if (!(sender instanceof org.bukkit.entity.Player p ? p.isOp() : true)) { noPerms(sender); return true; }
                if (args.length < 1) { sender.sendMessage("§cUsage: /endportal <disable|enable>"); return true; }
                if (args[0].equalsIgnoreCase("disable")) {
                    endDisabled = true;
                    Bukkit.broadcast(Component.text("§5⚠ The End has been §cdisabled§5 by an admin."));
                } else {
                    endDisabled = false;
                    Bukkit.broadcast(Component.text("§5✔ The End has been §are-enabled§5 by an admin."));
                }
            }
            case "nether" -> {
                if (!(sender instanceof org.bukkit.entity.Player p ? p.isOp() : true)) { noPerms(sender); return true; }
                if (args.length < 1) { sender.sendMessage("§cUsage: /nether <disable|enable>"); return true; }
                if (args[0].equalsIgnoreCase("disable")) {
                    netherDisabled = true;
                    Bukkit.broadcast(Component.text("§4⚠ The Nether has been §cdisabled§4 by an admin."));
                } else {
                    netherDisabled = false;
                    Bukkit.broadcast(Component.text("§4✔ The Nether has been §are-enabled§4 by an admin."));
                }
            }
            case "pvp" -> {
                if (!(sender instanceof org.bukkit.entity.Player p ? p.isOp() : true)) { noPerms(sender); return true; }
                if (args.length < 1) { sender.sendMessage("§cUsage: /pvp <on|off> [world]"); return true; }
                boolean on = args[0].equalsIgnoreCase("on");
                if (args.length >= 2) {
                    World w = Bukkit.getWorld(args[1]);
                    if (w == null) { sender.sendMessage("§cWorld not found: " + args[1]); return true; }
                    pvpWorlds.put(args[1], on);
                    Bukkit.broadcast(Component.text("§ePvP in §f" + args[1] + "§e has been turned §" + (on ? "a" : "c") + (on ? "ON" : "OFF") + "§e."));
                } else {
                    globalPvp = on;
                    pvpWorlds.clear();
                    Bukkit.broadcast(Component.text("§eGlobal PvP has been turned §" + (on ? "a" : "c") + (on ? "ON" : "OFF") + "§e."));
                }
            }
            case "spawn" -> {
                if (args.length >= 1 && args[0].equalsIgnoreCase("set")) {
                    if (!(sender instanceof org.bukkit.entity.Player p ? p.isOp() : true)) { noPerms(sender); return true; }
                    if (!(sender instanceof Player p)) { sender.sendMessage("Only players can set spawn."); return true; }
                    p.getWorld().setSpawnLocation(p.getLocation());
                    sender.sendMessage("§aWorld spawn set.");
                } else {
                    Player target = (sender instanceof Player p) ? p : null;
                    if (args.length >= 1) target = Bukkit.getPlayer(args[0]);
                    if (target == null) { sender.sendMessage("§cPlayer not found."); return true; }
                    target.teleport(target.getWorld().getSpawnLocation());
                    target.sendMessage("§aTeleported to spawn.");
                    if (!sender.equals(target)) sender.sendMessage("§aTeleported §e" + target.getName() + " §ato spawn.");
                }
            }
            case "freeze" -> {
                if (!(sender instanceof org.bukkit.entity.Player p ? p.isOp() : true)) { noPerms(sender); return true; }
                if (args.length < 1) { sender.sendMessage("§cUsage: /freeze <player>"); return true; }
                Player t = Bukkit.getPlayer(args[0]);
                if (t == null) { sender.sendMessage("§cPlayer not found."); return true; }
                frozenPlayers.add(t.getUniqueId());
                t.sendMessage("§c§lYou have been frozen!");
                sender.sendMessage("§aFroze §e" + t.getName() + "§a.");
            }
            case "unfreeze" -> {
                if (!(sender instanceof org.bukkit.entity.Player p ? p.isOp() : true)) { noPerms(sender); return true; }
                if (args.length < 1) { sender.sendMessage("§cUsage: /unfreeze <player>"); return true; }
                Player t = Bukkit.getPlayer(args[0]);
                if (t == null) { sender.sendMessage("§cPlayer not found."); return true; }
                frozenPlayers.remove(t.getUniqueId());
                t.sendMessage("§aYou have been unfrozen.");
                sender.sendMessage("§aUnfroze §e" + t.getName() + "§a.");
            }
            case "feed" -> {
                Player t = resolveTarget(sender, args); if (t == null) return true;
                t.setFoodLevel(20); t.setSaturation(20f);
                t.sendMessage("§aFed!");
                if (!sender.equals(t)) sender.sendMessage("§aFed §e" + t.getName() + "§a.");
            }
            case "smite" -> {
                if (!(sender instanceof org.bukkit.entity.Player p ? p.isOp() : true)) { noPerms(sender); return true; }
                if (args.length < 1) { sender.sendMessage("§cUsage: /smite <player>"); return true; }
                Player t = Bukkit.getPlayer(args[0]); if (t == null) { sender.sendMessage("§cPlayer not found."); return true; }
                t.getWorld().strikeLightning(t.getLocation());
                sender.sendMessage("§e⚡ Smote §f" + t.getName() + "§e.");
            }
            case "gmc" -> setGM(sender, GameMode.CREATIVE, args);
            case "gms" -> setGM(sender, GameMode.SURVIVAL, args);
            case "gmsp" -> setGM(sender, GameMode.SPECTATOR, args);
            case "gmadv" -> setGM(sender, GameMode.ADVENTURE, args);
            case "clear" -> {
                if (!(sender instanceof org.bukkit.entity.Player p ? p.isOp() : true)) { noPerms(sender); return true; }
                Player t = resolveTarget(sender, args); if (t == null) return true;
                t.getInventory().clear();
                t.sendMessage("§cInventory cleared.");
                if (!sender.equals(t)) sender.sendMessage("§aCleared §e" + t.getName() + "§a's inventory.");
            }
        }
        return true;
    }

    private void setGM(CommandSender sender, GameMode mode, String[] args) {
        if (!sender.hasPermission("runicsmp.admin")) { noPerms(sender); return; }
        Player t;
        if (args.length >= 1) { t = Bukkit.getPlayer(args[0]); if (t == null) { sender.sendMessage("§cPlayer not found."); return; } }
        else { if (!(sender instanceof Player p)) { sender.sendMessage("Specify a player."); return; } t = p; }
        t.setGameMode(mode);
        t.sendMessage("§aGamemode: §e" + mode.name().toLowerCase());
        if (!sender.equals(t)) sender.sendMessage("§aSet §e" + t.getName() + "§a to §e" + mode.name().toLowerCase() + "§a.");
    }

    private Player resolveTarget(CommandSender sender, String[] args) {
        if (args.length >= 1) {
            Player t = Bukkit.getPlayer(args[0]);
            if (t == null) { sender.sendMessage("§cPlayer not found: " + args[0]); return null; }
            if (!sender.hasPermission("runicsmp.admin") && !sender.equals(t)) { noPerms(sender); return null; }
            return t;
        }
        if (sender instanceof Player p) return p;
        sender.sendMessage("§cSpecify a player."); return null;
    }

    private void noPerms(CommandSender s) { s.sendMessage("§cNo permission."); }

    public boolean isPvpEnabled() { return globalPvp; }
    public boolean isEndEnabled() { return !endDisabled; }
    public boolean isNetherEnabled() { return !netherDisabled; }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) return switch (label.toLowerCase()) {
            case "endportal" -> List.of("disable", "enable");
            case "pvp" -> List.of("on", "off");
            case "spawn" -> List.of("set");
            default -> Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        };
        if (args.length == 2 && label.equalsIgnoreCase("pvp")) return Bukkit.getWorlds().stream().map(World::getName).toList();
        return List.of();
    }
}
