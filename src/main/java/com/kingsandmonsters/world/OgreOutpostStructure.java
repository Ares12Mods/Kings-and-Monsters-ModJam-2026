package com.kingsandmonsters.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pools.DimensionPadding;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasBinding;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.core.registries.Registries;
import com.kingsandmonsters.Config;
import com.kingsandmonsters.KingsAndMonsters;
import com.kingsandmonsters.tribute.OutpostCaptainType;
import com.kingsandmonsters.tribute.TributeManager;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Rigid, single-level outpost placement. Candidate selection intentionally
 * follows vanilla jigsaw placement; terrain integration happens in afterPlace
 * and never participates in /locate.
 */
public class OgreOutpostStructure extends Structure {

    private static final ResourceKey<LootTable> SWAMP_TRIBUTE_WITH_MAP = ResourceKey.create(
            Registries.LOOT_TABLE,
            Identifier.fromNamespaceAndPath(KingsAndMonsters.MODID, "chests/ogre_outpost_tribute_swamp_map"));
    private static final ResourceKey<LootTable> SWAMP_TRIBUTE_WITHOUT_MAP = ResourceKey.create(
            Registries.LOOT_TABLE,
            Identifier.fromNamespaceAndPath(KingsAndMonsters.MODID, "chests/ogre_outpost_tribute_swamp"));

    private final StructureSettings structureSettings;
    private final Holder<StructureTemplatePool> startPool;
    private final Optional<Identifier> startJigsawName;
    private final int maxDepth;
    private final HeightProvider startHeight;
    private final boolean useExpansionHack;
    private final Optional<Heightmap.Types> projectStartToHeightmap;
    private final int maxDistanceFromCenter;
    private final List<PoolAliasBinding> poolAliases;
    private final DimensionPadding dimensionPadding;
    private final LiquidSettings liquidSettings;
    private final boolean swamp;

    public static final MapCodec<OgreOutpostStructure> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    settingsCodec(instance),
                    StructureTemplatePool.CODEC.fieldOf("start_pool").forGetter(s -> s.startPool),
                    Identifier.CODEC.optionalFieldOf("start_jigsaw_name").forGetter(s -> s.startJigsawName),
                    Codec.intRange(0, 7).fieldOf("size").forGetter(s -> s.maxDepth),
                    HeightProvider.CODEC.fieldOf("start_height").forGetter(s -> s.startHeight),
                    Codec.BOOL.fieldOf("use_expansion_hack").forGetter(s -> s.useExpansionHack),
                    Heightmap.Types.CODEC.optionalFieldOf("project_start_to_heightmap").forGetter(s -> s.projectStartToHeightmap),
                    Codec.intRange(1, 128).fieldOf("max_distance_from_center").forGetter(s -> s.maxDistanceFromCenter),
                    PoolAliasBinding.CODEC.listOf().optionalFieldOf("pool_aliases", List.of()).forGetter(s -> s.poolAliases),
                    DimensionPadding.CODEC.optionalFieldOf("dimension_padding", DimensionPadding.ZERO).forGetter(s -> s.dimensionPadding),
                    LiquidSettings.CODEC.optionalFieldOf("liquid_settings", LiquidSettings.APPLY_WATERLOGGING).forGetter(s -> s.liquidSettings),
                    Codec.BOOL.optionalFieldOf("swamp", false).forGetter(s -> s.swamp)
            ).apply(instance, OgreOutpostStructure::new)
    );

    public OgreOutpostStructure(
            StructureSettings settings,
            Holder<StructureTemplatePool> startPool,
            Optional<Identifier> startJigsawName,
            int maxDepth,
            HeightProvider startHeight,
            boolean useExpansionHack,
            Optional<Heightmap.Types> projectStartToHeightmap,
            int maxDistanceFromCenter,
            List<PoolAliasBinding> poolAliases,
            DimensionPadding dimensionPadding,
            LiquidSettings liquidSettings,
            boolean swamp) {
        super(settings);
        this.structureSettings = settings;
        this.startPool = startPool;
        this.startJigsawName = startJigsawName;
        this.maxDepth = maxDepth;
        this.startHeight = startHeight;
        this.useExpansionHack = useExpansionHack;
        this.projectStartToHeightmap = projectStartToHeightmap;
        this.maxDistanceFromCenter = maxDistanceFromCenter;
        this.poolAliases = poolAliases;
        this.dimensionPadding = dimensionPadding;
        this.liquidSettings = liquidSettings;
        this.swamp = swamp;
    }

    @Override
    public Optional<GenerationStub> findValidGenerationPoint(GenerationContext context) {
        /*
         * Structure.findValidGenerationPoint normally performs its biome
         * check only after findGenerationPoint. That made /locate pay for
         * jigsaw assembly for candidates that could never generate. This
         * single climate-sampler
         * lookup uses the jigsaw's exact start X/Z and rejects those first.
         * Vanilla's final biome check still runs through super for candidates
         * that pass, so this cannot admit an invalid start.
         */
        ChunkPos chunk = context.chunkPos();
        int x = chunk.getMinBlockX();
        int z = chunk.getMinBlockZ();
        int y = context.chunkGenerator().getSeaLevel();
        Holder<net.minecraft.world.level.biome.Biome> biome =
                context.biomeSource().getNoiseBiome(
                        QuartPos.fromBlock(x),
                        QuartPos.fromBlock(y),
                        QuartPos.fromBlock(z),
                        context.randomState().sampler());
        if (!context.validBiome().test(biome)) {
            return Optional.empty();
        }
        if (!swamp && !isSuitablePlainsSite(context, chunk.getMiddleBlockX(), chunk.getMiddleBlockZ())) {
            return Optional.empty();
        }
        return super.findValidGenerationPoint(context);
    }

    /** Rejects only clearly steep candidates; ordinary rolling plains remain valid. */
    private static boolean isSuitablePlainsSite(GenerationContext context, int centerX, int centerZ) {
        int[][] offsets = {
                {0, 0}, {-10, 0}, {10, 0}, {0, -10}, {0, 10},
                {-10, -10}, {-10, 10}, {10, -10}, {10, 10}
        };
        int minimum = Integer.MAX_VALUE;
        int maximum = Integer.MIN_VALUE;
        for (int[] offset : offsets) {
            int height = context.chunkGenerator().getFirstFreeHeight(
                    centerX + offset[0], centerZ + offset[1],
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    context.heightAccessor(), context.randomState());
            minimum = Math.min(minimum, height);
            maximum = Math.max(maximum, height);
            if (maximum - minimum > 4) return false;
        }
        return true;
    }

    @Override
    public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        JigsawStructure delegate = new JigsawStructure(
                structureSettings, startPool, startJigsawName, maxDepth,
                startHeight, useExpansionHack, projectStartToHeightmap, new JigsawStructure.MaxDistance(maxDistanceFromCenter, maxDistanceFromCenter),
                poolAliases, dimensionPadding, liquidSettings
        );
        return delegate.findGenerationPoint(context);
    }

    @Override
    public void afterPlace(
            WorldGenLevel level,
            StructureManager structureManager,
            ChunkGenerator chunkGenerator,
            RandomSource random,
            BoundingBox chunkBox,
            ChunkPos chunkPos,
            PiecesContainer pieces) {
        BoundingBox structureBox = pieces.calculateBoundingBox();
        BlockPos campOrigin = structureBox.getCenter();
        OutpostCaptainType captainType = resolveCaptainType(pieces);
        BlockPos fortTarget = FortTargetAuthority.findNearestGuaranteedFort(level.getLevel(), campOrigin).orElse(null);
        WorldgenSavedDataQueue.registerOutpost(
                level.getLevel(), campOrigin, captainType, structureBox, fortTarget);
        if (!swamp) {
            // Every Plains variant's tribute chest carries a guaranteed map pool (see
            // chests/ogre_outpost_tribute.json) — capture its position once so
            // FortMapDeliveryService can repair legacy chests that unpacked without a valid map.
            findFirstTrappedChest(level, chunkBox, structureBox).ifPresent(chestPos -> {
                WorldgenSavedDataQueue.registerMapChest(level.getLevel(), campOrigin, chestPos);
            });
        }
        configureSwampTributeChest(level, chunkBox, structureBox, campOrigin);
        Map<Long, int[]> columnExtents = FortFootprintProcessor.takeCapturedColumns();
        Set<Long> authoredBlocks = FortFootprintProcessor.takeCapturedBlocks();
        OutpostTerrainCleaner.integrate(
                level, chunkBox, structureBox, columnExtents, authoredBlocks, swamp);
        OutpostVegetationCleaner.captureStructureBlocks(
                level, chunkBox, chunkPos, pieces, authoredBlocks, swamp);
    }

    /**
     * The four Outpost templates each now run through their own dedicated
     * {@link OutpostMobLootProcessor} instance (see {@code worldgen/processor_list/ogre_outpost_*.json}),
     * so the processor itself knows exactly which template is being placed — no guessing required.
     * That capture is authoritative; the old {@link #captainTypeFor} string-parse is kept only as a
     * defensive fallback for older development-world templates.
     */
    private static OutpostCaptainType resolveCaptainType(PiecesContainer pieces) {
        String capturedVariant = OutpostMobLootProcessor.takeCapturedVariant();
        OutpostCaptainType resolved = switch (capturedVariant == null ? "" : capturedVariant) {
            case "grunt" -> OutpostCaptainType.GRUNT;
            case "mage", "mage_2" -> OutpostCaptainType.MAGE;
            case "brute" -> OutpostCaptainType.BRUTE;
            default -> null;
        };

        return resolved != null ? resolved : captainTypeFor(pieces);
    }

    private static OutpostCaptainType captainTypeFor(PiecesContainer pieces) {
        String templateDescription = pieces.pieces().stream()
                .filter(PoolElementStructurePiece.class::isInstance)
                .map(PoolElementStructurePiece.class::cast)
                .map(piece -> piece.getElement().toString().toLowerCase(java.util.Locale.ROOT))
                .findFirst()
                .orElse("");

        if (templateDescription.contains("mage") || templateDescription.contains("variant_2")) {
            return OutpostCaptainType.MAGE;
        }
        if (templateDescription.contains("grunt") || templateDescription.contains("variant_3")) {
            return OutpostCaptainType.GRUNT;
        }
        // The current first and fourth variants are Brute settlements. Future named templates
        // fall through here only when they do not explicitly identify another captain role.
        return OutpostCaptainType.BRUTE;
    }

    private void configureSwampTributeChest(
            WorldGenLevel level,
            BoundingBox chunkBox,
            BoundingBox structureBox,
            BlockPos campOrigin) {
        if (!swamp) {
            return;
        }

        long hash = level.getSeed()
                ^ ((long) structureBox.minX() * 341873128712L)
                ^ ((long) structureBox.minZ() * 132897987541L);
        RandomSource mapRoll = RandomSource.create(hash);
        boolean includeMap = mapRoll.nextDouble() < Config.SWAMP_OUTPOST_FORT_MAP_CHANCE.get();
        ResourceKey<LootTable> lootTable = includeMap
                ? SWAMP_TRIBUTE_WITH_MAP
                : SWAMP_TRIBUTE_WITHOUT_MAP;

        int minX = Math.max(chunkBox.minX(), structureBox.minX());
        int maxX = Math.min(chunkBox.maxX(), structureBox.maxX());
        int minZ = Math.max(chunkBox.minZ(), structureBox.minZ());
        int maxZ = Math.min(chunkBox.maxZ(), structureBox.maxZ());
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = structureBox.minY(); y <= structureBox.maxY(); y++) {
                    pos.set(x, y, z);
                    if (level.getBlockState(pos).is(Blocks.TRAPPED_CHEST)
                            && level.getBlockEntity(pos) instanceof RandomizableContainerBlockEntity chest) {
                        chest.setLootTable(lootTable);
                        chest.setLootTableSeed(hash ^ pos.asLong());
                        chest.setChanged();
                        if (includeMap) {
                            // This roll intentionally carries the guaranteed map pool — track it
                            // the same way Plains does so FortMapDeliveryService can help if this
                            // chest opens before the Fort target resolves. A Swamp Outpost whose
                            // roll excluded the map is never registered here, matching the design:
                            // the map is chance-gated for Swamp, not guaranteed (see Config).
                            WorldgenSavedDataQueue.registerMapChest(level.getLevel(), campOrigin, pos.immutable());
                        }
                    }
                }
            }
        }
    }

    /** First TRAPPED_CHEST found within this chunk pass's intersection, in scan order. */
    private static Optional<BlockPos> findFirstTrappedChest(
            WorldGenLevel level, BoundingBox chunkBox, BoundingBox structureBox) {
        int minX = Math.max(chunkBox.minX(), structureBox.minX());
        int maxX = Math.min(chunkBox.maxX(), structureBox.maxX());
        int minZ = Math.max(chunkBox.minZ(), structureBox.minZ());
        int maxZ = Math.min(chunkBox.maxZ(), structureBox.maxZ());
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = structureBox.minY(); y <= structureBox.maxY(); y++) {
                    pos.set(x, y, z);
                    if (level.getBlockState(pos).is(Blocks.TRAPPED_CHEST)) {
                        return Optional.of(pos.immutable());
                    }
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.OGRE_OUTPOST.get();
    }

}
