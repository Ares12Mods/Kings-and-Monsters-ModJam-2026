package com.kingsandmonsters.item;

import com.kingsandmonsters.ClientConfig;

import com.kingsandmonsters.client.item.OgreKingsClubRenderer;
import com.kingsandmonsters.compat.bettercombat.BetterCombatClubCompat;
import com.kingsandmonsters.compat.bettercombat.BetterCombatClubClientCompat;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import com.geckolib.animatable.GeoItem;
import com.geckolib.animatable.client.GeoRenderProvider;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.util.GeckoLibUtil;
import com.geckolib.renderer.GeoItemRenderer;

import java.util.function.Consumer;
import java.util.List;

public final class OgreKingsClubItem extends Item implements GeoItem {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public OgreKingsClubItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(stack)) {
            return InteractionResult.FAIL;
        }

        if (level.isClientSide() && ModList.get().isLoaded("bettercombat")) {
            BetterCombatClubClientCompat.playSlam(player);
        }

        if (player instanceof ServerPlayer serverPlayer) {
            OgreKingsClubEvents.beginSlam(serverPlayer);
            player.getCooldowns().addCooldown(stack, OgreKingsClubEvents.COOLDOWN_TICKS);
            stack.hurtAndBreak(1, player,
                    hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
            if (ModList.get().isLoaded("bettercombat")) {
                BetterCombatClubCompat.playSlam(serverPlayer);
            } else {
                player.swing(hand, true);
            }
        }
        return level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
        if (!ClientConfig.showCustomTooltips()) return;
        tooltip.accept(Component.empty());
        tooltip.accept(Component.literal("Royal Ground Slam").withStyle(ChatFormatting.GOLD));
        tooltip.accept(Component.literal("Right-click:").withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.literal("Deals 15 damage in a 4.5-block area")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.accept(Component.literal("Launches targets upward")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.accept(Component.literal("15s cooldown").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(new GeoRenderProvider() {
            private OgreKingsClubRenderer renderer;

            @Override
            public GeoItemRenderer<?> getGeoItemRenderer() {
                if (renderer == null) {
                    renderer = new OgreKingsClubRenderer();
                }
                return renderer;
            }
        });
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // Player attacks are driven by Better Combat's built-in mace animation set.
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
