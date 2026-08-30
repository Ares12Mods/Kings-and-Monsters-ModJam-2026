package com.kingsandmonsters.world;

import com.kingsandmonsters.KingsAndMonsters;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.ChunkPos;

import java.util.Optional;
import java.util.HashMap;
import java.util.Map;

/**
 * Resolves the nearest guaranteed Ogre King's Fort directly from the Fort structure set's real
 * random-spread placement. Fort generation deliberately has no later rejection gates, so this
 * arithmetic candidate is both the map target and the eventual structure-start chunk.
 */
public final class FortTargetAuthority {
    static final Identifier FORT_STRUCTURE_SET_ID =
            Identifier.fromNamespaceAndPath(KingsAndMonsters.MODID, "ogre_kings_fort");

    private FortTargetAuthority() {
    }

    public static Optional<BlockPos> findNearestGuaranteedFort(ServerLevel level, BlockPos origin) {
        // A default superflat world exposes only the plains biome. The Fort grid search requires
        // a swamp candidate and would otherwise scan its entire safety radius for a target that
        // cannot exist. Keep the normal-world targeting algorithm unchanged, but fail immediately
        // for biome sources that can never produce a valid Fort biome.
        if (level.getChunkSource().getGenerator().getBiomeSource().possibleBiomes().stream()
                .noneMatch(biome -> biome.is(Biomes.SWAMP))) {
            return Optional.empty();
        }
        return fortPlacement(level).map(placement -> {
            Map<Long, Boolean> biomeMemo = new HashMap<>();
            FortGridPlacement.SwampOracle swamp = (x, z) -> biomeMemo.computeIfAbsent(
                    ChunkPos.pack(x, z), ignored -> level.getChunkSource().getGenerator().getBiomeSource().getNoiseBiome(
                                    QuartPos.fromBlock(new ChunkPos(x, z).getMiddleBlockX()), QuartPos.fromBlock(64),
                                    QuartPos.fromBlock(new ChunkPos(x, z).getMiddleBlockZ()),
                                    level.getChunkSource().randomState().sampler()).is(Biomes.SWAMP));
            ChunkPos candidate = nearestCandidateChunk(level.getSeed(), ChunkPos.containing(origin), placement, swamp);
            BlockPos target = new BlockPos(candidate.getMiddleBlockX(), level.getSeaLevel(), candidate.getMiddleBlockZ());
            return target;
        });
    }

    static Optional<SwampFortStructurePlacement> fortPlacement(ServerLevel level) {
        return level.registryAccess()
                .lookupOrThrow(Registries.STRUCTURE_SET)
                .get(ResourceKey.create(Registries.STRUCTURE_SET, FORT_STRUCTURE_SET_ID))
                .map(holder -> holder.value().placement())
                .filter(SwampFortStructurePlacement.class::isInstance)
                .map(SwampFortStructurePlacement.class::cast);
    }

    static ChunkPos nearestCandidateChunk(
            long seed, ChunkPos origin, SwampFortStructurePlacement placement,
            FortGridPlacement.SwampOracle swamp) {
        return FortGridPlacement.nearestAuthoritativeCandidate(seed, origin, placement.spacing(),
                placement.separation(), placement.gridSalt(), placement.spawnExclusionChunks(),
                placement.minimumSpacingChunks(), swamp);
    }
}
