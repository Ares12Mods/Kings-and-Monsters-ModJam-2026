package com.kingsandmonsters.compat.bettercombat;

import net.bettercombat.client.animation.PlayerAttackAnimatable;
import net.bettercombat.logic.AnimatedHand;
import net.minecraft.world.entity.player.Player;

/** Client-side entry point for starting the local player's custom club attack. */
public final class BetterCombatClubClientCompat {
    private BetterCombatClubClientCompat() {
    }

    public static void playSlam(Player player) {
        ((PlayerAttackAnimatable) player).playAttackAnimation(
                "bettercombat:one_handed_slam",
                AnimatedHand.MAIN_HAND,
                1.0F,
                0.5F);
    }
}
