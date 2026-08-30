package com.kingsandmonsters.effect;

import com.kingsandmonsters.ModMobEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/** Shared application helpers keep durations at the call site/config and effect flags consistent. */
public final class CombatEffects {
    private CombatEffects() {}

    public static void applyDazed(LivingEntity target, int durationTicks, LivingEntity source) {
        target.addEffect(new MobEffectInstance(ModMobEffects.DAZED, durationTicks, 0, false, true, true), source);
    }

    public static void applyCrippled(LivingEntity target, int durationTicks, LivingEntity source) {
        // Crippled has a fixed commitment window: repeated bites or spear procs cannot reset
        // its timer. Once the active instance expires, a later hit may apply a fresh one.
        if (target.hasEffect(ModMobEffects.CRIPPLED)) {
            return;
        }
        target.addEffect(new MobEffectInstance(ModMobEffects.CRIPPLED, durationTicks, 0, false, true, true), source);
    }

    public static void applyBleeding(LivingEntity target, int durationTicks, LivingEntity source, int amplifier) {
        target.addEffect(new MobEffectInstance(ModMobEffects.BLEEDING, durationTicks, amplifier, false, true, true), source);
    }
}
