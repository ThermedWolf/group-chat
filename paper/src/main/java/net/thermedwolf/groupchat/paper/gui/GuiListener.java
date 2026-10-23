package net.thermedwolf.groupchat.paper.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.thermedwolf.groupchat.core.CommandResult;
import net.thermedwolf.groupchat.core.GroupChatService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

public class GuiListener implements Listener {

    private final GroupChatService service;
    private final GuiManager gui;

    public GuiListener(GroupChatService service, GuiManager gui) {
        this.service = service;
        this.gui = gui;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof GuiHolders.GroupChatHolder)) {
            return;
        }
        // Every one of our menus is click-to-act, not click-to-take.
        event.setCancelled(true);
        // Ignore clicks in the player's own inventory - the holder check below
        // is the top inventory of the view and would otherwise map hotbar
        // slots onto our menu actions (e.g. deleting join messages).
        if (event.getClickedInventory() == null
                || !event.getClickedInventory().equals(event.getView().getTopInventory())) {
            return;
        }

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (holder instanceof GuiHolders.MainMenuHolder) {
            handleMainMenu(player, event.getSlot());
        } else if (holder instanceof GuiHolders.GroupListHolder listHolder) {
            handleGroupList(player, listHolder, event.getSlot(), event.getInventory().getSize());
        } else if (holder instanceof GuiHolders.GroupDetailHolder detail) {
            handleGroupDetail(player, detail, event.getSlot());
        } else if (holder instanceof GuiHolders.PlayerPickerHolder picker) {
            handlePlayerPicker(player, picker, event.getCurrentItem(), event.getSlot(), event.getInventory().getSize());
        } else if (holder instanceof GuiHolders.CreateGroupAnvilHolder) {
            handleAnvil(player, event.getSlot(), event.getCurrentItem());
        } else if (holder instanceof GuiHolders.ServerToolsHolder) {
            handleServerTools(player, event.getSlot());
        } else if (holder instanceof GuiHolders.JoinMessagesHolder joinHolder) {
            handleJoinMessages(player, joinHolder, event.getSlot(), event.getClick().isRightClick());
        }
    }

    private void handleMainMenu(Player player, int slot) {
        switch (slot) {
            case 11 -> gui.openCreateGroupAnvil(player);
            case 13 -> gui.openGroupList(player);
            case 15 -> gui.openPlayerPicker(player, GuiHolders.PickerMode.MESSAGE_PLAYER, null);
            case 22 -> {
                // Item is only shown to admins, but re-check so a stale menu
                // can't be used to reach the tools.
                if (service.isAdmin(player.getUniqueId())) {
                    gui.openServerTools(player);
                }
            }
            default -> {
            }
        }
    }

    private void handleServerTools(Player player, int slot) {
        if (!service.isAdmin(player.getUniqueId())) {
            return;
        }
        switch (slot) {
            case 11 -> {
                CommandResult result = service.startComposeAnnouncement(player.getUniqueId(), false);
                player.closeInventory();
                gui.sendResult(player, result);
            }
            case 13 -> {
                CommandResult result = service.startComposeAnnouncement(player.getUniqueId(), true);
                player.closeInventory();
                gui.sendResult(player, result);
            }
            case 15 -> gui.openJoinMessages(player);
            case 22 -> gui.openMainMenu(player);
            default -> {
            }
        }
    }

    private void handleJoinMessages(Player player, GuiHolders.JoinMessagesHolder holder, int slot, boolean rightClick) {
        if (!service.isAdmin(player.getUniqueId())) {
            return;
        }
        if (slot == 22) {
            gui.openServerTools(player);
            return;
        }
        if (slot >= 0 && slot < holder.getMessageCount()) {
            CommandResult result = service.removeJoinMessage(slot + 1);
            gui.sendResult(player, result);
            gui.openJoinMessages(player);
            return;
        }
        switch (slot) {
            case 11 -> {
                CommandResult result = service.startComposeJoinMessage(player.getUniqueId());
                player.closeInventory();
                gui.sendResult(player, result);
            }
            case 13 -> {
                int direction = rightClick ? -1 : 1;
                int next = GuiManager.nextCooldown(service.getJoinMessageCooldown(), direction);
                service.setJoinMessageCooldown(next);
                gui.openJoinMessages(player);
            }
            default -> {
            }
        }
    }

    private void handleGroupList(Player player, GuiHolders.GroupListHolder holder, int slot, int size) {
        if (slot == size - 1) {
            gui.openMainMenu(player);
            return;
        }
        var slotToGroupName = holder.getSlotToGroupName();
        if (slot < 0 || slot >= slotToGroupName.size()) {
            return;
        }
        gui.openGroupDetail(player, slotToGroupName.get(slot));
    }

    private void handleGroupDetail(Player player, GuiHolders.GroupDetailHolder detail, int slot) {
        switch (slot) {
            case 1 -> {
                CommandResult result = service.startComposeGroupMessage(player.getUniqueId(), detail.getGroupName());
                player.closeInventory();
                gui.sendResult(player, result);
            }
            case 3 -> gui.openPlayerPicker(player, GuiHolders.PickerMode.INVITE_TO_GROUP, detail.getGroupName());
            case 5 -> gui.openPlayerPicker(player, GuiHolders.PickerMode.KICK_FROM_GROUP, detail.getGroupName());
            case 7 -> {
                CommandResult result = service.leaveGroup(player.getUniqueId(), detail.getGroupName());
                player.closeInventory();
                gui.sendResult(player, result);
            }
            case 8 -> gui.openGroupList(player);
            default -> {
            }
        }
    }

    private void handlePlayerPicker(Player player, GuiHolders.PlayerPickerHolder picker, ItemStack clicked, int slot,
            int size) {
        if (slot == size - 1) {
            if (picker.getMode() == GuiHolders.PickerMode.MESSAGE_PLAYER) {
                gui.openMainMenu(player);
            } else {
                gui.openGroupDetail(player, picker.getContext());
            }
            return;
        }
        if (clicked == null || !(clicked.getItemMeta() instanceof SkullMeta skullMeta)
                || skullMeta.getOwningPlayer() == null) {
            return;
        }
        var target = skullMeta.getOwningPlayer();

        switch (picker.getMode()) {
            case INVITE_TO_GROUP -> {
                CommandResult result = service.invite(player.getUniqueId(), picker.getContext(), target.getName());
                player.closeInventory();
                gui.sendResult(player, result);
            }
            case KICK_FROM_GROUP -> {
                CommandResult result = service.kickMember(player.getUniqueId(), picker.getContext(), target.getName());
                player.closeInventory();
                gui.sendResult(player, result);
            }
            case MESSAGE_PLAYER -> {
                CommandResult result = service.startComposeDirectMessage(player.getUniqueId(), target.getUniqueId());
                player.closeInventory();
                gui.sendResult(player, result);
            }
        }
    }

    private void handleAnvil(Player player, int slot, ItemStack currentItem) {
        // Slot 2 is the anvil's result/output slot.
        if (slot != 2 || currentItem == null) {
            return;
        }
        var meta = currentItem.getItemMeta();
        if (meta == null || meta.displayName() == null) {
            player.sendMessage(Component.text("Type a name first.", NamedTextColor.RED));
            return;
        }
        String name = PlainTextComponentSerializer.plainText().serialize(meta.displayName());
        gui.handleGroupNameSubmitted(player, name);
    }
}