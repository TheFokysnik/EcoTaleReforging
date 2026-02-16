package com.crystalrealm.ecotalereforging.model;

/**
 * Result of a reforge attempt.
 */
public enum ReforgeResult {

    /** Reforge succeeded — item upgraded. */
    SUCCESS,

    /** Reforge failed — item destroyed, materials returned via reverse crafting. */
    FAILURE,

    /** Reforge failed but protection was active — item kept, level reset to +0. */
    FAILURE_PROTECTED,

    /** Could not attempt — insufficient materials, coins, or invalid item. */
    CANNOT_ATTEMPT
}
