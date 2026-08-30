package com.kingsandmonsters.item;

import com.kingsandmonsters.ClientConfig;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.List;
import java.util.function.Consumer;

public class GoldenOgreToothItem extends Item {
    public GoldenOgreToothItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        if (!ClientConfig.showCustomTooltips()) return;
        tooltip.accept(Component.literal("A gilded ogre trophy, prized by the tribe")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC));
        tooltip.accept(Component.literal("and accepted by its greediest merchants.")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC));
    }
}
