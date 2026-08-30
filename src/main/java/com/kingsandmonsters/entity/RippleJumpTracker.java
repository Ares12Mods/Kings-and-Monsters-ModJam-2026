package com.kingsandmonsters.entity;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.living.LivingEvent;

import java.util.Map;
import java.util.WeakHashMap;

/** Records actual player jump actions without confusing attack-applied vertical velocity for input. */
public final class RippleJumpTracker {
    private static final Map<LivingEntity, Long> LAST_JUMP_GAME_TICK = new WeakHashMap<>();
    private static final Map<LivingEntity, Long> LAST_FORCED_RIPPLE_LAUNCH_TICK = new WeakHashMap<>();

    private RippleJumpTracker() {
    }

    public static void onLivingJump(LivingEvent.LivingJumpEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            LAST_JUMP_GAME_TICK.put(player, player.level().getGameTime());
        }
    }

    public static boolean hasRecentIntentionalJump(LivingEntity target, int graceTicks) {
        Long jumpTick = LAST_JUMP_GAME_TICK.get(target);
        return jumpTick != null
                && target.level().getGameTime() - jumpTick <= graceTicks
                && target.getDeltaMovement().y > 0.03;
    }

    public static void recordForcedRippleLaunch(LivingEntity target) {
        LAST_FORCED_RIPPLE_LAUNCH_TICK.put(target, target.level().getGameTime());
    }

    public static boolean wasRecentlyForcedUpward(LivingEntity target, int ticks) {
        Long launchTick = LAST_FORCED_RIPPLE_LAUNCH_TICK.get(target);
        return launchTick != null && target.level().getGameTime() - launchTick <= ticks;
    }
}
