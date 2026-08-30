package com.kingsandmonsters.item;

import com.kingsandmonsters.enchantment.ModEnchantmentEffects;
import com.kingsandmonsters.enchantment.ModEnchantments;
import com.kingsandmonsters.entity.OgreSpear;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** A trident-style charge-and-release weapon backed by the custom OgreSpear projectile. */
public final class HunterSpearItem extends Item {
    private static final int THROW_CHARGE_TICKS = 10;
    private static final int USE_DURATION_TICKS = 72_000;
    private static final float BASE_THROW_DAMAGE = 8.0F;
    private static final float BASE_THROW_SPEED = 2.5F;

    public HunterSpearItem(Properties properties) {
        super(properties);
    }

    public static ItemAttributeModifiers createAttributes() {
        return ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, 7.0,
                        AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, -2.9,
                        AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .build();
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.getDamageValue() >= stack.getMaxDamage() - 1) {
            return InteractionResult.FAIL;
        }
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity user, int timeLeft) {
        if (!(user instanceof Player player) || getUseDuration(stack, user) - timeLeft < THROW_CHARGE_TICKS) {
            return false;
        }

        ItemStack thrownStack = stack.copyWithCount(1);
        thrownStack.hurtAndBreak(1, player,
                player.getUsedItemHand() == InteractionHand.MAIN_HAND ?
                        net.minecraft.world.entity.EquipmentSlot.MAINHAND : net.minecraft.world.entity.EquipmentSlot.OFFHAND);
        if (!level.isClientSide()) {
            int heavyThrowLevel = ModEnchantmentEffects.level(level, thrownStack, ModEnchantments.HEAVY_THROW);
            float speed = ModEnchantmentEffects.heavyThrowSpeed(BASE_THROW_SPEED, heavyThrowLevel);
            float damage = ModEnchantmentEffects.heavyThrowDamage(BASE_THROW_DAMAGE, heavyThrowLevel);
            Vec3 origin = player.getEyePosition().add(player.getLookAngle().scale(0.35));
            OgreSpear.Pickup pickup = player.hasInfiniteMaterials()
                    ? OgreSpear.Pickup.CREATIVE_ONLY : OgreSpear.Pickup.ALLOWED;
            OgreSpear spear = new OgreSpear(level, player, thrownStack, origin,
                    player.getLookAngle().scale(speed), damage, pickup);
            level.addFreshEntity(spear);
        }

        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_THROW,
                player.getSoundSource(), 1.0F, 1.0F);
        player.awardStat(Stats.ITEM_USED.get(this));
        if (!player.hasInfiniteMaterials()) {
            stack.shrink(1);
        }
        return true;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        // NOTE (26.1.2 port): the enum constant was renamed between versions. 1.21.1's
        // UseAnim.SPEAR *was* the trident charge/throw pose; in 26.1.2 that pose is
        // ItemUseAnimation.TRIDENT, and ItemUseAnimation.SPEAR is a brand-new pose for
        // vanilla's kinetic spear weapons (driven by SpearAnimations + the KINETIC_WEAPON /
        // SWING_ANIMATION components, and flagged hasCustomArmTransform()). Using SPEAR here
        // skipped ItemInHandRenderer.applyItemArmTransform entirely, which is why the spear
        // rendered as an unpositioned oversized held item.
        return ItemUseAnimation.TRIDENT;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return USE_DURATION_TICKS;
    }

    public int getEnchantmentValue(ItemStack stack) {
        return 15;
    }

}

