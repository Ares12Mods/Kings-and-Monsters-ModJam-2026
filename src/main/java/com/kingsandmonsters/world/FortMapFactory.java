package com.kingsandmonsters.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.Optional;

/** Creates fort maps only from positions already recorded in TributeSavedData. */
public final class FortMapFactory {
    private FortMapFactory() {
    }

    /** Resolves from the same unconditional deterministic placement used by Fort worldgen. */
    public static Optional<ItemStack> createNearestGuaranteedFortMap(ServerLevel level, BlockPos origin) {
        return FortTargetAuthority.findNearestGuaranteedFort(level, origin)
                .map(fort -> createFortMap(level, fort));
    }

    public static ItemStack createFortMap(ServerLevel level, BlockPos knownFortTarget) {
        ItemStack map = MapItem.create(level, knownFortTarget.getX(), knownFortTarget.getZ(),
                (byte) 2, true, true);
        MapItemSavedData.addTargetDecoration(map, knownFortTarget, "+", MapDecorationTypes.RED_X);
        map.set(DataComponents.CUSTOM_NAME,
                Component.translatable("filled_map.kingsandmonsters.ogre_kings_fort"));
        return map;
    }
}
