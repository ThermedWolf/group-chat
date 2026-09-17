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

        // Confirmed against the actual fabric-message-api-v1 jar: the event field
        // is ALLOW_CHAT_MESSAGE and the callback signature is
        // allowChatMessage(PlayerChatMessage, ServerPlayer, ChatType.Bound) -> boolean.
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, senderPlayer, params) -> {
            if (service == null || !service.isComposing(senderPlayer.getUUID())) {
                return true;
            }
            String plain = message.signedContent();
            service.tryHandleChatAsCompose(senderPlayer.getUUID(), plain);
            return false;
        });
    }

    private void onServerStarted(MinecraftServer server) {
        // getServerDirectory() returns a Path in 26.1.2 (confirmed by the compiler,
        // not a guess) - .toFile() bridges it to the java.io.File the rest of the
        // core module expects.
        File dataFolder = new File(server.getServerDirectory().toFile(), "groupchat");
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        service = new GroupChatService(dataFolder, new FabricPlatformBridge(server));
        gui = new FabricGuiManager(service);
    }
}