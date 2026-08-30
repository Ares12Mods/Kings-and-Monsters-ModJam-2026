package com.kingsandmonsters.world;

import com.kingsandmonsters.ModEntities;
import com.kingsandmonsters.entity.OgreGrunt;
import com.kingsandmonsters.entity.OgreGuard;
import com.kingsandmonsters.entity.OgreLord;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Defers fort entity creation until worldgen has released the destination chunks. */
public final class FortPopulationSpawner {
    private static final ConcurrentMap<SpawnKey, KingTask> PENDING_KINGS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<SpawnKey, ResidentTask> PENDING_RESIDENTS = new ConcurrentHashMap<>();

    private FortPopulationSpawner() {
    }

    public static void reset() {
        PENDING_KINGS.clear();
        PENDING_RESIDENTS.clear();
    }

    public static void queueKing(
            ResourceKey<Level> dimension,
            Vec3 position,
            Direction facing,
            List<BlockPos> trials) {
        BlockPos blockPos = BlockPos.containing(position);
        PENDING_KINGS.putIfAbsent(
                new SpawnKey(dimension, blockPos, "king"),
                new KingTask(position, facing, List.copyOf(trials)));
    }

    public static void queueResident(
            ResourceKey<Level> dimension,
            String id,
            BlockPos position,
            Direction facing,
            EntityType<? extends OgreGrunt> entityType,
            int homeRadius,
            boolean eliteGuard) {
        PENDING_RESIDENTS.putIfAbsent(
                new SpawnKey(dimension, position, id),
                new ResidentTask(position.immutable(), facing, entityType, homeRadius, eliteGuard));
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        for (SpawnKey key : Set.copyOf(PENDING_KINGS.keySet())) {
            ServerLevel level = server.getLevel(key.dimension());
            if (level == null || !isChunkLoaded(level, key.position())) {
                continue;
            }
            KingTask task = PENDING_KINGS.remove(key);
            if (task != null) {
                spawnKing(level, task);
            }
        }

        for (SpawnKey key : Set.copyOf(PENDING_RESIDENTS.keySet())) {
            ServerLevel level = server.getLevel(key.dimension());
            if (level == null || !isChunkLoaded(level, key.position())) {
                continue;
            }
            ResidentTask task = PENDING_RESIDENTS.remove(key);
            if (task != null) {
                spawnResident(level, key.id(), task);
            }
        }
    }

    private static boolean isChunkLoaded(ServerLevel level, BlockPos pos) {
        return level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4);
    }

    private static void spawnKing(ServerLevel level, KingTask task) {
        BlockPos blockPos = BlockPos.containing(task.position());
        if (!level.getEntitiesOfClass(OgreLord.class, new AABB(blockPos).inflate(4.0)).isEmpty()) {
            return;
        }

        OgreLord king = new OgreLord(ModEntities.OGRE_LORD.get(), level);
        float yaw = task.facing().toYRot();
        king.snapTo(task.position().x, task.position().y, task.position().z, yaw, 0.0F);
        king.setYHeadRot(yaw);
        king.setYBodyRot(yaw);
        king.setPersistenceRequired();
        king.setFortEncounterTrials(task.trials());
        level.addFreshEntity(king);
    }

    private static void spawnResident(ServerLevel level, String id, ResidentTask task) {
        String residentTag = "KingsAndMonstersFortResident_" + id;
        if (!level.getEntitiesOfClass(OgreGrunt.class, new AABB(task.position()).inflate(2.0),
                ogre -> ogre.entityTags().contains(residentTag)).isEmpty()) {
            return;
        }

        OgreGrunt resident = task.entityType().spawn(level, task.position(), EntitySpawnReason.STRUCTURE);
        if (resident == null) {
            return;
        }
        float yaw = task.facing().toYRot();
        resident.setYRot(yaw);
        resident.setYBodyRot(yaw);
        resident.setYHeadRot(yaw);
        resident.setHomeTo(task.position(), task.homeRadius());
        resident.setPersistenceRequired();
        resident.addTag(residentTag);
        if (task.eliteGuard() && resident instanceof OgreGuard guard) {
            guard.rollEliteSpearEnchantment();
        }
    }

    private record SpawnKey(ResourceKey<Level> dimension, BlockPos position, String id) {
    }

    private record KingTask(Vec3 position, Direction facing, List<BlockPos> trials) {
    }

    private record ResidentTask(
            BlockPos position,
            Direction facing,
            EntityType<? extends OgreGrunt> entityType,
            int homeRadius,
            boolean eliteGuard) {
    }
}
