package net.thermedwolf.groupchat.core;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * When a player picks "message this group" or "message this player" in the
 * GUI, we can't collect free-text input from an inventory click - so instead
 * we remember what they picked, close the GUI, and let their next chat
 * message become that message instead of a normal public one. This class
 * tracks that pending state per player. In-memory only; nothing here needs
 * to survive a restart.
 */
public class ComposeSession {

    public record Pending(boolean isGroup, String targetName) {
    }

    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    public void beginGroupMessage(UUID player, String groupName) {
        pending.put(player, new Pending(true, groupName));
    }

    public void beginDirectMessage(UUID player, String targetName) {
        pending.put(player, new Pending(false, targetName));
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
