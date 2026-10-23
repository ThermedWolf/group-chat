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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Registry of every player who has joined while the plugin/mod was loaded,
 * persisted to known-players.json. Server-wide announcements need the full
 * set of players (online and offline) to deliver to, and the core has no
 * platform-independent way to enumerate them: Paper has
 * Bukkit.getOfflinePlayers() but Fabric has no equivalent. Instead both
 * platforms simply record joins here, so the recipient set builds itself.
 * Players who joined before this feature existed are picked up the next
 * time they join.
 */
public class KnownPlayerStore {

    private static final Logger LOGGER = Logger.getLogger(KnownPlayerStore.class.getName());

    private final File file;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Map<String, Long> lastSeen = new HashMap<>();

    public KnownPlayerStore(File dataFolder) {
        this.file = new File(dataFolder, "known-players.json");
        load();
    }

    public synchronized void record(UUID uuid) {
        lastSeen.put(uuid.toString(), System.currentTimeMillis());
        save();
    }

    public synchronized Set<UUID> getAll() {
        Set<UUID> players = new HashSet<>();
        for (String key : lastSeen.keySet()) {
            try {
                players.add(UUID.fromString(key));
            } catch (IllegalArgumentException ex) {
                LOGGER.log(Level.WARNING, "Skipping invalid UUID key in known-players.json: {0}", key);
            }
        }
        return players;
    }

    private synchronized void load() {
        if (!file.exists()) {
            return;
        }
        try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            Type type = new TypeToken<Map<String, Long>>() {}.getType();
            Map<String, Long> loaded = gson.fromJson(reader, type);
            if (loaded != null) {
                lastSeen.clear();
                lastSeen.putAll(loaded);
            }
        } catch (IOException | JsonSyntaxException e) {
            LOGGER.log(Level.WARNING, "Failed to load known-players.json (corrupt or unreadable). Starting empty.", e);
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
                gson.toJson(lastSeen, writer);
            }
            try {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException atomicFail) {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to save known-players.json", e);
        }
    }

    private void backupCorruptFile() {
        try {
            File backup = new File(file.getParentFile(), file.getName() + ".corrupt." + System.currentTimeMillis() + ".bak");
            Files.copy(file.toPath(), backup.toPath());
            LOGGER.log(Level.WARNING, "Corrupt known-players.json backed up to {0}", backup.getName());
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "Failed to backup corrupt known-players.json", ex);
        }
    }
}
