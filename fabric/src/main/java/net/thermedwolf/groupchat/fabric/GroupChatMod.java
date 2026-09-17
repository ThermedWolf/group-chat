package net.thermedwolf.groupchat.fabric;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.thermedwolf.groupchat.core.GroupChatService;
import net.thermedwolf.groupchat.fabric.gui.FabricGuiManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.File;

public class GroupChatMod implements DedicatedServerModInitializer {

    /** Package-visible so GroupChatCommands can reach it without extra wiring. */
    static GroupChatService service;
    static FabricGuiManager gui;

    @Override
    public void onInitializeServer() {
        CommandRegistrationCallback.EVENT
                .register((dispatcher, registryAccess, environment) -> GroupChatCommands.register(dispatcher));

        ServerLifecycleEvents.SERVER_STARTED.register(this::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (service != null) {
                service.saveAll();
            }
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (service == null) {
                return;
            }
            ServerPlayer player = handler.player;
            int unread = service.getUnreadCount(player.getUUID());
            if (unread > 0) {
                player.sendSystemMessage(Component.literal("\u00A7e[GroupChat] \u00A77You have \u00A7e" + unread
                        + " \u00A77unread message" + (unread == 1 ? "" : "s")
                        + ". Type \u00A7e/unread \u00A77to view them."));
            }

            String inviteNotice = service.getPendingInviteNotice(player.getUUID());
            if (inviteNotice != null) {
                player.sendSystemMessage(
                        Component.literal(net.thermedwolf.groupchat.core.ChatFormat.color(inviteNotice)));
            }
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            if (service != null) {
                service.clearToggleOnDisconnect(handler.player.getUUID());
            }
        });

        // Confirmed against the actual fabric-message-api-v1 jar: the event field
        // is ALLOW_CHAT_MESSAGE and the callback signature is
        // allowChatMessage(PlayerChatMessage, ServerPlayer, ChatType.Bound) -> boolean.
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, senderPlayer, params) -> {
            if (service == null) {
                return true;
            }
            // unified intercept: pending history/toggle selection, then compose, then toggle
            java.util.UUID uuid = senderPlayer.getUUID();
            if (!service.isComposing(uuid) && !service.isToggled(uuid) && !service.isPendingToggleSelection(uuid) && !service.isPendingHistorySelection(uuid)) {
                return true;
            }
            String plain = message.signedContent();
            boolean consumed = service.handleChatIntercept(uuid, plain);
            return !consumed;
        });
    }

    private void onServerStarted(MinecraftServer server) {
        // Fabric's most reliable persistent location is the game dir (server root).
        // Earlier versions used server.getServerDirectory() which on some launchers
        // resolves to an empty/relative path (e.g. ""), causing data to be written
        // to an ephemeral CWD and appear to vanish after a restart (Paper uses
        // plugins/GroupChat correctly, so it worked). Now we resolve via FabricLoader
        // and migrate any legacy data.
        File dataFolder = resolveDataFolder(server);
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        server.sendSystemMessage(Component.literal("[GroupChat] data folder: " + dataFolder.getAbsolutePath()));
        service = new GroupChatService(dataFolder, new FabricPlatformBridge(server));
        gui = new FabricGuiManager(service);
    }

    private static File resolveDataFolder(MinecraftServer server) {
        java.nio.file.Path gameDir = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir();
        File preferred = gameDir.resolve("groupchat").toFile();
        // Legacy path: server.getServerDirectory()/groupchat (may be relative/empty on some setups)
        File legacy = new File(server.getServerDirectory().toFile(), "groupchat");
        // Also check world/groupchat (in case data was written per-world) and config/groupchat
        File configDir = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("groupchat").toFile();
        java.nio.file.Path worldPath = null;
        try {
            worldPath = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
        } catch (Exception ignored) {}
        File worldGroupchat = worldPath != null ? worldPath.resolve("groupchat").toFile() : null;

        // Pick first existing folder that actually contains data, otherwise preferred
        File[] candidates = new File[]{preferred, legacy, configDir, worldGroupchat};
        File existingWithData = null;
        for (File c : candidates) {
            if (c != null && c.isDirectory()) {
                File groups = new File(c, "groups.json");
                if (groups.exists() && groups.length() > 2) {
                    existingWithData = c;
                    break;
                }
            }
        }
        File chosen = existingWithData != null ? existingWithData : preferred;
        // Migrate legacy -> preferred if preferred empty but legacy has data and they're different paths
        if (existingWithData != null && !chosen.equals(preferred) && existingWithData != preferred) {
            try {
                if (!preferred.exists()) preferred.mkdirs();
                for (File src : new File[]{new File(existingWithData, "groups.json"), new File(existingWithData, "messages.json"), new File(existingWithData, "history.json")}) {
                    File dst = new File(preferred, src.getName());
                    if (src.exists() && !dst.exists()) {
                        java.nio.file.Files.copy(src.toPath(), dst.toPath());
                    }
                }
                // After migration, keep using preferred so future saves are stable
                chosen = preferred;
            } catch (Exception ignored) {}
        }
        return chosen;
    }
}