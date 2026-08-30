package com.kingsandmonsters.world;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;

import java.util.List;
import java.util.Optional;

/**
 * Random-spread placement with more than vanilla's single exclusion zone.
 *
 * This checks other sets' deterministic candidate chunks only. It does not
 * load terrain, generate starts, or inspect blocks, so locate remains cheap.
 */
@SuppressWarnings("deprecation")
public final class MultiExclusionRandomSpreadStructurePlacement extends RandomSpreadStructurePlacement {
    public static final MapCodec<MultiExclusionRandomSpreadStructurePlacement> CODEC =
            RecordCodecBuilder.<MultiExclusionRandomSpreadStructurePlacement>mapCodec(instance ->
                    placementCodec(instance)
                            .and(instance.group(
                                    com.mojang.serialization.Codec.intRange(1, 4096)
                                            .fieldOf("spacing")
                                            .forGetter(RandomSpreadStructurePlacement::spacing),
                                    com.mojang.serialization.Codec.intRange(0, 4095)
                                            .fieldOf("separation")
                                            .forGetter(RandomSpreadStructurePlacement::separation),
                                    StructurePlacement.ExclusionZone.CODEC.listOf()
                                            .optionalFieldOf("exclusion_zones", List.of())
                                            .forGetter(MultiExclusionRandomSpreadStructurePlacement::exclusionZones)
                            ))
                            .apply(instance, MultiExclusionRandomSpreadStructurePlacement::new))
                    .validate(placement -> placement.spacing() <= placement.separation()
                            ? DataResult.error(() -> "Spacing has to be larger than separation")
                            : DataResult.success(placement));

    private final List<StructurePlacement.ExclusionZone> exclusionZones;

    public MultiExclusionRandomSpreadStructurePlacement(
            Vec3i locateOffset,
            FrequencyReductionMethod frequencyReductionMethod,
            float frequency,
            int salt,
            Optional<ExclusionZone> exclusionZone,
            int spacing,
            int separation,
            List<ExclusionZone> exclusionZones) {
        super(locateOffset, frequencyReductionMethod, frequency, salt, exclusionZone,
                spacing, separation, RandomSpreadType.LINEAR);
        this.exclusionZones = List.copyOf(exclusionZones);
    }

    public List<ExclusionZone> exclusionZones() {
        return exclusionZones;
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

    @Override
    public StructurePlacementType<?> type() {
        return ModStructures.MULTI_EXCLUSION_RANDOM_SPREAD.get();
    }
}
