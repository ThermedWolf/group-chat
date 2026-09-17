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
            "leave", "kick", "list", "members");

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
            return true;
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
                if (args.length < 2) {
                    player.sendMessage(ChatFormat.color("&cUsage: /group delete <name>"));
                    return true;
                }
                result = service.deleteGroup(player.getUniqueId(), args[1]);
                reply(player, result);
            }
            case "invite" -> {
                if (args.length < 3) {
                    player.sendMessage(ChatFormat.color("&cUsage: /group invite <name> <player>"));
                    return true;
                }
                result = service.invite(player.getUniqueId(), args[1], args[2]);
                reply(player, result);
            }
            case "accept" -> {
                if (args.length < 2) {
                    player.sendMessage(ChatFormat.color("&cUsage: /group accept <name>"));
                    return true;
                }
                result = service.acceptInvite(player.getUniqueId(), args[1]);
                reply(player, result);
            }
            case "decline" -> {
                if (args.length < 2) {
                    player.sendMessage(ChatFormat.color("&cUsage: /group decline <name>"));
                    return true;
                }
                result = service.declineInvite(player.getUniqueId(), args[1]);
                reply(player, result);
            }
            case "leave" -> {
                if (args.length < 2) {
                    player.sendMessage(ChatFormat.color("&cUsage: /group leave <name>"));
                    return true;
                }
                result = service.leaveGroup(player.getUniqueId(), args[1]);
                reply(player, result);
            }
            case "kick" -> {
                if (args.length < 3) {
                    player.sendMessage(ChatFormat.color("&cUsage: /group kick <name> <player>"));
                    return true;
                }
                result = service.kickMember(player.getUniqueId(), args[1], args[2]);
                reply(player, result);
            }
            case "list" -> player.sendMessage(ChatFormat.color(service.listGroupsFormatted(player.getUniqueId())));
            case "members" -> {
                if (args.length < 2) {
                    player.sendMessage(ChatFormat.color("&cUsage: /group members <name>"));
                    return true;
                }
                result = service.members(player.getUniqueId(), args[1]);
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

        if (args.length == 1) {
            List<String> matches = new ArrayList<>();
            StringUtil.copyPartialMatches(args[0], SUBCOMMANDS, matches);
            return matches;
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            List<String> options = switch (sub) {
                // Groups you're already in.
                case "delete", "leave", "kick", "invite", "members" ->
                    service.getGroupNamesForPlayer(player.getUniqueId());
                // Groups you've been invited to but haven't joined yet.
                case "accept", "decline" -> service.getInvitedGroupNames(player.getUniqueId());
                default -> List.<String>of();
            };
            List<String> matches = new ArrayList<>();
            StringUtil.copyPartialMatches(args[1], options, matches);
            return matches;
        }

        if (args.length == 3 && (args[0].equalsIgnoreCase("invite") || args[0].equalsIgnoreCase("kick"))) {
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

        return List.of();
    }
}