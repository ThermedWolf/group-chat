package net.thermedwolf.groupchat.paper;

import net.thermedwolf.groupchat.core.ChatFormat;
import net.thermedwolf.groupchat.core.PlatformBridge;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PaperPlatformBridge implements PlatformBridge {

    private final ConcurrentHashMap<String, UUID> nameToUuidCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, String> uuidToNameCache = new ConcurrentHashMap<>();

    @Override
    public boolean isOnline(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        return player != null && player.isOnline();
    }

    @Override
    public void sendMessage(UUID uuid, String message) {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            player.sendMessage(ChatFormat.color(message));
        }
    }

    @Override
    public void sendHighlight(UUID uuid, String message) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) {
            return;
        }
        // Message as subtitle (empty main title) - mid-screen emphasis without
        // a big title block covering the view. Text arrives pre-translated to
        // section-sign codes; parse it with the legacy serializer so the codes
        // render as real styles (Adventure components don't interpret them).
        player.showTitle(Title.title(
                Component.empty(),
                LegacyComponentSerializer.legacySection().deserialize(message),
                Title.Times.times(Duration.ofMillis(500), Duration.ofMillis(3500), Duration.ofMillis(1000))));
        // Entity-targeted overload: only this player hears the sound (the
        // location overload would let anyone nearby hear it too).
        player.playSound(player, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
    }

    @Override
    public List<UUID> getOnlinePlayerUuids() {
        return Bukkit.getOnlinePlayers().stream().map(Player::getUniqueId).toList();
    }

    @Override
    public String getName(UUID uuid) {
        // Check cache first — avoids blocking Bukkit.getOfflinePlayer on hot paths
        String cached = uuidToNameCache.get(uuid);
        if (cached != null) {
            return cached;
        }
        // Online players never hit disk
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            String name = online.getName();
            uuidToNameCache.put(uuid, name);
            nameToUuidCache.put(name.toLowerCase(java.util.Locale.ROOT), uuid);
            return name;
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        String name = offline.getName();
        String result = name != null ? name : uuid.toString().substring(0, 8);
        if (name != null) {
            uuidToNameCache.put(uuid, result);
        }
        return result;
    }

    @Override
    @SuppressWarnings("deprecation") // getOfflinePlayer(String) is the only sync lookup available
    public Optional<UUID> getUuidByName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        String key = name.toLowerCase(java.util.Locale.ROOT);
        UUID cached = nameToUuidCache.get(key);
        if (cached != null) {
            return Optional.of(cached);
        }
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            UUID uuid = online.getUniqueId();
            nameToUuidCache.put(key, uuid);
            uuidToNameCache.put(uuid, online.getName());
            return Optional.of(uuid);
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        if (offline.hasPlayedBefore() || offline.isOnline()) {
            UUID uuid = offline.getUniqueId();
            nameToUuidCache.put(key, uuid);
            if (offline.getName() != null) {
                uuidToNameCache.put(uuid, offline.getName());
            }
            return Optional.of(uuid);
        }
        return Optional.empty();
    }
}
