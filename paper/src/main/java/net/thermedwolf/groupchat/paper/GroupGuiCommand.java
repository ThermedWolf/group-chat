package net.thermedwolf.groupchat.paper;

import net.thermedwolf.groupchat.paper.gui.GuiManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class GroupGuiCommand implements CommandExecutor {

    private final GuiManager gui;

    public GroupGuiCommand(GuiManager gui) {
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("groupchat.use")) {
            sender.sendMessage(net.thermedwolf.groupchat.core.ChatFormat.color("&cYou don't have permission to use GroupChat."));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }
        gui.openMainMenu(player);
        return true;
    }
}
