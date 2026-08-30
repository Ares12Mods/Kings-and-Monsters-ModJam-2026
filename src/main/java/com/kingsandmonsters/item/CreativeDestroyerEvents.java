package com.kingsandmonsters.item;

import com.kingsandmonsters.ModItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;

/** Isolated Creative-only kill switch for rapidly cycling development encounters. */
public final class CreativeDestroyerEvents {
    private CreativeDestroyerEvents() {
    }

    public static void onAttackEntity(AttackEntityEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()
                || !player.isCreative()
                || !player.getMainHandItem().is(ModItems.CREATIVE_DESTROYER.get())
                || !(event.getTarget() instanceof LivingEntity target)
                || !(player.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        // kill() uses the target's own death lifecycle (including the Ogre King's
        // authored delayed death) without relying on the item's own attack damage.
        target.kill(serverLevel);
        event.setCanceled(true);
    }
}
