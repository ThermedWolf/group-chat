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

public class GmOfflineCommand implements CommandExecutor, TabCompleter {

    private final GroupChatService service;

    public GmOfflineCommand(GroupChatService service) {
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
        if (args.length < 1) {
            player.sendMessage(ChatFormat.color("&cUsage: /gmoffline <group> <message>  &7(or &e/gmoffline <message> &7if you have 1 group)"));
            player.sendMessage(ChatFormat.color("&7Also: &e/gmoffline toggle [group] &7and &e/gmoffline history [group] [1-5]"));
            return true;
        }

        // --- toggle / history shortcuts (mirrors GmsgCommand) ---
        if (args.length >= 2 && args[1].equalsIgnoreCase("toggle")) {
            String group = args[0];
            CommandResult result = service.toggleGroupChat(player.getUniqueId(), group);
            if (result.getMessage() != null) player.sendMessage(ChatFormat.color(result.getMessage()));
            return true;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("history")) {
            String group = args[0];
            int count = 5;
            if (args.length >= 3) {
                try {
                    count = Integer.parseInt(args[2]);
                    if (count < 1 || count > 5) throw new NumberFormatException();
                } catch (NumberFormatException ex) {
                    player.sendMessage(ChatFormat.color("&cCount must be 1-5."));
                    return true;
                }
            }
            for (String line : service.getGroupHistory(player.getUniqueId(), group, count)) player.sendMessage(line);
            return true;
        }
        if (args[0].equalsIgnoreCase("toggle")) {
            String group = args.length >= 2 ? args[1] : null;
            CommandResult result = service.toggleGroupChat(player.getUniqueId(), group);
            if (result.getMessage() != null) player.sendMessage(ChatFormat.color(result.getMessage()));
            return true;
        }
        if (args[0].equalsIgnoreCase("history")) {
            String group = null;
            int count = 5;
            if (args.length == 1) {
            } else if (args.length == 2) {
                String a = args[1];
                try {
                    int n = Integer.parseInt(a);
                    if (n >= 1 && n <= 5) count = n;
                    else throw new NumberFormatException();
                } catch (NumberFormatException ex) {
                    group = a;
                }
            } else if (args.length >= 3) {
                group = args[1];
                try {
                    count = Integer.parseInt(args[2]);
                    if (count < 1 || count > 5) throw new NumberFormatException();
                } catch (NumberFormatException ex) {
                    player.sendMessage(ChatFormat.color("&cCount must be 1-5."));
                    return true;
                }
            }
            for (String line : service.getGroupHistory(player.getUniqueId(), group, count)) player.sendMessage(line);
            return true;
        }

        String group;
        String message;
        if (args.length == 1) {
            group = null;
            message = args[0];
        } else {
            List<String> ownGroups = service.getGroupNamesForPlayer(player.getUniqueId());
            boolean firstIsGroup = ownGroups.stream().anyMatch(g -> g.equalsIgnoreCase(args[0]));
            if (!firstIsGroup && ownGroups.size() == 1) {
                group = null;
                message = String.join(" ", args);
            } else {
                group = args[0];
                message = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            }
        }
        if (message == null || message.isBlank()) {
            player.sendMessage(ChatFormat.color("&cUsage: /gmoffline <group> <message>"));
            return true;
        }
        CommandResult result = service.sendGroupMessagePersistent(player.getUniqueId(), group, message);
        if (result.getMessage() != null) {
            player.sendMessage(ChatFormat.color(result.getMessage()));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player)) {
            return List.of();
        }
        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            options.addAll(service.getGroupNamesForPlayer(player.getUniqueId()));
            options.add("toggle");
            options.add("history");
            List<String> matches = new ArrayList<>();
            StringUtil.copyPartialMatches(args[0], options, matches);
            return matches;
        }
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("toggle")) {
                List<String> matches = new ArrayList<>();
                StringUtil.copyPartialMatches(args[1], service.getGroupNamesForPlayer(player.getUniqueId()), matches);
                return matches;
            }
            if (args[0].equalsIgnoreCase("history")) {
                List<String> options = new ArrayList<>(service.getGroupNamesForPlayer(player.getUniqueId()));
                options.addAll(List.of("1", "2", "3", "4", "5"));
                List<String> matches = new ArrayList<>();
                StringUtil.copyPartialMatches(args[1], options, matches);
                return matches;
            }
            List<String> actions = List.of("toggle", "history");
            List<String> matches = new ArrayList<>();
            StringUtil.copyPartialMatches(args[1], actions, matches);
            if (!matches.isEmpty()) return matches;
            return List.of();
        }
        if (args.length == 3) {
            if (args[0].equalsIgnoreCase("history") || args[1].equalsIgnoreCase("history")) {
                List<String> counts = List.of("1", "2", "3", "4", "5");
                List<String> matches = new ArrayList<>();
                StringUtil.copyPartialMatches(args[2], counts, matches);
                return matches;
            }
        }
        return List.of();
    }
}