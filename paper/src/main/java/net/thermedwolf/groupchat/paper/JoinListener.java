package net.thermedwolf.groupchat.paper;

import net.thermedwolf.groupchat.core.ChatFormat;
import net.thermedwolf.groupchat.core.GroupChatService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class JoinListener implements Listener {

    private final GroupChatService service;

    public JoinListener(GroupChatService service) {
        this.service = service;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        int unread = service.getUnreadCount(event.getPlayer().getUniqueId());
        if (unread > 0) {
            event.getPlayer().sendMessage(ChatFormat.color(
                    "&e[GroupChat] &7You have &e" + unread + " &7unread message" + (unread == 1 ? "" : "s")
                            + ". Type &e/unread &7to view them."));
        }

        String inviteNotice = service.getPendingInviteNotice(event.getPlayer().getUniqueId());
        if (inviteNotice != null) {
            event.getPlayer().sendMessage(ChatFormat.color(inviteNotice));
        }
    }
}