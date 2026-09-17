package net.thermedwolf.groupchat.paper.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/**
 * Each menu type gets its own holder so GuiListener can tell menus apart via
 * `instanceof` instead of matching on the inventory's title text (fragile,
 * and breaks if we ever localize the titles).
 */
public final class GuiHolders {

    private GuiHolders() {
    }

    /**
     * Every menu holder implements this so a single instanceof check covers all our
     * GUIs.
     */
    public interface GroupChatHolder extends InventoryHolder {
    }

    public static class MainMenuHolder implements GroupChatHolder {
        private Inventory inventory;

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    public static class GroupListHolder implements GroupChatHolder {
        private Inventory inventory;
        private final java.util.List<String> slotToGroupName = new java.util.ArrayList<>();

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        public java.util.List<String> getSlotToGroupName() {
            return slotToGroupName;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    public static class GroupDetailHolder implements GroupChatHolder {
        private final String groupName;
        private Inventory inventory;

        public GroupDetailHolder(String groupName) {
            this.groupName = groupName;
        }

        public String getGroupName() {
            return groupName;
        }

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    public enum PickerMode {
        INVITE_TO_GROUP,
        KICK_FROM_GROUP,
        MESSAGE_PLAYER
    }

    public static class PlayerPickerHolder implements GroupChatHolder {
        private final PickerMode mode;
        /**
         * For INVITE_TO_GROUP: the group name being invited into. Unused for
         * MESSAGE_PLAYER.
         */
        private final String context;
        private Inventory inventory;

        public PlayerPickerHolder(PickerMode mode, String context) {
            this.mode = mode;
            this.context = context;
        }

        public PickerMode getMode() {
            return mode;
        }

        public String getContext() {
            return context;
        }

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    public static class CreateGroupAnvilHolder implements GroupChatHolder {
        private Inventory inventory;

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }
}