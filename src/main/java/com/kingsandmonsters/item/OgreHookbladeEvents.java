package com.kingsandmonsters.item;

import com.kingsandmonsters.ModItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

public final class OgreHookbladeEvents {
    private static final int PULL_COOLDOWN_TICKS = 3 * 20;
    private static final int SLOWNESS_TICKS = 30;
    private static final double PULL_STRENGTH = 0.85;

    private OgreHookbladeEvents() {
    }

    public static void onLivingDamagePost(LivingDamageEvent.Post event) {
        if (event.getHealthDamage() <= 0.0F
                || !(event.getSource().getEntity() instanceof ServerPlayer attacker)
                || event.getSource().getDirectEntity() != attacker
                || !attacker.getMainHandItem().is(ModItems.OGRE_HOOKBLADE.get())
                || attacker.getCooldowns().isOnCooldown(attacker.getMainHandItem())) {
            return;
        }

        LivingEntity target = event.getEntity();
        if (!target.isAlive()
                || target.is(Tags.EntityTypes.BOSSES)
                || target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) >= 1.0) {
            return;
        }

        Vec3 towardAttacker = attacker.position().subtract(target.position());
        Vec3 horizontal = new Vec3(towardAttacker.x, 0.0, towardAttacker.z);
        if (horizontal.lengthSqr() < 1.0E-4) {
            return;
        }

        Vec3 pull = horizontal.normalize().scale(PULL_STRENGTH);
        Vec3 existing = target.getDeltaMovement();
        target.setDeltaMovement(
                pull.x + existing.x * 0.2,
                Math.max(existing.y, 0.12),
                pull.z + existing.z * 0.2);
        target.hurtMarked = true;
        target.hurtMarked = true;
        target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SLOWNESS_TICKS, 0), attacker);
        attacker.getCooldowns().addCooldown(attacker.getMainHandItem(), PULL_COOLDOWN_TICKS);
    }
}
