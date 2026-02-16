package com.crystalrealm.ecotalereforging.model;

import javax.annotation.Nonnull;

/**
 * Holds information about a reforge attempt for display / event purposes.
 */
public class ReforgeAttemptInfo {

    private final String itemId;
    private final int currentLevel;
    private final int targetLevel;
    private final double successChance;
    private final double coinCost;
    private final double damageBonus;
    private final double defenseBonus;
    private final ReforgeResult result;

    public ReforgeAttemptInfo(@Nonnull String itemId,
                              int currentLevel,
                              int targetLevel,
                              double successChance,
                              double coinCost,
                              double damageBonus,
                              double defenseBonus,
                              @Nonnull ReforgeResult result) {
        this.itemId = itemId;
        this.currentLevel = currentLevel;
        this.targetLevel = targetLevel;
        this.successChance = successChance;
        this.coinCost = coinCost;
        this.damageBonus = damageBonus;
        this.defenseBonus = defenseBonus;
        this.result = result;
    }

    @Nonnull public String getItemId() { return itemId; }
    public int getCurrentLevel() { return currentLevel; }
    public int getTargetLevel() { return targetLevel; }
    public double getSuccessChance() { return successChance; }
    public double getCoinCost() { return coinCost; }
    public double getDamageBonus() { return damageBonus; }
    public double getDefenseBonus() { return defenseBonus; }
    @Nonnull public ReforgeResult getResult() { return result; }
}
