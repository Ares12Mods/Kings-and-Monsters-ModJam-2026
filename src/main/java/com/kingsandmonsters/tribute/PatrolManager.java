package com.kingsandmonsters.tribute;

import com.kingsandmonsters.Config;
import com.kingsandmonsters.ModEntities;
import com.kingsandmonsters.api.patrol.PatrolTier;
import com.kingsandmonsters.entity.OgreArcher;
import com.kingsandmonsters.entity.OgreBrute;
import com.kingsandmonsters.entity.OgreGrunt;
import com.kingsandmonsters.entity.OgreGruntCaptain;
import com.kingsandmonsters.entity.OgreMage;
import com.kingsandmonsters.network.OgreOverlayPayload;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.SectionPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class PatrolManager {
    // Vanilla patrols choose both horizontal offsets from the 24-47 range,
    // which normally puts them more than 32 blocks from their target.
    private static final int MINIMUM_SAFE_PATROL_DISTANCE = 32;
    private static final int REQUIRED_LOADED_RADIUS = 10;
    private static final int PATROL_CHASE_RADIUS = 32;
    private static final int PATROL_REENGAGE_COOLDOWN_TICKS = 200;
    private static final int PATROL_COHESION_INTERVAL_TICKS = 10;
    private static final double PATROL_RALLY_DISTANCE_SQR = 8.0 * 8.0;
    private static final String PATROL_GROUP_ID_TAG = "PatrolGroupId";
    // Navigation-only anchor; deliberately separate from any captain, banner, rank, or loot state.
    private static final String PATROL_COHESION_ANCHOR_TAG = "PatrolCohesionAnchor";
    private static final String PATROL_ENGAGEMENT_POS_TAG = "PatrolEngagementPos";
    private static final String PATROL_REENGAGE_TIME_TAG = "PatrolReengageTime";
    // Delay between a meaningful faction-anger provocation (e.g. looting a tribute chest) and the
    // earliest a retaliation patrol targeting that player becomes eligible to actually spawn — the
    // tribe "learning what happened and organizing a patrol" instead of an instant response.
    public static final int PATROL_RETALIATION_DELAY_TICKS = 20 * 60 * 3;
    private static int ticksUntilNextCheck;
    private static int ticksUntilNextCohesionUpdate;

    private PatrolManager() {
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        if (--ticksUntilNextCohesionUpdate <= 0) {
            ticksUntilNextCohesionUpdate = PATROL_COHESION_INTERVAL_TICKS;
            tickActivePatrolGroups(event.getServer());
        }
        if (!Config.PATROLS_ENABLED.get()) {
            return;
        }

        ticksUntilNextCheck--;
        if (ticksUntilNextCheck > 0) {
            return;
        }

        ticksUntilNextCheck = Config.PATROL_CHECK_INTERVAL_TICKS.get();
        tickPatrols(event.getServer());
        tickPatrolRetaliations(event.getServer());
    }

    private static void tickPatrols(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            tickLevel(level);
        }
    }

    /**
     * Delivers pending per-player retaliations once their delay has elapsed. Runs at the same
     * throttled cadence as the normal patrol check — a due retaliation whose player isn't currently
     * online in this level is left pending and simply re-checked next time, so it survives
     * logout/login instead of being dropped.
     */
    private static void tickPatrolRetaliations(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            long gameTime = level.getGameTime();
            for (TributeSavedData.DuePatrolRetaliation due : TributeManager.peekDuePatrolRetaliations(level, gameTime)) {
                ServerPlayer player = server.getPlayerList().getPlayer(due.playerId());
                if (player == null || player.level() != level || player.isSpectator() || player.isCreative()) {
                    continue;
                }

                PatrolTier tier = TributeManager.getOrCreate(level, due.campOrigin()).getCurrentPatrolTier();
                spawnImmediatePatrol(level, due.campOrigin(), player, tier);
                TributeManager.clearPatrolRetaliation(level, due.playerId());
            }
        }
    }

    private static void tickLevel(ServerLevel level) {
        long gameTime = level.getGameTime();
        long decayInterval = Config.ANGER_DECAY_INTERVAL_TICKS.get();
        long checkInterval = Config.PATROL_CHECK_INTERVAL_TICKS.get();
        boolean doDecay = decayInterval > 0 && (gameTime % decayInterval) < checkInterval;

        if (doDecay && TributeManager.getFactionAngerLevel(level) > 0) {
            TributeManager.decayFactionAnger(level, 1);
        }

        for (TributeSavedData.CampPatrolState camp : TributeManager.getPatrolStates(level)) {
            if (camp.angerLevel() <= 0) {
                continue;
            }
            if (camp.nextPatrolGameTime() <= 0L) {
                // Newly discovered camps start their first real cooldown when
                // they first become angered instead of attacking immediately.
                scheduleNextPatrol(level, camp.campOrigin(), gameTime, true);
                continue;
            }
            if (gameTime < camp.nextPatrolGameTime()) {
                continue;
            }

            Optional<ServerPlayer> target = findTargetPlayer(level, camp.campOrigin());
            if (target.isEmpty()) {
                scheduleNextPatrol(level, camp.campOrigin(), gameTime, false);
                continue;
            }

            TributeManager.preparePatrol(level, camp.campOrigin())
                    .ifPresent(tier -> {
                        spawnPatrol(level, camp.campOrigin(), target.get(), tier);
                        scheduleNextPatrol(level, camp.campOrigin(), gameTime, true);
                    });
        }
    }

    private static Optional<ServerPlayer> findTargetPlayer(ServerLevel level, BlockPos campOrigin) {
        double searchRadiusSqr = Mth.square(Config.PATROL_PLAYER_SEARCH_RADIUS.get());
        List<ServerPlayer> candidates = level.getPlayers(player ->
                !player.isSpectator()
                        && !player.isCreative()
                        && !level.isCloseToVillage(player.blockPosition(), 2)
                        && player.distanceToSqr(campOrigin.getX() + 0.5, campOrigin.getY() + 0.5, campOrigin.getZ() + 0.5) <= searchRadiusSqr);

        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(candidates.get(level.getRandom().nextInt(candidates.size())));
    }

    private static void scheduleNextPatrol(ServerLevel level, BlockPos campOrigin, long gameTime, boolean fullCooldown) {
        RandomSource random = level.getRandom();
        int minCooldown = Config.PATROL_MIN_COOLDOWN_TICKS.get();
        int maxCooldown = Math.max(minCooldown, Config.PATROL_MAX_COOLDOWN_TICKS.get());
        int cooldown = fullCooldown
                ? minCooldown + random.nextInt(maxCooldown - minCooldown + 1)
                : Config.PATROL_CHECK_INTERVAL_TICKS.get();

        TributeManager.setNextPatrolGameTime(level, campOrigin, gameTime + cooldown);
    }

    public static int spawnImmediatePatrol(ServerLevel level, BlockPos campOrigin, ServerPlayer player, PatrolTier tier) {
        int spawned = spawnPatrol(level, campOrigin, player, tier);
        // An immediate retaliation replaces this camp's next scheduled
        // patrol; it must not be followed by another patrol on the next check.
        scheduleNextPatrol(level, campOrigin, level.getGameTime(), true);
        return spawned;
    }

    private static int spawnPatrol(ServerLevel level, BlockPos campOrigin, ServerPlayer target, PatrolTier tier) {
        return spawnPatrol(level, campOrigin, target, target.blockPosition(), tier);
    }

    private static int spawnPatrol(ServerLevel level, BlockPos campOrigin, ServerPlayer target, BlockPos targetPos, PatrolTier tier) {
        List<EntityType<? extends OgreGrunt>> composition = createComposition(tier);
        Optional<BlockPos> baseSpawn = findSpawnPos(level, campOrigin, targetPos, composition.getFirst());
        if (baseSpawn.isEmpty()) {
            return 0;
        }
        BlockPos baseSpawnPos = baseSpawn.get();
        UUID groupId = UUID.randomUUID();
        int spawned = 0;

        for (int i = 0; i < composition.size(); i++) {
            Optional<BlockPos> nearbySpawn = findNearbySpawnPos(
                    level, baseSpawnPos, targetPos, composition.get(i), i);
            if (nearbySpawn.isEmpty()) {
                continue;
            }
            BlockPos spawnPos = nearbySpawn.get();
            OgreGrunt ogre = composition.get(i).spawn(level, spawnPos, EntitySpawnReason.PATROL);
            if (ogre != null) {
                if (target != null) {
                    ogre.setTarget(target);
                }
                ogre.setPersistenceRequired();
                ogre.getPersistentData().putLong("PatrolCampPos", campOrigin.asLong());
                ogre.getPersistentData().putInt("PatrolAngerReduction", patrolAngerReduction(ogre));
                ogre.getPersistentData().store(PATROL_GROUP_ID_TAG, UUIDUtil.CODEC, groupId);
                ogre.getPersistentData().putBoolean(PATROL_COHESION_ANCHOR_TAG, spawned == 0);
                ogre.getPersistentData().putLong(PATROL_ENGAGEMENT_POS_TAG, targetPos.asLong());
                spawned++;
            }
        }

        if (spawned > 0 && target != null) {
            PacketDistributor.sendToPlayer(target, OgreOverlayPayload.location(
                    "OGRE PATROL", "The King's hunters have found your trail.", false));
        }

        return spawned;
    }

    private static void tickActivePatrolGroups(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            Map<UUID, List<OgreGrunt>> groups = new HashMap<>();
            for (var entity : level.getAllEntities()) {
                if (entity instanceof OgreGrunt ogre
                        && ogre.isAlive()
                        && ogre.getPersistentData().read(PATROL_GROUP_ID_TAG, UUIDUtil.CODEC).isPresent()) {
                    groups.computeIfAbsent(
                            ogre.getPersistentData().read(PATROL_GROUP_ID_TAG, UUIDUtil.CODEC).orElseThrow(),
                            ignored -> new ArrayList<>()).add(ogre);
                }
            }
            groups.values().forEach(group -> tickPatrolGroup(level, group));
        }
    }

    private static void tickPatrolGroup(ServerLevel level, List<OgreGrunt> group) {
        OgreGrunt leader = group.stream()
                .filter(ogre -> ogre.getPersistentData().getBooleanOr(PATROL_COHESION_ANCHOR_TAG, false))
                .findFirst()
                .orElse(group.getFirst());
        long gameTime = level.getGameTime();
        long reengageTime = group.stream()
                .mapToLong(ogre -> ogre.getPersistentData().getLongOr(PATROL_REENGAGE_TIME_TAG, 0L))
                .max().orElse(0L);

        Player sharedTarget = group.stream()
                .map(OgreGrunt::getTarget)
                .filter(Player.class::isInstance)
                .map(Player.class::cast)
                .filter(Player::isAlive)
                .findFirst().orElse(null);

        if (sharedTarget != null) {
            if (reengageTime > 0L && gameTime >= reengageTime) {
                BlockPos newEngagementOrigin = sharedTarget.blockPosition();
                for (OgreGrunt ogre : group) {
                    ogre.getPersistentData().putLong(
                            PATROL_ENGAGEMENT_POS_TAG, newEngagementOrigin.asLong());
                    ogre.getPersistentData().remove(PATROL_REENGAGE_TIME_TAG);
                }
                reengageTime = 0L;
            }
            BlockPos engagementPos = BlockPos.of(group.getFirst().getPersistentData()
                    .getLongOr(PATROL_ENGAGEMENT_POS_TAG, 0L));
            if (gameTime < reengageTime
                    || sharedTarget.distanceToSqr(
                    engagementPos.getX() + 0.5,
                    engagementPos.getY() + 0.5,
                    engagementPos.getZ() + 0.5) > (double) PATROL_CHASE_RADIUS * PATROL_CHASE_RADIUS) {
                long nextEngagement = gameTime + PATROL_REENGAGE_COOLDOWN_TICKS;
                for (OgreGrunt ogre : group) {
                    ogre.setTarget(null);
                    ogre.getNavigation().stop();
                    ogre.getPersistentData().putLong(PATROL_REENGAGE_TIME_TAG, nextEngagement);
                }
                sharedTarget = null;
            } else {
                for (OgreGrunt ogre : group) {
                    if (ogre.getTarget() != sharedTarget) {
                        ogre.setTarget(sharedTarget);
                    }
                }
            }
        }

        if (sharedTarget == null) {
            if (gameTime >= reengageTime) {
                Player newlyAcquiredTarget = group.stream()
                        .map(OgreGrunt::getTarget)
                        .filter(Player.class::isInstance)
                        .map(Player.class::cast)
                        .findFirst().orElse(null);
                if (newlyAcquiredTarget != null) {
                    BlockPos origin = newlyAcquiredTarget.blockPosition();
                    for (OgreGrunt ogre : group) {
                        ogre.getPersistentData().putLong(PATROL_ENGAGEMENT_POS_TAG, origin.asLong());
                        ogre.setTarget(newlyAcquiredTarget);
                    }
                    return;
                }
            }
            for (OgreGrunt ogre : group) {
                if (ogre != leader && ogre.getTarget() == null
                        && ogre.distanceToSqr(leader) > PATROL_RALLY_DISTANCE_SQR) {
                    ogre.getNavigation().moveTo(leader, 0.9);
                }
            }
        }
    }

    private static List<EntityType<? extends OgreGrunt>> createComposition(PatrolTier tier) {
        List<EntityType<? extends OgreGrunt>> composition = new ArrayList<>();

        switch (tier) {
            case SMALL -> {
                composition.add(ModEntities.OGRE_GRUNT_CAPTAIN.get());
                addGrunts(composition, 2);
                composition.add(ModEntities.OGRE_ARCHER.get());
            }
            case MEDIUM -> {
                composition.add(ModEntities.OGRE_MAGE.get());
                addGrunts(composition, 2);
                composition.add(ModEntities.OGRE_ARCHER.get());
            }
            case LARGE -> {
                composition.add(ModEntities.OGRE_BRUTE.get());
                addGrunts(composition, 2);
                composition.add(ModEntities.OGRE_ARCHER.get());
                composition.add(ModEntities.OGRE_MAGE.get());
            }
        }

        return composition;
    }

    private static int patrolAngerReduction(OgreGrunt ogre) {
        if (ogre instanceof OgreBrute) return 3;
        if (ogre instanceof com.kingsandmonsters.entity.OgreGuard) return 2;
        if (ogre instanceof OgreMage || ogre instanceof OgreGruntCaptain) return 2;
        return 1; // grunt or archer
    }

    private static void addGrunts(List<EntityType<? extends OgreGrunt>> composition, int count) {
        for (int i = 0; i < count; i++) {
            composition.add(ModEntities.OGRE_GRUNT.get());
        }
    }


    private static Optional<BlockPos> findSpawnPos(
            ServerLevel level,
            BlockPos campOrigin,
            BlockPos targetPos,
            EntityType<? extends OgreGrunt> entityType) {
        RandomSource random = level.getRandom();
        int minDistance = Math.max(MINIMUM_SAFE_PATROL_DISTANCE, Config.PATROL_MIN_SPAWN_DISTANCE.get());
        int maxDistance = Math.max(minDistance, Config.PATROL_MAX_SPAWN_DISTANCE.get());

        double dx = campOrigin.getX() - targetPos.getX();
        double dz = campOrigin.getZ() - targetPos.getZ();
        double angle = Math.atan2(dz, dx);

        if (dx * dx + dz * dz < 1.0) {
            angle = random.nextDouble() * Math.TAU;
        }

        for (int attempt = 0; attempt < 12; attempt++) {
            double spreadAngle = angle + (random.nextDouble() - 0.5) * 1.4;
            int distance = minDistance + random.nextInt(maxDistance - minDistance + 1);
            int x = targetPos.getX() + Mth.floor(Math.cos(spreadAngle) * distance);
            int z = targetPos.getZ() + Mth.floor(Math.sin(spreadAngle) * distance);
            BlockPos spawnPos = atSurface(level, x, z);

            if (isSafePatrolSpawn(level, spawnPos, targetPos, minDistance, entityType)) {
                return Optional.of(spawnPos);
            }
        }

        return Optional.empty();
    }

    private static Optional<BlockPos> findNearbySpawnPos(
            ServerLevel level,
            BlockPos baseSpawnPos,
            BlockPos targetPos,
            EntityType<? extends OgreGrunt> entityType,
            int index) {
        int minDistance = Math.max(MINIMUM_SAFE_PATROL_DISTANCE, Config.PATROL_MIN_SPAWN_DISTANCE.get());
        if (index == 0) {
            return Optional.of(baseSpawnPos);
        }

        RandomSource random = level.getRandom();
        for (int attempt = 0; attempt < 16; attempt++) {
            int x = baseSpawnPos.getX() + random.nextInt(9) - 4;
            int z = baseSpawnPos.getZ() + random.nextInt(9) - 4;
            BlockPos spawnPos = atSurface(level, x, z);

            if (isSafePatrolSpawn(level, spawnPos, targetPos, minDistance, entityType)) {
                return Optional.of(spawnPos);
            }
        }

        return Optional.empty();
    }

    private static BlockPos atSurface(ServerLevel level, int x, int z) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        return new BlockPos(x, y, z);
    }

    private static boolean isSafePatrolSpawn(
            ServerLevel level,
            BlockPos pos,
            BlockPos targetPos,
            int minDistance,
            EntityType<? extends OgreGrunt> entityType) {
        long dx = pos.getX() - targetPos.getX();
        long dz = pos.getZ() - targetPos.getZ();
        if (dx * dx + dz * dz < (long) minDistance * minDistance) {
            return false;
        }

        // Match vanilla's patrol safety envelope: never load terrain just to
        // find a patrol position and keep the whole group inside loaded chunks.
        if (!hasLoadedPatrolArea(level, pos)) {
            return false;
        }

        if (!level.canSeeSky(pos)
                || level.isCloseToVillage(pos, 2)
                || level.getBiome(pos).is(net.minecraft.tags.BiomeTags.WITHOUT_WANDERING_TRADER_SPAWNS)
                || isInsideGeneratedStructure(level, pos)
                || !level.getBlockState(pos.below()).is(BlockTags.VALID_SPAWN)) {
            return false;
        }

        return NaturalSpawner.isValidEmptySpawnBlock(
                level,
                pos,
                level.getBlockState(pos),
                level.getFluidState(pos),
                entityType);
    }

    private static boolean hasLoadedPatrolArea(ServerLevel level, BlockPos pos) {
        int minChunkX = SectionPos.blockToSectionCoord(pos.getX() - REQUIRED_LOADED_RADIUS);
        int maxChunkX = SectionPos.blockToSectionCoord(pos.getX() + REQUIRED_LOADED_RADIUS);
        int minChunkZ = SectionPos.blockToSectionCoord(pos.getZ() - REQUIRED_LOADED_RADIUS);
        int maxChunkZ = SectionPos.blockToSectionCoord(pos.getZ() + REQUIRED_LOADED_RADIUS);

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (!level.hasChunk(chunkX, chunkZ)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean isInsideGeneratedStructure(ServerLevel level, BlockPos pos) {
        StructureStart structure = level.structureManager()
                .getStructureWithPieceAt(pos, holder -> true);
        return structure != StructureStart.INVALID_START && structure.isValid();
    }
}
