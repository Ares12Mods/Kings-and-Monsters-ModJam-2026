package com.kingsandmonsters.entity;

import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;

public final class OgreLordCombatEvents {

    // He's the boss of the faction — a shield should take the edge off, not shrug him off entirely.
    private static final float SHIELD_PIERCE_FRACTION = 0.5F;

    private OgreLordCombatEvents() {}

    public static void onShieldBlock(LivingShieldBlockEvent event) {
        if (!(event.getDamageSource().getEntity() instanceof OgreLord)) {
            return;
        }

        event.setBlockedDamage(event.getOriginalBlockedDamage() * (1.0F - SHIELD_PIERCE_FRACTION));
    }
}
