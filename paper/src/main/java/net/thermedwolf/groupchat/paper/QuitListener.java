package net.thermedwolf.groupchat.paper;

import net.thermedwolf.groupchat.core.GroupChatService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class QuitListener implements Listener {

    private final GroupChatService service;

    public QuitListener(GroupChatService service) {
        this.service = service;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        service.clearToggleOnDisconnect(event.getPlayer().getUniqueId());
    }
}
