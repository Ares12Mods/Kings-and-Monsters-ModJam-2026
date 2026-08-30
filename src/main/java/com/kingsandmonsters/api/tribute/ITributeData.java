package com.kingsandmonsters.api.tribute;

import com.kingsandmonsters.api.patrol.PatrolTier;
import net.minecraft.core.BlockPos;

/**
 * Read/write access to the ogre faction's shared anger and the
 * chief-alive state tracked for a single tribute camp.
 *
 * <p>Instances are obtained through
 * {@link com.kingsandmonsters.api.KingsAndMonstersAPI#getTributeData(net.minecraft.resources.ResourceKey, BlockPos)}.
 * All setters mark the underlying {@link net.minecraft.world.level.saveddata.SavedData}
 * as dirty so the values are persisted across sessions.</p>
 *
 * <p>Other mods should treat this interface as stable API; the
 * implementation class is not part of the public surface.</p>
 */
public interface ITributeData {

    // ------------------------------------------------------------------ //
    //  Anger                                                               //
    // ------------------------------------------------------------------ //

    /**
     * Returns the current shared anger level for the ogre faction.
     * Actions at any tribute camp contribute to this value.
     *
     * @return current anger level, always &gt;= 0
     */
    int getAngerLevel();

    /**
     * Directly sets the shared anger level for the ogre faction.
     * Values are clamped to [0, max] by the implementation.
     *
     * @param level new anger level
     */
    void setAngerLevel(int level);

    /**
     * Increments anger by {@code delta}, respecting the maximum cap.
     * Negative deltas reduce anger (minimum 0).
     *
     * @param delta amount to add (may be negative)
     */
    void incrementAnger(int delta);

    /**
     * Returns the maximum anger level the ogre faction can reach.
     *
     * @return maximum anger cap
     */
    int getMaxAngerLevel();

    // ------------------------------------------------------------------ //
    //  Chief                                                               //
    // ------------------------------------------------------------------ //

    /**
     * Returns {@code true} if this camp's chief is currently alive.
     * Newly created camps start with the chief alive.
     *
     * @return {@code true} when the chief is alive
     */
    boolean isChiefAlive();

    /**
     * Sets whether this camp's chief is alive.
     *
     * @param alive {@code false} to mark the chief as dead
     */
    void setChiefAlive(boolean alive);

    // ------------------------------------------------------------------ //
    //  Derived helpers                                                     //
    // ------------------------------------------------------------------ //

    /**
     * Convenience method: returns the {@link PatrolTier} that should be
     * used for the next patrol spawn at this camp, given current state.
     *
     * @return appropriate patrol tier
     */
    default PatrolTier getCurrentPatrolTier() {
        return PatrolTier.fromAnger(getAngerLevel(), getMaxAngerLevel());
    }

    /**
     * Returns {@code true} if the camp is in a "fully angered" state
     * (anger is at its maximum cap and the chief is alive).
     *
     * @return {@code true} when anger is maxed out
     */
    default boolean isFullyAngered() {
        return isChiefAlive() && getAngerLevel() >= getMaxAngerLevel();
    }
}
