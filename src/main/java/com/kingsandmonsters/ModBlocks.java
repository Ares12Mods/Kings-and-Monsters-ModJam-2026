package com.kingsandmonsters;

import com.kingsandmonsters.block.OgreKingsCrownBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlocks {

    private static BlockBehaviour.Properties blockProperties(String name) {
        return BlockBehaviour.Properties.of().setId(ResourceKey.create(Registries.BLOCK,
                Identifier.fromNamespaceAndPath(KingsAndMonsters.MODID, name)));
    }

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(KingsAndMonsters.MODID);

    public static final DeferredBlock<Block> BOG_IRON_ORE = BLOCKS.register("bog_iron_ore",
            () -> new DropExperienceBlock(
                    UniformInt.of(0, 2),
                    blockProperties("bog_iron_ore")
                            .mapColor(MapColor.STONE)
                            .requiresCorrectToolForDrops()
                            .strength(3.0f, 3.0f)
                            .sound(SoundType.STONE)));

    public static final DeferredBlock<Block> DEEPSLATE_BOG_IRON_ORE = BLOCKS.register("deepslate_bog_iron_ore",
            () -> new DropExperienceBlock(
                    UniformInt.of(0, 2),
                    blockProperties("deepslate_bog_iron_ore")
                            .mapColor(MapColor.DEEPSLATE)
                            .requiresCorrectToolForDrops()
                            .strength(4.5f, 3.0f)
                            .sound(SoundType.DEEPSLATE)));

    public static final DeferredBlock<Block> BOG_IRON_BLOCK = BLOCKS.register("bog_iron_block",
            () -> new Block(blockProperties("bog_iron_block")
                    .mapColor(MapColor.METAL)
                    .requiresCorrectToolForDrops()
                    .strength(5.0f, 6.0f)
                    .sound(SoundType.METAL)));

    public static final DeferredBlock<Block> RAW_BOG_IRON_BLOCK = BLOCKS.register("raw_bog_iron_block",
            () -> new Block(blockProperties("raw_bog_iron_block")
                    .mapColor(MapColor.COLOR_LIGHT_GREEN)
                    .requiresCorrectToolForDrops()
                    .strength(5.0f, 6.0f)
                    .sound(SoundType.STONE)));

    public static final DeferredBlock<RotatedPillarBlock> BOG_OAK_LOG = BLOCKS.register("bog_oak_log",
            () -> new RotatedPillarBlock(blockProperties("bog_oak_log")
                    .mapColor(MapColor.WOOD)
                    .strength(2.0f)
                    .sound(SoundType.WOOD)
                            .ignitedByLava()));

    public static final DeferredBlock<OgreKingsCrownBlock> OGRE_KINGS_CROWN_DISPLAY = BLOCKS.register(
            "ogre_kings_crown_display",
            () -> new OgreKingsCrownBlock(blockProperties("ogre_kings_crown_display")
                    .mapColor(MapColor.GOLD)
                    .strength(0.5F)
                    .sound(SoundType.METAL)
                    .noOcclusion()));

    public static final DeferredItem<BlockItem> BOG_IRON_ORE_ITEM =
            ModItems.ITEMS.registerSimpleBlockItem(BOG_IRON_ORE);

    public static final DeferredItem<BlockItem> DEEPSLATE_BOG_IRON_ORE_ITEM =
            ModItems.ITEMS.registerSimpleBlockItem(DEEPSLATE_BOG_IRON_ORE);

    public static final DeferredItem<BlockItem> BOG_IRON_BLOCK_ITEM =
            ModItems.ITEMS.registerSimpleBlockItem(BOG_IRON_BLOCK);

    public static final DeferredItem<BlockItem> RAW_BOG_IRON_BLOCK_ITEM =
            ModItems.ITEMS.registerSimpleBlockItem(RAW_BOG_IRON_BLOCK);

    public static final DeferredItem<BlockItem> BOG_OAK_LOG_ITEM =
            ModItems.ITEMS.registerSimpleBlockItem(BOG_OAK_LOG);
}
