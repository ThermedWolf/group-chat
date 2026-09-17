package net.thermedwolf.groupchat.core;

import java.util.Optional;
import java.util.UUID;

/**
 * Everything the core logic needs from the underlying game platform.
 * Paper and Fabric each provide their own implementation of this.
 */
public interface PlatformBridge {

    boolean isOnline(UUID uuid);

    /** Send a message (containing '&' colour codes) to an online player. No-op if offline. */
    void sendMessage(UUID uuid, String message);

    /** Best-effort name lookup, online or offline. */
    String getName(UUID uuid);

    /** Resolve a player name to a UUID, checking online players first, then any offline cache. */
    Optional<UUID> getUuidByName(String name);
}
