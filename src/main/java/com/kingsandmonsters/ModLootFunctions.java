package com.kingsandmonsters;

import com.kingsandmonsters.loot.CachedFortMapFunction;
import net.minecraft.core.registries.Registries;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModLootFunctions {
    public static final DeferredRegister<MapCodec<? extends LootItemFunction>> LOOT_FUNCTIONS =
            DeferredRegister.create(Registries.LOOT_FUNCTION_TYPE, KingsAndMonsters.MODID);

    public static final DeferredHolder<MapCodec<? extends LootItemFunction>, MapCodec<CachedFortMapFunction>>
            CACHED_FORT_MAP = LOOT_FUNCTIONS.register("cached_fort_map",
            () -> CachedFortMapFunction.CODEC);

    private ModLootFunctions() {
    }
}
