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

public class OgrebloodTotemItem extends Item {
    public OgrebloodTotemItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        if (!ClientConfig.showCustomTooltips()) return;
        tooltip.accept(Component.empty());
        tooltip.accept(Component.literal("When held in either hand:").withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.literal("Saves you at 2 hearts and unleashes an Ogre shockwave")
                .withStyle(ChatFormatting.DARK_GREEN));
        tooltip.accept(Component.literal("Resistance II (0:05), Strength I (0:08), Regeneration II (0:05)")
                .withStyle(ChatFormatting.BLUE));
        tooltip.accept(Component.literal("Consumed on use").withStyle(ChatFormatting.DARK_GRAY));
    }
}
