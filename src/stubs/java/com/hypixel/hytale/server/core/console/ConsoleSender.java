package com.hypixel.hytale.server.core.console;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandSender;

import java.util.UUID;

/**
 * Stub — Hytale console sender singleton.
 * Real class: com.hypixel.hytale.server.core.console.ConsoleSender
 */
public class ConsoleSender implements CommandSender {
    public static final ConsoleSender INSTANCE = new ConsoleSender();

    @Override public String getDisplayName() { return "Console"; }
    @Override public UUID getUuid() { return new UUID(0, 0); }
    @Override public boolean hasPermission(String permission) { return true; }
    @Override public void sendMessage(Message message) {}
}
