package net.thermedwolf.groupchat.core;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A chat group. Stored with a display name (case preserved) while
 * {@link GroupManager} keys groups internally by lowercase name.
 */
public class Group {

    private final String name;
    private UUID owner;
    private final Set<UUID> members = new LinkedHashSet<>();
    private final Set<UUID> invites = new LinkedHashSet<>();

    public Group(String name, UUID owner) {
        this.name = name;
        this.owner = owner;
        this.members.add(owner);
    }

    public String getName() {
        return name;
    }

    public UUID getOwner() {
        return owner;
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
    }

    public Set<UUID> getMembers() {
        return members;
    }

    public Set<UUID> getInvites() {
        return invites;
    }

    public boolean isMember(UUID uuid) {
        return members.contains(uuid);
    }

    public boolean isInvited(UUID uuid) {
        return invites.contains(uuid);
    }
}
