package com.kingsandmonsters.item;

import com.kingsandmonsters.ModItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

public final class KingsCleaverEvents {
    private static final String MAWS_FEAST_LAST_PROC_TAG = "KingsAndMonstersMawsFeastLastProc";
    private static final int MAWS_FEAST_COOLDOWN_TICKS = 3 * 20;
    private static final float MAWS_FEAST_HEALING = 4.0F;
    private static final int MAWS_DESPERATION_EFFECT_TICKS = 30;

    private KingsCleaverEvents() {}

    // Two-handed: the offhand is kept locked empty while the cleaver is in the main hand — no
    // shield, food, or anything else usable there. Self-corrects every tick rather than relying on
    // intercepting the specific interaction/swap packet.
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) {
            return;
        }

        if (!player.getMainHandItem().is(ModItems.KINGS_CLEAVER.get())) {
            return;
        }

        if (player.getHealth() <= player.getMaxHealth() * 0.5F) {
            player.addEffect(new MobEffectInstance(
                    MobEffects.STRENGTH, MAWS_DESPERATION_EFFECT_TICKS, 0,
                    false, false, true));
            player.addEffect(new MobEffectInstance(
                    MobEffects.SPEED, MAWS_DESPERATION_EFFECT_TICKS, 0,
                    false, false, true));
        }

        ItemStack offhand = player.getOffhandItem();
        if (offhand.isEmpty()) {
            return;
        }

        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        if (!player.getInventory().add(offhand)) {
            player.drop(offhand, false);
        }
    }

    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)
                || !(player.level() instanceof ServerLevel level)
                || !player.getMainHandItem().is(ModItems.KINGS_CLEAVER.get())
                || player.getHealth() >= player.getMaxHealth()) {
            return;
        }

        long gameTime = level.getGameTime();
        var data = player.getPersistentData();
        if (data.contains(MAWS_FEAST_LAST_PROC_TAG)
                && gameTime - data.getLongOr(MAWS_FEAST_LAST_PROC_TAG, 0L) < MAWS_FEAST_COOLDOWN_TICKS) {
            return;
        }

        player.heal(MAWS_FEAST_HEALING);
        data.putLong(MAWS_FEAST_LAST_PROC_TAG, gameTime);
        level.sendParticles(ParticleTypes.HEART,
                player.getX(), player.getY() + player.getBbHeight() * 0.65, player.getZ(),
                12, 0.45, 0.55, 0.45, 0.08);
    }
}
