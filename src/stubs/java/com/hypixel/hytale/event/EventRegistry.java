package com.hypixel.hytale.event;

import java.util.function.Consumer;

/**
 * Stub — Real EventRegistry from com.hypixel.hytale.event.
 * Replaces the old wrong-package stub at server.core.event.EventRegistry.
 */
public class EventRegistry {

    /**
     * Register a typed event handler.
     *
     * @param eventClass the event class to listen for
     * @param handler    the consumer handler
     * @param <T>        event type
     * @return an EventRegistration handle (stub returns null)
     */
    public <T> Object register(Class<? super T> eventClass, Consumer<T> handler) {
        return null;
    }

    /**
     * Register a typed event handler globally.
     * Real method returns EventRegistration.
     *
     * @param eventClass the event class to listen for
     * @param handler    the consumer handler
     * @param <T>        event type
     * @return an EventRegistration handle (stub returns null)
     */
    public <T> EventRegistration registerGlobal(Class<T> eventClass, Consumer<T> handler) { return null; }
}
