package com.kingsandmonsters.item;

import com.kingsandmonsters.ClientConfig;

import com.kingsandmonsters.ModBlocks;
import com.kingsandmonsters.client.armor.OgreKingsCrownRenderer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import org.jetbrains.annotations.Nullable;
import com.geckolib.animatable.GeoItem;
import com.geckolib.animatable.client.GeoRenderProvider;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.util.GeckoLibUtil;

import java.util.List;
import java.util.function.Consumer;

public class OgreKingsCrownItem extends Item implements GeoItem {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public OgreKingsCrownItem(ArmorMaterial material, Properties properties) {
        super(properties.humanoidArmor(material, ArmorType.HELMET));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getClickedFace() != Direction.UP) {
            return InteractionResult.FAIL;
        }

        Level level = context.getLevel();
        BlockPlaceContext placementContext = new BlockPlaceContext(context);
        BlockPos pos = placementContext.getClickedPos();
        BlockState replacedState = level.getBlockState(pos);
        BlockPos supportPos = pos.below();
        if (!replacedState.canBeReplaced(placementContext)
                || !level.getBlockState(supportPos).isFaceSturdy(level, supportPos, Direction.UP)) {
            return InteractionResult.FAIL;
        }

        Player player = context.getPlayer();
        if (player != null && !player.mayUseItemAt(pos, Direction.UP, context.getItemInHand())) {
            return InteractionResult.FAIL;
        }

        BlockState crownState = ModBlocks.OGRE_KINGS_CROWN_DISPLAY.get().defaultBlockState()
                .setValue(com.kingsandmonsters.block.OgreKingsCrownBlock.FACING,
                        context.getHorizontalDirection().getOpposite());
        if (!crownState.canSurvive(level, pos)
                || !level.setBlock(pos, crownState, Block.UPDATE_ALL_IMMEDIATE)) {
            return InteractionResult.FAIL;
        }

        var soundType = crownState.getSoundType(level, pos, player);
        level.playSound(player, pos, soundType.getPlaceSound(), SoundSource.BLOCKS,
                (soundType.getVolume() + 1.0F) / 2.0F, soundType.getPitch() * 0.8F);
        level.gameEvent(player, GameEvent.BLOCK_PLACE, pos);
        if (player == null || !player.hasInfiniteMaterials()) {
            context.getItemInHand().shrink(1);
        }
        return level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
        if (!ClientConfig.showCustomTooltips()) return;
        tooltip.accept(Component.empty());
        tooltip.accept(Component.literal("Ogre Sovereignty").withStyle(ChatFormatting.GOLD));
        tooltip.accept(Component.literal("When worn:").withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.literal("Lesser ogres remain neutral until attacked")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.accept(Component.literal("Captains, Mages, and Brutes may challenge your claim")
                .withStyle(ChatFormatting.DARK_PURPLE));
        tooltip.accept(Component.literal("Ogre Kings always remain hostile")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(new GeoRenderProvider() {
            private OgreKingsCrownRenderer renderer;

            @Override
            public OgreKingsCrownRenderer getGeoArmorRenderer(ItemStack itemStack, EquipmentSlot equipmentSlot) {
                if (renderer == null) {
                    renderer = new OgreKingsCrownRenderer();
                }
                return renderer;
            }
        });
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
