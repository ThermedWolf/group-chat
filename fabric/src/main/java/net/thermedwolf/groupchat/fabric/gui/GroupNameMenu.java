package net.thermedwolf.groupchat.fabric.gui;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

/**
 * A real vanilla anvil menu (AnvilMenu(syncId, Inventory) - the no-block
 * constructor confirmed present and public on the actual class) used purely
 * for its rename text field. Renaming goes over a separate network packet
 * from slot clicks, so overriding clicked() here to intercept the result
 * slot doesn't interfere with typing at all - it only stops the vanilla
 * "take the renamed item" behavior once they click the result.
 */
public class GroupNameMenu extends AnvilMenu {

    @FunctionalInterface
    public interface NameSubmitted {
        void onSubmit(String name, ServerPlayer player);
    }

    private final NameSubmitted callback;

    public GroupNameMenu(int syncId, Inventory playerInventory, NameSubmitted callback) {
        super(syncId, playerInventory);
        this.callback = callback;
    }

    @Override
    public void clicked(int slotId, int button, ContainerInput input, Player player) {
        if (slotId == this.getResultSlot() && player instanceof ServerPlayer serverPlayer) {
            ItemStack result = this.getSlot(slotId).getItem();
            if (!result.isEmpty()) {
                callback.onSubmit(result.getHoverName().getString(), serverPlayer);
            }
            return;
        }
        // Any other slot (the input paper item, the player's own inventory) -
        // leave it locked in place rather than letting items move around.
    }
}
