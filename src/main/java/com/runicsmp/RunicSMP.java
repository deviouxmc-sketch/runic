package com.runicsmp;

import com.runicsmp.listeners.*;
import com.runicsmp.managers.*;
import com.runicsmp.utils.RuneItemBuilder;
import org.bukkit.plugin.java.JavaPlugin;

public class RunicSMP extends JavaPlugin {

    private RuneManager runeManager;
    private HudManager hudManager;
    private RitualManager ritualManager;
    private RuneCraftingManager craftingManager;
    private EnergyManager energyManager;
    private RuneParticleManager particleManager;
    private com.runicsmp.managers.RitualTpManager ritualTpManager;
    private com.runicsmp.managers.FirstJoinManager firstJoinManager;
    private com.runicsmp.managers.TrustManager trustManager;
    private com.runicsmp.managers.NotifManager notifManager;
    private com.runicsmp.managers.KillTracker killTracker;
    private com.runicsmp.managers.PlayerDataManager playerDataManager;
    private com.runicsmp.managers.TabListManager tabListManager;
    private com.runicsmp.listeners.AltarListener altarListener;
    private com.runicsmp.managers.JuggernautManager juggernautManager;
    private com.runicsmp.managers.ScavengerHuntManager scavengerHuntManager;
    private com.runicsmp.listeners.ArachnidPassiveListener arachnidListener;
    private com.runicsmp.listeners.LightningPassiveListener lightningPassiveListener;
    private com.runicsmp.runes.RuneAbilityHandler abilityHandler;
    private com.runicsmp.runes.SecondaryAbilityHandler secondaryHandler;
    private com.runicsmp.listeners.ShapeshifterListener shapeshifterListener;
    private com.runicsmp.listeners.SettingsCommandListener settingsCmd;
    private com.runicsmp.listeners.CombatListener combatListener;
    private com.runicsmp.listeners.RitualModeCommandListener ritualModeCmd;
    private com.runicsmp.managers.ResurrectionManager resurrectionManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        RuneItemBuilder.init(this);

        runeManager     = new RuneManager(this);
        hudManager      = new HudManager(this);
        ritualManager   = new RitualManager(this);
        craftingManager = new RuneCraftingManager(this);
        energyManager   = new EnergyManager(this);
        particleManager = new RuneParticleManager(this);
        ritualTpManager = new com.runicsmp.managers.RitualTpManager();
        firstJoinManager = new com.runicsmp.managers.FirstJoinManager(this);
        trustManager = new com.runicsmp.managers.TrustManager(this);
        notifManager = new com.runicsmp.managers.NotifManager();
        killTracker = new com.runicsmp.managers.KillTracker(this);
        playerDataManager = new com.runicsmp.managers.PlayerDataManager(this);
        tabListManager = new com.runicsmp.managers.TabListManager(this);
        new com.runicsmp.listeners.TotemListener(this);
        new com.runicsmp.listeners.WindMaceListener(this);
        lightningPassiveListener = new com.runicsmp.listeners.LightningPassiveListener(this);
        new com.runicsmp.listeners.WardenPassiveListener(this);
        new com.runicsmp.listeners.WindDoubleJumpListener(this);
        new com.runicsmp.listeners.WindSpearListener(this);
        shapeshifterListener = new com.runicsmp.listeners.ShapeshifterListener(this);
        getServer().getPluginManager().registerEvents(shapeshifterListener, this);
        arachnidListener = new com.runicsmp.listeners.ArachnidPassiveListener(this);
        getServer().getPluginManager().registerEvents(arachnidListener, this);

        com.runicsmp.listeners.GodLeggingsListener godLegsCmd = new com.runicsmp.listeners.GodLeggingsListener(this);
        getCommand("godleggings").setExecutor(godLegsCmd);
        getCommand("godleggings").setTabCompleter(godLegsCmd);

        com.runicsmp.listeners.GodChestplateListener godChestCmd = new com.runicsmp.listeners.GodChestplateListener(this);
        getCommand("godchestplate").setExecutor(godChestCmd);
        getCommand("godchestplate").setTabCompleter(godChestCmd);

        settingsCmd = new com.runicsmp.listeners.SettingsCommandListener(this);
        getCommand("runicsmpsettings").setExecutor(settingsCmd);
        getCommand("setting").setExecutor(settingsCmd);

        com.runicsmp.listeners.AbilityCommandListener abilityCmd = new com.runicsmp.listeners.AbilityCommandListener(this);
        getCommand("ability").setExecutor(abilityCmd);
        getCommand("ability").setTabCompleter(abilityCmd);

        // Broadcast command
        com.runicsmp.listeners.BroadcastCommandListener bcCmd = new com.runicsmp.listeners.BroadcastCommandListener(this);
        getCommand("broadcast").setExecutor(bcCmd);
        getCommand("broadcast").setTabCompleter(bcCmd);

        // Scavenger Hunt
        scavengerHuntManager = new com.runicsmp.managers.ScavengerHuntManager(this);
        com.runicsmp.listeners.HuntCommandListener huntCmd = new com.runicsmp.listeners.HuntCommandListener(this);
        getCommand("hunt").setExecutor(huntCmd);
        getCommand("hunt").setTabCompleter(huntCmd);
        juggernautManager = new com.runicsmp.managers.JuggernautManager(this);
        com.runicsmp.listeners.JuggernautCommandListener juggCmd =
                new com.runicsmp.listeners.JuggernautCommandListener(this);
        getCommand("jugg").setExecutor(juggCmd);
        getCommand("jugg").setTabCompleter(juggCmd);
        getServer().getPluginManager().registerEvents(juggernautManager.createJoinListener(), this);
        altarListener = new com.runicsmp.listeners.AltarListener(this);
        com.runicsmp.listeners.AltarListener altarCmd = altarListener;
        getServer().getPluginManager().registerEvents(altarCmd, this);
        getCommand("altar").setExecutor(altarCmd);
        getCommand("altar").setTabCompleter(altarCmd);

        // Periodic check for god boots speed passive
        getServer().getScheduler().runTaskTimer(this, () -> {
            for (org.bukkit.entity.Player p : getServer().getOnlinePlayers()) {
                com.runicsmp.listeners.AltarListener.applyBootsPassive(p);
            }
        }, 40L, 40L);
        new com.runicsmp.listeners.FrostPassiveListener(this);
        ritualModeCmd = new com.runicsmp.listeners.RitualModeCommandListener(this);
        resurrectionManager = new com.runicsmp.managers.ResurrectionManager();

        hudManager.start();
        // energyManager removed
        particleManager.start();

        // Listeners
        getServer().getPluginManager().registerEvents(new RuneInteractListener(this), this);
        getServer().getPluginManager().registerEvents(new com.runicsmp.listeners.FirstJoinListener(this), this);
        getServer().getPluginManager().registerEvents(new com.runicsmp.listeners.GuiClickListener(this), this);
        com.runicsmp.listeners.TrustCommandListener trustCmd = new com.runicsmp.listeners.TrustCommandListener(this);
        getCommand("trust").setExecutor(trustCmd);
        getCommand("trust").setTabCompleter(trustCmd);
        getCommand("untrust").setExecutor(trustCmd);
        getCommand("untrust").setTabCompleter(trustCmd);
        getCommand("trustlist").setExecutor(trustCmd);
        getCommand("trustlist").setTabCompleter(trustCmd);
        com.runicsmp.listeners.NotifCommandListener notifCmd = new com.runicsmp.listeners.NotifCommandListener(this);
        getCommand("runenotif").setExecutor(notifCmd);
        getCommand("runenotif").setTabCompleter(notifCmd);
        getCommand("ritualmode").setExecutor(ritualModeCmd);
        getCommand("ritualmode").setTabCompleter(ritualModeCmd);
        com.runicsmp.listeners.LeaderboardCommandListener lbCmd = new com.runicsmp.listeners.LeaderboardCommandListener(this);
        getCommand("leaderboard").setExecutor(lbCmd);
        getCommand("leaderboard").setTabCompleter(lbCmd);
        combatListener = new CombatListener(this);
        getServer().getPluginManager().registerEvents(combatListener, this);
        getServer().getPluginManager().registerEvents(new com.runicsmp.listeners.LockdownBlockListener(this), this);
        getServer().getPluginManager().registerEvents(new com.runicsmp.listeners.WindLandingListener(this), this);
        getServer().getPluginManager().registerEvents(new com.runicsmp.listeners.LifestealDropListener(this), this);
        com.runicsmp.listeners.GodPickaxeListener godPickCmd = new com.runicsmp.listeners.GodPickaxeListener(this);
        getServer().getPluginManager().registerEvents(godPickCmd, this);
        getCommand("godpickaxe").setExecutor(godPickCmd);
        getCommand("godpickaxe").setTabCompleter(godPickCmd);
        getServer().getPluginManager().registerEvents(new SmithingTableListener(this), this);

        ServerCommandListener serverCmds = new ServerCommandListener(this);
        getServer().getPluginManager().registerEvents(serverCmds, this);

        // Commands
        RuneCommandListener runeCmds = new RuneCommandListener(this);
        getCommand("rune").setExecutor(runeCmds);
        getCommand("rune").setTabCompleter(runeCmds);
        getCommand("runeadmin").setExecutor(runeCmds);
        getCommand("runeadmin").setTabCompleter(runeCmds);

        RecipeCommandListener recipeCmds = new RecipeCommandListener(this);
        getCommand("recipes").setExecutor(recipeCmds);
        getCommand("recipes").setTabCompleter(recipeCmds);

        AbilityCommandListener abilityCmds = new AbilityCommandListener(this);
        getCommand("ability").setExecutor(abilityCmds);
        getCommand("ability").setTabCompleter(abilityCmds);

        getCommand("withdraw").setExecutor(runeCmds);
        getCommand("withdraw").setTabCompleter(runeCmds);
        getCommand("ritualtpto").setExecutor(runeCmds);
        getCommand("ritualtpto").setTabCompleter(runeCmds);

        // Energy system removed - no energy command

        for (String cmd : new String[]{"endportal","nether","pvp","spawn","freeze","unfreeze","smite","gmc","gms","gmsp","gmadv","clear"}) {
            getCommand(cmd).setExecutor(serverCmds);
            getCommand(cmd).setTabCompleter(serverCmds);
        }

        getLogger().info("RunicSMP enabled!");
    }

    @Override
    public void onDisable() {
        if (playerDataManager != null) playerDataManager.saveAll();
        hudManager.stop();
        // energyManager removed
        particleManager.stop();
        getLogger().info("RunicSMP disabled.");
    }

    public RuneManager getRuneManager()             { return runeManager; }
    public HudManager getHudManager()               { return hudManager; }
    public RitualManager getRitualManager()         { return ritualManager; }
    public RuneCraftingManager getCraftingManager() { return craftingManager; }
    public EnergyManager getEnergyManager()         { return energyManager; }
    public RuneParticleManager getParticleManager() { return particleManager; }
    public com.runicsmp.utils.CooldownManager getCooldowns() { return runeManager.getCooldowns(); }
    public com.runicsmp.managers.RitualTpManager getRitualTpManager() { return ritualTpManager; }
    public com.runicsmp.managers.FirstJoinManager getFirstJoinManager() { return firstJoinManager; }
    public com.runicsmp.managers.TrustManager getTrustManager() { return trustManager; }
    public com.runicsmp.managers.NotifManager getNotifManager() { return notifManager; }
    public com.runicsmp.managers.KillTracker getKillTracker() { return killTracker; }
    public com.runicsmp.managers.PlayerDataManager getPlayerDataManager() { return playerDataManager; }
    public com.runicsmp.managers.TabListManager getTabListManager() { return tabListManager; }
    public com.runicsmp.listeners.CombatListener getCombatListener() { return combatListener; }
    public com.runicsmp.listeners.AltarListener getAltarListener() { return altarListener; }
    public com.runicsmp.managers.JuggernautManager getJuggernautManager() { return juggernautManager; }
    public com.runicsmp.managers.ScavengerHuntManager getScavengerHuntManager() { return scavengerHuntManager; }
    public com.runicsmp.listeners.SettingsCommandListener getSettingsCmd() { return settingsCmd; }
    public com.runicsmp.listeners.ArachnidPassiveListener getArachnidListener() { return arachnidListener; }
    public com.runicsmp.listeners.LightningPassiveListener getLightningPassiveListener() { return lightningPassiveListener; }
    public com.runicsmp.listeners.ShapeshifterListener getShapeshifterListener() { return shapeshifterListener; }
    public com.runicsmp.runes.RuneAbilityHandler getAbilityHandler() { return abilityHandler; }
    public com.runicsmp.runes.SecondaryAbilityHandler getSecondaryHandler() { return secondaryHandler; }
    public com.runicsmp.listeners.RitualModeCommandListener getRitualModeCmd() { return ritualModeCmd; }
    public com.runicsmp.managers.ResurrectionManager getResurrectionManager() { return resurrectionManager; }
}
