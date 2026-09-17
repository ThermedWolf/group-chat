package net.thermedwolf.groupchat.core;

import java.util.UUID;

/**
 * One entry in a player's per-group rolling history (last 5, persisted).
 */
public class GroupHistoryEntry {

    private final UUID fromUuid;
    private final String fromName;
    private final String message;
    private final long timestamp;

    public GroupHistoryEntry(UUID fromUuid, String fromName, String message, long timestamp) {
        this.fromUuid = fromUuid;
        this.fromName = fromName;
        this.message = message;
        this.timestamp = timestamp;
    }

    public UUID getFromUuid() {
        return fromUuid;
    }

    public String getFromName() {
        return fromName;
    }

    public String getMessage() {
        return message;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
