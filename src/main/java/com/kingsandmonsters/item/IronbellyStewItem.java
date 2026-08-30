package com.kingsandmonsters.item;

import com.kingsandmonsters.ClientConfig;

import com.kingsandmonsters.ModMobEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.function.Consumer;

public class IronbellyStewItem extends Item {
    private static final int ONE_MINUTE = 20 * 60;
    private static final int TWO_MINUTES = 20 * 60 * 2;

    public IronbellyStewItem(Properties properties) {
        super(properties);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity consumer) {
        ItemStack result = super.finishUsingItem(stack, level, consumer);
        if (!level.isClientSide()) {
            consumer.removeEffect(MobEffects.POISON);
            consumer.addEffect(new MobEffectInstance(ModMobEffects.POISON_RESISTANCE, TWO_MINUTES));
            consumer.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, ONE_MINUTE));
            consumer.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, TWO_MINUTES));
        }
        return consumer.hasInfiniteMaterials() ? result : new ItemStack(Items.BOWL);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        if (!ClientConfig.showCustomTooltips()) return;
        tooltip.accept(Component.empty());
        tooltip.accept(Component.literal("Food:").withStyle(ChatFormatting.GOLD));
        tooltip.accept(Component.literal("  Nutrition: 8 (4 drumsticks)")
                .withStyle(ChatFormatting.BLUE));
        tooltip.accept(Component.literal("  Saturation: 14.4")
                .withStyle(ChatFormatting.BLUE));
        tooltip.accept(Component.empty());
        tooltip.accept(Component.literal("Effects:").withStyle(ChatFormatting.GOLD));
        tooltip.accept(Component.literal("  Clears existing Poison").withStyle(ChatFormatting.BLUE));
        tooltip.accept(Component.literal("  Poison Resistance (2:00)").withStyle(ChatFormatting.BLUE));
        tooltip.accept(Component.literal("  Resistance I (1:00)").withStyle(ChatFormatting.BLUE));
        tooltip.accept(Component.literal("  Absorption I (2:00)").withStyle(ChatFormatting.BLUE));
        tooltip.accept(Component.empty());
        tooltip.accept(Component.literal("A heavy swamp meal fit for an ogre.")
                .withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.ITALIC));
    }
}
