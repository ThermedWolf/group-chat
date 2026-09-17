package net.thermedwolf.groupchat.paper;

import net.thermedwolf.groupchat.core.GroupChatService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class UnreadCommand implements CommandExecutor {

    private final GroupChatService service;

    public UnreadCommand(GroupChatService service) {
        this.service = service;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("groupchat.use")) {
            sender.sendMessage(net.thermedwolf.groupchat.core.ChatFormat.color("&cYou don't have permission to use GroupChat."));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("clear")) {
            int count = service.getUnreadCount(player.getUniqueId());
            for (String line : service.viewAndClearUnread(player.getUniqueId())) {
                player.sendMessage(line);
            }
            if (count == 0) {
                // viewAndClearUnread already sent "no unread messages"
            }
            return true;
        }
        if (args.length == 1) {
            try {
                int page = Integer.parseInt(args[0]);
                for (String line : service.viewUnreadPage(player.getUniqueId(), page)) {
                    player.sendMessage(line);
                }
                return true;
            } catch (NumberFormatException ignored) {
                // fall through to default clear behavior
            }
        }
        for (String line : service.viewAndClearUnread(player.getUniqueId())) {
            player.sendMessage(line);
        }
        return true;
    }
}
