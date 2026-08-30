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

public class KingsCleaverItem extends Item {
    public KingsCleaverItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
        if (!ClientConfig.showCustomTooltips()) return;
        tooltip.accept(Component.empty());
        tooltip.accept(Component.literal("Maw's Feast").withStyle(ChatFormatting.GOLD));
        tooltip.accept(Component.literal("Kills restore 2 hearts").withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.accept(Component.literal("3s cooldown").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.accept(Component.empty());
        tooltip.accept(Component.literal("Maw's Desperation").withStyle(ChatFormatting.DARK_RED));
        tooltip.accept(Component.literal("Below half health: Strength I and Speed I")
                .withStyle(ChatFormatting.RED));
    }
}
