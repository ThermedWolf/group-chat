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
        // Mirror Fabric's logging: show where data lives and how many groups were loaded (core's GroupManager already logs via JUL at FINE/INFO)
        getLogger().info("GroupChat data folder: " + getDataFolder().getAbsolutePath());
        getLogger().info("Loaded " + service.getTotalGroupCount() + " groups from " + new java.io.File(getDataFolder(), "groups.json").getAbsolutePath());
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

        AnnounceCommand announceCommand = new AnnounceCommand(service);
        getCommand("announce").setExecutor(announceCommand);
        getCommand("announce").setTabCompleter(announceCommand);

        JoinMsgCommand joinMsgCommand = new JoinMsgCommand(service);
        getCommand("joinmsg").setExecutor(joinMsgCommand);
        getCommand("joinmsg").setTabCompleter(joinMsgCommand);

        // /gc, /groupchat, /gchat - umbrella commands whose subcommands are all
        // GroupChat commands, so autocomplete doubles as command discovery.
        GroupChatDispatchCommand dispatch = new GroupChatDispatchCommand()
                .register("group", groupCommand)
                .register("gmsg", gmsgCommand)
                .register("gm", gmsgCommand)
                .register("gmoffline", gmOfflineCommand)
                .register("dm", dmCommand)
                .register("unread", new UnreadCommand(service))
                .register("groupgui", new GroupGuiCommand(gui))
                .register("announce", announceCommand)
                .register("joinmsg", joinMsgCommand);
        for (String name : new String[]{"gc", "groupchat", "gchat"}) {
            getCommand(name).setExecutor(dispatch);
            getCommand(name).setTabCompleter(dispatch);
        }

        getServer().getPluginManager().registerEvents(new JoinListener(service), this);
        getServer().getPluginManager().registerEvents(new QuitListener(service), this);
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