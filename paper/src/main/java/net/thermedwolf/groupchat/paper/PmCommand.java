package net.thermedwolf.groupchat.paper;

import net.thermedwolf.groupchat.core.ChatFormat;
import net.thermedwolf.groupchat.core.CommandResult;
import net.thermedwolf.groupchat.core.GroupChatService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;

public class PmCommand implements CommandExecutor {

    private final GroupChatService service;

    public PmCommand(GroupChatService service) {
        this.service = service;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("groupchat.use")) {
            sender.sendMessage(ChatFormat.color("&cYou don't have permission to use GroupChat."));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }
        if (args.length < 2) {
            player.sendMessage(ChatFormat.color("&cUsage: /pm <player> <message>"));
            return true;
        }
        String target = args[0];
        String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        CommandResult result = service.sendDirectMessage(player.getUniqueId(), target, message);
        if (result.getMessage() != null) {
            player.sendMessage(ChatFormat.color(result.getMessage()));
        }
        return true;
    }
}
