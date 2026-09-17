package net.thermedwolf.groupchat.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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

    private final File file;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Map<String, List<PendingMessage>> pending = new HashMap<>();

    public MessageStore(File dataFolder) {
        this.file = new File(dataFolder, "messages.json");
        load();
    }

    public synchronized void addMessage(UUID recipient, PendingMessage message) {
        pending.computeIfAbsent(recipient.toString(), k -> new ArrayList<>()).add(message);
        save();
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

    private synchronized void load() {
        if (!file.exists()) {
            return;
        }
        try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            Type type = new TypeToken<Map<String, List<PendingMessage>>>() {}.getType();
            Map<String, List<PendingMessage>> loaded = gson.fromJson(reader, type);
            if (loaded != null) {
                pending.clear();
                pending.putAll(loaded);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public synchronized void save() {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
                gson.toJson(pending, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
