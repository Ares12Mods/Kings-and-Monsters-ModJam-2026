package com.kingsandmonsters.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;

import java.util.Optional;

/**
 * The complete Fort placement authority. Random-spread positions are probes, not promises: an
 * authoritative candidate exists only when the probe is swamp and wins deterministic local
 * ownership. Once this method returns true no later biome, frequency, exclusion, or terrain gate
 * is permitted to reject the Fort.
 */
public final class SwampFortStructurePlacement extends RandomSpreadStructurePlacement {
    public static final MapCodec<SwampFortStructurePlacement> CODEC =
            RecordCodecBuilder.<SwampFortStructurePlacement>mapCodec(instance -> instance.group(
                            Codec.INT.fieldOf("salt").forGetter(SwampFortStructurePlacement::ownSalt),
                            Codec.intRange(1, 4096).fieldOf("spacing").forGetter(RandomSpreadStructurePlacement::spacing),
                            Codec.intRange(0, 4095).fieldOf("separation").forGetter(RandomSpreadStructurePlacement::separation),
                            Codec.intRange(0, 4096).fieldOf("spawn_exclusion_chunks").forGetter(SwampFortStructurePlacement::spawnExclusionChunks),
                            Codec.intRange(0, 4096).fieldOf("minimum_spacing_chunks").forGetter(SwampFortStructurePlacement::minimumSpacingChunks)
                    ).apply(instance, SwampFortStructurePlacement::new))
                    .validate(value -> value.spacing() <= value.separation()
                            ? DataResult.error(() -> "Spacing has to be larger than separation")
                            : DataResult.success(value));

    private final int spawnExclusionChunks;
    private final int minimumSpacingChunks;

    public SwampFortStructurePlacement(
            int salt,
            int spacing,
            int separation,
            int spawnExclusionChunks,
            int minimumSpacingChunks) {
        super(Vec3i.ZERO, FrequencyReductionMethod.DEFAULT, 1.0F, salt, Optional.empty(),
                spacing, separation, RandomSpreadType.LINEAR);
        this.spawnExclusionChunks = spawnExclusionChunks;
        this.minimumSpacingChunks = minimumSpacingChunks;
    }

    public int spawnExclusionChunks() {
        return spawnExclusionChunks;
    }

    public int minimumSpacingChunks() {
        return minimumSpacingChunks;
    }

    @Override
    protected boolean isPlacementChunk(ChunkGeneratorStructureState state, int chunkX, int chunkZ) {
        FortGridPlacement.SwampOracle swamp = (x, z) -> state.biomeSource.getNoiseBiome(
                QuartPos.fromBlock(new ChunkPos(x, z).getMiddleBlockX()),
                QuartPos.fromBlock(64),
                QuartPos.fromBlock(new ChunkPos(x, z).getMiddleBlockZ()),
                state.randomState().sampler()).is(Biomes.SWAMP);
        return isAuthoritativeCandidate(state.getLevelSeed(), chunkX, chunkZ, swamp);
    }

    /** Shared verbatim by worldgen, map targeting, and corpus tests. */
    public boolean isAuthoritativeCandidate(long seed, int chunkX, int chunkZ, FortGridPlacement.SwampOracle swamp) {
        return FortGridPlacement.isAuthoritativeCandidate(seed, chunkX, chunkZ, spacing(), separation(),
                ownSalt(), spawnExclusionChunks, minimumSpacingChunks, swamp);
    }

    private int ownSalt() {
        return salt();
    }

    public int gridSalt() {
        return ownSalt();
    }

    @Override
    public StructurePlacementType<?> type() {
        return ModStructures.SWAMP_FORT_RANDOM_SPREAD.get();
    }
}
