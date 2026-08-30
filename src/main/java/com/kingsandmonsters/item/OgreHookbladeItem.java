package com.kingsandmonsters.item;

import com.kingsandmonsters.ClientConfig;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.List;
import java.util.function.Consumer;

public class OgreHookbladeItem extends Item {
    public OgreHookbladeItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        if (!ClientConfig.showCustomTooltips()) return;
        tooltip.accept(Component.literal("Successful hits hook enemies toward you")
                .withStyle(ChatFormatting.BLUE));
        tooltip.accept(Component.literal("Applies Slowness I for 1.5 seconds")
                .withStyle(ChatFormatting.BLUE));
        tooltip.accept(Component.literal("Pull cooldown: 3 seconds")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.accept(Component.literal("Bosses and immovable targets resist the hook")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
