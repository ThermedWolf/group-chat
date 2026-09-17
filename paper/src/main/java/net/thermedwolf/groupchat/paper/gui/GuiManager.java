package net.thermedwolf.groupchat.paper.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.thermedwolf.groupchat.core.CommandResult;
import net.thermedwolf.groupchat.core.Group;
import net.thermedwolf.groupchat.core.GroupChatService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;
import java.util.UUID;

public class GuiManager {

    private final GroupChatService service;

    public GuiManager(GroupChatService service) {
        this.service = service;
    }

    // ---- Main menu ----

    public void openMainMenu(Player player) {
        GuiHolders.MainMenuHolder holder = new GuiHolders.MainMenuHolder();
        Inventory inv = Bukkit.createInventory(holder, 27, Component.text("GroupChat", NamedTextColor.DARK_PURPLE));
        holder.setInventory(inv);

        inv.setItem(11, namedItem(Material.WRITABLE_BOOK, "&aCreate Group", "&7Start a new group chat"));
        inv.setItem(13, namedItem(Material.BOOK, "&eMy Groups", "&7View, message, invite, or leave"));
        inv.setItem(15, namedItem(Material.PLAYER_HEAD, "&bMessage a Player", "&7Send a private message"));

        player.openInventory(inv);
    }

    // ---- My Groups list ----

    public void openGroupList(Player player) {
        List<Group> groups = service.getGroupsForPlayer(player.getUniqueId());
        GuiHolders.GroupListHolder holder = new GuiHolders.GroupListHolder();
        int size = Math.max(9, ((groups.size() / 9) + 1) * 9);
        Inventory inv = Bukkit.createInventory(holder, size, Component.text("My Groups", NamedTextColor.DARK_PURPLE));
        holder.setInventory(inv);

        int slot = 0;
        for (Group group : groups) {
            inv.setItem(slot++, namedItem(Material.CHEST, "&e" + group.getName(),
                    "&7" + group.getMembers().size() + " member" + (group.getMembers().size() == 1 ? "" : "s"),
                    "&8Click to manage"));
            holder.getSlotToGroupName().add(group.getName());
        }
        if (groups.isEmpty()) {
            inv.setItem(4, namedItem(Material.BARRIER, "&7No groups yet", "&7Create one from the main menu"));
        }
        inv.setItem(size - 1, namedItem(Material.ARROW, "&cBack"));

        player.openInventory(inv);
    }

    // ---- Group detail (message / invite / leave) ----

    public void openGroupDetail(Player player, String groupName) {
        service.getGroup(groupName).ifPresentOrElse(group -> {
            GuiHolders.GroupDetailHolder holder = new GuiHolders.GroupDetailHolder(group.getName());
            Inventory inv = Bukkit.createInventory(holder, 9,
                    Component.text(group.getName(), NamedTextColor.DARK_PURPLE));
            holder.setInventory(inv);

            inv.setItem(1, namedItem(Material.PAPER, "&aMessage Group",
                    "&7" + group.getMembers().size() + " member" + (group.getMembers().size() == 1 ? "" : "s")));
            inv.setItem(3, namedItem(Material.PLAYER_HEAD, "&bInvite Player"));
            if (group.getOwner().equals(player.getUniqueId())) {
                inv.setItem(5, namedItem(Material.IRON_SWORD, "&6Kick Player", "&7Owner only"));
            }
            inv.setItem(7, namedItem(Material.RED_DYE, "&cLeave Group"));
            inv.setItem(8, namedItem(Material.ARROW, "&7Back"));

            player.openInventory(inv);
        }, () -> player.sendMessage(Component.text("That group no longer exists.", NamedTextColor.RED)));
    }

    // ---- Player picker (used for "invite", "kick", and "message a player") ----

    public void openPlayerPicker(Player player, GuiHolders.PickerMode mode, String context) {
        List<? extends OfflinePlayer> targets = switch (mode) {
            case KICK_FROM_GROUP -> service.getGroup(context)
                    .map(group -> group.getMembers().stream()
                            .filter(uuid -> !uuid.equals(group.getOwner()) && !uuid.equals(player.getUniqueId()))
                            .map(Bukkit::getOfflinePlayer)
                            .toList())
                    .orElse(List.of());
            case INVITE_TO_GROUP, MESSAGE_PLAYER -> Bukkit.getOnlinePlayers().stream()
                    .filter(p -> !p.getUniqueId().equals(player.getUniqueId()))
                    .toList();
        };

        GuiHolders.PlayerPickerHolder holder = new GuiHolders.PlayerPickerHolder(mode, context);
        int size = Math.max(9, ((targets.size() / 9) + 1) * 9);
        String title = switch (mode) {
            case INVITE_TO_GROUP -> "Invite to " + context;
            case KICK_FROM_GROUP -> "Kick from " + context;
            case MESSAGE_PLAYER -> "Message a player";
        };
        Inventory inv = Bukkit.createInventory(holder, size, Component.text(title, NamedTextColor.DARK_PURPLE));
        holder.setInventory(inv);

        int slot = 0;
        for (OfflinePlayer target : targets) {
            String name = target.getName() != null ? target.getName() : target.getUniqueId().toString().substring(0, 8);
            inv.setItem(slot++, playerHeadItem(target, "&e" + name));
        }
        if (targets.isEmpty()) {
            inv.setItem(4, namedItem(Material.BARRIER, mode == GuiHolders.PickerMode.KICK_FROM_GROUP
                    ? "&7No other members to kick"
                    : "&7No other players online"));
        }
        inv.setItem(size - 1, namedItem(Material.ARROW, "&cBack"));

        player.openInventory(inv);
    }

    // ---- Create-group name input (anvil trick: no plugin can pop up a real text
    // box,
    // but a virtual anvil GUI lets the player type a name via the rename field)
    // ----

    public void openCreateGroupAnvil(Player player) {
        GuiHolders.CreateGroupAnvilHolder holder = new GuiHolders.CreateGroupAnvilHolder();
        Inventory inv = Bukkit.createInventory(holder, org.bukkit.event.inventory.InventoryType.ANVIL,
                Component.text("Name your group", NamedTextColor.DARK_PURPLE));
        holder.setInventory(inv);
        inv.setItem(0, namedItem(Material.PAPER, "&fgroupname"));
        player.openInventory(inv);
    }

    /**
     * Called by GuiListener once it reads a submitted name out of the anvil's
     * result slot.
     */
    public void handleGroupNameSubmitted(Player player, String rawName) {
        CommandResult result = service.createGroup(player.getUniqueId(), rawName);
        player.closeInventory();
        sendResult(player, result);
        if (result.isSuccess()) {
            openMainMenu(player);
        }
    }

    // ---- shared helpers ----

    public void sendResult(Player player, CommandResult result) {
        if (result.getMessage() != null) {
            player.sendMessage(legacy(result.getMessage()));
        }
    }

    private Component legacy(String colored) {
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
                .deserialize(net.thermedwolf.groupchat.core.ChatFormat.color(colored));
    }

    private ItemStack namedItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(legacy(name));
            if (lore.length > 0) {
                meta.lore(List.of(lore).stream().map(this::legacy).toList());
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack playerHeadItem(OfflinePlayer owner, String name) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(owner);
            meta.displayName(legacy(name));
            item.setItemMeta(meta);
        }
        return item;
    }
}