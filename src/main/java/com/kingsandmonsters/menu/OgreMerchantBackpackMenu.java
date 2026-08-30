package com.kingsandmonsters.menu;

import com.kingsandmonsters.ModItems;
import com.kingsandmonsters.ModMenus;
import com.kingsandmonsters.item.OgreMerchantBackpackItem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

public class OgreMerchantBackpackMenu extends AbstractContainerMenu {
    private static final int COLUMNS = 9;
    private static final int ROWS = 6;
    private final ItemStack backpack;
    private final int lockedInventorySlot;

    public OgreMerchantBackpackMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(containerId, inventory, ItemStack.EMPTY, buffer.readVarInt());
    }

    public OgreMerchantBackpackMenu(int containerId, Inventory inventory, ItemStack backpack, int lockedInventorySlot) {
        super(ModMenus.OGRE_MERCHANT_BACKPACK.get(), containerId);
        this.backpack = backpack;
        this.lockedInventorySlot = lockedInventorySlot;
        ItemStackHandler contents = backpack.isEmpty()
                ? new ItemStackHandler(OgreMerchantBackpackItem.SLOT_COUNT)
                : OgreMerchantBackpackItem.loadInventory(backpack, inventory.player.registryAccess());

        for (int row = 0; row < ROWS; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                addSlot(new SlotItemHandler(contents, column + row * COLUMNS,
                        8 + column * 18, 18 + row * 18));
            }
        }

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                int inventoryIndex = column + row * 9 + 9;
                addSlot(playerSlot(inventory, inventoryIndex, 8 + column * 18, 139 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(playerSlot(inventory, column, 8 + column * 18, 197));
        }
    }

    private Slot playerSlot(Inventory inventory, int index, int x, int y) {
        return new Slot(inventory, index, x, y) {
            @Override
            public boolean mayPickup(Player player) {
                return index != lockedInventorySlot && super.mayPickup(player);
            }
        };
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack original = slot.getItem();
        ItemStack copy = original.copy();

        if (index < OgreMerchantBackpackItem.SLOT_COUNT) {
            if (!moveItemStackTo(original, OgreMerchantBackpackItem.SLOT_COUNT, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            if (original.is(ModItems.OGRE_MERCHANT_BACKPACK.get())
                    || !moveItemStackTo(original, 0, OgreMerchantBackpackItem.SLOT_COUNT, false)) {
                return ItemStack.EMPTY;
            }
        }
        if (original.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return backpack.isEmpty() || backpack.getItem() instanceof OgreMerchantBackpackItem;
    }
}
