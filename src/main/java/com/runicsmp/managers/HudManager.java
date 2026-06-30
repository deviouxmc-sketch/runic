package com.runicsmp.managers;

import com.runicsmp.RunicSMP;
import com.runicsmp.data.PlayerRuneData;
import com.runicsmp.data.RuneType;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * HUD layout:  <primaryCD> [Primary]  ●●●●●  [Secondary] <secondaryCD>
 * CD timer left of primary, right of secondary.
 */
public class HudManager {

    private final RunicSMP plugin;
    private BukkitRunnable hudTask;
    private static final String RUNE_FONT = "minecraft:rune_hud";

    private final Map<UUID, Long> primaryFlashUntil    = new HashMap<>();
    private final Map<UUID, Long> primaryCooldownEnd   = new HashMap<>();
    private final Map<UUID, Long> secondaryFlashUntil  = new HashMap<>();
    private final Map<UUID, Long> secondaryCooldownEnd = new HashMap<>();

    public HudManager(RunicSMP plugin) { this.plugin = plugin; }

    public void start() {
        hudTask = new BukkitRunnable() {
            @Override public void run() {
                for (Player p : plugin.getServer().getOnlinePlayers()) sendHud(p);
            }
        };
        hudTask.runTaskTimer(plugin, 0L, 4L); // throttled from every 0.1s to every 0.2s to reduce packet load
    }

    public void stop() { if (hudTask != null) hudTask.cancel(); }
    public void updateHud(Player player) { sendHud(player); }

    public void clearPrimaryCd(UUID id) { primaryCooldownEnd.remove(id); }
    public void clearSecondaryCd(UUID id) { secondaryCooldownEnd.remove(id); }

    public void notifyAbilityUsed(Player player, int cooldownSeconds) {
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        primaryFlashUntil.put(id, now + 1000L);
        if (cooldownSeconds > 0) primaryCooldownEnd.put(id, now + cooldownSeconds * 1000L);
    }

    public void notifySecondaryUsed(Player player, int cooldownSeconds) {
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        secondaryFlashUntil.put(id, now + 1000L);
        if (cooldownSeconds > 0) secondaryCooldownEnd.put(id, now + cooldownSeconds * 1000L);
    }

    private void sendHud(Player player) {
        PlayerRuneData data = plugin.getRuneManager().getData(player);
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();

        // Primary
        String primaryChar = data.hasPrimary() ? data.getPrimaryRune().getFontChar() : RuneType.EMPTY_CHAR;
        boolean pFlashing = primaryFlashUntil.containsKey(id) && now < primaryFlashUntil.get(id);
        boolean pFlashOn  = pFlashing && (now / 100) % 2 == 0;
        TextColor primaryColor = pFlashOn ? TextColor.color(0xFFFF55) : TextColor.color(0xFFFFFF);
        Component primaryIcon = Component.text(primaryChar).font(Key.key(RUNE_FONT)).color(primaryColor);

        // Secondary
        String secondaryChar = data.hasSecondary() ? data.getSecondaryRune().getFontChar() : RuneType.EMPTY_CHAR;
        boolean sFlashing = secondaryFlashUntil.containsKey(id) && now < secondaryFlashUntil.get(id);
        boolean sFlashOn  = sFlashing && (now / 100) % 2 == 0;
        TextColor secondaryColor = sFlashOn ? TextColor.color(0x55FFFF) : TextColor.color(0xCCCCCC);
        Component secondaryIcon = Component.text(secondaryChar).font(Key.key(RUNE_FONT)).color(secondaryColor);

        // CD timers — primary CD on LEFT, secondary CD on RIGHT
        Component primaryCd = buildCd(id, pFlashing, primaryCooldownEnd, now, true);
        Component secondaryCd = buildCd(id, sFlashing, secondaryCooldownEnd, now, false);

        // Layout: <primaryCD> [Primary]   [Secondary] <secondaryCD>   🔍 Item X - Name
        net.kyori.adventure.text.ComponentBuilder<?, ?> hudBuilder = Component.text()
                .append(primaryCd)
                .append(primaryIcon)
                .append(Component.text("   "))
                .append(secondaryIcon)
                .append(secondaryCd);

        // Hunt shown on boss bar — nothing extra needed in HUD

        // Respect HUD enabled setting
        var settings = plugin.getSettingsCmd();
        if (settings != null && !settings.getSetting(player, "hud_enabled").equals("On")) return;
        player.sendActionBar(hudBuilder.build());
    }

    private Component buildCd(UUID id, boolean flashing, Map<UUID, Long> cdMap, long now, boolean isLeft) {
        if (flashing) return Component.empty();
        if (!cdMap.containsKey(id)) return Component.empty();
        long remaining = cdMap.get(id) - now;
        if (remaining <= 0) { cdMap.remove(id); return Component.empty(); }
        int secs = (int) Math.ceil(remaining / 1000.0);
        TextColor color = TextColor.color(0xFFFFFF); // white always
        // Left side: timer then space | Right side: space then timer
        String text = isLeft ? secs + "s " : " " + secs + "s";
        return Component.text(text).color(color);
    }


}
