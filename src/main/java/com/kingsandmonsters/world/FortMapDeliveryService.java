package com.kingsandmonsters.world;

import com.kingsandmonsters.tribute.TributeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;

/**
 * Repairs old development-world Outpost chests whose loot table was unpacked before a usable Fort
 * target existed. Fresh worlds resolve their deterministic target before the chest can be opened.
 *
 * <p>If the chest never had a chance to unpack (nobody opened it yet), nothing needs to happen
     * here — the normal loot-function path in {@code CachedFortMapFunction} handles it on first open.
 */
public final class FortMapDeliveryService {

    private FortMapDeliveryService() {
    }

    /** Safe to call opportunistically and repeatedly; a no-op unless every precondition is met. */
    public static void tryDeliver(ServerLevel level, BlockPos campOrigin) {
        if (TributeManager.isMapRewardFulfilled(level, campOrigin)) {
            return;
        }

        // Always recalculate: a persisted target may be an obsolete reservation from a development
        // world. Refuse it rather than creating another map to terrain that is no longer authoritative.
        var target = FortTargetAuthority.findNearestGuaranteedFort(level, campOrigin);
        target.ifPresent(value -> TributeManager.setOutpostFortMapTarget(level, campOrigin, value));
        if (target.isEmpty()) {
            return;
        }

        var chestPos = TributeManager.getOutpostMapChestPos(level, campOrigin);
        if (chestPos.isEmpty()) {
            return;
        }
        if (!level.isLoaded(chestPos.get())) {
            return;
        }

        BlockEntity blockEntity = level.getBlockEntity(chestPos.get());
        if (!(blockEntity instanceof RandomizableContainerBlockEntity chest)) {
            return;
        }
        if (chest.getLootTable() != null) {
            // Never opened yet — the ordinary loot-function path will deliver the map correctly
            // the first time a player opens it, now that the target is resolved.
            return;
        }

        ItemStack map = FortMapFactory.createFortMap(level, target.get());
        Container container = chest;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            if (container.getItem(slot).isEmpty()) {
                container.setItem(slot, map);
                container.setChanged();
                // Marked fulfilled only now that the map is physically in the container — never
                // before a real insertion actually succeeds (see class doc on markMapRewardFulfilled).
                TributeManager.markMapRewardFulfilled(level, campOrigin);
                return;
            }
        }

        // Chest is full — drop the reward rather than silently losing it. Still counts as
        // physically delivered: the item exists in the world, not merely intended.
        BlockPos pos = chestPos.get();
        ItemEntity itemEntity = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, map);
        level.addFreshEntity(itemEntity);
        TributeManager.markMapRewardFulfilled(level, campOrigin);
    }
}
