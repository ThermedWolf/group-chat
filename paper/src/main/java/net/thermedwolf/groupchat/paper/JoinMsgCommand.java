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
import java.util.List;

/**
 * /joinmsg - manage the automatic messages shown to players when they join
 * (with a configurable cooldown). Console-friendly except for /joinmsg
 * preview. Requires the groupchat.admin permission.
 */
public class JoinMsgCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("add", "list", "remove", "clear", "cooldown", "preview");

    private final GroupChatService service;

    public JoinMsgCommand(GroupChatService service) {
        this.service = service;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("groupchat.admin")) {
            sender.sendMessage(ChatFormat.color("&cYou don't have permission to use this command."));
            return true;
        }
        if (args.length == 0) {
            sendUsage(sender, label);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "add" -> {
                if (args.length < 2) {
                    sender.sendMessage(ChatFormat.color("&cUsage: /" + label + " add <message>"));
                    return true;
                }
                sendResult(sender, service.addJoinMessage(String.join(" ", List.of(args).subList(1, args.length))));
            }
            case "list" -> sendResult(sender, service.listJoinMessages());
            case "remove" -> {
                Integer id = parseId(sender, args, label);
                if (id != null) {
                    sendResult(sender, service.removeJoinMessage(id));
                }
            }
            case "clear" -> sendResult(sender, service.clearJoinMessages());
            case "cooldown" -> {
                if (args.length < 2) {
                    sender.sendMessage(ChatFormat.color("&7Current join message cooldown: &e"
                            + service.getJoinMessageCooldown() + " &7minutes (0 = every join)."));
                    return true;
                }
                Integer minutes = parseInt(sender, args[1]);
                if (minutes != null) {
                    sendResult(sender, service.setJoinMessageCooldown(minutes));
                }
            }
            case "preview" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Only players can preview join messages.");
                    return true;
                }
                for (String line : service.previewJoinMessages()) {
                    player.sendMessage(line);
                }
            }
            default -> sendUsage(sender, label);
        }
        return true;
    }

    private void sendUsage(CommandSender sender, String label) {
        sender.sendMessage(ChatFormat.color("&cUsage: /" + label + " <add|list|remove|clear|cooldown|preview> [args]"));
        sender.sendMessage(ChatFormat.color("&7- &e/" + label + " add <message> &7- add a message (Markdown and &-code styling supported)"));
        sender.sendMessage(ChatFormat.color("&7- &e/" + label + " cooldown <minutes> &7- show at most once per window (0 = every join)"));
        sender.sendMessage(ChatFormat.color("&7- &e/" + label + " preview &7- render them as a joining player would see them"));
    }

    private Integer parseId(CommandSender sender, String[] args, String label) {
        if (args.length < 2) {
            sender.sendMessage(ChatFormat.color("&cUsage: /" + label + " remove <id>"));
            return null;
        }
        return parseInt(sender, args[1]);
    }

    private Integer parseInt(CommandSender sender, String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            sender.sendMessage(ChatFormat.color("&c'" + raw + "' is not a number."));
            return null;
        }
    }

    private void sendResult(CommandSender sender, CommandResult result) {
        if (result.getMessage() != null) {
            sender.sendMessage(ChatFormat.color(result.getMessage()));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> matches = new ArrayList<>();
            StringUtil.copyPartialMatches(args[0], SUBCOMMANDS, matches);
            return matches;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("remove")) {
            List<String> ids = new ArrayList<>();
            for (int i = 1; i <= service.getJoinMessageCount(); i++) {
                ids.add(String.valueOf(i));
            }
            List<String> matches = new ArrayList<>();
            StringUtil.copyPartialMatches(args[1], ids, matches);
            return matches;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("cooldown")) {
            List<String> matches = new ArrayList<>();
            StringUtil.copyPartialMatches(args[1], List.of("0", "30", "60", "180", "1440"), matches);
            return matches;
        }
        return List.of();
    }
}
