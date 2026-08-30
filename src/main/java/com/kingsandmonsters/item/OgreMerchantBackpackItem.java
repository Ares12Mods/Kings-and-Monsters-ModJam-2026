package com.kingsandmonsters.item;

import com.kingsandmonsters.ClientConfig;

import com.kingsandmonsters.menu.OgreMerchantBackpackMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.ItemStackHandler;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.List;
import java.util.function.Consumer;

public class OgreMerchantBackpackItem extends CurioItem {
    public static final int SLOT_COUNT = 54;
    private static final String INVENTORY_TAG = "BackpackInventory";

    public OgreMerchantBackpackItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            open(serverPlayer, stack, hand == InteractionHand.MAIN_HAND ? player.getInventory().getSelectedSlot() : -1);
        }
        return level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER;
    }

    public static void openEquipped(ServerPlayer player) {
        CuriosApi.getCuriosInventory(player).flatMap(handler -> handler.findFirstCurio(
                stack -> stack.getItem() instanceof OgreMerchantBackpackItem))
                .ifPresent(result -> open(player, result.stack(), -1));
    }

    private static void open(ServerPlayer player, ItemStack stack, int lockedInventorySlot) {
        player.openMenu(new SimpleMenuProvider(
                (containerId, inventory, ignored) -> new OgreMerchantBackpackMenu(
                        containerId, inventory, stack, lockedInventorySlot),
                Component.translatable("container.kingsandmonsters.ogre_merchant_backpack")),
                buffer -> buffer.writeVarInt(lockedInventorySlot));
    }

    public static ItemStackHandler loadInventory(ItemStack backpack, net.minecraft.core.HolderLookup.Provider registries) {
        ItemStackHandler handler = new ItemStackHandler(SLOT_COUNT) {
            @Override
            public void setSize(int size) {
                // Backpacks saved before the capacity increase report a smaller size; never shrink
                // below SLOT_COUNT or the extra menu slots would index out of range.
                super.setSize(Math.max(size, SLOT_COUNT));
            }

            @Override
            protected void onContentsChanged(int slot) {
                saveInventory(backpack, this, registries);
            }

            @Override
            public boolean isItemValid(int slot, ItemStack candidate) {
                return !(candidate.getItem() instanceof OgreMerchantBackpackItem);
            }
        };
        CustomData data = backpack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        if (data.contains(INVENTORY_TAG)) {
            handler.deserialize(net.minecraft.world.level.storage.TagValueInput.create(
                    net.minecraft.util.ProblemReporter.DISCARDING, registries,
                    data.copyTag().getCompoundOrEmpty(INVENTORY_TAG)));
        }
        return handler;
    }

    private static void saveInventory(ItemStack backpack, ItemStackHandler handler,
                                      net.minecraft.core.HolderLookup.Provider registries) {
        CustomData.update(DataComponents.CUSTOM_DATA, backpack, tag -> {
            var output = net.minecraft.world.level.storage.TagValueOutput.createWithContext(
                    net.minecraft.util.ProblemReporter.DISCARDING, registries);
            handler.serialize(output);
            tag.put(INVENTORY_TAG, output.buildResult());
        });
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        if (!ClientConfig.showCustomTooltips()) return;
        tooltip.accept(Component.translatable("tooltip.kingsandmonsters.backpack.slots", SLOT_COUNT)
                .withStyle(ChatFormatting.BLUE));
        tooltip.accept(Component.translatable("tooltip.kingsandmonsters.backpack.open")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
