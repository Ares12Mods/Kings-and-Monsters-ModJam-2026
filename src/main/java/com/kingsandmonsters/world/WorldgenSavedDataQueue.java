package com.kingsandmonsters.world;

import com.kingsandmonsters.tribute.OutpostCaptainType;
import com.kingsandmonsters.tribute.TributeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Moves worldgen discoveries onto the owning server thread and deduplicates per-chunk afterPlace calls. */
public final class WorldgenSavedDataQueue {
    private static final Set<RegistrationKey> FORTS = ConcurrentHashMap.newKeySet();
    private static final Set<RegistrationKey> OUTPOSTS = ConcurrentHashMap.newKeySet();
    private static final Set<RegistrationKey> MAP_CHESTS = ConcurrentHashMap.newKeySet();

    private WorldgenSavedDataQueue() {
    }

    public static void registerFort(ServerLevel level, BlockPos origin) {
        RegistrationKey key = key(level, origin);
        if (!FORTS.add(key)) {
            return;
        }
        level.getServer().execute(() -> TributeManager.registerFort(level, origin.immutable()));
    }

    public static void registerOutpost(
            ServerLevel level,
            BlockPos origin,
            OutpostCaptainType captainType,
            BoundingBox bounds,
            BlockPos fortTarget) {
        RegistrationKey key = key(level, origin);
        if (!OUTPOSTS.add(key)) {
            return;
        }
        BoundingBox immutableBounds = new BoundingBox(
                bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxX(), bounds.maxY(), bounds.maxZ());
        level.getServer().execute(() -> {
            TributeManager.registerOutpost(level, origin.immutable(), captainType, immutableBounds);
            if (fortTarget != null) {
                TributeManager.setOutpostFortMapTarget(level, origin, fortTarget.immutable());
            }
        });
    }

    public static void registerMapChest(ServerLevel level, BlockPos origin, BlockPos chestPos) {
        RegistrationKey key = key(level, origin);
        if (!MAP_CHESTS.add(key)) {
            return;
        }
        level.getServer().execute(() ->
                TributeManager.setOutpostMapChestPosIfAbsent(level, origin.immutable(), chestPos.immutable()));
    }

    public static void reset() {
        FORTS.clear();
        OUTPOSTS.clear();
        MAP_CHESTS.clear();
    }

    private static RegistrationKey key(ServerLevel level, BlockPos origin) {
        return new RegistrationKey(level.getServer(), level.dimension(), origin.asLong());
    }

    private record RegistrationKey(MinecraftServer server, ResourceKey<Level> dimension, long origin) {
    }
}
