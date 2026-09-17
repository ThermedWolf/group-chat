package net.thermedwolf.groupchat.core;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks per-player toggle state: when enabled, normal chat is routed to that group.
 * In-memory only; cleared on disconnect or cancel.
 */
public class ToggleSession {

    private final Map<UUID, String> toggled = new ConcurrentHashMap<>();

    /**
     * Toggle group chat for a player. If they were already toggled to the same group, disable.
     * If they were toggled to a different group, switch. If not toggled, enable.
     *
     * @return true if now enabled, false if now disabled
     */
    public boolean toggle(UUID player, String groupName) {
        String normalized = groupName; // preserve display casing
        String existing = toggled.get(player);
        if (existing != null && existing.equalsIgnoreCase(normalized)) {
            toggled.remove(player);
            return false;
        }
        toggled.put(player, normalized);
        return true;
    }

    public Optional<String> getGroup(UUID player) {
        return Optional.ofNullable(toggled.get(player));
    }

    public boolean isToggled(UUID player) {
        return toggled.containsKey(player);
    }

    public void clear(UUID player) {
        toggled.remove(player);
    }

    public void clearForGroup(String groupName) {
        String lower = groupName.toLowerCase(Locale.ROOT);
        toggled.entrySet().removeIf(e -> e.getValue().toLowerCase(Locale.ROOT).equals(lower));
    }
}
