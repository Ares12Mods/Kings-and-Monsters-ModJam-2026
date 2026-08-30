package com.kingsandmonsters.tribute;

import com.kingsandmonsters.Config;
import com.kingsandmonsters.entity.OgreGrunt;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

public final class OutpostEvents {

    private OutpostEvents() {}

    public static void onTributeChestOpened(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ServerLevel level = (ServerLevel) player.level();
        BlockPos pos = event.getPos();

        if (!level.getBlockState(pos).is(Blocks.TRAPPED_CHEST)) return;

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof RandomizableContainerBlockEntity)) return;

        // Opportunistic: if this chest belongs to an Outpost whose loot table already unpacked
        // (LootTable tag gone) before its Fort target resolved, the guaranteed map never got a
        // chance to generate normally — deliver it now, right before the player opens the GUI.
        // No-op unless every precondition is met (see FortMapDeliveryService).
        TributeManager.findOutpostCampOrigin(level, pos)
                .ifPresent(campOrigin -> com.kingsandmonsters.world.FortMapDeliveryService.tryDeliver(level, campOrigin));

        // Only trigger the rest (anger/patrol) on first open — loot table tag is present before loot generates
        CompoundTag tag = be.saveWithoutMetadata(level.registryAccess());
        if (!tag.contains("LootTable")) return;

        int campRadius = Config.TRIBUTE_CAMP_KILL_RADIUS.get();
        TributeManager.findNearestCamp(level, pos, campRadius * 4)
            .ifPresent(campOrigin -> {
                boolean angerApplied = TributeManager.recordTributeChestLooted(player, pos, campOrigin,
                    Config.TRIBUTE_CHEST_ANGER_DELTA.get());

                // Anger itself is immediate. Retaliation is not — arm this player's one pending
                // retaliation timer instead of spawning a patrol on the spot, so the tribe gets a
                // believable amount of time to "learn what happened and organize" before a patrol
                // actually shows up. A repeat provocation during that window simply refreshes it.
                if (angerApplied && level.getRandom().nextFloat() < Config.TRIBUTE_PATROL_CHANCE.get()) {
                    long eligibleGameTime = level.getGameTime() + PatrolManager.PATROL_RETALIATION_DELAY_TICKS;
                    TributeManager.armPatrolRetaliation(level, player.getUUID(), campOrigin, eligibleGameTime);
                }
            });
    }

    public static void onOgreKilled(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof OgreGrunt ogre)) return;
        if (ogre.level().isClientSide()) return;
        if (!(ogre.level() instanceof ServerLevel level)) return;

        CompoundTag data = ogre.getPersistentData();

        if (ogre.isOutpostCaptain() && ogre.getOutpostHome() != null) {
            TributeManager.markOutpostCaptainKilled(
                    level,
                    ogre.getOutpostHome(),
                    level.getGameTime() + OutpostPopulationManager.CAPTAIN_RESPAWN_TICKS);
        } else if (ogre.isOutpostResident() && ogre.getOutpostHome() != null) {
            OutpostPopulationManager.scheduleResidentReplacement(level, ogre.getOutpostHome());
        }

        if (data.contains("PatrolCampPos")) {
            // Patrol ogre killed — reduce anger
            BlockPos campPos = BlockPos.of(data.getLongOr("PatrolCampPos", 0L));
            int reduction = data.getIntOr("PatrolAngerReduction", 0);
            TributeManager.getOrCreate(level, campPos).incrementAnger(-reduction);
        } else {
            // Check if killed near a known camp — increase anger
            int radius = Config.TRIBUTE_CAMP_KILL_RADIUS.get();
            TributeManager.findNearestCamp(level, ogre.blockPosition(), radius)
                .ifPresent(campOrigin ->
                    TributeManager.getOrCreate(level, campOrigin)
                        .incrementAnger(Config.TRIBUTE_CAMP_KILL_ANGER.get()));
        }
    }
}
