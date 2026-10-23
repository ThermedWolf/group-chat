package net.thermedwolf.groupchat.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Admin-configured messages shown to players when they join the server,
 * persisted to joinmessages.json together with the cooldown setting and the
 * per-player "last shown" timestamps that implement it.
 *
 * <p>Cooldown semantics: a player who has seen the messages within the last
 * {@code cooldownMinutes} minutes does not see them again. The timestamp only
 * moves forward when the messages are actually shown, so "joins three times
 * within 30 minutes, sees the message once" is cooldownMinutes=30.
 */
public class JoinMessageStore {

    private static final Logger LOGGER = Logger.getLogger(JoinMessageStore.class.getName());

    public static final int MAX_MESSAGES = 10;
    public static final int MAX_MESSAGE_LENGTH = 300;
    public static final int MAX_COOLDOWN_MINUTES = 10_080; // one week
    private static final long SEEN_PRUNE_MILLIS = 30L * 24 * 60 * 60 * 1000;

    private final File file;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final List<String> messages = new ArrayList<>();
    private final Map<String, Long> lastShown = new HashMap<>();
    private int cooldownMinutes = 0;

    /** Gson persistence shape - kept in one place so the JSON stays inspectable. */
    private static class Persisted {
        List<String> messages = new ArrayList<>();
        int cooldownMinutes = 0;
        Map<String, Long> lastShown = new HashMap<>();
    }

    public JoinMessageStore(File dataFolder) {
        this.file = new File(dataFolder, "joinmessages.json");
        load();
    }

    public synchronized int getCooldownMinutes() {
        return cooldownMinutes;
    }

    public synchronized void setCooldownMinutes(int minutes) {
        this.cooldownMinutes = Math.max(0, Math.min(MAX_COOLDOWN_MINUTES, minutes));
        save();
    }

    public synchronized List<String> getMessages() {
        return new ArrayList<>(messages);
    }

    public synchronized int getMessageCount() {
        return messages.size();
    }

    /** @return true if added, false if the list is already at its cap. */
    public synchronized boolean add(String message) {
        if (messages.size() >= MAX_MESSAGES) {
            return false;
        }
        messages.add(message);
        save();
        return true;
    }

    /** @return true if removed, false if the index is out of range. */
    public synchronized boolean removeAt(int index) {
        if (index < 0 || index >= messages.size()) {
            return false;
        }
        messages.remove(index);
        save();
        return true;
    }

    public synchronized void clear() {
        messages.clear();
        save();
    }

    /**
     * Decides whether the join messages should be shown to this player right
     * now, and if so records the show time. Returns false while the player is
     * still inside the cooldown window.
     */
    public synchronized boolean tryMarkShown(UUID player, long now) {
        if (cooldownMinutes <= 0) {
            // No cooldown configured - don't track "last shown", so enabling
            // a cooldown later starts everyone fresh (as documented).
            return true;
        }
        Long last = lastShown.get(player.toString());
        if (last != null && now - last < cooldownMinutes * 60_000L) {
            return false;
        }
        lastShown.put(player.toString(), now);
        save();
        return true;
    }

    private synchronized void load() {
        if (!file.exists()) {
            return;
        }
        try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            Persisted loaded = gson.fromJson(reader, Persisted.class);
            if (loaded != null) {
                messages.clear();
                for (String message : loaded.messages) {
                    if (message != null && !message.isBlank() && message.length() <= MAX_MESSAGE_LENGTH) {
                        messages.add(message);
                    } else {
                        LOGGER.warning("Dropped invalid join message from joinmessages.json");
                    }
                }
                cooldownMinutes = Math.max(0, Math.min(MAX_COOLDOWN_MINUTES, loaded.cooldownMinutes));
                long cutoff = System.currentTimeMillis() - SEEN_PRUNE_MILLIS;
                lastShown.clear();
                for (Map.Entry<String, Long> entry : loaded.lastShown.entrySet()) {
                    if (entry.getValue() != null && entry.getValue() >= cutoff) {
                        lastShown.put(entry.getKey(), entry.getValue());
                    }
                }
            }
        } catch (IOException | JsonSyntaxException e) {
            LOGGER.log(Level.WARNING, "Failed to load joinmessages.json (corrupt or unreadable). Starting empty.", e);
            backupCorruptFile();
        }
    }

    public synchronized void save() {
        try {
            Persisted out = new Persisted();
            out.messages = messages;
            out.cooldownMinutes = cooldownMinutes;
            out.lastShown = lastShown;
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            File tmp = new File(parent, file.getName() + ".tmp");
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8)) {
                gson.toJson(out, writer);
            }
            try {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException atomicFail) {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to save joinmessages.json", e);
        }
    }

    private void backupCorruptFile() {
        try {
            File backup = new File(file.getParentFile(), file.getName() + ".corrupt." + System.currentTimeMillis() + ".bak");
            Files.copy(file.toPath(), backup.toPath());
            LOGGER.log(Level.WARNING, "Corrupt joinmessages.json backed up to {0}", backup.getName());
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "Failed to backup corrupt joinmessages.json", ex);
        }
    }
}
