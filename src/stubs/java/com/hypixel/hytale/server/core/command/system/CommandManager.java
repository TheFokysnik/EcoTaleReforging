package com.hypixel.hytale.server.core.command.system;

import java.util.concurrent.CompletableFuture;

/**
 * Stub — Central command dispatcher.
 * Real class: com.hypixel.hytale.server.core.command.system.CommandManager
 *
 * <p>Dispatches commands on behalf of a {@link CommandSender} (console or player).</p>
 */
public class CommandManager {

    /**
     * Returns the console sender (server operator).
     *
     * @return the console {@link CommandSender}
     */
    public CommandSender getConsoleSender() {
        throw new UnsupportedOperationException("Stub");
    }

    /**
     * Dispatches a command as the given sender.
     *
     * @param sender  the command sender (console or player)
     * @param command the raw command string (without leading '/')
     * @return a future that completes when the command finishes
     */
    public CompletableFuture<?> handleCommand(CommandSender sender, String command) {
        throw new UnsupportedOperationException("Stub");
    }
}
