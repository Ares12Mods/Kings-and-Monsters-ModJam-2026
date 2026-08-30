package com.kingsandmonsters.effect;

import com.kingsandmonsters.ModMobEffects;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Lightweight sprint suppression. Running on both logical sides prevents client rubber-banding. */
public final class CombatEffectEvents {
    private CombatEffectEvents() {}

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.hasEffect(ModMobEffects.CRIPPLED) && player.isSprinting()) {
            player.setSprinting(false);
        }
    }
}
