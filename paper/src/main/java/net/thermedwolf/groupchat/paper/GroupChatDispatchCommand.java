package net.thermedwolf.groupchat.paper;

import net.thermedwolf.groupchat.core.ChatFormat;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Umbrella command behind /gc, /groupchat and /gchat: every GroupChat command
 * is a subcommand here, so typing "/gc " lists them all in autocomplete.
 * Delegation just strips the first argument and hands the rest to the real
 * executor with the subcommand as the label, so usage messages read
 * "/gc announce ..." as expected. Permission checks live inside the
 * individual executors and stay in effect.
 */
public class GroupChatDispatchCommand implements CommandExecutor, TabCompleter {

    private final Map<String, CommandExecutor> executors = new LinkedHashMap<>();

    /** Registers a subcommand name (without slash) with its executor. */
    public GroupChatDispatchCommand register(String name, CommandExecutor executor) {
        executors.put(name.toLowerCase(Locale.ROOT), executor);
        return this;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatFormat.color("&bGroupChat commands: &e" + String.join("&7, &e", executors.keySet())));
            sender.sendMessage(ChatFormat.color("&7Use &e/" + label + " <command> <args>&7, e.g. &e/" + label + " gmsg mygroup Hello!&7."));
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        CommandExecutor target = executors.get(sub);
        if (target == null) {
            sender.sendMessage(ChatFormat.color("&cUnknown GroupChat command '&e" + args[0] + "&c'. Try one of: &e"
                    + String.join("&7, &e", executors.keySet())));
            return true;
        }
        String[] rest = Arrays.copyOfRange(args, 1, args.length);
        return target.onCommand(sender, command, sub, rest);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> matches = new ArrayList<>();
            StringUtil.copyPartialMatches(args[0], executors.keySet(), matches);
            return matches;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        CommandExecutor target = executors.get(sub);
        if (target instanceof TabCompleter tabCompleter) {
            String[] rest = Arrays.copyOfRange(args, 1, args.length);
            return tabCompleter.onTabComplete(sender, command, sub, rest);
        }
        return List.of();
    }
}
