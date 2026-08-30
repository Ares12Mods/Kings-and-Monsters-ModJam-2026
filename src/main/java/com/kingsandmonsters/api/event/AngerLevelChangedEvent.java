package com.kingsandmonsters.api.event;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.Event;

/**
 * Fired on {@link net.neoforged.neoforge.common.NeoForge#EVENT_BUS} after
 * the ogre faction's shared anger level has changed and been saved.
 *
 * <p>This event is <em>not</em> cancellable — the change has already been
 * applied. Use {@link TributeChestLootedEvent} if you need to prevent an
 * anger change before it happens.</p>
 *
 * <p>Typical uses: UI overlays, achievements, advancements, logging.</p>
 *
 * <p>To listen for this event:</p>
 * <pre>{@code
 * @SubscribeEvent
 * public void onAngerChanged(AngerLevelChangedEvent event) {
 *     if (event.getNewAngerLevel() >= 8) {
 *         // warn nearby players about imminent large patrol
 *     }
 * }
 * }</pre>
 */
public class AngerLevelChangedEvent extends Event {

    private final BlockPos campOrigin;
    private final ResourceKey<Level> dimension;
    private final int oldAngerLevel;
    private final int newAngerLevel;

    /**
     * @param campOrigin    origin of the camp whose activity caused the change
     * @param dimension     dimension the camp is in
     * @param oldAngerLevel anger value before the change
     * @param newAngerLevel anger value after the change (already clamped)
     */
    public AngerLevelChangedEvent(BlockPos campOrigin, ResourceKey<Level> dimension,
                                  int oldAngerLevel, int newAngerLevel) {
        this.campOrigin = campOrigin;
        this.dimension = dimension;
        this.oldAngerLevel = oldAngerLevel;
        this.newAngerLevel = newAngerLevel;
    }

    /** The origin of the camp whose activity caused the faction anger change. */
    public BlockPos getCampOrigin() {
        return campOrigin;
    }

    /** The dimension the camp is in. */
    public ResourceKey<Level> getDimension() {
        return dimension;
    }

    /**
     * Anger level before this change was applied.
     *
     * @return previous anger level
     */
    public int getOldAngerLevel() {
        return oldAngerLevel;
    }

    /**
     * Anger level after this change was applied (already clamped to [0, max]).
     *
     * @return new anger level
     */
    public int getNewAngerLevel() {
        return newAngerLevel;
    }

    /**
     * Returns {@code true} if anger increased (loot was stolen).
     *
     * @return {@code true} when newAngerLevel &gt; oldAngerLevel
     */
    public boolean isIncreasing() {
        return newAngerLevel > oldAngerLevel;
    }
}
