package net.thermedwolf.groupchat.paper;

import net.thermedwolf.groupchat.core.ChatFormat;
import net.thermedwolf.groupchat.core.CommandResult;
import net.thermedwolf.groupchat.core.GroupChatService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * /announce [highlight] <message> - server-wide announcement. Works from
 * console too. Requires the groupchat.admin permission.
 */
public class AnnounceCommand implements CommandExecutor, TabCompleter {

    private static final List<String> FLAGS = List.of("highlight", "-h");

    private final GroupChatService service;

    public AnnounceCommand(GroupChatService service) {
        this.service = service;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("groupchat.admin")) {
            sender.sendMessage(ChatFormat.color("&cYou don't have permission to use this command."));
            return true;
        }
        boolean highlight = false;
        int startIndex = 0;
        if (args.length >= 1 && FLAGS.stream().anyMatch(flag -> flag.equalsIgnoreCase(args[0]))) {
            highlight = true;
            startIndex = 1;
        }
        if (args.length <= startIndex) {
            sender.sendMessage(ChatFormat.color("&cUsage: /" + label + " [highlight] <message>"));
            return true;
        }
        String message = String.join(" ", Arrays.asList(args).subList(startIndex, args.length));
        UUID senderUuid = (sender instanceof Player player) ? player.getUniqueId() : null;
        CommandResult result = service.sendServerAnnouncement(senderUuid, message, highlight);
        if (result.getMessage() != null) {
            sender.sendMessage(ChatFormat.color(result.getMessage()));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> matches = new ArrayList<>();
            StringUtil.copyPartialMatches(args[0], FLAGS, matches);
            return matches;
        }
        return List.of();
    }
}
