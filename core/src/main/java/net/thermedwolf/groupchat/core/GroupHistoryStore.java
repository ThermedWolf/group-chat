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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Per-player per-group rolling history of the last {@value #MAX_HISTORY_PER_GROUP}
 * messages (both sent and received). Persisted to history.json.
 *
 * <p>Structure: Map&lt;playerUuidString, Map&lt;lowerGroupName, Deque&lt;GroupHistoryEntry&gt;&gt;&gt;
 */
public class GroupHistoryStore {

    private static final Logger LOGGER = Logger.getLogger(GroupHistoryStore.class.getName());

    public static final int MAX_HISTORY_PER_GROUP = 5;
    public static final int MAX_GROUPS_PER_PLAYER = 200; // safety cap

    private final File file;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    // In-memory ring per player per group
    private final Map<String, Map<String, Deque<GroupHistoryEntry>>> history = new HashMap<>();

    public GroupHistoryStore(File dataFolder) {
        this.file = new File(dataFolder, "history.json");
        load();
    }

    public synchronized void addMessage(UUID recipient, String groupName, GroupHistoryEntry entry) {
        if (recipient == null || groupName == null || entry == null) {
            return;
        }
        String playerKey = recipient.toString();
        String groupKey = groupName.toLowerCase(Locale.ROOT);
        Map<String, Deque<GroupHistoryEntry>> perGroup =
                history.computeIfAbsent(playerKey, k -> new HashMap<>());
        // cap groups per player to avoid unbounded growth if many group names
        if (!perGroup.containsKey(groupKey) && perGroup.size() >= MAX_GROUPS_PER_PLAYER) {
            return;
        }
        Deque<GroupHistoryEntry> deque = perGroup.computeIfAbsent(groupKey, k -> new ArrayDeque<>());
        deque.addLast(entry);
        while (deque.size() > MAX_HISTORY_PER_GROUP) {
            deque.removeFirst();
        }
        save();
    }

    public synchronized List<GroupHistoryEntry> getHistory(UUID player, String groupName) {
        if (player == null || groupName == null) {
            return List.of();
        }
        Map<String, Deque<GroupHistoryEntry>> perGroup = history.get(player.toString());
        if (perGroup == null) {
            return List.of();
        }
        Deque<GroupHistoryEntry> deque = perGroup.get(groupName.toLowerCase(Locale.ROOT));
        if (deque == null || deque.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(deque);
    }

    public synchronized void clearGroup(UUID player, String groupName) {
        // not currently used but handy when group deleted
        Map<String, Deque<GroupHistoryEntry>> perGroup = history.get(player.toString());
        if (perGroup != null) {
            perGroup.remove(groupName.toLowerCase(Locale.ROOT));
            if (perGroup.isEmpty()) {
                history.remove(player.toString());
            }
            save();
        }
    }

    private synchronized void load() {
        if (!file.exists()) {
            return;
        }
        try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            Type type = new TypeToken<Map<String, Map<String, List<GroupHistoryEntry>>>>() {}.getType();
            Map<String, Map<String, List<GroupHistoryEntry>>> loaded = gson.fromJson(reader, type);
            if (loaded == null) {
                return;
            }
            Map<String, Map<String, Deque<GroupHistoryEntry>>> validated = new HashMap<>();
            for (Map.Entry<String, Map<String, List<GroupHistoryEntry>>> playerEntry : loaded.entrySet()) {
                String playerKey = playerEntry.getKey();
                try {
                    UUID.fromString(playerKey);
                } catch (IllegalArgumentException ex) {
                    LOGGER.log(Level.WARNING, "Dropping history with invalid UUID key: {0}", playerKey);
                    continue;
                }
                Map<String, List<GroupHistoryEntry>> groupMap = playerEntry.getValue();
                if (groupMap == null) continue;
                Map<String, Deque<GroupHistoryEntry>> validatedGroups = new HashMap<>();
                for (Map.Entry<String, List<GroupHistoryEntry>> grp : groupMap.entrySet()) {
                    String gKey = grp.getKey();
                    if (gKey == null || gKey.isBlank()) continue;
                    String lower = gKey.toLowerCase(Locale.ROOT);
                    List<GroupHistoryEntry> list = grp.getValue();
                    if (list == null || list.isEmpty()) continue;
                    // keep only last MAX entries and drop nulls/oversized
                    List<GroupHistoryEntry> filtered = new ArrayList<>();
                    for (GroupHistoryEntry e : list) {
                        if (e == null || e.getMessage() == null || e.getFromName() == null) continue;
                        if (e.getMessage().length() > MessageStore.MAX_MESSAGE_LENGTH) continue;
                        filtered.add(e);
                    }
                    if (filtered.size() > MAX_HISTORY_PER_GROUP) {
                        filtered = filtered.subList(filtered.size() - MAX_HISTORY_PER_GROUP, filtered.size());
                    }
                    if (!filtered.isEmpty()) {
                        validatedGroups.put(lower, new ArrayDeque<>(filtered));
                    }
                }
                if (!validatedGroups.isEmpty()) {
                    validated.put(playerKey, validatedGroups);
                }
            }
            history.clear();
            history.putAll(validated);
        } catch (IOException | JsonSyntaxException e) {
            LOGGER.log(Level.WARNING, "Failed to load history.json (corrupt). Backing up and starting empty.", e);
            backupCorruptFile();
        }
    }

    public synchronized void save() {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            File tmp = new File(parent, file.getName() + ".tmp");
            // Serialize as Map<String, Map<String, List<...>>> for readability
            Map<String, Map<String, List<GroupHistoryEntry>>> toSave = new HashMap<>();
            for (Map.Entry<String, Map<String, Deque<GroupHistoryEntry>>> pe : history.entrySet()) {
                Map<String, List<GroupHistoryEntry>> gMap = new HashMap<>();
                for (Map.Entry<String, Deque<GroupHistoryEntry>> ge : pe.getValue().entrySet()) {
                    gMap.put(ge.getKey(), new ArrayList<>(ge.getValue()));
                }
                toSave.put(pe.getKey(), gMap);
            }
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8)) {
                gson.toJson(toSave, writer);
            }
            try {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException atomicFail) {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to save history.json", e);
        }
    }

    private void backupCorruptFile() {
        try {
            File backup = new File(file.getParentFile(), file.getName() + ".corrupt." + System.currentTimeMillis() + ".bak");
            Files.copy(file.toPath(), backup.toPath());
            LOGGER.log(Level.WARNING, "Corrupt history.json backed up to {0}", backup.getName());
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "Failed to backup corrupt history.json", ex);
        }
    }
}
