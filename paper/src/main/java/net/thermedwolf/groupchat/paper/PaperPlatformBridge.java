package net.thermedwolf.groupchat.paper;

import net.thermedwolf.groupchat.core.ChatFormat;
import net.thermedwolf.groupchat.core.PlatformBridge;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;

public class PaperPlatformBridge implements PlatformBridge {

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
    public String getName(UUID uuid) {
        OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        String name = offline.getName();
        return name != null ? name : uuid.toString().substring(0, 8);
    }

    @Override
    @SuppressWarnings("deprecation") // getOfflinePlayer(String) is the only sync lookup available
    public Optional<UUID> getUuidByName(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return Optional.of(online.getUniqueId());
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        if (offline.hasPlayedBefore() || offline.isOnline()) {
            return Optional.of(offline.getUniqueId());
        }
        return Optional.empty();
    }
}
