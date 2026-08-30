package com.kingsandmonsters.api;

import com.kingsandmonsters.api.tribute.ITributeData;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Public entry point for the Kings and Monsters API.
 *
 * <h2>Usage</h2>
 * <p>Other mods (and Kings and Monsters internals) access camp data through
 * this class rather than directly touching implementation classes:</p>
 * <pre>{@code
 * Optional<ITributeData> data = KingsAndMonstersAPI.getTributeData(
 *         level.dimension(), campOriginPos);
 * data.ifPresent(d -> System.out.println("Anger: " + d.getAngerLevel()));
 * }</pre>
 *
 * <h2>Provider registration</h2>
 * <p>The Kings and Monsters mod itself registers the provider implementation
 * during world-load. Third-party mods should treat the provider as
 * potentially absent (it returns {@link Optional#empty()} if no provider is
 * registered or if the requested camp does not exist).</p>
 *
 * <h2>Events</h2>
 * All public events are in the {@link com.kingsandmonsters.api.event} package
 * and are fired on {@link net.neoforged.neoforge.common.NeoForge#EVENT_BUS}.
 */
public final class KingsAndMonstersAPI {

    /** Current version of this API surface. Follows semantic versioning. */
    public static final String API_VERSION = "1.0.0";

    private KingsAndMonstersAPI() {}

    // ------------------------------------------------------------------ //
    //  Provider registration (called by the mod, not by addon mods)       //
    // ------------------------------------------------------------------ //

    @Nullable
    private static ITributeDataProvider provider = null;

    /**
     * Registers the tribute-data provider.
     *
     * <p><strong>This method is for internal use by Kings and Monsters only.</strong>
     * Calling it from another mod will replace the active provider and break
     * the mod.</p>
     *
     * @param p the provider to register; {@code null} clears it (e.g. on world unload)
     */
    public static void setTributeDataProvider(@Nullable ITributeDataProvider p) {
        provider = p;
    }

    // ------------------------------------------------------------------ //
    //  Public API                                                          //
    // ------------------------------------------------------------------ //

    /**
     * Returns the {@link ITributeData} for the tribute camp nearest to
     * (or anchored at) {@code campOrigin} in the given dimension.
     *
     * <p>Returns {@link Optional#empty()} when:
     * <ul>
     *   <li>No provider is registered (world not loaded yet)</li>
     *   <li>No tribute camp exists at/near {@code campOrigin}</li>
     * </ul>
     *
     * @param dimension  the dimension to query
     * @param campOrigin the anchor position of the camp
     * @return tribute data, or empty if unavailable
     */
    public static Optional<ITributeData> getTributeData(ResourceKey<Level> dimension,
                                                        BlockPos campOrigin) {
        if (provider == null) return Optional.empty();
        return Optional.ofNullable(provider.get(dimension, campOrigin));
    }

    /**
     * Returns {@code true} if a tribute-data provider is currently registered.
     * You can use this as a quick guard before calling {@link #getTributeData}.
     *
     * @return {@code true} when a provider is active
     */
    public static boolean isAvailable() {
        return provider != null;
    }

    // ------------------------------------------------------------------ //
    //  Provider interface                                                  //
    // ------------------------------------------------------------------ //

    /**
     * Internal interface implemented by Kings and Monsters to supply
     * {@link ITributeData} instances. Not part of the stable addon API.
     */
    @FunctionalInterface
    public interface ITributeDataProvider {

        /**
         * Looks up (or creates) tribute data for the camp at the given position.
         *
         * @param dimension  dimension to look in
         * @param campOrigin anchor position of the camp
         * @return tribute data, or {@code null} if no camp exists there
         */
        @Nullable
        ITributeData get(ResourceKey<Level> dimension, BlockPos campOrigin);
    }
}
