package com.kingsandmonsters.world;

import com.kingsandmonsters.KingsAndMonsters;
import com.kingsandmonsters.ModEntities;
import com.kingsandmonsters.entity.OgreGrunt;
import com.kingsandmonsters.entity.OgreGuard;
import com.kingsandmonsters.entity.OgreLord;
import com.kingsandmonsters.tribute.TributeManager;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.pools.DimensionPadding;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasBinding;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Single-piece, rigid Ogre King's Fort. Swamp selection belongs exclusively to the structure
 * placement; this class never rejects a candidate. Piece-aware terrain integration runs here.
 */
public final class OgreKingsFortStructure extends Structure {
    // Local (unrotated) offset of the main gate's walk-through level within
    // the NBT, measured via a Load-mode structure block at the schematic's
    // origin and the player's F3 position standing at the entrance
    // threshold: dx = 1, dy = 3, dz = 51. Independently confirmed against
    // the schematic's own spruce_fence_gate blocks, which sit at the same
    // Y=3. The whole piece is shifted after placement so this point lands
    // exactly on the sampled terrain height, instead of anchoring on the
    // schematic's raw (0,0,0) corner the way vanilla's heightmap projection
    // does by default.
    private static final BlockPos ENTRANCE_LOCAL_POS = new BlockPos(1, 3, 51);

    // Authored encounter markers relative to the vanilla structure NBT origin.
    // Original nitpick baseline: (37.911, 4.0625, 50.991). The King faces local west,
    // so +0.50 X moves him backward into the throne without shifting sideways.
    private static final Vec3 SLEEPING_KING_LOCAL_POS = new Vec3(38.411, 4.0625, 50.991);
    private static final BlockPos MAGE_TRIAL_LOCAL_POS = new BlockPos(30, 3, 9);
    private static final BlockPos BRUTE_TRIAL_LOCAL_POS = new BlockPos(31, 4, 90);
    private static final BlockPos GRUNT_TRIAL_LOCAL_POS = new BlockPos(12, 3, 65);
    private static final String FORT_RESIDENT_TAG_PREFIX = "KingsAndMonstersFortResident_";
    private static final List<FortResidentMarker> FORT_RESIDENT_MARKERS = List.of(
            // The royal Guards patrol separate sections of the compound. Broad homes keep
            // them roaming through the fort instead of clustering around the throne brazier.
            new FortResidentMarker("throne_guard_1", new BlockPos(34, 3, 48), ModEntities.OGRE_GUARD, 30, true),
            new FortResidentMarker("throne_guard_2", new BlockPos(34, 3, 54), ModEntities.OGRE_GUARD, 30, true),
            new FortResidentMarker("throne_guard_3", new BlockPos(31, 3, 51), ModEntities.OGRE_GUARD, 30, true),
            // General residents patrol broader sections of the royal compound.
            new FortResidentMarker("grunt_1", new BlockPos(10, 4, 50), ModEntities.OGRE_GRUNT, 28, false),
            new FortResidentMarker("grunt_2", new BlockPos(20, 2, 30), ModEntities.OGRE_GRUNT, 28, false),
            new FortResidentMarker("archer_1", new BlockPos(50, 3, 70), ModEntities.OGRE_ARCHER, 28, false),
            new FortResidentMarker("archer_2", new BlockPos(30, 5, 80), ModEntities.OGRE_ARCHER, 28, false));

    // Deliberate bias above the raw entrance-flush target - see
    // alignEntranceToTerrain for why a perfectly flush entrance still let
    // other, relatively-lower sections of the rigid perimeter flood.
    private static final int ENTRANCE_CLEARANCE = 1;

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

    public static final MapCodec<OgreKingsFortStructure> CODEC = RecordCodecBuilder.mapCodec(instance ->
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
                    LiquidSettings.CODEC.optionalFieldOf("liquid_settings", LiquidSettings.APPLY_WATERLOGGING).forGetter(s -> s.liquidSettings)
            ).apply(instance, OgreKingsFortStructure::new)
    );

    private OgreKingsFortStructure(
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
            LiquidSettings liquidSettings) {
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
    }

    @Override
    public Optional<GenerationStub> findValidGenerationPoint(GenerationContext context) {
        // Progression invariant: every placement-grid candidate produces a Fort. Biome identity is
        // preserved through the template, processors, terrain grading, entrance alignment, and
        // vegetation cleanup; it must never be allowed to reject the structure start.
        return findGenerationPoint(context);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        JigsawStructure delegate = new JigsawStructure(
                structureSettings, startPool, startJigsawName, maxDepth,
                startHeight, useExpansionHack, projectStartToHeightmap, new JigsawStructure.MaxDistance(maxDistanceFromCenter, maxDistanceFromCenter),
                poolAliases, dimensionPadding, liquidSettings
        );
        Optional<GenerationStub> stubOpt = delegate.findGenerationPoint(context);
        if (stubOpt.isEmpty()) {
            return Optional.empty();
        }

        GenerationStub stub = stubOpt.get();
        StructurePiecesBuilder piecesBuilder = stub.getPiecesBuilder();
        // The entrance anchor below is the only custom height query on an
        // accepted candidate. Terrain integration is deferred to afterPlace.
        alignEntranceToTerrain(context, piecesBuilder);
        return Optional.of(new GenerationStub(stub.position(), Either.right(piecesBuilder)));
    }

    /**
     * Vanilla's project_start_to_heightmap anchors on the schematic's raw
     * (0,0,0) corner, which is nowhere near the entrance - that's what left
     * the gate floating above the ground. This re-anchors the whole piece
     * on the entrance's own walk-through point instead, so the gate lands
     * flush with the terrain sampled right in front of it. FortTerrainCleaner
     * then handles the rest of the perimeter per-column afterward.
     */
    private void alignEntranceToTerrain(GenerationContext context, StructurePiecesBuilder piecesBuilder) {
        for (StructurePiece piece : piecesBuilder.build().pieces()) {
            if (!(piece instanceof PoolElementStructurePiece poolPiece)) {
                continue;
            }

            BlockPos rotatedOffset = StructureTemplate.transform(ENTRANCE_LOCAL_POS, Mirror.NONE, poolPiece.getRotation(), BlockPos.ZERO);
            BlockPos entranceWorldPos = poolPiece.getPosition().offset(rotatedOffset);

            // getFirstFreeHeight (== getBaseHeight) is the "stand here" surface
            // Y - the same convention vanilla's own project_start_to_heightmap
            // uses. getFirstOccupiedHeight is one lower, at the top of the
            // solid ground itself; using it
            // here sank the entrance flush with the ground surface instead of
            // the walkable air above it, letting swamp water flood straight in.
            int terrainY = context.chunkGenerator().getFirstFreeHeight(
                    entranceWorldPos.getX(), entranceWorldPos.getZ(),
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    context.heightAccessor(), context.randomState()
            );

            // The entrance is a single point; other perimeter sections sit at
            // their own fixed relative height baked into the rigid schematic
            // and can land right at the local swamp water table even when the
            // entrance itself is exactly flush. A 1-block clearance keeps the
            // entrance an easy, unnoticeable step while giving lower sections
            // enough margin to stay above ambient water.
            poolPiece.move(0, (terrainY + ENTRANCE_CLEARANCE) - entranceWorldPos.getY(), 0);
            return;
        }
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
        BlockPos fortOrigin = pieces.calculateBoundingBox().getCenter();
        WorldgenSavedDataQueue.registerFort(level.getLevel(), fortOrigin);
        placeSleepingKing(level.getLevel(), chunkBox, pieces);
        placeFortResidents(level.getLevel(), chunkBox, pieces);
        Map<Long, int[]> columnExtents = FortFootprintProcessor.takeCapturedColumns();
        Set<Long> authoredBlocks = FortFootprintProcessor.takeCapturedBlocks();
        FortTerrainCleaner.grade(level, chunkBox, columnExtents, authoredBlocks);
        FortCliffGuard.guard(level, chunkBox, columnExtents);
        FortVegetationCleaner.captureStructureBlocks(level, chunkBox, chunkPos, pieces, authoredBlocks);
    }

    private static void placeSleepingKing(
            ServerLevel level,
            BoundingBox chunkBox,
            PiecesContainer pieces) {
        for (StructurePiece piece : pieces.pieces()) {
            if (!(piece instanceof PoolElementStructurePiece poolPiece)) {
                continue;
            }

            Vec3 transformedLocal = StructureTemplate.transform(
                    SLEEPING_KING_LOCAL_POS,
                    Mirror.NONE,
                    poolPiece.getRotation(),
                    BlockPos.ZERO);
            Vec3 worldPosition = transformedLocal.add(Vec3.atLowerCornerOf(poolPiece.getPosition()));
            BlockPos kingBlockPos = BlockPos.containing(worldPosition);
            if (!chunkBox.isInside(kingBlockPos)) {
                continue;
            }

            Direction facing = poolPiece.getRotation().rotate(Direction.WEST);
            FortPopulationSpawner.queueKing(
                    level.dimension(),
                    worldPosition,
                    facing,
                    List.of(
                            transformLocalBlock(poolPiece, MAGE_TRIAL_LOCAL_POS),
                            transformLocalBlock(poolPiece, BRUTE_TRIAL_LOCAL_POS),
                            transformLocalBlock(poolPiece, GRUNT_TRIAL_LOCAL_POS)));
            return;
        }
    }

    private static BlockPos transformLocalBlock(
            PoolElementStructurePiece piece,
            BlockPos localPosition) {
        BlockPos transformed = StructureTemplate.transform(
                localPosition, Mirror.NONE, piece.getRotation(), BlockPos.ZERO);
        return piece.getPosition().offset(transformed);
    }

    private static void placeFortResidents(
            ServerLevel level,
            BoundingBox chunkBox,
            PiecesContainer pieces) {
        for (StructurePiece piece : pieces.pieces()) {
            if (!(piece instanceof PoolElementStructurePiece poolPiece)) {
                continue;
            }

            Direction facing = poolPiece.getRotation().rotate(Direction.WEST);
            for (FortResidentMarker marker : FORT_RESIDENT_MARKERS) {
                BlockPos worldPos = transformLocalBlock(poolPiece, marker.localPos());
                if (!chunkBox.isInside(worldPos)) {
                    continue;
                }

                FortPopulationSpawner.queueResident(
                        level.dimension(), marker.id(), worldPos, facing,
                        marker.entityType().get(), marker.homeRadius(), marker.eliteGuard());
            }
            return;
        }
    }

    private record FortResidentMarker(
            String id,
            BlockPos localPos,
            net.neoforged.neoforge.registries.DeferredHolder<EntityType<?>, ? extends EntityType<? extends OgreGrunt>> entityType,
            int homeRadius,
            boolean eliteGuard) {
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.OGRE_KINGS_FORT.get();
    }
}
