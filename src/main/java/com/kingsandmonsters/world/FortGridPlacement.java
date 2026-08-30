package com.kingsandmonsters.world;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;

/** Bootstrap-independent arithmetic shared by Fort placement, targeting, and corpus tests. */
public final class FortGridPlacement {
    public record Candidate(int x, int z) {}
    @FunctionalInterface
    public interface SwampOracle {
        boolean isSwamp(int chunkX, int chunkZ);
    }

    private FortGridPlacement() {
    }

    public static ChunkPos candidateChunkAt(
            long seed, int regionX, int regionZ, int spacing, int separation, int salt) {
        Candidate candidate = candidateCoordinatesAt(seed, regionX, regionZ, spacing, separation, salt);
        return new ChunkPos(candidate.x(), candidate.z());
    }

    public static Candidate candidateCoordinatesAt(
            long seed, int regionX, int regionZ, int spacing, int separation, int salt) {
        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
        random.setLargeFeatureWithSalt(seed, regionX, regionZ, salt);
        int bound = spacing - separation;
        return new Candidate(
                regionX * spacing + random.nextInt(bound),
                regionZ * spacing + random.nextInt(bound));
    }

    public static boolean isAuthoritativeCandidate(
            long seed, int chunkX, int chunkZ, int spacing, int separation, int salt,
            int spawnExclusionChunks, int minimumSpacingChunks, SwampOracle swamp) {
        int regionX = Math.floorDiv(chunkX, spacing);
        int regionZ = Math.floorDiv(chunkZ, spacing);
        Candidate probe = candidateCoordinatesAt(seed, regionX, regionZ, spacing, separation, salt);
        if (probe.x() != chunkX || probe.z() != chunkZ
                || insideSpawnExclusion(chunkX, chunkZ, spawnExclusionChunks)
                || !swamp.isSwamp(chunkX, chunkZ)) return false;

        long minimumSq = (long) minimumSpacingChunks * minimumSpacingChunks;
        int regionRadius = Math.floorDiv(minimumSpacingChunks + spacing - 1, spacing) + 1;
        long priority = priority(seed, regionX, regionZ);
        for (int otherRegionX = regionX - regionRadius; otherRegionX <= regionX + regionRadius; otherRegionX++) {
            for (int otherRegionZ = regionZ - regionRadius; otherRegionZ <= regionZ + regionRadius; otherRegionZ++) {
                if (otherRegionX == regionX && otherRegionZ == regionZ) continue;
                Candidate other = candidateCoordinatesAt(seed, otherRegionX, otherRegionZ, spacing, separation, salt);
                long dx = (long) other.x() - chunkX;
                long dz = (long) other.z() - chunkZ;
                if (dx * dx + dz * dz >= minimumSq
                        || insideSpawnExclusion(other.x(), other.z(), spawnExclusionChunks)
                        || !swamp.isSwamp(other.x(), other.z())) continue;
                long otherPriority = priority(seed, otherRegionX, otherRegionZ);
                if (Long.compareUnsigned(otherPriority, priority) < 0
                        || (otherPriority == priority && (otherRegionX < regionX
                        || (otherRegionX == regionX && otherRegionZ < regionZ)))) return false;
            }
        }
        return true;
    }

    private static boolean insideSpawnExclusion(int chunkX, int chunkZ, int radius) {
        return (long) chunkX * chunkX + (long) chunkZ * chunkZ < (long) radius * radius;
    }

    private static long priority(long seed, int regionX, int regionZ) {
        long value = seed ^ (long) regionX * 0x9E3779B97F4A7C15L
                ^ (long) regionZ * 0xC2B2AE3D27D4EB4FL ^ 0x165667B19E3779F9L;
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    public static ChunkPos nearestAuthoritativeCandidate(
            long seed, ChunkPos origin, int spacing, int separation, int salt,
            int spawnExclusionChunks, int minimumSpacingChunks, SwampOracle swamp) {
        Candidate result = nearestAuthoritativeCoordinates(seed, origin.x(), origin.z(), spacing, separation, salt,
                spawnExclusionChunks, minimumSpacingChunks, swamp);
        return new ChunkPos(result.x(), result.z());
    }

    public static Candidate nearestAuthoritativeCoordinates(
            long seed, int originX, int originZ, int spacing, int separation, int salt,
            int spawnExclusionChunks, int minimumSpacingChunks, SwampOracle swamp) {
        int originRegionX = Math.floorDiv(originX, spacing);
        int originRegionZ = Math.floorDiv(originZ, spacing);
        Candidate nearest = null;
        long nearestDistance = Long.MAX_VALUE;
        for (int radius = 0; radius < 4096; radius++) {
            for (int regionX = originRegionX - radius; regionX <= originRegionX + radius; regionX++) {
                for (int regionZ = originRegionZ - radius; regionZ <= originRegionZ + radius; regionZ++) {
                    if (radius > 0 && Math.abs(regionX - originRegionX) != radius
                            && Math.abs(regionZ - originRegionZ) != radius) continue;
                    Candidate candidate = candidateCoordinatesAt(seed, regionX, regionZ, spacing, separation, salt);
                    if (!isAuthoritativeCandidate(seed, candidate.x(), candidate.z(), spacing, separation, salt,
                            spawnExclusionChunks, minimumSpacingChunks, swamp)) continue;
                    long dx = (long) candidate.x() - originX;
                    long dz = (long) candidate.z() - originZ;
                    long distance = dx * dx + dz * dz;
                    if (distance < nearestDistance || (distance == nearestDistance
                            && (nearest == null || candidate.x() < nearest.x()
                            || (candidate.x() == nearest.x() && candidate.z() < nearest.z())))) {
                        nearest = candidate;
                        nearestDistance = distance;
                    }
                }
            }
            long outsideLowerBound = Math.max(0L, (long) (radius - 2) * spacing);
            if (nearest != null && outsideLowerBound * outsideLowerBound > nearestDistance) return nearest;
        }
        throw new IllegalStateException("No swamp Fort candidate found within 4096 placement regions");
    }
}
