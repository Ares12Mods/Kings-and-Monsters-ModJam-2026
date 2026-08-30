package com.kingsandmonsters.client;

/** Client-side state for the backpack currently being viewed by this player. */
public final class BackpackAnimationState {
    private static final long DURATION_MS = 500L;
    private static long transitionStarted;
    private static boolean open;
    private static float startDegrees;

    private BackpackAnimationState() {}

    public static void open() {
        startDegrees = angleDegrees();
        transitionStarted = System.currentTimeMillis();
        open = true;
    }

    public static void close() {
        startDegrees = angleDegrees();
        transitionStarted = System.currentTimeMillis();
        open = false;
    }

    public static float angleDegrees() {
        float target = open ? 30F : 0F;
        float progress = Math.min(1F, (System.currentTimeMillis() - transitionStarted) / (float) DURATION_MS);
        // A smooth endpoint curve closely matches the supplied Catmull-Rom two-keyframe motion.
        float eased = progress * progress * (3F - 2F * progress);
        return startDegrees + (target - startDegrees) * eased;
    }
}
