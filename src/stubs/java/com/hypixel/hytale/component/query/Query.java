package com.hypixel.hytale.component.query;

public interface Query<ECS_TYPE> {

    /** Returns a query that matches all entities (no filtering). */
    @SuppressWarnings("unchecked")
    static <T> Query<T> any() {
        return (Query<T>) AnyQuery.INSTANCE;
    }
}
