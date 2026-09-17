package net.thermedwolf.groupchat.fabric.gui;

import net.thermedwolf.groupchat.core.ChatFormat;
import net.thermedwolf.groupchat.core.CommandResult;
import net.thermedwolf.groupchat.core.Group;
import net.thermedwolf.groupchat.core.GroupChatService;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.ItemLike;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class FabricGuiManager {

    public enum PickerMode {
        INVITE_TO_GROUP,
        KICK_FROM_GROUP,
        MESSAGE_PLAYER
    }

    private final GroupChatService service;

    public FabricGuiManager(GroupChatService service) {
        this.service = service;
    }

    // ---- Main menu ----

    public void openMainMenu(ServerPlayer player) {
        SimpleContainer container = new SimpleContainer(27);
        container.setItem(11, item(Items.WRITABLE_BOOK, "&aCreate Group", "&7Start a new group chat"));
        container.setItem(13, item(Items.BOOK, "&eMy Groups", "&7View, message, invite, or leave"));
        container.setItem(15, item(Items.PLAYER_HEAD, "&bMessage a Player", "&7Send a private message"));

        open(player, 3, container, "GroupChat", (slot, p) -> {
            switch (slot) {
                case 11 -> openCreateGroupAnvil(p);
                case 13 -> openGroupList(p);
                case 15 -> openPlayerPicker(p, PickerMode.MESSAGE_PLAYER, null);
                default -> {
                }
            }
        });
    }

    // ---- My Groups list ----

    public void openGroupList(ServerPlayer player) {
        List<Group> groups = service.getGroupsForPlayer(player.getUUID());
        int rows = Math.max(1, (groups.size() / 9) + 1);
        int size = rows * 9;
        SimpleContainer container = new SimpleContainer(size);
        List<String> slotToGroupName = new ArrayList<>();

        for (Group group : groups) {
            int memberCount = group.getMembers().size();
            container.setItem(slotToGroupName.size(), item(Items.CHEST, "&e" + group.getName(),
                    "&7" + memberCount + " member" + (memberCount == 1 ? "" : "s"),
                    "&8Click to manage"));
            slotToGroupName.add(group.getName());
        }
        if (groups.isEmpty()) {
            container.setItem(4, item(Items.BARRIER, "&7No groups yet", "&7Create one from the main menu"));
        }
        int backSlot = size - 1;
        container.setItem(backSlot, item(Items.ARROW, "&cBack"));

        open(player, rows, container, "My Groups", (slotClicked, p) -> {
            if (slotClicked == backSlot) {
                openMainMenu(p);
                return;
            }
            if (slotClicked < 0 || slotClicked >= slotToGroupName.size()) {
                return;
            }
            openGroupDetail(p, slotToGroupName.get(slotClicked));
        });
    }

    // ---- Group detail (message / invite / leave) ----

    public void openGroupDetail(ServerPlayer player, String groupName) {
        service.getGroup(groupName).ifPresentOrElse(group -> {
            SimpleContainer container = new SimpleContainer(9);
            int memberCount = group.getMembers().size();
            boolean isOwner = group.getOwner().equals(player.getUUID());
            container.setItem(1, item(Items.PAPER, "&aMessage Group",
                    "&7" + memberCount + " member" + (memberCount == 1 ? "" : "s")));
            container.setItem(3, item(Items.PLAYER_HEAD, "&bInvite Player"));
            if (isOwner) {
                container.setItem(5, item(Items.IRON_SWORD, "&6Kick Player", "&7Owner only"));
            }
            container.setItem(7, item(Items.TNT, "&cLeave Group"));
            container.setItem(8, item(Items.ARROW, "&7Back"));

            open(player, 1, container, group.getName(), (slot, p) -> {
                switch (slot) {
                    case 1 -> {
                        CommandResult result = service.startComposeGroupMessage(p.getUUID(), group.getName());
                        p.closeContainer();
                        sendResult(p, result);
                    }
                    case 3 -> openPlayerPicker(p, PickerMode.INVITE_TO_GROUP, group.getName());
                    case 5 -> openPlayerPicker(p, PickerMode.KICK_FROM_GROUP, group.getName());
                    case 7 -> {
                        CommandResult result = service.leaveGroup(p.getUUID(), group.getName());
                        p.closeContainer();
                        sendResult(p, result);
                    }
                    case 8 -> openGroupList(p);
                    default -> {
                    }
                }
            });
        }, () -> player.sendSystemMessage(Component.literal(ChatFormat.color("&cThat group no longer exists."))));
    }

    // ---- Player picker (used for "invite", "kick", and "message a player") ----

    public void openPlayerPicker(ServerPlayer player, PickerMode mode, String context) {
        List<UUID> candidateUuids;
        if (mode == PickerMode.KICK_FROM_GROUP) {
            candidateUuids = service.getGroup(context)
                    .map(group -> group.getMembers().stream()
                            .filter(uuid -> !uuid.equals(group.getOwner()) && !uuid.equals(player.getUUID()))
                            .toList())
                    .orElse(List.of());
        } else {
            candidateUuids = player.level().getServer().getPlayerList().getPlayers().stream()
                    .filter(p -> !p.getUUID().equals(player.getUUID()))
                    .map(ServerPlayer::getUUID)
                    .toList();
        }

        int rows = Math.max(1, (candidateUuids.size() / 9) + 1);
        int size = rows * 9;
        SimpleContainer container = new SimpleContainer(size);
        List<UUID> slotToUuid = new ArrayList<>();

        for (UUID uuid : candidateUuids) {
            container.setItem(slotToUuid.size(), item(Items.PLAYER_HEAD, "&e" + service.getDisplayName(uuid)));
            slotToUuid.add(uuid);
        }
        if (candidateUuids.isEmpty()) {
            container.setItem(4, item(Items.BARRIER, mode == PickerMode.KICK_FROM_GROUP
                    ? "&7No other members to kick"
                    : "&7No other players online"));
        }
        int backSlot = size - 1;
        container.setItem(backSlot, item(Items.ARROW, "&cBack"));

        String title = switch (mode) {
            case INVITE_TO_GROUP -> "Invite to " + context;
            case KICK_FROM_GROUP -> "Kick from " + context;
            case MESSAGE_PLAYER -> "Message a player";
        };

        open(player, rows, container, title, (slotClicked, p) -> {
            if (slotClicked == backSlot) {
                if (mode == PickerMode.MESSAGE_PLAYER) {
                    openMainMenu(p);
                } else {
                    openGroupDetail(p, context);
                }
                return;
            }
            if (slotClicked < 0 || slotClicked >= slotToUuid.size()) {
                return;
            }
            UUID targetUuid = slotToUuid.get(slotClicked);

            switch (mode) {
                case INVITE_TO_GROUP -> {
                    CommandResult result = service.invite(p.getUUID(), context, service.getDisplayName(targetUuid));
                    p.closeContainer();
                    sendResult(p, result);
                }
                case KICK_FROM_GROUP -> {
                    CommandResult result = service.kickMember(p.getUUID(), context, service.getDisplayName(targetUuid));
                    p.closeContainer();
                    sendResult(p, result);
                }
                case MESSAGE_PLAYER -> {
                    CommandResult result = service.startComposeDirectMessage(p.getUUID(), targetUuid);
                    p.closeContainer();
                    sendResult(p, result);
                }
            }
        });
    }

    // ---- Create-group name input ----

    public void openCreateGroupAnvil(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider(
                (syncId, inv, p) -> {
                    GroupNameMenu menu = new GroupNameMenu(syncId, inv, this::handleGroupNameSubmitted);
                    menu.getSlot(0).set(item(Items.PAPER, "&fgroupname"));
                    return menu;
                },
                Component.literal("Name your group")));
    }

    private void handleGroupNameSubmitted(String rawName, ServerPlayer player) {
        CommandResult result = service.createGroup(player.getUUID(), rawName);
        player.closeContainer();
        sendResult(player, result);
        if (result.isSuccess()) {
            openMainMenu(player);
        }
    }

    // ---- shared helpers ----

    private void open(ServerPlayer player, int rows, SimpleContainer container, String title,
            PickerMenu.ClickHandler handler) {
        var type = PickerMenu.menuTypeForRows(rows);
        player.openMenu(new SimpleMenuProvider(
                (syncId, inv, p) -> new PickerMenu(type, rows, syncId, inv, container, handler),
                Component.literal(title)));
    }

    private void sendResult(ServerPlayer player, CommandResult result) {
        if (result.getMessage() != null) {
            player.sendSystemMessage(Component.literal(ChatFormat.color(result.getMessage())));
        }
    }

    private ItemStack item(ItemLike itemType, String name, String... lore) {
        ItemStack stack = new ItemStack(itemType);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(ChatFormat.color(name)));
        if (lore.length > 0) {
            List<Component> lines = new ArrayList<>();
            for (String line : lore) {
                lines.add(Component.literal(ChatFormat.color(line)));
            }
            stack.set(DataComponents.LORE, new ItemLore(lines));
        }
        return stack;
    }
}