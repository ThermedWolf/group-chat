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

public class GmsgCommand implements CommandExecutor, TabCompleter {

    private final GroupChatService service;

    public GmsgCommand(GroupChatService service) {
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
            player.sendMessage(ChatFormat.color("&cUsage: /gmsg <group> <message>"));
            return true;
        }
        String group = args[0];
        String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        CommandResult result = service.sendGroupMessage(player.getUniqueId(), group, message);
        if (result.getMessage() != null) {
            player.sendMessage(ChatFormat.color(result.getMessage()));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1 || !(sender instanceof Player player)) {
            return List.of();
        }
        List<String> matches = new ArrayList<>();
        StringUtil.copyPartialMatches(args[0], service.getGroupNamesForPlayer(player.getUniqueId()), matches);
        return matches;
    }
}