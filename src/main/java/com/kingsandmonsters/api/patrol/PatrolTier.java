package com.kingsandmonsters.api.patrol;

/**
 * Represents the strength tier of a tribute camp patrol.
 *
 * <p>Tier is determined by the camp's current anger level.
 * Other mods can read this from {@link
 * com.kingsandmonsters.api.event.PatrolSpawnEvent} to adjust their
 * own behaviour (e.g. a difficulty mod scaling enemy stats).</p>
 *
 * <ul>
 *   <li>{@link #SMALL}  — low anger</li>
 *   <li>{@link #MEDIUM} — moderate anger</li>
 *   <li>{@link #LARGE}  — high anger</li>
 * </ul>
 */
public enum PatrolTier {

    /** Small patrol: a Grunt Captain leads 2 grunts and an archer. Low anger. */
    SMALL,

    /** Medium patrol: a Mage leads 2 grunts and an archer. Moderate anger. */
    MEDIUM,

    /** Large patrol: a Brute leads a mage, 2 grunts, and an archer. High anger. */
    LARGE;

    /**
     * Returns the tier that corresponds to the given anger level.
     *
     * <ul>
     *   <li>0–9    → {@link #SMALL}  (0-30% of max anger)</li>
     *   <li>10–20  → {@link #MEDIUM} (40-70% of max anger)</li>
     *   <li>21+    → {@link #LARGE}  (80-100% of max anger)</li>
     * </ul>
     *
     * @param angerLevel current anger value (0 or higher)
     * @return the matching tier
     */
    public static PatrolTier fromAnger(int angerLevel) {
        return fromAnger(angerLevel, 30);
    }

    /**
     * Uses percentage thresholds so custom anger caps preserve the documented
     * Low (0-30%), Moderate (31-70%), and High (71-100%) progression.
     */
    public static PatrolTier fromAnger(int angerLevel, int maxAngerLevel) {
        int anger = Math.max(0, angerLevel);
        int maximum = Math.max(1, maxAngerLevel);
        long scaledAnger = (long) anger * 10L;
        if (scaledAnger <= (long) maximum * 3L) return SMALL;
        if (scaledAnger <= (long) maximum * 7L) return MEDIUM;
        return LARGE;
    }
}
