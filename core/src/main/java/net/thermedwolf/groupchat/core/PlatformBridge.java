package net.thermedwolf.groupchat.core;

import java.util.List;
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

    /**
     * Emphasized "highlight" delivery of an announcement to an online player -
     * on top of the normal chat line. The message arrives already translated
     * to section-sign codes. Implementations typically show it as a screen
     * title or overlay and may play a sound. No-op if offline.
     */
    void sendHighlight(UUID uuid, String message);

    /** UUIDs of every player currently online. */
    List<UUID> getOnlinePlayerUuids();

    /**
     * Whether this player counts as a GroupChat admin (groupchat.admin on
     * Paper, gamemaster permission level on Fabric). Controls whether the
     * admin tools appear in the GUI; the commands themselves gate separately.
     */
    boolean isAdmin(UUID uuid);

    /** Best-effort name lookup, online or offline. */
    String getName(UUID uuid);

    /** Resolve a player name to a UUID, checking online players first, then any offline cache. */
    Optional<UUID> getUuidByName(String name);
}
