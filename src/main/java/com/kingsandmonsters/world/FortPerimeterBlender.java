package com.kingsandmonsters.world;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;

import java.util.Map;
import java.util.Set;

/**
 * Purely cosmetic pass that runs after FortTerrainCleaner's footprint-based
 * grading. That grading is deliberately crisp - it only ever touches the
 * exact columns the fort occupies, which is correct for not scarring
 * terrain but leaves a clean, readable silhouette of the schematic visible
 * from above. This softens that silhouette without touching any placement,
 * carving or fill logic: for a noise-varying band just outside the real
 * footprint, it sparsely swaps surface blocks for blend-compatible swamp
 * textures and occasionally lets a shallow puddle reach toward the wall.
 * Most cells in the band are left completely untouched on purpose, so
 * existing vegetation and terrain survive right up to the walls in places.
 */
public final class FortPerimeterBlender {
    private static final int UPDATE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static final int MIN_BLEND_RADIUS = 1;
    private static final int MAX_BLEND_RADIUS = 6;

    // Low frequency: how far the blend band reaches at this stretch of wall,
    // varying smoothly over ~20-30 blocks so it doesn't sit at a constant
    // distance all the way around.
    private static final double RADIUS_NOISE_SCALE = 1.0 / 18.0;
    // Higher frequency: which cells inside the band actually get touched,
    // in a few-block-wide clumps rather than block-by-block static.
    private static final double COVERAGE_NOISE_SCALE = 1.0 / 5.0;
    private static final double COVERAGE_THRESHOLD = 0.55;
    private static final double PUDDLE_NOISE_SCALE = 1.0 / 7.0;
    private static final double PUDDLE_THRESHOLD = 0.62;
    private static final int PUDDLE_MAX_DISTANCE = 2;

    private static final BlockState[] BLEND_SURFACES = {
            Blocks.MUD.defaultBlockState(),
            Blocks.COARSE_DIRT.defaultBlockState(),
            Blocks.MOSS_BLOCK.defaultBlockState(),
            Blocks.MUD.defaultBlockState(),
            Blocks.DIRT.defaultBlockState()
    };

    private FortPerimeterBlender() {
    }

    public static void blend(WorldGenLevel level, BoundingBox chunkBox, Map<Long, int[]> columnExtents) {
        if (columnExtents.isEmpty()) {
            return;
        }

        Set<Long> footprint = columnExtents.keySet();
        long seed = level.getSeed();
        ImprovedNoise radiusNoise = new ImprovedNoise(RandomSource.create(seed ^ 0x9E3779B97F4A7C15L));
        ImprovedNoise coverageNoise = new ImprovedNoise(RandomSource.create(seed ^ 0xC2B2AE3D27D4EB4FL));
        ImprovedNoise puddleNoise = new ImprovedNoise(RandomSource.create(seed ^ 0x165667B19E3779F9L));

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int x = chunkBox.minX(); x <= chunkBox.maxX(); x++) {
            for (int z = chunkBox.minZ(); z <= chunkBox.maxZ(); z++) {
                long column = BlockPos.asLong(x, 0, z);
                if (footprint.contains(column)) {
                    continue;
                }

                int nearestDist = nearestFootprintDistance(x, z, footprint, MAX_BLEND_RADIUS);
                if (nearestDist > MAX_BLEND_RADIUS) {
                    continue;
                }

                double localRadiusSample = normalize(radiusNoise.noise(x * RADIUS_NOISE_SCALE, 0, z * RADIUS_NOISE_SCALE));
                int localRadius = MIN_BLEND_RADIUS + (int) Math.round(localRadiusSample * (MAX_BLEND_RADIUS - MIN_BLEND_RADIUS));
                if (nearestDist > localRadius) {
                    continue;
                }

                double coverageSample = normalize(coverageNoise.noise(x * COVERAGE_NOISE_SCALE, 0, z * COVERAGE_NOISE_SCALE));
                if (coverageSample < COVERAGE_THRESHOLD) {
                    continue;
                }

                applyBlend(level, pos, x, z, nearestDist, puddleNoise);
            }
        }
    }

    private static void applyBlend(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int nearestDist, ImprovedNoise puddleNoise) {
        int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        pos.set(x, surfaceY, z);
        BlockState surface = level.getBlockState(pos);
        if (!isBlendableGround(surface)) {
            return;
        }

        if (nearestDist <= PUDDLE_MAX_DISTANCE) {
            double puddleSample = normalize(puddleNoise.noise(x * PUDDLE_NOISE_SCALE, 0, z * PUDDLE_NOISE_SCALE));
            if (puddleSample > PUDDLE_THRESHOLD) {
                level.setBlock(pos.atY(surfaceY + 1), Blocks.WATER.defaultBlockState(), UPDATE_FLAGS);
                return;
            }
        }

        int paletteIndex = Math.floorMod((int) hash(x, z), BLEND_SURFACES.length);
        level.setBlock(pos, BLEND_SURFACES[paletteIndex], UPDATE_FLAGS);
    }

    private static boolean isBlendableGround(BlockState state) {
        return state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.DIRT)
                || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.MUD)
                || state.is(Blocks.PODZOL);
    }

    private static int nearestFootprintDistance(int x, int z, Set<Long> footprint, int maxRadius) {
        int nearest = Integer.MAX_VALUE;
        for (int dx = -maxRadius; dx <= maxRadius; dx++) {
            for (int dz = -maxRadius; dz <= maxRadius; dz++) {
                int dist = Math.max(Math.abs(dx), Math.abs(dz));
                if (dist >= nearest) {
                    continue;
                }
                if (footprint.contains(BlockPos.asLong(x + dx, 0, z + dz))) {
                    nearest = dist;
                }
            }
        }
        return nearest;
    }

    private static double normalize(double noiseValue) {
        double scaled = (noiseValue + 1.0) / 2.0;
        return Math.max(0.0, Math.min(1.0, scaled));
    }

    private static long hash(int x, int z) {
        long h = (long) x * 341873128712L + (long) z * 132897987541L;
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        return h;
    }
}
