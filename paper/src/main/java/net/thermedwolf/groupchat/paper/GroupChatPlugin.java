package net.thermedwolf.groupchat.paper;

import net.thermedwolf.groupchat.core.GroupChatService;
import net.thermedwolf.groupchat.paper.gui.GuiListener;
import net.thermedwolf.groupchat.paper.gui.GuiManager;
import org.bukkit.plugin.java.JavaPlugin;

public class GroupChatPlugin extends JavaPlugin {

    private GroupChatService service;

    @Override
    public void onEnable() {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }
        this.service = new GroupChatService(getDataFolder(), new PaperPlatformBridge());
        GuiManager gui = new GuiManager(service);

        GroupCommand groupCommand = new GroupCommand(service);
        getCommand("group").setExecutor(groupCommand);
        getCommand("group").setTabCompleter(groupCommand);

        GmsgCommand gmsgCommand = new GmsgCommand(service);
        getCommand("gmsg").setExecutor(gmsgCommand);
        getCommand("gmsg").setTabCompleter(gmsgCommand);

        GmOfflineCommand gmOfflineCommand = new GmOfflineCommand(service);
        getCommand("gmoffline").setExecutor(gmOfflineCommand);
        getCommand("gmoffline").setTabCompleter(gmOfflineCommand);

        DmCommand dmCommand = new DmCommand(service);
        getCommand("dm").setExecutor(dmCommand);
        getCommand("dm").setTabCompleter(dmCommand);

        getCommand("unread").setExecutor(new UnreadCommand(service));
        getCommand("groupgui").setExecutor(new GroupGuiCommand(gui));

        getServer().getPluginManager().registerEvents(new JoinListener(service), this);
        getServer().getPluginManager().registerEvents(new GuiListener(service, gui), this);
        getServer().getPluginManager().registerEvents(new ChatComposeListener(service, this), this);

        getLogger().info("GroupChat enabled.");
    }

    @Override
    public void onDisable() {
        if (service != null) {
            service.saveAll();
        }
    }
}