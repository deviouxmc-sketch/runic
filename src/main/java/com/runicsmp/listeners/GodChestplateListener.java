package com.runicsmp.listeners;

import com.runicsmp.RunicSMP;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class GodChestplateListener implements CommandExecutor, TabCompleter {

    private final RunicSMP plugin;

    public GodChestplateListener(RunicSMP plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void onJoin(org.bukkit.event.player.PlayerJoinEvent e) {
                plugin.getServer().getScheduler().runTaskLater(plugin, () ->
                        AltarListener.applyChestplatePassive(e.getPlayer()), 2L);
            }
        }, plugin);

        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) AltarListener.applyChestplatePassive(p);
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
            sender.sendMessage("§cUsage: /godchestplate give <player>");
            return true;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) { sender.sendMessage("§cPlayer not found."); return true; }

        ItemStack chest = AltarListener.createGodChestplate();
        if (target.getInventory().firstEmpty() != -1) {
            target.getInventory().addItem(chest);
        } else {
            target.getWorld().dropItemNaturally(target.getLocation(), chest);
        }
        sender.sendMessage("§a✦ Gave God Chestplate to §e" + target.getName() + "§a.");
        target.sendMessage("§b✦ You received the §3§lGod Chestplate§b!");
        target.playSound(target.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
        AltarListener.applyChestplatePassive(target);
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
