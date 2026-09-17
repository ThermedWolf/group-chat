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
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }
        gui.openMainMenu(player);
        return true;
    }
}
