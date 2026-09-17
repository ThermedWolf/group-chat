package net.thermedwolf.groupchat.fabric.gui;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

/**
 * A read-only, click-to-act inventory menu. Uses the vanilla GENERIC_9xN
 * menu types (confirmed present as public static fields on MenuType) so any
 * client - modded or vanilla - can render it with zero client-side code on
 * our end, which matters since this is a server-only mod.
 *
 * clicked() is overridden to run our own handler and never calls super(),
 * so nothing ever actually moves/leaves the menu - it's purely a picker.
 */
public class PickerMenu extends ChestMenu {

    @FunctionalInterface
    public interface ClickHandler {
        void onClick(int slotIndex, ServerPlayer player);
    }

    private final ClickHandler clickHandler;

    public PickerMenu(MenuType<?> type, int rows, int syncId, Inventory playerInventory, Container container, ClickHandler clickHandler) {
        super(type, syncId, playerInventory, container, rows);
        this.clickHandler = clickHandler;
    }

    public static MenuType<?> menuTypeForRows(int rows) {
        return switch (rows) {
            case 1 -> MenuType.GENERIC_9x1;
            case 2 -> MenuType.GENERIC_9x2;
            case 3 -> MenuType.GENERIC_9x3;
            case 4 -> MenuType.GENERIC_9x4;
            case 5 -> MenuType.GENERIC_9x5;
            default -> MenuType.GENERIC_9x6;
        };
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        // Shift-click should never actually transfer anything out of a picker menu.
        return ItemStack.EMPTY;
    }

    @Override
    public void clicked(int slotId, int button, ContainerInput input, Player player) {
        if (slotId >= 0 && slotId < this.getContainer().getContainerSize() && player instanceof ServerPlayer serverPlayer) {
            clickHandler.onClick(slotId, serverPlayer);
        }
        // Deliberately not calling super.clicked(...) - we never want the
        // normal pick-up/place/shift-click item-movement behavior here.
    }
}
