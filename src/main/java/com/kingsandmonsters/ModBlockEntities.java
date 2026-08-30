package com.kingsandmonsters;

import com.kingsandmonsters.block.entity.OgreKingsCrownBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, KingsAndMonsters.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<OgreKingsCrownBlockEntity>> OGRE_KINGS_CROWN =
            BLOCK_ENTITY_TYPES.register("ogre_kings_crown", () ->
                    new BlockEntityType<>(OgreKingsCrownBlockEntity::new,
                            ModBlocks.OGRE_KINGS_CROWN_DISPLAY.get()));

    private ModBlockEntities() {
    }
}
