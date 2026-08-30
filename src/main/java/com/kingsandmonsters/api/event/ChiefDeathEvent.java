package com.kingsandmonsters.api.event;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.Event;

/**
 * Fired on {@link net.neoforged.neoforge.common.NeoForge#EVENT_BUS} after a
 * tribute camp's chief has died and the camp's state has been updated.
 *
 * <p>This event is <em>not</em> cancellable — the chief is already dead.</p>
 *
 * <p>Typical uses: advancements, quest systems, map-markers, sound cues.</p>
 *
 * <p>To listen for this event:</p>
 * <pre>{@code
 * @SubscribeEvent
 * public void onChiefDeath(ChiefDeathEvent event) {
 *     // Award a quest objective
 *     questSystem.complete(event.getKiller(), "kill_camp_chief");
 * }
 * }</pre>
 */
public class ChiefDeathEvent extends Event {

    private final LivingEntity chief;
    private final BlockPos campOrigin;
    private final ResourceKey<Level> dimension;

    /**
     * @param chief      the chief entity that just died
     * @param campOrigin origin block position of the tribute camp this chief belonged to
     * @param dimension  dimension the camp is in
     */
    public ChiefDeathEvent(LivingEntity chief, BlockPos campOrigin,
                           ResourceKey<Level> dimension) {
        this.chief = chief;
        this.campOrigin = campOrigin;
        this.dimension = dimension;
    }

    /**
     * The chief entity that died. At the time this event fires the entity
     * is still in the world but is dead (health &lt;= 0). Do not re-kill it.
     *
     * @return the chief entity
     */
    public LivingEntity getChief() {
        return chief;
    }

    /**
     * Convenience: returns the entity that killed the chief, or
     * {@code null} if it died by other means (fall, environment, etc.).
     *
     * @return the killer entity, or {@code null}
     */
    public LivingEntity getKiller() {
        return chief.getKillCredit();
    }

    /** The origin (anchor) block position of the tribute camp. */
    public BlockPos getCampOrigin() {
        return campOrigin;
    }

    /** The dimension the camp is in. */
    public ResourceKey<Level> getDimension() {
        return dimension;
    }
}
