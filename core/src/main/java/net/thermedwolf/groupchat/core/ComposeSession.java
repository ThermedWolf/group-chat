package net.thermedwolf.groupchat.core;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * When a player picks "message this group", "message this player", "send an
 * announcement" or "add a join message" in the GUI, we can't collect free-text
 * input from an inventory click - so instead we remember what they picked,
 * close the GUI, and let their next chat message become that action instead
 * of a normal public one. This class tracks that pending state per player.
 * In-memory only; nothing here needs to survive a restart.
 */
public class ComposeSession {

    /** What the next chat message will be used for. */
    public enum Target {
        GROUP,
        DM,
        ANNOUNCE,
        ANNOUNCE_HIGHLIGHT,
        JOIN_MESSAGE
    }

    /**
     * {@code target} is the group/player name for GROUP and DM; unused for the
     * other types.
     */
    public record Pending(Target type, String target) {
        public boolean isHighlight() {
            return type == Target.ANNOUNCE_HIGHLIGHT;
        }
    }

    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    public void beginGroupMessage(UUID player, String groupName) {
        pending.put(player, new Pending(Target.GROUP, groupName));
    }

    public void beginDirectMessage(UUID player, String targetName) {
        pending.put(player, new Pending(Target.DM, targetName));
    }

    public void beginAnnouncement(UUID player, boolean highlight) {
        pending.put(player, new Pending(highlight ? Target.ANNOUNCE_HIGHLIGHT : Target.ANNOUNCE, null));
    }

    public void beginJoinMessage(UUID player) {
        pending.put(player, new Pending(Target.JOIN_MESSAGE, null));
    }

    public boolean isComposing(UUID player) {
        return pending.containsKey(player);
    }

    public void cancel(UUID player) {
        pending.remove(player);
    }

    public Optional<Pending> consume(UUID player) {
        return Optional.ofNullable(pending.remove(player));
    }
}
