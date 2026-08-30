package com.kingsandmonsters.world;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Removes decoration-stage vegetation while protecting every authored tree-like
 * block captured immediately after the outpost template is placed.
 */
public final class OutpostVegetationCleaner {
    private static final int UPDATE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static final int VEGETATION_BUFFER = 3;
    private static final int TREE_SEARCH_BUFFER = 8;
    private static final int BELOW_CLEARANCE = 4;
    private static final int CANOPY_CLEARANCE = 24;
    private static final int MAX_CONNECTED_TREE_BLOCKS = 2048;
    private static final ConcurrentMap<CleanupKey, CleanupTask> PENDING = new ConcurrentHashMap<>();

    private OutpostVegetationCleaner() {
    }

    public static void reset() {
        PENDING.clear();
    }

    public static void captureStructureBlocks(
            WorldGenLevel level,
            BoundingBox chunkBox,
            ChunkPos chunkPos,
            PiecesContainer pieces,
            Set<Long> authoredBlocks,
            boolean swamp) {
        BoundingBox structureBox = pieces.calculateBoundingBox();
        BoundingBox seedBox = clippedHorizontal(
                expanded(structureBox, VEGETATION_BUFFER, BELOW_CLEARANCE, CANOPY_CLEARANCE),
                chunkBox,
                level);
        BoundingBox searchBox = clippedHorizontal(
                expanded(structureBox, TREE_SEARCH_BUFFER, BELOW_CLEARANCE, CANOPY_CLEARANCE),
                chunkBox,
                level);
        if (seedBox == null || searchBox == null) {
            return;
        }

        Set<Long> protectedBlocks = new HashSet<>(authoredBlocks);

        CleanupKey key = new CleanupKey(level.getLevel().dimension(), chunkPos.pack());
        PENDING.merge(
                key,
                new CleanupTask(seedBox, searchBox, structureBox, protectedBlocks, swamp),
                CleanupTask::merge);
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        for (CleanupKey key : Set.copyOf(PENDING.keySet())) {
            ServerLevel level = server.getLevel(key.dimension());
            ChunkPos chunkPos = ChunkPos.unpack(key.chunkPos());
            if (level == null || !level.hasChunk(chunkPos.x(), chunkPos.z())) {
                continue;
            }

            CleanupTask task = PENDING.remove(key);
            if (task != null) {
                clear(level, task);
            }
        }
    }

    private static void clear(ServerLevel level, CleanupTask task) {
        Set<Long> removableTrees = new HashSet<>();
        ArrayDeque<BlockPos> open = new ArrayDeque<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        forEach(task.seedBox(), cursor, pos -> {
            long packed = pos.asLong();
            if (task.protectedBlocks().contains(packed)) {
                return;
            }
            BlockState state = level.getBlockState(pos);
            if (isTreePart(state)) {
                if (removableTrees.add(packed)) {
                    open.add(pos.immutable());
                }
            } else if (OutpostTerrainCleaner.isSmallVegetation(state)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), UPDATE_FLAGS);
            }
        });

        while (!open.isEmpty() && removableTrees.size() < MAX_CONNECTED_TREE_BLOCKS) {
            BlockPos current = open.removeFirst();
            for (BlockPos neighbor : sixNeighbors(current)) {
                if (!task.searchBox().isInside(neighbor)) {
                    continue;
                }
                long packed = neighbor.asLong();
                if (task.protectedBlocks().contains(packed) || removableTrees.contains(packed)) {
                    continue;
                }
                if (isTreePart(level.getBlockState(neighbor))) {
                    removableTrees.add(packed);
                    open.addLast(neighbor);
                }
            }
        }

        for (long packed : removableTrees) {
            BlockPos pos = BlockPos.of(packed);
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), UPDATE_FLAGS);
        }
    }

    private static boolean isTreePart(BlockState state) {
        return state.is(BlockTags.LOGS)
                || state.is(BlockTags.LEAVES)
                || state.is(Blocks.VINE)
                || state.is(Blocks.MANGROVE_ROOTS)
                || state.is(Blocks.MUDDY_MANGROVE_ROOTS)
                || state.is(Blocks.HANGING_ROOTS);
    }

    private static BoundingBox expanded(BoundingBox box, int horizontal, int below, int above) {
        return new BoundingBox(
                box.minX() - horizontal, box.minY() - below, box.minZ() - horizontal,
                box.maxX() + horizontal, box.maxY() + above, box.maxZ() + horizontal);
    }

    private static BoundingBox clippedHorizontal(
            BoundingBox box,
            BoundingBox chunkBox,
            WorldGenLevel level) {
        int minX = Math.max(box.minX(), chunkBox.minX());
        int maxX = Math.min(box.maxX(), chunkBox.maxX());
        int minZ = Math.max(box.minZ(), chunkBox.minZ());
        int maxZ = Math.min(box.maxZ(), chunkBox.maxZ());
        if (minX > maxX || minZ > maxZ) {
            return null;
        }
        return new BoundingBox(
                minX, Math.max(level.getMinY(), box.minY()), minZ,
                maxX, Math.min(level.getMaxY(), box.maxY()), maxZ);
    }

    private static BlockPos[] sixNeighbors(BlockPos pos) {
        return new BlockPos[]{
                pos.above(), pos.below(), pos.north(), pos.south(), pos.east(), pos.west()
        };
    }

    private static void forEach(BoundingBox box, BlockPos.MutableBlockPos cursor, PositionConsumer consumer) {
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                for (int y = box.minY(); y <= box.maxY(); y++) {
                    cursor.set(x, y, z);
                    consumer.accept(cursor);
                }
            }
        }
    }

    @FunctionalInterface
    private interface PositionConsumer {
        void accept(BlockPos pos);
    }

    private record CleanupKey(ResourceKey<Level> dimension, long chunkPos) {
    }

    private record CleanupTask(
            BoundingBox seedBox,
            BoundingBox searchBox,
            BoundingBox structureBox,
            Set<Long> protectedBlocks,
            boolean swamp) {
        private CleanupTask merge(CleanupTask other) {
            Set<Long> protectedCombined = new HashSet<>(protectedBlocks);
            protectedCombined.addAll(other.protectedBlocks);
            return new CleanupTask(
                    encompass(seedBox, other.seedBox),
                    encompass(searchBox, other.searchBox),
                    encompass(structureBox, other.structureBox),
                    protectedCombined,
                    swamp || other.swamp);
        }

        private static BoundingBox encompass(BoundingBox first, BoundingBox second) {
            return new BoundingBox(
                    Math.min(first.minX(), second.minX()),
                    Math.min(first.minY(), second.minY()),
                    Math.min(first.minZ(), second.minZ()),
                    Math.max(first.maxX(), second.maxX()),
                    Math.max(first.maxY(), second.maxY()),
                    Math.max(first.maxZ(), second.maxZ()));
        }
    }
}
