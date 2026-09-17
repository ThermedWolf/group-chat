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
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }
        for (String line : service.viewAndClearUnread(player.getUniqueId())) {
            player.sendMessage(line);
        }
        return true;
    }
}
