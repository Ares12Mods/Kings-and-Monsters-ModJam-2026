package com.kingsandmonsters.effect;

import com.kingsandmonsters.ModMobEffects;
import com.kingsandmonsters.entity.OgreGrunt;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

public final class BogfumeRageEvents {
    public static final int REFRESH_DURATION_TICKS = 160;
    public static final float DAMAGE_TAKEN_MULTIPLIER = 0.85F;
    public static final float DAMAGE_DEALT_MULTIPLIER = 1.2F;

    private BogfumeRageEvents() {
    }

    public static void applyToOgre(OgreGrunt ogre) {
        ogre.addEffect(new MobEffectInstance(
                ModMobEffects.BOGFUME_RAGE,
                REFRESH_DURATION_TICKS,
                0,
                false,
                true,
                true));
    }

    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        float amount = event.getAmount();

        Entity attacker = event.getSource().getEntity();
        if (attacker instanceof OgreGrunt ogre && hasBogfumeRage(ogre)) {
            amount *= DAMAGE_DEALT_MULTIPLIER;
        }

        if (event.getEntity() instanceof OgreGrunt ogre && hasBogfumeRage(ogre)) {
            amount *= DAMAGE_TAKEN_MULTIPLIER;
        }

        event.setAmount(amount);
    }

    private static boolean hasBogfumeRage(LivingEntity entity) {
        return entity.hasEffect(ModMobEffects.BOGFUME_RAGE);
    }
}
