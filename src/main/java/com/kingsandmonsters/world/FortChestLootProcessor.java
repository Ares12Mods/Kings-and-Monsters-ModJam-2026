package com.kingsandmonsters.world;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import javax.annotation.Nullable;
import java.util.Set;

/**
 * Upgrades generic fort chests without replacing mob-hut loot. The king's
 * personal chest is identified in template-local coordinates, so rotation
 * cannot move the guaranteed Iron Maw assignment onto another chest.
 */
public final class FortChestLootProcessor extends StructureProcessor {
    public static final MapCodec<FortChestLootProcessor> CODEC = MapCodec.unit(FortChestLootProcessor::new);
    private static final BlockPos KING_CHEST = new BlockPos(50, 4, 58);
    private static final Set<BlockPos> KING_HUT_CHESTS = Set.of(
            new BlockPos(46, 4, 45),
            KING_CHEST,
            new BlockPos(53, 5, 45));
    private static final String GENERIC_OUTPOST_LOOT = "kingsandmonsters:chests/ogre_outpost_common";
    private static final String FORT_LOOT = "kingsandmonsters:chests/ogre_kings_fort";
    private static final String KING_HUT_LOOT = "kingsandmonsters:chests/ogre_kings_fort_king_hut";
    private static final String KING_LOOT = "kingsandmonsters:chests/ogre_kings_fort_king";
    private static final String HUT_BARREL_LOOT = "kingsandmonsters:chests/ogre_kings_fort_hut_barrel";

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
        if (relativeInfo.nbt() == null) {
            return relativeInfo;
        }

        CompoundTag nbt = relativeInfo.nbt().copy();
        if (relativeInfo.state().is(Blocks.BARREL)) {
            BlockPos localPos = rawInfo.pos();
            if (localPos.getY() <= 5 && !isInsideKingHut(localPos)) {
                nbt.putString("LootTable", HUT_BARREL_LOOT);
                return new StructureTemplate.StructureBlockInfo(
                        relativeInfo.pos(), relativeInfo.state(), nbt);
            }
            return relativeInfo;
        }
        if (!relativeInfo.state().is(Blocks.CHEST)) {
            return relativeInfo;
        }

        String currentLoot = nbt.getStringOr("LootTable", "");
        if (rawInfo.pos().equals(KING_CHEST)) {
            nbt.putString("LootTable", KING_LOOT);
        } else if (KING_HUT_CHESTS.contains(rawInfo.pos())) {
            nbt.putString("LootTable", KING_HUT_LOOT);
        } else if (currentLoot.isEmpty() || currentLoot.equals(GENERIC_OUTPOST_LOOT)) {
            nbt.putString("LootTable", FORT_LOOT);
        }
        return new StructureTemplate.StructureBlockInfo(relativeInfo.pos(), relativeInfo.state(), nbt);
    }

    private static boolean isInsideKingHut(BlockPos pos) {
        return pos.getX() >= 44 && pos.getX() <= 63
                && pos.getZ() >= 40 && pos.getZ() <= 61;
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return ModStructures.FORT_CHEST_LOOT.get();
    }
}
