package com.kingsandmonsters.entity;

import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

public final class OgreDamageCapEvents {

    // No single ogre hit should ever take more than this fraction of a player's max health,
    // regardless of armor — without this, the brute's heavier attacks can one-shot an unarmored
    // player. This fires after armor/shield reductions are already applied, so it's a final ceiling
    // on the actual damage about to land, not a change to how armor or blocking behave.
    private static final float MAX_SINGLE_HIT_FRACTION = 0.7F;

    private OgreDamageCapEvents() {}

    public static void onLivingDamagePre(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        // Indirect sources (arrows, the mage's bolt) don't consistently put the ogre in the same
        // slot — check both the causing and direct entity to catch every attack type.
        if (!(event.getSource().getEntity() instanceof OgreGrunt) && !(event.getSource().getDirectEntity() instanceof OgreGrunt)) {
            return;
        }

        float cap = player.getMaxHealth() * MAX_SINGLE_HIT_FRACTION;
        if (event.getNewDamage() > cap) {
            event.setNewDamage(cap);
        }
    }
}
