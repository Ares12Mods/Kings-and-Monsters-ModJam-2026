package com.kingsandmonsters.entity;

import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;

public final class OgreBruteCombatEvents {

    // A well-timed block should meaningfully reward iron+shield players, not just shave the edge off
    // a hit — only this fraction of a blocked attack's raw damage pierces through the shield.
    private static final float NON_TANTRUM_SHIELD_PIERCE_FRACTION = 0.35F;

    private OgreBruteCombatEvents() {}

    public static void onShieldBlock(LivingShieldBlockEvent event) {
        if (!(event.getDamageSource().getEntity() instanceof OgreBrute brute)) {
            return;
        }

        if (brute.isTemperTantrumActive()) {
            // Temper tantrum bypasses shields entirely — full damage and knockback go through.
            event.setBlocked(false);
            return;
        }

        // Other brute attacks still partially pierce shields rather than being fully negated.
        event.setBlockedDamage(event.getOriginalBlockedDamage() * (1.0F - NON_TANTRUM_SHIELD_PIERCE_FRACTION));
    }
}
