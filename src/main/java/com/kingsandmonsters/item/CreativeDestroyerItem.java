package com.kingsandmonsters.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.TooltipFlag;

import java.util.function.Consumer;

/** Clearly labelled development utility; deliberately has no survival acquisition path. */
public final class CreativeDestroyerItem extends Item {
    public CreativeDestroyerItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
                                 Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.literal("Development testing tool").withStyle(ChatFormatting.RED));
        tooltip.accept(Component.literal("Creative attacks instantly kill living targets")
                .withStyle(ChatFormatting.GRAY));
    }
}
