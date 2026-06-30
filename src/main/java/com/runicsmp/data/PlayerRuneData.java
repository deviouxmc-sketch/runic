package com.runicsmp.data;

import java.util.UUID;

/**
 * Holds all rune-related state for a single player.
 * Energy system fully removed.
 */
public class PlayerRuneData {

    private final UUID playerId;

    private RuneType primaryRune;
    private RuneType secondaryRune;

    // Lifesteal bonus hearts tracker
    private int lifeStealBonusHearts = 0;

    // Tracker rune target
    private UUID trackerTarget;

    // Strength state
    private boolean strengthActive = false;
    private int strengthHitCount = 0;

    // Defender shield
    private boolean defenderShieldActive = false;

    // Clone rune target
    private UUID cloneTarget;

    // Trader discount active
    private boolean traderDiscountActive = false;

    public PlayerRuneData(UUID playerId) {
        this.playerId = playerId;
    }

    public UUID getPlayerId() { return playerId; }

    public RuneType getPrimaryRune() { return primaryRune; }
    public void setPrimaryRune(RuneType rune) { this.primaryRune = rune; }

    public RuneType getSecondaryRune() { return secondaryRune; }
    public void setSecondaryRune(RuneType rune) { this.secondaryRune = rune; }

    public boolean hasPrimary() { return primaryRune != null; }
    public boolean hasSecondary() { return secondaryRune != null; }

    // Stubs for removed energy system - always return true so nothing breaks
    public boolean canUseActive() { return true; }
    public boolean canUseSecondaryActive() { return true; }
    public boolean hasPassive() { return true; }
    public boolean isMaxEnergy() { return false; }
    public int getEnergyLevel() { return 1; }
    public void setEnergyLevel(int level) {}
    public int getEnergyBank() { return 0; }
    public void setEnergyBank(int bank) {}
    public void addEnergyBank(int amount) {}
    public boolean hasEnergyBanked() { return false; }

    public int getLifeStealBonusHearts() { return lifeStealBonusHearts; }
    public void setLifeStealBonusHearts(int h) { this.lifeStealBonusHearts = Math.max(0, h); }
    public void addLifeStealBonusHearts(int h) { this.lifeStealBonusHearts += h; }
    public void resetLifeStealBonusHearts() { this.lifeStealBonusHearts = 0; }

    public UUID getTrackerTarget() { return trackerTarget; }
    public void setTrackerTarget(UUID target) { this.trackerTarget = target; }

    public boolean isStrengthActive() { return strengthActive; }
    public void setStrengthActive(boolean active) { this.strengthActive = active; }
    public int getStrengthHitCount() { return strengthHitCount; }
    public void setStrengthHitCount(int count) { this.strengthHitCount = count; }
    public void incrementStrengthHitCount() { this.strengthHitCount++; }

    public boolean isDefenderShieldActive() { return defenderShieldActive; }
    public void setDefenderShieldActive(boolean active) { this.defenderShieldActive = active; }

    public UUID getCloneTarget() { return cloneTarget; }
    public void setCloneTarget(UUID target) { this.cloneTarget = target; }

    public boolean isTraderDiscountActive() { return traderDiscountActive; }
    public void setTraderDiscountActive(boolean active) { this.traderDiscountActive = active; }
}
