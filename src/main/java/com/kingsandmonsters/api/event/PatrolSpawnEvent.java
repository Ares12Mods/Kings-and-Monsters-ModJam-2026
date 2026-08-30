package com.kingsandmonsters.api.event;

import com.kingsandmonsters.api.patrol.PatrolTier;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/**
 * Fired on {@link net.neoforged.neoforge.common.NeoForge#EVENT_BUS} just
 * before a tribute camp patrol is spawned into the world.
 *
 * <p><b>Cancelling</b> this event fully suppresses the patrol spawn —
 * no entities are placed and no cooldown is consumed. Use this sparingly;
 * it may make camps feel unresponsive if overused.</p>
 *
 * <p>Listeners can also change the {@link PatrolTier} to escalate or
 * de-escalate the patrol without touching the camp's stored anger value.</p>
 *
 * <p>To listen for this event:</p>
 * <pre>{@code
 * @SubscribeEvent
 * public void onPatrolSpawn(PatrolSpawnEvent event) {
 *     // Force large patrols on hard difficulty
 *     if (event.getLevel().getDifficulty() == Difficulty.HARD) {
 *         event.setTier(PatrolTier.LARGE);
 *     }
 * }
 * }</pre>
 */
public class PatrolSpawnEvent extends Event implements ICancellableEvent {

    private final BlockPos campOrigin;
    private final ResourceKey<Level> dimension;
    private PatrolTier tier;

    /**
     * @param campOrigin origin block position of the tribute camp structure
     * @param dimension  dimension the camp is in
     * @param tier       patrol tier that would normally spawn
     */
    public PatrolSpawnEvent(BlockPos campOrigin, ResourceKey<Level> dimension,
                            PatrolTier tier) {
        this.campOrigin = campOrigin;
        this.dimension = dimension;
        this.tier = tier;
    }

    /** The origin (anchor) position of the tribute camp structure. */
    public BlockPos getCampOrigin() {
        return campOrigin;
    }

    /** The dimension the camp (and patrol) are in. */
    public ResourceKey<Level> getDimension() {
        return dimension;
    }

    /**
     * The patrol tier that will be spawned.
     * May have been modified by an earlier listener.
     *
     * @return current tier
     */
    public PatrolTier getTier() {
        return tier;
    }

    /**
     * Override the patrol tier. This only affects the patrol about to
     * spawn; it does not change the camp's stored anger level.
     *
     * @param tier new tier
     */
    public void setTier(PatrolTier tier) {
        this.tier = tier;
    }
}
