package com.kingsandmonsters.api.event;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/**
 * Fired on {@link net.neoforged.neoforge.common.NeoForge#EVENT_BUS} just
 * before a tribute chest is marked as looted and anger is increased.
 *
 * <p><b>Cancelling</b> this event prevents the anger increment from
 * happening (the chest still opens; only the anger change is suppressed).
 * This is useful for mods that want to grant a "free loot" mechanic or
 * implement their own anger gating.</p>
 *
 * <p>To listen for this event:</p>
 * <pre>{@code
 * @SubscribeEvent
 * public void onTributeChestLooted(TributeChestLootedEvent event) {
 *     // e.g. reduce anger delta when player has a special item
 *     if (event.getPlayer().getMainHandItem().is(MyItems.TRIBUTE_TOKEN)) {
 *         event.setAngerDelta(0);
 *     }
 * }
 * }</pre>
 */
public class TributeChestLootedEvent extends Event implements ICancellableEvent {

    private final Player player;
    private final BlockPos chestPos;
    private final ResourceKey<Level> dimension;
    private int angerDelta;

    /**
     * @param player     the player who opened/looted the chest
     * @param chestPos   world position of the tribute chest block
     * @param dimension  dimension the chest is in
     * @param angerDelta how much anger would normally be added (positive int)
     */
    public TributeChestLootedEvent(Player player, BlockPos chestPos,
                                   ResourceKey<Level> dimension, int angerDelta) {
        this.player = player;
        this.chestPos = chestPos;
        this.dimension = dimension;
        this.angerDelta = angerDelta;
    }

    /** The player who looted the chest. */
    public Player getPlayer() {
        return player;
    }

    /** World position of the tribute chest block. */
    public BlockPos getChestPos() {
        return chestPos;
    }

    /** The dimension this chest is in. */
    public ResourceKey<Level> getDimension() {
        return dimension;
    }

    /**
     * How much anger will be added to the camp if the event is not
     * cancelled. Defaults to the value set in the chest config.
     *
     * @return anger delta (may have been modified by a listener)
     */
    public int getAngerDelta() {
        return angerDelta;
    }

    /**
     * Override the anger delta. Set to {@code 0} to suppress the anger
     * increase without cancelling the entire event.
     *
     * @param angerDelta new delta; negative values will reduce anger
     */
    public void setAngerDelta(int angerDelta) {
        this.angerDelta = angerDelta;
    }
}
