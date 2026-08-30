package com.kingsandmonsters.compat.bettercombat;

import net.bettercombat.logic.AnimatedHand;
import net.bettercombat.network.Packets;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public final class BetterCombatClubCompat {
    private BetterCombatClubCompat() {
    }

    public static void playSlam(ServerPlayer player) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
                new Packets.AttackAnimation(
                        player.getId(),
                        AnimatedHand.MAIN_HAND,
                        "bettercombat:one_handed_slam",
                        1.0F,
                        0.5F,
                        4.5F,
                        OgreKingsClubEventsImpactTicks.VALUE,
                        Packets.SwingParticles.EMPTY));
    }

    private static final class OgreKingsClubEventsImpactTicks {
        private static final int VALUE = 10;
    }
}
