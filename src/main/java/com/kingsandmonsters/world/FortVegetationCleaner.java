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
 * Removes decoration-stage trees that intersect the fort. Exact blocks emitted
 * by the template processor are protected, so authored wood and foliage survive
 * regardless of biome or modded tree species.
 */
public final class FortVegetationCleaner {
    private static final int UPDATE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static final int CANOPY_CLEARANCE = 16;
    private static final int TREE_SEARCH_BUFFER = 8;
    private static final int MAX_CONNECTED_TREE_BLOCKS = 4096;
    private static final ConcurrentMap<CleanupKey, CleanupTask> PENDING = new ConcurrentHashMap<>();

    private FortVegetationCleaner() {
    }

    public static void reset() {
        PENDING.clear();
    }

    public static void captureStructureBlocks(
            WorldGenLevel level,
            BoundingBox chunkBox,
            ChunkPos chunkPos,
            PiecesContainer pieces,
            Set<Long> authoredBlocks) {
        BoundingBox structureBox = pieces.calculateBoundingBox();
        int minX = Math.max(structureBox.minX(), chunkBox.minX());
        int maxX = Math.min(structureBox.maxX(), chunkBox.maxX());
        int minZ = Math.max(structureBox.minZ(), chunkBox.minZ());
        int maxZ = Math.min(structureBox.maxZ(), chunkBox.maxZ());
        if (minX > maxX || minZ > maxZ) {
            return;
        }

        BoundingBox seedBox = new BoundingBox(
                minX,
                Math.max(level.getMinY(), structureBox.minY()),
                minZ,
                maxX,
                Math.min(level.getMaxY(), structureBox.maxY() + CANOPY_CLEARANCE),
                maxZ
        );
        BoundingBox searchBox = new BoundingBox(
                Math.max(chunkBox.minX(), structureBox.minX() - TREE_SEARCH_BUFFER),
                seedBox.minY(),
                Math.max(chunkBox.minZ(), structureBox.minZ() - TREE_SEARCH_BUFFER),
                Math.min(chunkBox.maxX(), structureBox.maxX() + TREE_SEARCH_BUFFER),
                seedBox.maxY(),
                Math.min(chunkBox.maxZ(), structureBox.maxZ() + TREE_SEARCH_BUFFER)
        );

        Set<Long> protectedBlocks = new HashSet<>(authoredBlocks);

        CleanupKey key = new CleanupKey(level.getLevel().dimension(), chunkPos.pack());
        PENDING.merge(key, new CleanupTask(seedBox, searchBox, structureBox, protectedBlocks), CleanupTask::merge);
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
                removeGeneratedTrees(level, task);
            }
        }
    }

    private static void removeGeneratedTrees(ServerLevel level, CleanupTask task) {
        Set<Long> removable = new HashSet<>();
        ArrayDeque<BlockPos> open = new ArrayDeque<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        forEach(task.seedBox(), cursor, pos -> {
            long packed = pos.asLong();
            if (task.structureBox().isInside(pos)
                    && !task.protectedBlocks().contains(packed)
                    && isTreePart(level.getBlockState(pos))) {
                removable.add(packed);
                open.add(pos.immutable());
            }
        });

        while (!open.isEmpty() && removable.size() < MAX_CONNECTED_TREE_BLOCKS) {
            BlockPos current = open.removeFirst();
            for (BlockPos neighbor : sixNeighbors(current)) {
                if (!task.searchBox().isInside(neighbor)) {
                    continue;
                }

                long packed = neighbor.asLong();
                if (task.protectedBlocks().contains(packed) || removable.contains(packed)) {
                    continue;
                }

                BlockState state = level.getBlockState(neighbor);
                if (isTreePart(state)) {
                    removable.add(packed);
                    open.addLast(neighbor);
                }
            }
        }

        for (long packed : removable) {
            level.setBlock(BlockPos.of(packed), Blocks.AIR.defaultBlockState(), UPDATE_FLAGS);
        }

        // Small decoration can also be placed after surface structures. Only
        // remove it when the terrain work has actually left it unsupported;
        // this preserves authored/naturally supported plants while catching
        // floating carpets, propagules, roots, and vines.
        forEach(task.seedBox(), cursor, pos -> {
            if (task.structureBox().isInside(pos)
                    && !task.protectedBlocks().contains(pos.asLong())) {
                BlockState state = level.getBlockState(pos);
                if (isLooseVegetation(state) && !state.canSurvive(level, pos)) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), UPDATE_FLAGS);
                }
            }
        });
    }

    private static boolean isTreePart(BlockState state) {
        return state.is(BlockTags.LOGS)
                || state.is(BlockTags.LEAVES)
                || state.is(Blocks.VINE)
                || state.is(Blocks.MANGROVE_ROOTS)
                || state.is(Blocks.MUDDY_MANGROVE_ROOTS)
                || state.is(Blocks.HANGING_ROOTS);
    }

    private static boolean isLooseVegetation(BlockState state) {
        return OutpostTerrainCleaner.isSmallVegetation(state)
                || state.is(Blocks.MOSS_CARPET)
                || state.is(Blocks.MANGROVE_PROPAGULE)
                || state.is(Blocks.GLOW_LICHEN);
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
            Set<Long> protectedBlocks) {
        private CleanupTask merge(CleanupTask other) {
            Set<Long> combined = new HashSet<>(protectedBlocks);
            combined.addAll(other.protectedBlocks);
            BoundingBox combinedSeedBox = encompass(seedBox, other.seedBox);
            BoundingBox combinedSearchBox = encompass(searchBox, other.searchBox);
            BoundingBox combinedStructureBox = new BoundingBox(
                    Math.min(structureBox.minX(), other.structureBox.minX()),
                    Math.min(structureBox.minY(), other.structureBox.minY()),
                    Math.min(structureBox.minZ(), other.structureBox.minZ()),
                    Math.max(structureBox.maxX(), other.structureBox.maxX()),
                    Math.max(structureBox.maxY(), other.structureBox.maxY()),
                    Math.max(structureBox.maxZ(), other.structureBox.maxZ())
            );
            return new CleanupTask(combinedSeedBox, combinedSearchBox, combinedStructureBox, combined);
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
