package net.thermedwolf.groupchat.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Owns the set of groups and persists them to groups.json in the plugin/mod
 * data folder. Group names are matched case-insensitively but the original
 * casing is preserved for display.
 */
public class GroupManager {

    private static final Logger LOGGER = Logger.getLogger(GroupManager.class.getName());
    private static final String NAME_PATTERN = "[A-Za-z0-9_]{2,24}";

    private final File file;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Map<String, Group> groups = new LinkedHashMap<>();

    public GroupManager(File dataFolder) {
        this.file = new File(dataFolder, "groups.json");
        load();
    }

    public synchronized boolean createGroup(String name, UUID owner) {
        String key = name.toLowerCase(Locale.ROOT);
        if (groups.containsKey(key)) {
            return false;
        }
        groups.put(key, new Group(name, owner));
        save();
        return true;
    }

    public synchronized boolean deleteGroup(String name, UUID requester) {
        String key = name.toLowerCase(Locale.ROOT);
        Group group = groups.get(key);
        if (group == null || !group.getOwner().equals(requester)) {
            return false;
        }
        groups.remove(key);
        save();
        return true;
    }

    public synchronized boolean invite(String name, UUID target) {
        Group group = getGroup(name).orElse(null);
        if (group == null || group.isMember(target) || group.isInvited(target)) {
            return false;
        }
        group.getInvites().add(target);
        save();
        return true;
    }

    public synchronized boolean acceptInvite(String name, UUID player) {
        Group group = getGroup(name).orElse(null);
        if (group == null || !group.isInvited(player)) {
            return false;
        }
        group.getInvites().remove(player);
        group.getMembers().add(player);
        save();
        return true;
    }

    public synchronized boolean declineInvite(String name, UUID player) {
        Group group = getGroup(name).orElse(null);
        if (group == null || !group.isInvited(player)) {
            return false;
        }
        group.getInvites().remove(player);
        save();
        return true;
    }

    public synchronized boolean leaveGroup(String name, UUID player) {
        Group group = getGroup(name).orElse(null);
        if (group == null || !group.isMember(player)) {
            return false;
        }
        group.getMembers().remove(player);
        if (group.getMembers().isEmpty()) {
            groups.remove(name.toLowerCase(Locale.ROOT));
            LOGGER.log(Level.INFO, "Group ''{0}'' removed (no members left) after {1} left", new Object[]{group.getName(), player});
        } else if (group.getOwner().equals(player)) {
            UUID newOwner = group.getMembers().iterator().next();
            group.setOwner(newOwner);
            LOGGER.log(Level.INFO, "Ownership of group ''{0}'' transferred from {1} to {2}",
                    new Object[]{group.getName(), player, newOwner});
        }
        save();
        return true;
    }

    /**
     * Only the owner can kick, and the owner can't be kicked this way - use
     * delete/leave instead.
     */
    public synchronized boolean kickMember(String name, UUID target) {
        Group group = getGroup(name).orElse(null);
        if (group == null || !group.isMember(target) || group.getOwner().equals(target)) {
            return false;
        }
        group.getMembers().remove(target);
        save();
        return true;
    }

    public synchronized List<Group> getGroupsWithPendingInvite(UUID player) {
        List<Group> result = new ArrayList<>();
        for (Group group : groups.values()) {
            if (group.isInvited(player)) {
                result.add(group);
            }
        }
        return result;
    }

    public synchronized Optional<Group> getGroup(String name) {
        return Optional.ofNullable(groups.get(name.toLowerCase(Locale.ROOT)));
    }

    public synchronized List<Group> getGroupsForPlayer(UUID player) {
        List<Group> result = new ArrayList<>();
        for (Group group : groups.values()) {
            if (group.isMember(player)) {
                result.add(group);
            }
        }
        return result;
    }

    public synchronized Collection<Group> getAllGroups() {
        return Collections.unmodifiableCollection(groups.values());
    }

    private synchronized void load() {
        if (!file.exists()) {
            return;
        }
        try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            Type type = new TypeToken<Map<String, Group>>() {
            }.getType();
            Map<String, Group> loaded = gson.fromJson(reader, type);
            if (loaded != null) {
                Map<String, Group> validated = new LinkedHashMap<>();
                for (Map.Entry<String, Group> entry : loaded.entrySet()) {
                    String key = entry.getKey();
                    Group group = entry.getValue();
                    if (!isValidGroup(key, group)) {
                        LOGGER.log(Level.WARNING, "Dropping invalid group entry key=''{0}'' during load", key);
                        continue;
                    }
                    validated.put(key.toLowerCase(Locale.ROOT), group);
                }
                groups.clear();
                groups.putAll(validated);
            }
        } catch (IOException | JsonSyntaxException e) {
            LOGGER.log(Level.WARNING, "Failed to load groups.json (corrupt or unreadable). Backing up and starting empty.", e);
            backupCorruptFile();
        }
    }

    public synchronized void save() {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            File tmp = new File(parent, file.getName() + ".tmp");
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8)) {
                gson.toJson(groups, writer);
            }
            try {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException atomicFail) {
                // Fallback when ATOMIC_MOVE not supported (FAT32, network mounts)
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to save groups.json", e);
        }
    }

    private boolean isValidGroup(String key, Group group) {
        if (group == null || key == null || group.getName() == null || group.getOwner() == null) {
            return false;
        }
        if (!group.getName().matches(NAME_PATTERN)) {
            return false;
        }
        if (!key.equalsIgnoreCase(group.getName())) {
            return false;
        }
        if (group.getMembers() == null || group.getMembers().isEmpty()) {
            return false;
        }
        if (!group.getMembers().contains(group.getOwner())) {
            return false;
        }
        return true;
    }

    private void backupCorruptFile() {
        try {
            File backup = new File(file.getParentFile(), file.getName() + ".corrupt." + System.currentTimeMillis() + ".bak");
            Files.copy(file.toPath(), backup.toPath());
            LOGGER.log(Level.WARNING, "Corrupt groups.json backed up to {0}", backup.getName());
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "Failed to backup corrupt groups.json", ex);
        }
    }
}