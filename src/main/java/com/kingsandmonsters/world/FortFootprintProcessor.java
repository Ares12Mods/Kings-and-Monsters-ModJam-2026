package com.kingsandmonsters.world;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Records, per world (x, z) column, the lowest and highest Y that actually
 * receive fort content (walls, towers, paths, foundations) as the template
 * is pasted. Must run after minecraft:block_ignore in the fort's processor
 * list so it only sees blocks that survived the air-ignore filter - those
 * are the real footprint, as opposed to the exterior/interior air baked
 * into the NBT's rectangular bounding box. FortTerrainCleaner reads this
 * back in afterPlace so grading only ever touches ground the fort actually
 * occupies, using each column's own real extent rather than a single
 * bounding-box-wide floor/roof line.
 */
public final class FortFootprintProcessor extends StructureProcessor {
    public static final MapCodec<FortFootprintProcessor> CODEC = MapCodec.unit(FortFootprintProcessor::new);
    private static final ThreadLocal<Map<Long, int[]>> CAPTURED_COLUMNS = ThreadLocal.withInitial(HashMap::new);
    private static final ThreadLocal<Set<Long>> CAPTURED_BLOCKS = ThreadLocal.withInitial(HashSet::new);

    @Nullable
    @Override
    public StructureTemplate.StructureBlockInfo process(
            LevelReader level,
            BlockPos offset,
            BlockPos pos,
            StructureTemplate.StructureBlockInfo rawInfo,
            StructureTemplate.StructureBlockInfo relativeInfo,
            StructurePlaceSettings settings,
            StructureTemplate template) {
        BlockPos worldPos = relativeInfo.pos();
        // Outpost templates intentionally place air to clear their interiors,
        // while fort templates filter air before this processor. Air must still
        // be returned to the placer, but it is not an occupied/support column.
        if (relativeInfo.state().isAir()) {
            return relativeInfo;
        }
        long column = BlockPos.asLong(worldPos.getX(), 0, worldPos.getZ());
        int[] extent = CAPTURED_COLUMNS.get().computeIfAbsent(column, k -> new int[]{worldPos.getY(), worldPos.getY()});
        extent[0] = Math.min(extent[0], worldPos.getY());
        extent[1] = Math.max(extent[1], worldPos.getY());
        CAPTURED_BLOCKS.get().add(worldPos.asLong());
        return relativeInfo;
    }

    /**
     * Consumes and clears the column extents captured on this thread since
     * the last call. Each value is {minY, maxY} for that column.
     */
    public static Map<Long, int[]> takeCapturedColumns() {
        Map<Long, int[]> columns = CAPTURED_COLUMNS.get();
        CAPTURED_COLUMNS.remove();
        return columns;
    }

    public static Set<Long> takeCapturedBlocks() {
        Set<Long> blocks = CAPTURED_BLOCKS.get();
        CAPTURED_BLOCKS.remove();
        return blocks;
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return ModStructures.FORT_FOOTPRINT_RECORDER.get();
    }
}
