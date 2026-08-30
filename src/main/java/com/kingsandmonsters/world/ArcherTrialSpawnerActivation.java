package com.kingsandmonsters.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.TrialSpawnerBlockEntity;
import net.minecraft.world.level.block.entity.trialspawner.PlayerDetector;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawner;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.event.level.ChunkEvent;

/**
 * Lets tower Archer trials detect players through the tower floor. Vanilla's
 * first trial-spawner scan requires line of sight, which prevents a player
 * walking directly below a rooftop spawner from activating it.
 */
public final class ArcherTrialSpawnerActivation {
    private static final int ARCHER_TOWER_ACTIVATION_RANGE = 24;
    private static final int LEGACY_ARCHER_TOWER_ACTIVATION_RANGE = 48;
    private static final double TOWER_HORIZONTAL_RADIUS = 12.0;
    private static final double NORMAL_ACTIVATION_RANGE = 14.0;
    private static final PlayerDetector THROUGH_TOWER_DETECTOR =
            (level, selector, pos, maxDistance, requireLineOfSight) ->
                    selector.getPlayers(level, player ->
                                    isInActivationArea(player, pos, maxDistance)
                                            && !player.isCreative()
                                            && !player.isSpectator())
                            .stream()
                            .map(Entity::getUUID)
                            .toList();

    private ArcherTrialSpawnerActivation() {
    }

    private static boolean isInActivationArea(Player player, BlockPos spawnerPos,
                                              double maxDistance) {
        if (player.blockPosition().closerThan(spawnerPos, NORMAL_ACTIVATION_RANGE)) {
            return true;
        }

        double dx = player.getX() - (spawnerPos.getX() + 0.5);
        double dz = player.getZ() - (spawnerPos.getZ() + 0.5);
        double downwardDistance = spawnerPos.getY() + 0.5 - player.getEyeY();
        return downwardDistance >= 0.0
                && downwardDistance <= Math.min(maxDistance, ARCHER_TOWER_ACTIVATION_RANGE)
                && dx * dx + dz * dz <= TOWER_HORIZONTAL_RADIUS * TOWER_HORIZONTAL_RADIUS;
    }

    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel)
                || !(event.getChunk() instanceof LevelChunk levelChunk)) {
            return;
        }

        for (BlockEntity blockEntity : levelChunk.getBlockEntities().values()) {
            if (!(blockEntity instanceof TrialSpawnerBlockEntity trialSpawnerBlockEntity)) {
                continue;
            }

            TrialSpawner trialSpawner = trialSpawnerBlockEntity.getTrialSpawner();
            int requiredRange = trialSpawner.getRequiredPlayerRange();
            if (requiredRange == ARCHER_TOWER_ACTIVATION_RANGE
                    || requiredRange == LEGACY_ARCHER_TOWER_ACTIVATION_RANGE) {
                trialSpawner.setPlayerDetector(THROUGH_TOWER_DETECTOR);
            }
        }
    }
}
