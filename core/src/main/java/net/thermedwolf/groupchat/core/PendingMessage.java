package net.thermedwolf.groupchat.core;

import java.util.UUID;

/**
 * A message left for an offline player. {@code context} is the group name
 * it was sent through, or {@code null} for a direct/private message.
 */
public class PendingMessage {

    private final UUID fromUuid;
    private final String fromName;
    private final String context;
    private final String message;
    private final long timestamp;

    public PendingMessage(UUID fromUuid, String fromName, String context, String message, long timestamp) {
        this.fromUuid = fromUuid;
        this.fromName = fromName;
        this.context = context;
        this.message = message;
        this.timestamp = timestamp;
    }

    public UUID getFromUuid() {
        return fromUuid;
    }

    public String getFromName() {
        return fromName;
    }

    public String getContext() {
        return context;
    }

    public String getMessage() {
        return message;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
