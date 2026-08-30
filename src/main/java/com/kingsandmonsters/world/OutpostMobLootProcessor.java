package com.kingsandmonsters.world;

import com.mojang.serialization.Codec;
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
import java.util.Map;

/**
 * Assigns hut loot using template-local coordinates. Only storage containers
 * intentionally placed for players are listed here; the many barrels used as
 * roof, wall, and tower detailing remain untouched.
 *
 * <p>{@link StructureTemplate}/{@link StructurePlaceSettings} carry no identity info inside
 * {@link #process}, so a single shared coordinate map cannot tell the four outpost templates
 * apart (their local coordinates collide). Instead each of the four templates gets its own
 * processor instance, parameterized by {@link #variant} and wired to a dedicated processor_list
 * (see {@code worldgen/processor_list/ogre_outpost_*.json} and the per-element {@code processors}
 * field in {@code worldgen/template_pool/ogre_outpost/outposts.json}).
 */
public final class OutpostMobLootProcessor extends StructureProcessor {
    public static final MapCodec<OutpostMobLootProcessor> CODEC =
            Codec.STRING.optionalFieldOf("variant", "brute")
                    .xmap(OutpostMobLootProcessor::new, processor -> processor.variant);

    private static final String BRUTE_LOOT = "kingsandmonsters:chests/ogre_outpost_brute";
    private static final String GRUNT_LOOT = "kingsandmonsters:chests/ogre_outpost_grunt";
    private static final String MAGE_LOOT = "kingsandmonsters:chests/ogre_outpost_mage";
    private static final String BRUTE_BOOK_LOOT = "kingsandmonsters:chests/ogre_outpost_brute_book";
    private static final String GRUNT_BOOK_LOOT = "kingsandmonsters:chests/ogre_outpost_grunt_book";
    private static final String MAGE_BOOK_LOOT = "kingsandmonsters:chests/ogre_outpost_mage_book";

    // brute_outpost.nbt
    private static final Map<BlockPos, String> BRUTE_CHEST_LOOT = Map.of(
            new BlockPos(2, 1, 15), BRUTE_BOOK_LOOT,
            new BlockPos(3, 1, 15), BRUTE_LOOT);
    private static final Map<BlockPos, String> BRUTE_BARREL_LOOT = Map.of(
            new BlockPos(9, 2, 17), BRUTE_LOOT);

    // grunt_outpost.nbt
    private static final Map<BlockPos, String> GRUNT_CHEST_LOOT = Map.of(
            new BlockPos(9, 1, 22), GRUNT_LOOT,
            new BlockPos(21, 1, 3), GRUNT_LOOT,
            new BlockPos(21, 1, 4), GRUNT_BOOK_LOOT);
    private static final Map<BlockPos, String> GRUNT_BARREL_LOOT = Map.of(
            new BlockPos(15, 2, 10), GRUNT_LOOT);

    // mage_outpost.nbt (first mage variant; this template has no chest blocks at all)
    private static final Map<BlockPos, String> MAGE_CHEST_LOOT = Map.of();
    private static final Map<BlockPos, String> MAGE_BARREL_LOOT = Map.of(
            new BlockPos(10, 2, 19), MAGE_BOOK_LOOT,
            new BlockPos(23, 2, 19), MAGE_LOOT);

    // mage_outpost_2.nbt (second mage variant)
    private static final Map<BlockPos, String> MAGE_2_CHEST_LOOT = Map.of(
            new BlockPos(4, 1, 23), MAGE_BOOK_LOOT,
            new BlockPos(5, 1, 15), MAGE_LOOT,
            new BlockPos(28, 1, 2), MAGE_LOOT,
            new BlockPos(30, 1, 21), MAGE_LOOT);
    private static final Map<BlockPos, String> MAGE_2_BARREL_LOOT = Map.of(
            new BlockPos(5, 2, 9), MAGE_LOOT);

    // Ground truth for which template is actually being placed — captured here rather than
    // guessed later from PoolElementStructurePiece#toString() parsing, since every one of the
    // four templates now runs through its own dedicated processor instance (see class doc). Read
    // by OgreOutpostStructure#afterPlace once placement finishes for this chunk pass.
    private static final ThreadLocal<String> CAPTURED_VARIANT = new ThreadLocal<>();

    private final String variant;

    public OutpostMobLootProcessor(String variant) {
        this.variant = variant;
    }

    /** Consumes and clears the variant captured on this thread since the last call, if any. */
    @Nullable
    public static String takeCapturedVariant() {
        String captured = CAPTURED_VARIANT.get();
        CAPTURED_VARIANT.remove();
        return captured;
    }

    private Map<BlockPos, String> chestLoot() {
        return switch (variant) {
            case "grunt" -> GRUNT_CHEST_LOOT;
            case "mage" -> MAGE_CHEST_LOOT;
            case "mage_2" -> MAGE_2_CHEST_LOOT;
            default -> BRUTE_CHEST_LOOT;
        };
    }

    private Map<BlockPos, String> barrelLoot() {
        return switch (variant) {
            case "grunt" -> GRUNT_BARREL_LOOT;
            case "mage" -> MAGE_BARREL_LOOT;
            case "mage_2" -> MAGE_2_BARREL_LOOT;
            default -> BRUTE_BARREL_LOOT;
        };
    }

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
        Map<BlockPos, String> assignments;
        if (relativeInfo.state().is(Blocks.CHEST)) {
            assignments = chestLoot();
        } else if (relativeInfo.state().is(Blocks.BARREL)) {
            assignments = barrelLoot();
        } else {
            return relativeInfo;
        }

        String lootTable = assignments.get(rawInfo.pos());
        if (lootTable == null) {
            return relativeInfo;
        }

        CompoundTag nbt = relativeInfo.nbt() == null
                ? new CompoundTag()
                : relativeInfo.nbt().copy();
        nbt.putString("LootTable", lootTable);
        nbt.remove("Items");
        return new StructureTemplate.StructureBlockInfo(
                relativeInfo.pos(), relativeInfo.state(), nbt);
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return ModStructures.OUTPOST_MOB_LOOT.get();
    }
}
