package com.kingsandmonsters.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;

import java.util.List;
import java.util.Optional;

/**
 * Random-spread placement whose own candidates are additionally gated by a
 * purely arithmetic "is this within an Ogre King Fort's broader territory"
 * test, so bonus outposts can feel associated with a nearby fort without any
 * of the costly or cyclical machinery that would come from actually locating
 * one.
 *
 * The fort's grid (fort_spacing/fort_separation/fort_salt) is duplicated
 * here as plain numbers, NOT as a reference to the fort's real StructureSet.
 * That is the whole point: this class never calls into the fort's actual
 * StructurePlacement/isStructureChunk, so it can never form a recursive
 * exclusion cycle, never depends on whether a fort candidate has actually
 * passed its own biome/terrain checks (or even whether that chunk has ever
 * been generated), and costs nothing more than a bounded loop of plain
 * WorldgenRandom hashing - no chunk loading, no biome sampling, no terrain
 * query. Keep fort_spacing/fort_separation/fort_salt here in sync by hand
 * with ogre_kings_fort.json's placement block if that ever changes.
 */
@SuppressWarnings("deprecation")
public final class FortTerritoryStructurePlacement extends RandomSpreadStructurePlacement {
    /*
     * placementCodec(instance) (used by MultiExclusionRandomSpreadStructurePlacement)
     * returns a 5-field product, and Mojang's codec builder only defines
     * and()-chaining overloads that top out at 8 total fields combined -
     * P8 itself has no further and() at all. We need 9 fields, so instead
     * of chaining onto placementCodec, this builds one flat instance.group(...)
     * call (which supports up to 16 fields directly) and supplies the base
     * StructurePlacement fields we don't otherwise need (locate_offset,
     * frequency, the single legacy exclusion_zone) as fixed defaults, the
     * same way RandomSpreadStructurePlacement's own 4-arg convenience
     * constructor does.
     */
    public static final MapCodec<FortTerritoryStructurePlacement> CODEC =
            RecordCodecBuilder.<FortTerritoryStructurePlacement>mapCodec(instance -> instance.group(
                            Codec.INT.fieldOf("salt").forGetter(FortTerritoryStructurePlacement::ownSalt),
                            Codec.intRange(1, 4096).fieldOf("spacing")
                                    .forGetter(RandomSpreadStructurePlacement::spacing),
                            Codec.intRange(0, 4095).fieldOf("separation")
                                    .forGetter(RandomSpreadStructurePlacement::separation),
                            StructurePlacement.ExclusionZone.CODEC.listOf()
                                    .optionalFieldOf("exclusion_zones", List.of())
                                    .forGetter(FortTerritoryStructurePlacement::exclusionZones),
                            Codec.intRange(1, 4096).fieldOf("fort_spacing")
                                    .forGetter(FortTerritoryStructurePlacement::fortSpacing),
                            Codec.intRange(0, 4095).fieldOf("fort_separation")
                                    .forGetter(FortTerritoryStructurePlacement::fortSeparation),
                            Codec.INT.fieldOf("fort_salt")
                                    .forGetter(FortTerritoryStructurePlacement::fortSalt),
                            Codec.intRange(0, 1024).fieldOf("min_distance_chunks")
                                    .forGetter(FortTerritoryStructurePlacement::minDistanceChunks),
                            Codec.intRange(1, 2048).fieldOf("max_distance_chunks")
                                    .forGetter(FortTerritoryStructurePlacement::maxDistanceChunks)
                    ).apply(instance, FortTerritoryStructurePlacement::new))
                    .validate(FortTerritoryStructurePlacement::validate);

    private final int ownSalt;
    private final List<StructurePlacement.ExclusionZone> exclusionZones;
    private final int fortSpacing;
    private final int fortSeparation;
    private final int fortSalt;
    private final int minDistanceChunks;
    private final int maxDistanceChunks;

    public FortTerritoryStructurePlacement(
            int salt,
            int spacing,
            int separation,
            List<ExclusionZone> exclusionZones,
            int fortSpacing,
            int fortSeparation,
            int fortSalt,
            int minDistanceChunks,
            int maxDistanceChunks) {
        super(Vec3i.ZERO, FrequencyReductionMethod.DEFAULT, 1.0F, salt, Optional.empty(),
                spacing, separation, RandomSpreadType.LINEAR);
        this.ownSalt = salt;
        this.exclusionZones = List.copyOf(exclusionZones);
        this.fortSpacing = fortSpacing;
        this.fortSeparation = fortSeparation;
        this.fortSalt = fortSalt;
        this.minDistanceChunks = minDistanceChunks;
        this.maxDistanceChunks = maxDistanceChunks;
    }

    private int ownSalt() {
        return ownSalt;
    }

    private static DataResult<FortTerritoryStructurePlacement> validate(FortTerritoryStructurePlacement placement) {
        if (placement.spacing() <= placement.separation()) {
            return DataResult.error(() -> "Spacing has to be larger than separation");
        }
        if (placement.fortSpacing <= placement.fortSeparation) {
            return DataResult.error(() -> "fort_spacing has to be larger than fort_separation");
        }
        if (placement.minDistanceChunks >= placement.maxDistanceChunks) {
            return DataResult.error(() -> "min_distance_chunks must be less than max_distance_chunks");
        }
        return DataResult.success(placement);
    }

    public List<ExclusionZone> exclusionZones() {
        return exclusionZones;
    }

    public int fortSpacing() {
        return fortSpacing;
    }

    public int fortSeparation() {
        return fortSeparation;
    }

    public int fortSalt() {
        return fortSalt;
    }

    public int minDistanceChunks() {
        return minDistanceChunks;
    }

    public int maxDistanceChunks() {
        return maxDistanceChunks;
    }

    @Override
    public boolean applyAdditionalChunkRestrictions(int chunkX, int chunkZ, long levelSeed) {
        return super.applyAdditionalChunkRestrictions(chunkX, chunkZ, levelSeed)
                && isWithinFortTerritory(chunkX, chunkZ, levelSeed);
    }

    @Override
    public boolean applyInteractionsWithOtherStructures(
            ChunkGeneratorStructureState structureState,
            int chunkX,
            int chunkZ) {
        if (!super.applyInteractionsWithOtherStructures(structureState, chunkX, chunkZ)) {
            return false;
        }
        for (ExclusionZone zone : exclusionZones) {
            if (structureState.hasStructureChunkInRange(
                    zone.otherSet(), chunkX, chunkZ, zone.chunkCount())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Pure arithmetic, no world access: duplicates RandomSpreadStructurePlacement's
     * own candidate-chunk formula for the fort's grid and scans the fort-grid
     * regions whose possible candidate interval intersects max_distance_chunks.
     * This exact bound replaces the old oversized square scan and remains stateless.
     */
    private boolean isWithinFortTerritory(int chunkX, int chunkZ, long levelSeed) {
        int candidateOffsetMax = fortSpacing - fortSeparation - 1;
        int minRegionX = ceilDiv(chunkX - maxDistanceChunks - candidateOffsetMax, fortSpacing);
        int maxRegionX = Math.floorDiv(chunkX + maxDistanceChunks, fortSpacing);
        int minRegionZ = ceilDiv(chunkZ - maxDistanceChunks - candidateOffsetMax, fortSpacing);
        int maxRegionZ = Math.floorDiv(chunkZ + maxDistanceChunks, fortSpacing);
        long minDistSq = (long) minDistanceChunks * minDistanceChunks;
        long maxDistSq = (long) maxDistanceChunks * maxDistanceChunks;
        long nearestDistSq = Long.MAX_VALUE;
        for (int ri = minRegionX; ri <= maxRegionX; ri++) {
            for (int rj = minRegionZ; rj <= maxRegionZ; rj++) {
                ChunkPos fortCandidate = fortCandidateChunk(levelSeed, ri, rj);
                long dx = fortCandidate.x() - chunkX;
                long dz = fortCandidate.z() - chunkZ;
                long distSq = dx * dx + dz * dz;
                if (distSq < nearestDistSq) {
                    nearestDistSq = distSq;
                }
            }
        }
        return nearestDistSq >= minDistSq && nearestDistSq <= maxDistSq;
    }

    private ChunkPos fortCandidateChunk(long levelSeed, int regionX, int regionZ) {
        return fortCandidateChunkAt(levelSeed, regionX, regionZ, fortSpacing, fortSeparation, fortSalt);
    }

    /**
     * Deterministic per-region candidate chunk for the fort's grid — the exact same formula real
     * fort structure_set placement uses to pick that region's candidate. Pure arithmetic, no world
     * access. Shared by every caller that needs a specific region's candidate (this class's own
     * territory gating, {@link #nearestFortCandidate}, and {@code FortTargetAuthority}) so there is
     * only ever one implementation of this formula to keep in sync.
     */
    public static ChunkPos fortCandidateChunkAt(long levelSeed, int regionX, int regionZ,
                                                int fortSpacing, int fortSeparation, int fortSalt) {
        return FortGridPlacement.candidateChunkAt(
                levelSeed, regionX, regionZ, fortSpacing, fortSeparation, fortSalt);
    }

    /**
     * Returns the closest planned fort-grid candidate using only seed arithmetic. This does not
     * inspect terrain, load chunks, or invoke structure location.
     */
    public static ChunkPos nearestFortCandidate(long levelSeed, int chunkX, int chunkZ,
                                                int fortSpacing, int fortSeparation,
                                                int fortSalt, int maxDistanceChunks) {
        int candidateOffsetMax = fortSpacing - fortSeparation - 1;
        int minRegionX = ceilDiv(chunkX - maxDistanceChunks - candidateOffsetMax, fortSpacing);
        int maxRegionX = Math.floorDiv(chunkX + maxDistanceChunks, fortSpacing);
        int minRegionZ = ceilDiv(chunkZ - maxDistanceChunks - candidateOffsetMax, fortSpacing);
        int maxRegionZ = Math.floorDiv(chunkZ + maxDistanceChunks, fortSpacing);
        ChunkPos nearest = null;
        long nearestDistance = Long.MAX_VALUE;
        for (int regionX = minRegionX; regionX <= maxRegionX; regionX++) {
            for (int regionZ = minRegionZ; regionZ <= maxRegionZ; regionZ++) {
                ChunkPos candidate = fortCandidateChunkAt(levelSeed, regionX, regionZ, fortSpacing, fortSeparation, fortSalt);
                long dx = candidate.x() - chunkX;
                long dz = candidate.z() - chunkZ;
                long distance = dx * dx + dz * dz;
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearest = candidate;
                }
            }
        }
        return nearest;
    }

    private static int ceilDiv(int value, int divisor) {
        return -Math.floorDiv(-value, divisor);
    }

    @Override
    public StructurePlacementType<?> type() {
        return ModStructures.FORT_TERRITORY_RANDOM_SPREAD.get();
    }
}
