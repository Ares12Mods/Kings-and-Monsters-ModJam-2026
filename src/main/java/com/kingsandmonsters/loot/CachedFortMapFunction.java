package com.kingsandmonsters.loot;

import com.kingsandmonsters.ModLootFunctions;
import com.kingsandmonsters.tribute.TributeManager;
import com.kingsandmonsters.world.FortMapFactory;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

/** Converts a blank map to a fort map without invoking Minecraft's structure locator. */
public final class CachedFortMapFunction implements LootItemFunction {
    public static final MapCodec<CachedFortMapFunction> CODEC = MapCodec.unit(CachedFortMapFunction::new);

    @Override
    public MapCodec<? extends LootItemFunction> codec() {
        return ModLootFunctions.CACHED_FORT_MAP.get();
    }

    @Override
    public ItemStack apply(ItemStack stack, LootContext context) {
        if (!stack.is(Items.MAP)) return stack;
        var origin = context.getOptionalParameter(LootContextParams.ORIGIN);
        if (origin == null) return ItemStack.EMPTY;
        BlockPos lootOrigin = BlockPos.containing(origin);
        var level = context.getLevel();

        if (TributeManager.isMapRewardFulfilledNear(level, lootOrigin)) {
            // Another chest belonging to this same Outpost (e.g. grunt_outpost.nbt has two trapped
            // chests) already delivered the guaranteed map — never hand out a second one.
            return ItemStack.EMPTY;
        }

        var campOrigin = TributeManager.findOutpostCampOrigin(level, lootOrigin);
        var target = campOrigin.flatMap(outpostOrigin -> {
            var deterministic = com.kingsandmonsters.world.FortTargetAuthority
                    .findNearestGuaranteedFort(level, outpostOrigin);
            deterministic.ifPresent(value -> TributeManager.setOutpostFortMapTarget(level, outpostOrigin, value));
            return deterministic;
        });
        if (target.isPresent()) {
            if (!TributeManager.markMapRewardFulfilledNear(level, lootOrigin)) {
                return ItemStack.EMPTY;
            }
            return FortMapFactory.createFortMap(level, target.get());
        }

        if (campOrigin.isPresent()) {
            // A missing placement registry is safer than emitting a stale map from a legacy
            // reservation. Fresh correctly-loaded worlds never reach this branch.
            return ItemStack.EMPTY;
        }

        return FortMapFactory.createNearestGuaranteedFortMap(level, lootOrigin).orElse(ItemStack.EMPTY);
    }
}
