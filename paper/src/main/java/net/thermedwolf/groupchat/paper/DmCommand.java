package net.thermedwolf.groupchat.paper;

import net.thermedwolf.groupchat.core.ChatFormat;
import net.thermedwolf.groupchat.core.CommandResult;
import net.thermedwolf.groupchat.core.GroupChatService;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DmCommand implements CommandExecutor, TabCompleter {

    private final GroupChatService service;

    public DmCommand(GroupChatService service) {
        this.service = service;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }
        if (args.length < 2) {
            player.sendMessage(ChatFormat.color("&cUsage: /dm <player> <message>"));
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

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1 || !(sender instanceof Player player)) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.getUniqueId().equals(player.getUniqueId())) {
                names.add(online.getName());
            }
        }
        List<String> matches = new ArrayList<>();
        StringUtil.copyPartialMatches(args[0], names, matches);
        return matches;
    }
}