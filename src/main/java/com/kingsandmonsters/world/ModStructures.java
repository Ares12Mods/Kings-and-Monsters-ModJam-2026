package com.kingsandmonsters.world;

import com.kingsandmonsters.KingsAndMonsters;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModStructures {

    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_TYPE, KingsAndMonsters.MODID);

    public static final DeferredRegister<StructureProcessorType<?>> STRUCTURE_PROCESSOR_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_PROCESSOR, KingsAndMonsters.MODID);

    public static final DeferredRegister<StructurePlacementType<?>> STRUCTURE_PLACEMENT_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_PLACEMENT, KingsAndMonsters.MODID);

    public static final DeferredHolder<StructureType<?>, StructureType<OgreOutpostStructure>> OGRE_OUTPOST =
            STRUCTURE_TYPES.register("ogre_outpost", () -> () -> OgreOutpostStructure.CODEC);

    public static final DeferredHolder<StructureType<?>, StructureType<OgreKingsFortStructure>> OGRE_KINGS_FORT =
            STRUCTURE_TYPES.register("ogre_kings_fort", () -> () -> OgreKingsFortStructure.CODEC);

    public static final DeferredHolder<StructureProcessorType<?>, StructureProcessorType<FortFootprintProcessor>> FORT_FOOTPRINT_RECORDER =
            STRUCTURE_PROCESSOR_TYPES.register("fort_footprint_recorder", () -> () -> FortFootprintProcessor.CODEC);

    public static final DeferredHolder<StructureProcessorType<?>, StructureProcessorType<FortChestLootProcessor>> FORT_CHEST_LOOT =
            STRUCTURE_PROCESSOR_TYPES.register("fort_chest_loot", () -> () -> FortChestLootProcessor.CODEC);

    public static final DeferredHolder<StructureProcessorType<?>, StructureProcessorType<OutpostMobLootProcessor>> OUTPOST_MOB_LOOT =
            STRUCTURE_PROCESSOR_TYPES.register("outpost_mob_loot", () -> () -> OutpostMobLootProcessor.CODEC);

    public static final DeferredHolder<StructureProcessorType<?>, StructureProcessorType<OgreTrialSpawnerProcessor>> OGRE_TRIAL_SPAWNER =
            STRUCTURE_PROCESSOR_TYPES.register("ogre_trial_spawner", () -> () -> OgreTrialSpawnerProcessor.CODEC);

    public static final DeferredHolder<StructurePlacementType<?>, StructurePlacementType<MultiExclusionRandomSpreadStructurePlacement>>
            MULTI_EXCLUSION_RANDOM_SPREAD =
            STRUCTURE_PLACEMENT_TYPES.register(
                    "multi_exclusion_random_spread",
                    () -> () -> MultiExclusionRandomSpreadStructurePlacement.CODEC);

    public static final DeferredHolder<StructurePlacementType<?>, StructurePlacementType<FortTerritoryStructurePlacement>>
            FORT_TERRITORY_RANDOM_SPREAD =
            STRUCTURE_PLACEMENT_TYPES.register(
                    "fort_territory_random_spread",
                    () -> () -> FortTerritoryStructurePlacement.CODEC);

    public static final DeferredHolder<StructurePlacementType<?>, StructurePlacementType<SwampFortStructurePlacement>>
            SWAMP_FORT_RANDOM_SPREAD =
            STRUCTURE_PLACEMENT_TYPES.register(
                    "swamp_fort_random_spread",
                    () -> () -> SwampFortStructurePlacement.CODEC);

    public static void register(IEventBus modEventBus) {
        STRUCTURE_TYPES.register(modEventBus);
        STRUCTURE_PROCESSOR_TYPES.register(modEventBus);
        STRUCTURE_PLACEMENT_TYPES.register(modEventBus);
    }
}
