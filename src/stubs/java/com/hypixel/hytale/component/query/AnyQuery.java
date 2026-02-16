package com.hypixel.hytale.component.query;

/**
 * Stub — a query that matches all entities.
 */
public class AnyQuery<ECS_TYPE> implements Query<ECS_TYPE> {
    @SuppressWarnings("rawtypes")
    static final AnyQuery INSTANCE = new AnyQuery();
}
