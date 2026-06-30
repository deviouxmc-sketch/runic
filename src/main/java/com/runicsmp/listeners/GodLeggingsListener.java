package com.runicsmp.listeners;

import com.runicsmp.RunicSMP;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Sound;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class GodLeggingsListener implements CommandExecutor, TabCompleter {

    private final RunicSMP plugin;

    public GodLeggingsListener(RunicSMP plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void onJoin(org.bukkit.event.player.PlayerJoinEvent e) {
                plugin.getServer().getScheduler().runTaskLater(plugin, () ->
                        AltarListener.applyLeggingsPassive(e.getPlayer()), 2L);
            }
        }, plugin);

        // Periodic check every 2s
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) AltarListener.applyLeggingsPassive(p);
        }, 40L, 40L);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p && p.isOp()) && !sender.hasPermission("runicsmp.admin")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length < 2 || !args[0].equalsIgnoreCase("give")) {
            sender.sendMessage("§cUsage: /godleggings give <player>");
            return true;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) { sender.sendMessage("§cPlayer not found."); return true; }

        ItemStack legs = AltarListener.createGodLeggings();
        if (target.getInventory().firstEmpty() != -1) {
            target.getInventory().addItem(legs);
        } else {
            target.getWorld().dropItemNaturally(target.getLocation(), legs);
            sender.sendMessage("§eInventory full — dropped at their location.");
        }
        sender.sendMessage("§a✦ Gave God Leggings to §e" + target.getName() + "§a.");
        target.sendMessage("§d✦ You received the §5§lGod Leggings§d!");
        target.playSound(target.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
        AltarListener.applyLeggingsPassive(target);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command c,
                                                 @NotNull String l, @NotNull String[] a) {
        if (a.length == 1) return List.of("give");
        if (a.length == 2) return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        return List.of();
    }
}
