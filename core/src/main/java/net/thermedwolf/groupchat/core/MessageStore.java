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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Per-player mailbox of {@link PendingMessage}s, persisted to messages.json.
 * Keyed by UUID string (not raw UUID) so Gson serialization stays simple
 * and human-inspectable.
 */
public class MessageStore {

    private static final Logger LOGGER = Logger.getLogger(MessageStore.class.getName());

    public static final int MAX_MAILBOX_SIZE = 200;
    public static final int MAX_MESSAGE_LENGTH = 500;

    private final File file;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Map<String, List<PendingMessage>> pending = new HashMap<>();

    public MessageStore(File dataFolder) {
        this.file = new File(dataFolder, "messages.json");
        load();
    }

    /**
     * @return true if queued, false if rejected (mailbox full).
     */
    public synchronized boolean addMessage(UUID recipient, PendingMessage message) {
        String key = recipient.toString();
        List<PendingMessage> list = pending.computeIfAbsent(key, k -> new ArrayList<>());
        if (list.size() >= MAX_MAILBOX_SIZE) {
            return false;
        }
        // Defensive: also cap oversized payloads here even though service validates
        if (message.getMessage() != null && message.getMessage().length() > MAX_MESSAGE_LENGTH) {
            return false;
        }
        list.add(message);
        save();
        return true;
    }

    public synchronized List<PendingMessage> getMessages(UUID recipient) {
        return new ArrayList<>(pending.getOrDefault(recipient.toString(), Collections.emptyList()));
    }

    public synchronized int getUnreadCount(UUID recipient) {
        return pending.getOrDefault(recipient.toString(), Collections.emptyList()).size();
    }

    public synchronized void clearMessages(UUID recipient) {
        pending.remove(recipient.toString());
        save();
    }

    /**
     * Get a paginated view without clearing. Page is 0-indexed, 10 per page.
     */
    public synchronized List<PendingMessage> getMessagesPaged(UUID recipient, int page, int pageSize) {
        List<PendingMessage> all = pending.getOrDefault(recipient.toString(), Collections.emptyList());
        int from = page * pageSize;
        if (from >= all.size()) {
            return List.of();
        }
        int to = Math.min(from + pageSize, all.size());
        return new ArrayList<>(all.subList(from, to));
    }

    private synchronized void load() {
        if (!file.exists()) {
            return;
        }
        try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            Type type = new TypeToken<Map<String, List<PendingMessage>>>() {}.getType();
            Map<String, List<PendingMessage>> loaded = gson.fromJson(reader, type);
            if (loaded != null) {
                // Validate and cap each mailbox on load (handles old corrupt/oversized files)
                Map<String, List<PendingMessage>> validated = new HashMap<>();
                for (Map.Entry<String, List<PendingMessage>> entry : loaded.entrySet()) {
                    String key = entry.getKey();
                    try {
                        UUID.fromString(key);
                    } catch (IllegalArgumentException ex) {
                        LOGGER.log(Level.WARNING, "Dropping mailbox with invalid UUID key: {0}", key);
                        continue;
                    }
                    List<PendingMessage> msgs = entry.getValue();
                    if (msgs == null) {
                        continue;
                    }
                    // Prune expired (>30 days) and oversized entries
                    long cutoff = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000;
                    msgs.removeIf(m -> m == null || m.getMessage() == null || m.getTimestamp() < cutoff
                            || m.getMessage().length() > MAX_MESSAGE_LENGTH);
                    if (msgs.size() > MAX_MAILBOX_SIZE) {
                        msgs = new ArrayList<>(msgs.subList(msgs.size() - MAX_MAILBOX_SIZE, msgs.size()));
                        LOGGER.log(Level.WARNING, "Truncated oversized mailbox for {0} to {1} entries", new Object[]{key, MAX_MAILBOX_SIZE});
                    }
                    if (!msgs.isEmpty()) {
                        validated.put(key, msgs);
                    }
                }
                pending.clear();
                pending.putAll(validated);
            }
        } catch (IOException | JsonSyntaxException e) {
            LOGGER.log(Level.WARNING, "Failed to load messages.json (corrupt or unreadable). Backing up and starting empty.", e);
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
                gson.toJson(pending, writer);
            }
            try {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException atomicFail) {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to save messages.json", e);
        }
    }

    private void backupCorruptFile() {
        try {
            File backup = new File(file.getParentFile(), file.getName() + ".corrupt." + System.currentTimeMillis() + ".bak");
            Files.copy(file.toPath(), backup.toPath());
            LOGGER.log(Level.WARNING, "Corrupt messages.json backed up to {0}", backup.getName());
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "Failed to backup corrupt messages.json", ex);
        }
    }
}
