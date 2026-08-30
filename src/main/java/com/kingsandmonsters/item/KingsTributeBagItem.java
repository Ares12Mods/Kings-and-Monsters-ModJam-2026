package com.kingsandmonsters.item;

import com.kingsandmonsters.ClientConfig;

import com.kingsandmonsters.KingsAndMonsters;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;

import java.util.List;
import java.util.function.Consumer;
import java.util.Optional;

public class KingsTributeBagItem extends BundleItem {
    private static final String OPENED_TAG = "KingsTributeOpened";
    private static final ResourceKey<LootTable> LOOT_TABLE = ResourceKey.create(
            Registries.LOOT_TABLE,
            Identifier.fromNamespaceAndPath(KingsAndMonsters.MODID, "gameplay/kings_tribute_bag"));

    public KingsTributeBagItem(Properties properties) {
        super(properties);
    }

    public static boolean isOpened(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag().getBooleanOr(OPENED_TAG, false);
    }

    private static void markOpened(ItemStack stack) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putBoolean(OPENED_TAG, true));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack bag = player.getItemInHand(hand);
        if (isOpened(bag)) return super.use(level, player, hand);
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.FAIL;
        }

        // Persist the one-shot state before evaluating loot so re-entrant interactions cannot reroll it.
        markOpened(bag);
        LootParams params = new LootParams.Builder(serverLevel)
                .withParameter(LootContextParams.ORIGIN, player.position())
                .withOptionalParameter(LootContextParams.THIS_ENTITY, player)
                .withLuck(player.getLuck())
                .create(LootContextParamSets.CHEST);
        LootTable table = serverLevel.getServer().reloadableRegistries().getLootTable(LOOT_TABLE);
        for (ItemStack reward : table.getRandomItems(params)) {
            serverPlayer.getInventory().add(reward);
            if (!reward.isEmpty()) {
                serverPlayer.drop(reward, false);
            }
        }

        serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BUNDLE_DROP_CONTENTS, player.getSoundSource(), 0.8F,
                0.82F + serverLevel.getRandom().nextFloat() * 0.12F);
        serverLevel.sendParticles(ParticleTypes.WAX_ON,
                player.getX(), player.getY() + 1.0, player.getZ(),
                7, 0.25, 0.25, 0.25, 0.02);
        player.awardStat(Stats.ITEM_USED.get(this));
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    public boolean overrideStackedOnOther(ItemStack stack, Slot slot, ClickAction action, Player player) {
        return isOpened(stack) && super.overrideStackedOnOther(stack, slot, action, player);
    }

    @Override
    public boolean overrideOtherStackedOnMe(
            ItemStack stack, ItemStack other, Slot slot, ClickAction action, Player player, SlotAccess access) {
        return isOpened(stack) && super.overrideOtherStackedOnMe(stack, other, slot, action, player, access);
    }

    @Override
    public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
        return isOpened(stack) ? super.getTooltipImage(stack) : Optional.empty();
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        if (!ClientConfig.showCustomTooltips()) {
            if (isOpened(stack)) super.appendHoverText(stack, context, display, tooltip, flag);
            return;
        }
        if (isOpened(stack)) {
            super.appendHoverText(stack, context, display, tooltip, flag);
            tooltip.accept(Component.translatable("tooltip.kingsandmonsters.tribute_bag.opened")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC));
        } else {
            tooltip.accept(Component.translatable("tooltip.kingsandmonsters.tribute_bag.sealed")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC));
            tooltip.accept(Component.translatable("tooltip.kingsandmonsters.tribute_bag.open")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
