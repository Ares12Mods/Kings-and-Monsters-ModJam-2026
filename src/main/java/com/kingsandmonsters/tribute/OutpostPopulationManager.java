package com.kingsandmonsters.tribute;

import com.kingsandmonsters.ModEntities;
import com.kingsandmonsters.entity.OgreGrunt;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.List;
import java.util.Optional;
import java.util.Arrays;

/** Maintains the permanent 3 Grunt + 2 Archer + 1 Captain population at each Ogre outpost. */
public final class OutpostPopulationManager {
    public static final int RESIDENT_GRUNT_COUNT = 3;
    public static final int RESIDENT_ARCHER_COUNT = 2;
    public static final int CAPTAIN_RESPAWN_TICKS = 5 * 24_000;

    private static final int CHECK_INTERVAL_TICKS = 40;
    private static final int INITIAL_RETRY_TICKS = 20 * 20;
    private static final int MIN_RESIDENT_RESPAWN_TICKS = 2 * 60 * 20;
    private static final int MAX_RESIDENT_RESPAWN_TICKS = 4 * 60 * 20;
    private static final int ACTIVATION_RADIUS = 128;
    private static final int COUNT_RADIUS = 48;
    private static final int FOOTPRINT_INSET = 4;
    private static final int SPAWN_HEIGHT_BAND = 8;
    private static final int CAPTAIN_CENTER_SEARCH_RADIUS = 5;
    private static final int SPAWN_ATTEMPTS = 24;

    private OutpostPopulationManager() {}

    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % CHECK_INTERVAL_TICKS != 0) return;

        for (ServerLevel level : server.getAllLevels()) {
            tickLevel(level);
        }
    }

    private static void tickLevel(ServerLevel level) {
        if (level.getDifficulty() == Difficulty.PEACEFUL) return;

        for (TributeSavedData.OutpostPopulationState outpost
                : TributeManager.getOutpostPopulationStates(level)) {
            BlockPos origin = outpost.campOrigin();
            if (!hasNearbyPlayer(level, origin)
                    || !level.hasChunk(SectionPos.blockToSectionCoord(origin.getX()),
                    SectionPos.blockToSectionCoord(origin.getZ()))) continue;

            Population population = countPopulation(level, origin);
            long gameTime = level.getGameTime();

            if (!outpost.populationInitialized()) {
                if (gameTime < outpost.nextResidentSpawnGameTime()) continue;
                initializePopulation(level, outpost, population, gameTime);
                continue;
            }

            maintainResidents(level, outpost, population, gameTime);
            maintainCaptain(level, outpost, population, gameTime);
        }
    }

    private static boolean hasNearbyPlayer(ServerLevel level, BlockPos origin) {
        double radiusSqr = (double) ACTIVATION_RADIUS * ACTIVATION_RADIUS;
        return level.getPlayers(player -> !player.isSpectator()
                && player.distanceToSqr(Vec3.atCenterOf(origin)) <= radiusSqr).size() > 0;
    }

    private static Population countPopulation(ServerLevel level, BlockPos origin) {
        AABB bounds = new AABB(origin).inflate(COUNT_RADIUS, 24, COUNT_RADIUS);
        int grunts = 0;
        int archers = 0;
        int captains = 0;

        for (OgreGrunt ogre : level.getEntitiesOfClass(
                OgreGrunt.class, bounds, ogre -> ogre.isAlive() && ogre.isOutpostResidentAt(origin))) {
            if (ogre.isOutpostCaptain()) {
                captains++;
            } else if (ogre.getType() == ModEntities.OGRE_ARCHER.get()) {
                archers++;
            } else if (ogre.getType() == ModEntities.OGRE_GRUNT.get()) {
                grunts++;
            }
        }
        return new Population(grunts, archers, captains);
    }

    private static void initializePopulation(
            ServerLevel level,
            TributeSavedData.OutpostPopulationState outpost,
            Population population,
            long gameTime) {
        int grunts = population.grunts();
        int archers = population.archers();
        int captains = population.captains();

        while (grunts < RESIDENT_GRUNT_COUNT
                && spawnResident(level, outpost, ModEntities.OGRE_GRUNT.get(), false)) {
            grunts++;
        }
        while (archers < RESIDENT_ARCHER_COUNT
                && spawnResident(level, outpost, ModEntities.OGRE_ARCHER.get(), false)) {
            archers++;
        }
        if (captains < 1
                && spawnResident(level, outpost, outpost.captainType().entityType(), true)) {
            captains++;
        }

        if (grunts >= RESIDENT_GRUNT_COUNT
                && archers >= RESIDENT_ARCHER_COUNT
                && captains >= 1) {
            TributeManager.markPopulationInitialized(
                    level, outpost.campOrigin(), nextResidentRespawnTime(level, gameTime));
            TributeManager.markOutpostCaptainAlive(level, outpost.campOrigin());
        } else {
            TributeManager.setNextResidentSpawnGameTime(
                    level, outpost.campOrigin(), gameTime + INITIAL_RETRY_TICKS);
        }
    }

    private static void maintainResidents(
            ServerLevel level,
            TributeSavedData.OutpostPopulationState outpost,
            Population population,
            long gameTime) {
        if (population.grunts() >= RESIDENT_GRUNT_COUNT
                && population.archers() >= RESIDENT_ARCHER_COUNT) {
            return;
        }
        if (gameTime < outpost.nextResidentSpawnGameTime()) return;

        EntityType<? extends OgreGrunt> missingType = population.grunts() < RESIDENT_GRUNT_COUNT
                ? ModEntities.OGRE_GRUNT.get()
                : ModEntities.OGRE_ARCHER.get();
        if (spawnResident(level, outpost, missingType, false)) {
            TributeManager.setNextResidentSpawnGameTime(
                    level, outpost.campOrigin(), nextResidentRespawnTime(level, gameTime));
        } else {
            TributeManager.setNextResidentSpawnGameTime(
                    level, outpost.campOrigin(), gameTime + INITIAL_RETRY_TICKS);
        }
    }

    private static void maintainCaptain(
            ServerLevel level,
            TributeSavedData.OutpostPopulationState outpost,
            Population population,
            long gameTime) {
        if (population.captains() > 0) {
            if (outpost.captainRespawnGameTime() > 0L) {
                TributeManager.markOutpostCaptainAlive(level, outpost.campOrigin());
            }
            return;
        }

        if (outpost.captainRespawnGameTime() <= 0L) {
            TributeManager.markOutpostCaptainKilled(
                    level, outpost.campOrigin(), gameTime + CAPTAIN_RESPAWN_TICKS);
            return;
        }
        if (gameTime < outpost.captainRespawnGameTime()) return;

        if (spawnResident(level, outpost, outpost.captainType().entityType(), true)) {
            TributeManager.markOutpostCaptainAlive(level, outpost.campOrigin());
        }
    }

    private static long nextResidentRespawnTime(ServerLevel level, long gameTime) {
        int range = MAX_RESIDENT_RESPAWN_TICKS - MIN_RESIDENT_RESPAWN_TICKS;
        return gameTime + MIN_RESIDENT_RESPAWN_TICKS + level.getRandom().nextInt(range + 1);
    }

    public static void scheduleResidentReplacement(ServerLevel level, BlockPos campOrigin) {
        TributeManager.setNextResidentSpawnGameTime(
                level, campOrigin, nextResidentRespawnTime(level, level.getGameTime()));
    }

    private static boolean spawnResident(
            ServerLevel level,
            TributeSavedData.OutpostPopulationState outpost,
            EntityType<? extends OgreGrunt> entityType,
            boolean captain) {
        BlockPos origin = outpost.campOrigin();
        SpawnArea area = spawnArea(level, outpost);
        Optional<BlockPos> spawnPos = captain
                ? findCaptainSpawnPos(level, origin, area, entityType)
                : findSpawnPos(level, area, entityType);
        if (spawnPos.isEmpty()) return false;

        OgreGrunt ogre = entityType.spawn(level, spawnPos.get(), EntitySpawnReason.STRUCTURE);
        if (ogre == null) return false;
        ogre.assignOutpostResident(origin, captain);
        return true;
    }

    /**
     * Captains are the visual anchor of an outpost, so they spawn at its X/Z
     * center instead of sharing the residents' 6-24 block random ring. If the
     * exact center is obstructed, search only the nearest five blocks and pick
     * the first safe position; this keeps the captain inside the courtyard.
     */
    private static Optional<BlockPos> findCaptainSpawnPos(
            ServerLevel level,
            BlockPos origin,
            SpawnArea area,
            EntityType<? extends OgreGrunt> entityType) {
        for (int radius = 0; radius <= CAPTAIN_CENTER_SEARCH_RADIUS; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (radius > 0 && Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    Optional<BlockPos> candidate = safeSurfaceSpawn(
                            level, origin.getX() + dx, origin.getZ() + dz, area, entityType);
                    if (candidate.isPresent()) {
                        return candidate;
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<BlockPos> findSpawnPos(
            ServerLevel level,
            SpawnArea area,
            EntityType<? extends OgreGrunt> entityType) {
        RandomSource random = level.getRandom();
        for (int attempt = 0; attempt < SPAWN_ATTEMPTS; attempt++) {
            int x = random.nextIntBetweenInclusive(area.minX(), area.maxX());
            int z = random.nextIntBetweenInclusive(area.minZ(), area.maxZ());
            Optional<BlockPos> candidate = safeSurfaceSpawn(level, x, z, area, entityType);
            if (candidate.isPresent()) return candidate;
        }
        return Optional.empty();
    }

    private static Optional<BlockPos> safeSurfaceSpawn(
            ServerLevel level,
            int x,
            int z,
            SpawnArea area,
            EntityType<? extends OgreGrunt> entityType) {
        if (!area.contains(x, z)) return Optional.empty();
        if (!level.hasChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z))) {
            return Optional.empty();
        }

        for (int y = area.minY(); y <= area.maxY(); y++) {
            BlockPos pos = new BlockPos(x, y, z);
            if (isSafeGroundSpawn(level, pos, entityType) && hasWalkableExit(level, pos, area, entityType)) {
                return Optional.of(pos);
            }
        }
        return Optional.empty();
    }

    private static boolean isSafeGroundSpawn(
            ServerLevel level, BlockPos pos, EntityType<? extends OgreGrunt> entityType) {
        BlockState floor = level.getBlockState(pos.below());
        if (!level.getWorldBorder().isWithinBounds(pos)
                || !level.getFluidState(pos).isEmpty()
                || isRejectedFloor(floor)
                || !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                || !floor.isFaceSturdy(level, pos.below(), Direction.UP)) {
            return false;
        }

        AABB spawnBox = entityType.getSpawnAABB(
                pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        return level.noCollision(spawnBox);
    }

    private static boolean hasWalkableExit(
            ServerLevel level,
            BlockPos pos,
            SpawnArea area,
            EntityType<? extends OgreGrunt> entityType) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos adjacent = pos.relative(direction);
            if (area.contains(adjacent.getX(), adjacent.getZ())
                    && isSafeGroundSpawn(level, adjacent, entityType)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isRejectedFloor(BlockState floor) {
        return floor.is(Blocks.POINTED_DRIPSTONE)
                || floor.is(BlockTags.FENCES)
                || floor.is(BlockTags.WALLS)
                || floor.is(BlockTags.LEAVES)
                || floor.is(BlockTags.LOGS)
                || floor.getBlock() instanceof FenceGateBlock
                || floor.is(Blocks.CAMPFIRE)
                || floor.is(Blocks.SOUL_CAMPFIRE)
                || floor.is(Blocks.MAGMA_BLOCK)
                || floor.is(Blocks.CACTUS)
                || floor.is(Blocks.SWEET_BERRY_BUSH)
                || floor.getBlock() instanceof BaseFireBlock;
    }

    private static SpawnArea spawnArea(
            ServerLevel level, TributeSavedData.OutpostPopulationState outpost) {
        if (outpost.hasOutpostBounds()) {
            int minX = outpost.outpostMinX() + FOOTPRINT_INSET;
            int maxX = outpost.outpostMaxX() - FOOTPRINT_INSET;
            int minZ = outpost.outpostMinZ() + FOOTPRINT_INSET;
            int maxZ = outpost.outpostMaxZ() - FOOTPRINT_INSET;
            return new SpawnArea(minX, maxX, minZ, maxZ,
                    outpost.outpostGroundY(), outpost.outpostGroundY() + SPAWN_HEIGHT_BAND);
        }

        // Migration fallback for outposts saved before footprint bounds existed.
        // A low percentile ignores roofs while keeping the search finite and local.
        BlockPos origin = outpost.campOrigin();
        int minX = origin.getX() - 20;
        int maxX = origin.getX() + 20;
        int minZ = origin.getZ() - 20;
        int maxZ = origin.getZ() + 20;
        int[] samples = new int[25];
        int index = 0;
        for (int dx = -16; dx <= 16; dx += 8) {
            for (int dz = -16; dz <= 16; dz += 8) {
                samples[index++] = level.getHeight(
                        Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        origin.getX() + dx,
                        origin.getZ() + dz);
            }
        }
        Arrays.sort(samples);
        int groundY = samples[samples.length / 4] - 2;
        return new SpawnArea(minX, maxX, minZ, maxZ, groundY, groundY + SPAWN_HEIGHT_BAND);
    }

    private record SpawnArea(int minX, int maxX, int minZ, int maxZ, int minY, int maxY) {
        private boolean contains(int x, int z) {
            return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
        }
    }

    private record Population(int grunts, int archers, int captains) {}
}
