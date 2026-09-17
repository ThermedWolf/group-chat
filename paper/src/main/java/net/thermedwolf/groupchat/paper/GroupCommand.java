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

public class GroupCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = Arrays.asList("create", "delete", "invite", "accept", "decline",
            "leave", "kick", "list", "members", "history", "toggle");

    private final GroupChatService service;

    public GroupCommand(GroupChatService service) {
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
        if (args.length == 0) {
            player.sendMessage(ChatFormat.color("&7Usage: /group <" + String.join("|", SUBCOMMANDS) + "> [args]"));
            player.sendMessage(ChatFormat.color("&7Or: &e/group <group> <history|toggle> [args] &7(shorthand for single-group use without naming it)"));
            return true;
        }

        // Detect "group-first" syntax: /group <group> history [count]  or  /group <group> toggle
        // args[0] is potentially a group name, args[1] is history/toggle
        if (args.length >= 2 && (args[1].equalsIgnoreCase("history") || args[1].equalsIgnoreCase("toggle"))) {
            String groupName = args[0];
            String action = args[1].toLowerCase();
            if (action.equals("history")) {
                int count = 5;
                if (args.length >= 3) {
                    try {
                        count = Integer.parseInt(args[2]);
                    } catch (NumberFormatException ex) {
                        player.sendMessage(ChatFormat.color("&cCount must be a number 1-5."));
                        return true;
                    }
                }
                // Validate history handles its own membership checks
                List<String> lines = service.getGroupHistory(player.getUniqueId(), groupName, count);
                for (String line : lines) player.sendMessage(line);
                return true;
            } else { // toggle
                CommandResult result = service.toggleGroupChat(player.getUniqueId(), groupName);
                reply(player, result);
                return true;
            }
        }

        String sub = args[0].toLowerCase();
        CommandResult result;

        switch (sub) {
            case "create" -> {
                if (args.length < 2) {
                    player.sendMessage(ChatFormat.color("&cUsage: /group create <name>"));
                    return true;
                }
                result = service.createGroup(player.getUniqueId(), args[1]);
                reply(player, result);
            }
            case "delete" -> {
                // supports /group delete [group]
                String group = args.length >= 2 ? args[1] : null;
                result = service.deleteGroup(player.getUniqueId(), group);
                reply(player, result);
                if (result.getMessage() != null && result.getMessage().contains("Please specify")) {
                    player.sendMessage(ChatFormat.color("&7Usage: /group delete <name>"));
                }
            }
            case "invite" -> {
                // supports /group invite <player> when single group (group omitted)
                // and /group invite <group> <player>
                if (args.length < 2) {
                    player.sendMessage(ChatFormat.color("&cUsage: /group invite <name> <player>  &7(or &e/group invite <player> &7if you have 1 group)"));
                    return true;
                }
                String group;
                String target;
                if (args.length == 2) {
                    // try infer group
                    group = null;
                    target = args[1];
                } else {
                    group = args[1];
                    target = args[2];
                }
                result = service.invite(player.getUniqueId(), group, target);
                reply(player, result);
            }
            case "accept" -> {
                String group = args.length >= 2 ? args[1] : null;
                result = service.acceptInvite(player.getUniqueId(), group);
                reply(player, result);
            }
            case "decline" -> {
                String group = args.length >= 2 ? args[1] : null;
                result = service.declineInvite(player.getUniqueId(), group);
                reply(player, result);
            }
            case "leave" -> {
                String group = args.length >= 2 ? args[1] : null;
                result = service.leaveGroup(player.getUniqueId(), group);
                reply(player, result);
            }
            case "kick" -> {
                // /group kick <player> (single group) vs /group kick <group> <player>
                if (args.length < 2) {
                    player.sendMessage(ChatFormat.color("&cUsage: /group kick <name> <player>  &7(or &e/group kick <player> &7if you have 1 group)"));
                    return true;
                }
                String group;
                String target;
                if (args.length == 2) {
                    group = null;
                    target = args[1];
                } else {
                    group = args[1];
                    target = args[2];
                }
                result = service.kickMember(player.getUniqueId(), group, target);
                reply(player, result);
            }
            case "list" -> player.sendMessage(ChatFormat.color(service.listGroupsFormatted(player.getUniqueId())));
            case "members" -> {
                String group = args.length >= 2 ? args[1] : null;
                result = service.members(player.getUniqueId(), group);
                reply(player, result);
            }
            case "history" -> {
                // /group history [count]  -> infer group if single
                // /group history <group> [count]
                String group = null;
                int count = 5;
                if (args.length == 1) {
                    // no group, no count -> infer
                } else if (args.length == 2) {
                    String a = args[1];
                    // try parse as count
                    try {
                        int n = Integer.parseInt(a);
                        // numeric -> count with inferred group
                        count = n;
                    } catch (NumberFormatException ex) {
                        group = a;
                    }
                } else if (args.length >= 3) {
                    group = args[1];
                    try {
                        count = Integer.parseInt(args[2]);
                    } catch (NumberFormatException ex) {
                        player.sendMessage(ChatFormat.color("&cCount must be a number 1-5."));
                        return true;
                    }
                }
                List<String> lines = service.getGroupHistory(player.getUniqueId(), group, count);
                for (String line : lines) player.sendMessage(line);
            }
            case "toggle" -> {
                // /group toggle [group]
                String group = args.length >= 2 ? args[1] : null;
                result = service.toggleGroupChat(player.getUniqueId(), group);
                reply(player, result);
            }
            default -> player.sendMessage(
                    ChatFormat.color("&cUnknown subcommand. &7Options: " + String.join(", ", SUBCOMMANDS)));
        }
        return true;
    }

    private void reply(Player player, CommandResult result) {
        if (result.getMessage() != null) {
            player.sendMessage(ChatFormat.color(result.getMessage()));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player)) {
            return List.of();
        }

        // For "group-first" typing: /group MyGroup hi<TAB> we want history/toggle
        if (args.length == 2 && !SUBCOMMANDS.contains(args[0].toLowerCase())) {
            // args[0] might be a group name, suggest history/toggle for second arg
            List<String> actions = List.of("history", "toggle");
            List<String> matches = new ArrayList<>();
            StringUtil.copyPartialMatches(args[1], actions, matches);
            // if matches empty, maybe they typed group history and now want count? no suggestion
            if (!matches.isEmpty()) return matches;
        }

        if (args.length == 1) {
            List<String> options = new ArrayList<>(SUBCOMMANDS);
            // Also suggest group names for group-first syntax as first arg
            options.addAll(service.getGroupNamesForPlayer(player.getUniqueId()));
            List<String> matches = new ArrayList<>();
            StringUtil.copyPartialMatches(args[0], options, matches);
            return matches;
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            List<String> options = switch (sub) {
                case "delete", "leave", "kick", "invite", "members", "history", "toggle" ->
                    service.getGroupNamesForPlayer(player.getUniqueId());
                case "accept", "decline" -> service.getInvitedGroupNames(player.getUniqueId());
                default -> List.<String>of();
            };
            // For history/toggle secondary completions, also consider count hints for history
            List<String> matches = new ArrayList<>();
            StringUtil.copyPartialMatches(args[1], options, matches);
            // also suggest online players for invite/kick single-arg shorthand
            if ((sub.equals("invite") || sub.equals("kick")) && matches.isEmpty()) {
                List<String> names = new ArrayList<>();
                for (Player online : Bukkit.getOnlinePlayers()) {
                    if (!online.getUniqueId().equals(player.getUniqueId())) names.add(online.getName());
                }
                StringUtil.copyPartialMatches(args[1], names, matches);
                return matches;
            }
            return matches;
        }

        if (args.length == 3) {
            String sub = args[0].toLowerCase();
            if (sub.equals("invite") || sub.equals("kick")) {
                List<String> names = new ArrayList<>();
                for (Player online : Bukkit.getOnlinePlayers()) {
                    if (!online.getUniqueId().equals(player.getUniqueId())) {
                        names.add(online.getName());
                    }
                }
                List<String> matches = new ArrayList<>();
                StringUtil.copyPartialMatches(args[2], names, matches);
                return matches;
            }
            if (sub.equals("history")) {
                // third arg is count 1-5
                List<String> counts = List.of("1", "2", "3", "4", "5");
                List<String> matches = new ArrayList<>();
                StringUtil.copyPartialMatches(args[2], counts, matches);
                return matches;
            }
        }

        // "group first" third arg count hint: /group mygroup history <count>
        if (args.length == 3 && args[1].equalsIgnoreCase("history")) {
            List<String> counts = List.of("1", "2", "3", "4", "5");
            List<String> matches = new ArrayList<>();
            StringUtil.copyPartialMatches(args[2], counts, matches);
            return matches;
        }

        return List.of();
    }
}
