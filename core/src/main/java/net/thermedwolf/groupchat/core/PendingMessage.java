package net.thermedwolf.groupchat.core;

import java.util.UUID;

/**
 * A message left for an offline player. {@code context} is the group name
 * it was sent through, or {@code null} for a direct/private message.
 * {@code type} distinguishes how the message should be rendered; entries
 * persisted before types existed have {@code type == null} and fall back
 * based on {@code context}.
 */
public class PendingMessage {

    public static final String TYPE_GROUP = "group";
    public static final String TYPE_DM = "dm";
    public static final String TYPE_ANNOUNCEMENT = "announcement";

    private final UUID fromUuid;
    private final String fromName;
    private final String context;
    private final String message;
    private final long timestamp;
    private final String type;

    public PendingMessage(UUID fromUuid, String fromName, String context, String message, long timestamp) {
        this(fromUuid, fromName, context, message, timestamp, context != null ? TYPE_GROUP : TYPE_DM);
    }

    public PendingMessage(UUID fromUuid, String fromName, String context, String message, long timestamp, String type) {
        this.fromUuid = fromUuid;
        this.fromName = fromName;
        this.context = context;
        this.message = message;
        this.timestamp = timestamp;
        this.type = type;
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

    /** Type with fallback for entries persisted before this field existed. */
    public String getType() {
        if (type != null) {
            return type;
        }
        return context != null ? TYPE_GROUP : TYPE_DM;
    }
}
