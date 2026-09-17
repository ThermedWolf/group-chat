package net.thermedwolf.groupchat.fabric;

import net.thermedwolf.groupchat.core.ChatFormat;
import net.thermedwolf.groupchat.core.PlatformBridge;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;

import java.util.Optional;
import java.util.UUID;

/**
 * Confirmed against the actual 26.1.2 Minecraft jar (inspected the compiled
 * class files directly) - not a guess this time. Offline name/UUID lookups
 * go through MinecraftServer#services()#nameToIdCache(), a
 * UserNameToIdResolver whose get(String)/get(UUID) both return
 * Optional<NameAndId> - a record with .id() -> UUID and .name() -> String.
 * This is what replaced the old getProfileCache()/GameProfileCache pairing
 * from pre-26.1 Minecraft, and it doesn't require touching
 * com.mojang.authlib.GameProfile at all for our purposes.
 */
public class FabricPlatformBridge implements PlatformBridge {

    private final MinecraftServer server;

    public FabricPlatformBridge(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public boolean isOnline(UUID uuid) {
        return server.getPlayerList().getPlayer(uuid) != null;
    }

    @Override
    public void sendMessage(UUID uuid, String message) {
        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        if (player != null) {
            player.sendSystemMessage(Component.literal(ChatFormat.color(message)));
        }
    }

    @Override
    public String getName(UUID uuid) {
        ServerPlayer online = server.getPlayerList().getPlayer(uuid);
        if (online != null) {
            return online.getName().getString();
        }
        Optional<NameAndId> cached = server.services().nameToIdCache().get(uuid);
        if (cached.isPresent()) {
            return cached.get().name();
        }
        return uuid.toString().substring(0, 8);
    }

    @Override
    public Optional<UUID> getUuidByName(String name) {
        ServerPlayer online = server.getPlayerList().getPlayerByName(name);
        if (online != null) {
            return Optional.of(online.getUUID());
        }
        return server.services().nameToIdCache().get(name).map(NameAndId::id);
    }
}
