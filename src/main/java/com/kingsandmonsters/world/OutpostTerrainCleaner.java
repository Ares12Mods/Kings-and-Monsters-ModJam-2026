package com.kingsandmonsters.world;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Map;
import java.util.Set;

/**
 * Adds shallow foundations only beneath columns actually emitted by the
 * template. It never grades the surrounding terrain, avoiding visible
 * bounding-box or sparse perimeter patterns.
 */
public final class OutpostTerrainCleaner {
    private static final int UPDATE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static final int MAX_FOUNDATION_DEPTH = 8;
    private static final int PLAINS_BLEND_RADIUS = 3;
    private static final int PLAINS_FOUNDATION_DEPTH = 2;
    private static final int ENTRANCE_LENGTH = 7;

    private OutpostTerrainCleaner() {
    }

    /**
     * Development-item helper. World generation uses the protected delayed
     * cleanup in {@link OutpostVegetationCleaner}.
     */
    public static void clearBeforePlacement(LevelAccessor level, BoundingBox box) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int minY = Math.max(level.getMinY(), box.minY());
        int maxY = Math.min(level.getMaxY(), box.maxY() + 12);
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                for (int y = minY; y <= maxY; y++) {
                    pos.set(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (isSmallVegetation(state) || state.is(BlockTags.LEAVES)) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), UPDATE_FLAGS);
                    }
                }
            }
        }
    }

    /**
     * Kept for the structure testing items. No post-placement block-type pass
     * is allowed because it could mistake authored wood or leaves for a tree.
     */
    public static void finishAfterPlacement(LevelAccessor level, BoundingBox box) {
    }

    public static void integrate(
            WorldGenLevel level,
            BoundingBox chunkBox,
            BoundingBox structureBox,
            Map<Long, int[]> columnExtents,
            Set<Long> authoredBlocks,
            boolean swamp) {
        if (columnExtents.isEmpty()) {
            return;
        }
        if (!swamp) {
            normalizePlainsGround(level, chunkBox, columnExtents, authoredBlocks);
            blendPlainsFootprint(level, chunkBox, columnExtents, authoredBlocks);
            clearPlainsEntranceApproaches(level, chunkBox, columnExtents, authoredBlocks);
        }
        supportShallowGaps(level, chunkBox, columnExtents, swamp);
    }

    /**
     * A small generation-only taper outside the plains structure bounds. It
     * never touches authored/interior columns: the first two exterior blocks
     * meet the outpost base, then the allowed height gradually returns to the
     * original terrain over six blocks. Vertical edits are capped so this can
     * clear rolling plains without excavating a hillside.
     */
    private static void blendPlainsFootprint(
            WorldGenLevel level,
            BoundingBox chunkBox,
            Map<Long, int[]> columnExtents,
            Set<Long> authoredBlocks) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int x = chunkBox.minX(); x <= chunkBox.maxX(); x++) {
            for (int z = chunkBox.minZ(); z <= chunkBox.maxZ(); z++) {
                if (columnExtents.containsKey(BlockPos.asLong(x, 0, z))) continue;
                LocalFoundation nearest = nearestFoundation(x, z, columnExtents, PLAINS_BLEND_RADIUS);
                if (nearest == null || !opensAwayFromFootprint(x, z, nearest, columnExtents)) continue;

                int surfaceY = level.getHeight(
                        Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                pos.set(x, surfaceY, z);
                BlockState surface = level.getBlockState(pos);
                if (!isPlainsTerrain(surface) || !surface.getFluidState().isEmpty()) continue;

                int difference = surfaceY - nearest.foundationY();
                // One-block undulation is intentional. Larger contacts receive at most one
                // block of correction per ring, following the real footprint rather than its box.
                if (difference >= 2 && nearest.distance() <= 2) {
                    int newSurfaceY = surfaceY - 1;
                    lowerPlainsColumn(level, pos, x, z, surfaceY, newSurfaceY, authoredBlocks);
                } else if (difference <= -2 && nearest.distance() == 1) {
                    int newSurfaceY = surfaceY + 1;
                    raisePlainsColumn(level, pos, x, z, surfaceY, newSurfaceY, authoredBlocks);
                }
            }
        }
    }

    private static LocalFoundation nearestFoundation(int x, int z, Map<Long, int[]> footprint, int radius) {
        LocalFoundation nearest = null;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int distance = Math.max(Math.abs(dx), Math.abs(dz));
                if (distance == 0 || nearest != null && distance > nearest.distance()) continue;
                int fx = x + dx;
                int fz = z + dz;
                int[] extent = footprint.get(BlockPos.asLong(fx, 0, fz));
                if (extent != null && isFootprintEdge(fx, fz, footprint)) {
                    nearest = new LocalFoundation(fx, fz, extent[0], distance);
                }
            }
        }
        return nearest;
    }

    private static boolean isFootprintEdge(int x, int z, Map<Long, int[]> footprint) {
        return !footprint.containsKey(BlockPos.asLong(x + 1, 0, z))
                || !footprint.containsKey(BlockPos.asLong(x - 1, 0, z))
                || !footprint.containsKey(BlockPos.asLong(x, 0, z + 1))
                || !footprint.containsKey(BlockPos.asLong(x, 0, z - 1));
    }

    private static boolean opensAwayFromFootprint(
            int x, int z, LocalFoundation nearest, Map<Long, int[]> footprint) {
        int stepX = Integer.signum(x - nearest.x());
        int stepZ = Integer.signum(z - nearest.z());
        for (int step = 1; step <= 3; step++) {
            if (footprint.containsKey(BlockPos.asLong(x + stepX * step, 0, z + stepZ * step))) return false;
        }
        return stepX != 0 || stepZ != 0;
    }

    private static void clearPlainsEntranceApproaches(
            WorldGenLevel level, BoundingBox chunkBox, Map<Long, int[]> footprint, Set<Long> authoredBlocks) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (Map.Entry<Long, int[]> entry : footprint.entrySet()) {
            int x = BlockPos.getX(entry.getKey());
            int z = BlockPos.getZ(entry.getKey());
            int floorY = entry.getValue()[0];
            pos.set(x, floorY, z);
            if (!level.getBlockState(pos).is(Blocks.DIRT_PATH) || !isFootprintEdge(x, z, footprint)) continue;

            int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] direction : directions) {
                if (footprint.containsKey(BlockPos.asLong(x + direction[0], 0, z + direction[1]))) continue;
                gradeEntranceRay(level, chunkBox, authoredBlocks, x, z, floorY, direction[0], direction[1]);
            }
        }
    }

    private static void gradeEntranceRay(WorldGenLevel level, BoundingBox chunkBox, Set<Long> authoredBlocks,
            int entranceX, int entranceZ, int floorY, int stepX, int stepZ) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int distance = 1; distance <= ENTRANCE_LENGTH; distance++) {
            int halfWidth = distance <= 4 ? 1 : 0;
            for (int side = -halfWidth; side <= halfWidth; side++) {
                int x = entranceX + stepX * distance + stepZ * side;
                int z = entranceZ + stepZ * distance - stepX * side;
                if (x < chunkBox.minX() || x > chunkBox.maxX() || z < chunkBox.minZ() || z > chunkBox.maxZ()) continue;
                int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                int allowedRise = Math.max(0, (distance - 1) / 2);
                int targetMax = floorY + allowedRise;
                if (surfaceY > targetMax && surfaceY - targetMax <= 3) {
                    lowerPlainsColumn(level, pos, x, z, surfaceY, targetMax, authoredBlocks);
                }
            }
        }
    }

    private record LocalFoundation(int x, int z, int foundationY, int distance) {}

    private static void lowerPlainsColumn(
            WorldGenLevel level,
            BlockPos.MutableBlockPos pos,
            int x,
            int z,
            int oldSurfaceY,
            int newSurfaceY,
            Set<Long> authoredBlocks) {
        for (int y = oldSurfaceY; y > newSurfaceY; y--) {
            pos.set(x, y, z);
            if (authoredBlocks.contains(pos.asLong())
                    || !isPlainsTerrain(level.getBlockState(pos))) return;
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), UPDATE_FLAGS);
        }
        pos.set(x, newSurfaceY, z);
        if (!authoredBlocks.contains(pos.asLong()) && isPlainsTerrain(level.getBlockState(pos))) {
            level.setBlock(pos, Blocks.GRASS_BLOCK.defaultBlockState(), UPDATE_FLAGS);
        }
        clearUnsupportedPlants(level, pos, x, z, newSurfaceY + 1, oldSurfaceY + 2, authoredBlocks);
    }

    private static void raisePlainsColumn(
            WorldGenLevel level,
            BlockPos.MutableBlockPos pos,
            int x,
            int z,
            int oldSurfaceY,
            int newSurfaceY,
            Set<Long> authoredBlocks) {
        for (int y = oldSurfaceY + 1; y <= newSurfaceY; y++) {
            pos.set(x, y, z);
            if (authoredBlocks.contains(pos.asLong()) || !level.getFluidState(pos).isEmpty()) return;
            BlockState existing = level.getBlockState(pos);
            if (!existing.isAir() && !isSmallVegetation(existing)) return;
            level.setBlock(pos, y == newSurfaceY
                    ? Blocks.GRASS_BLOCK.defaultBlockState()
                    : Blocks.DIRT.defaultBlockState(), UPDATE_FLAGS);
        }
        clearUnsupportedPlants(level, pos, x, z, newSurfaceY + 1, newSurfaceY + 3, authoredBlocks);
    }

    private static void clearUnsupportedPlants(
            WorldGenLevel level,
            BlockPos.MutableBlockPos pos,
            int x,
            int z,
            int minY,
            int maxY,
            Set<Long> authoredBlocks) {
        for (int y = minY; y <= maxY; y++) {
            pos.set(x, y, z);
            if (authoredBlocks.contains(pos.asLong())) continue;
            BlockState state = level.getBlockState(pos);
            if (isSmallVegetation(state) && !state.canSurvive(level, pos)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), UPDATE_FLAGS);
            }
        }
    }

    private static boolean isPlainsTerrain(BlockState state) {
        return state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.DIRT)
                || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.PODZOL)
                || state.is(Blocks.ROOTED_DIRT);
    }

    /**
     * The shared outpost templates use a bog-style ground mosaic for their
     * swamp variants. On plains outposts, replace that mosaic at the authored
     * ground plane with grass while preserving intentional dirt paths.
     */
    private static void normalizePlainsGround(
            WorldGenLevel level,
            BoundingBox chunkBox,
            Map<Long, int[]> columnExtents,
            Set<Long> authoredBlocks) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (Map.Entry<Long, int[]> entry : columnExtents.entrySet()) {
            int x = BlockPos.getX(entry.getKey());
            int z = BlockPos.getZ(entry.getKey());
            if (x < chunkBox.minX() || x > chunkBox.maxX() || z < chunkBox.minZ() || z > chunkBox.maxZ()) {
                continue;
            }
            int groundY = entry.getValue()[0];
            pos.set(x, groundY, z);
            if (!authoredBlocks.contains(pos.asLong())) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (state.is(Blocks.PODZOL)
                    || state.is(Blocks.ROOTED_DIRT)
                    || state.is(Blocks.PACKED_MUD)
                    || state.is(Blocks.COARSE_DIRT)) {
                level.setBlock(pos, Blocks.GRASS_BLOCK.defaultBlockState(), UPDATE_FLAGS);
            }
        }
    }

    private static void supportShallowGaps(
            WorldGenLevel level,
            BoundingBox chunkBox,
            Map<Long, int[]> columnExtents,
            boolean swamp) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (Map.Entry<Long, int[]> entry : columnExtents.entrySet()) {
            int x = BlockPos.getX(entry.getKey());
            int z = BlockPos.getZ(entry.getKey());
            if (x < chunkBox.minX() || x > chunkBox.maxX() || z < chunkBox.minZ() || z > chunkBox.maxZ()) {
                continue;
            }
            int baseY = entry.getValue()[0];
            pos.set(x, baseY, z);
            BlockState structureBase = level.getBlockState(pos);
            if (structureBase.isAir() || !structureBase.getFluidState().isEmpty()) {
                continue;
            }
            int maxDepth = swamp ? MAX_FOUNDATION_DEPTH : PLAINS_FOUNDATION_DEPTH;
            for (int depth = 1; depth <= maxDepth; depth++) {
                pos.set(x, baseY - depth, z);
                BlockState state = level.getBlockState(pos);
                if (!state.isAir() && state.getFluidState().isEmpty()) {
                    break;
                }
                level.setBlock(pos, foundationState(swamp, depth), UPDATE_FLAGS);
            }
        }
    }

    private static BlockState foundationState(boolean swamp, int depth) {
        if (swamp && depth <= 2) {
            return Blocks.MUD.defaultBlockState();
        }
        return Blocks.DIRT.defaultBlockState();
    }

    static boolean isSmallVegetation(BlockState state) {
        return state.is(BlockTags.FLOWERS)
                || state.is(BlockTags.SAPLINGS)
                || state.is(Blocks.SHORT_GRASS)
                || state.is(Blocks.TALL_GRASS)
                || state.is(Blocks.LARGE_FERN)
                || state.is(Blocks.FERN)
                || state.is(Blocks.DEAD_BUSH)
                || state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(Blocks.VINE)
                || state.is(Blocks.HANGING_ROOTS)
                || state.is(Blocks.SEAGRASS)
                || state.is(Blocks.TALL_SEAGRASS)
                || state.is(Blocks.KELP)
                || state.is(Blocks.KELP_PLANT)
                || state.is(Blocks.LILY_PAD);
    }

}
