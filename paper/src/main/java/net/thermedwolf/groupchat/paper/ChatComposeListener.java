package net.thermedwolf.groupchat.paper;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.thermedwolf.groupchat.core.GroupChatService;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

public class ChatComposeListener implements Listener {

    private final GroupChatService service;
    private final Plugin plugin;

    public ChatComposeListener(GroupChatService service, Plugin plugin) {
        this.service = service;
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        java.util.UUID uuid = event.getPlayer().getUniqueId();
        if (!service.isComposing(uuid) && !service.isToggled(uuid) && !service.isPendingToggleSelection(uuid) && !service.isPendingHistorySelection(uuid)) {
            return;
        }
        // We're taking over this message - stop it from posting publicly.
        event.setCancelled(true);
        String plain = PlainTextComponentSerializer.plainText().serialize(event.message());

        // The service call touches shared data structures and sends messages -
        // hop back to the main thread rather than doing it from the async chat thread.
        Bukkit.getScheduler().runTask(plugin, () -> {
            // unified handler clears toggle/compose on "cancel" as needed, and routes toggled chat to group
            boolean consumed = service.handleChatIntercept(uuid, plain);
            // if not consumed (should not happen when isComposing/isToggled was true), at least ensure cancel clears
            if (!consumed && plain.trim().equalsIgnoreCase("cancel")) {
                service.clearToggle(uuid);
            }
        });
    }
}
