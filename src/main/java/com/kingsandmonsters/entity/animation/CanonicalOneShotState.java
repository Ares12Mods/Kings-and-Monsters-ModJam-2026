package com.kingsandmonsters.entity.animation;

import com.geckolib.animation.AnimationController;

import java.util.function.IntUnaryOperator;

/**
 * The single client-side owner of a synchronized full-body one-shot.
 *
 * <p>An accepted event is immutable until its authored absolute end. Server cleanup may clear the
 * observed fields early, but it cannot shorten the visual event. Conversely, a separately synced
 * ID can never replace the clip while the accepted timestamp is still active.</p>
 */
public final class CanonicalOneShotState {
    public enum Lifecycle {
        NONE,
        ACTIVE_VISIBLE,
        ACTIVE_AFTER_RENDER_GAP,
        EXPIRED
    }

    private int eventType;
    private long startGameTime = Long.MIN_VALUE;
    private int durationTicks;
    private long lastEvaluationGameTime = Long.MIN_VALUE;
    private Lifecycle lifecycle = Lifecycle.NONE;

    /**
     * Reconciles one cheap synchronized snapshot and returns the immutable active event type, or 0.
     */
    public int update(AnimationController<?> controller, int observedType, long observedStartGameTime,
                      IntUnaryOperator durationForType, long currentGameTime) {
        expireIfNecessary(currentGameTime);

        boolean validObservation = observedType > 0 && observedStartGameTime > 0L;
        if (validObservation) {
            int observedDuration = Math.max(1, durationForType.applyAsInt(observedType));
            long observedEnd = observedStartGameTime + observedDuration;

            if (lifecycle == Lifecycle.NONE || lifecycle == Lifecycle.EXPIRED) {
                // Accept a fresh, currently valid event immediately. Waiting for a second
                // controller evaluation left King and Brute in locomotion during real attacks.
                // The strictly newer timestamp still prevents an expired event from replaying.
                if (observedStartGameTime > startGameTime && currentGameTime < observedEnd) {
                    accept(observedType, observedStartGameTime, observedDuration);
                }
            } else if (observedType == eventType && observedStartGameTime == startGameTime) {
                // Same immutable event; normal continuous playback needs no reset.
            } else if (currentGameTime >= startGameTime + durationTicks
                    && observedStartGameTime > startGameTime && currentGameTime < observedEnd) {
                accept(observedType, observedStartGameTime, observedDuration);
            }
            // A different ID/start cannot mutate a still-active event. If the server genuinely
            // begins it after this event ends, the newer timestamp is accepted on a later frame.
        }

        if (lifecycle != Lifecycle.ACTIVE_VISIBLE && lifecycle != Lifecycle.ACTIVE_AFTER_RENDER_GAP) {
            lastEvaluationGameTime = currentGameTime;
            return 0;
        }

        boolean renderGap = lastEvaluationGameTime != Long.MIN_VALUE
                && currentGameTime - lastEvaluationGameTime > 2L;
        lifecycle = renderGap ? Lifecycle.ACTIVE_AFTER_RENDER_GAP : Lifecycle.ACTIVE_VISIBLE;
        lastEvaluationGameTime = currentGameTime;

        if (!SynchronizedAnimationController.revalidateEvent(
                controller, eventType, startGameTime, durationTicks, currentGameTime)) {
            lifecycle = Lifecycle.EXPIRED;
            return 0;
        }

        // revalidateEvent performs the one catch-up seek. The state immediately returns to normal
        // natural playback; ACTIVE_AFTER_RENDER_GAP never persists into another evaluation.
        lifecycle = Lifecycle.ACTIVE_VISIBLE;
        return eventType;
    }

    public Lifecycle lifecycle() {
        return lifecycle;
    }

    /** Intentional terminal override for exceptional states such as the Ogre King's death. */
    public int updateTerminal(AnimationController<?> controller, int terminalType,
                              long terminalStartGameTime, int terminalDurationTicks,
                              long currentGameTime) {
        int safeDuration = Math.max(1, terminalDurationTicks);
        if (eventType != terminalType || startGameTime != terminalStartGameTime
                || lifecycle == Lifecycle.NONE || lifecycle == Lifecycle.EXPIRED) {
            accept(terminalType, terminalStartGameTime, safeDuration);
        }
        if (!SynchronizedAnimationController.revalidateEvent(
                controller, eventType, startGameTime, durationTicks, currentGameTime)) {
            lifecycle = Lifecycle.EXPIRED;
            return 0;
        }
        lifecycle = Lifecycle.ACTIVE_VISIBLE;
        return eventType;
    }

    private void accept(int type, long start, int duration) {
        eventType = type;
        startGameTime = start;
        durationTicks = duration;
        lastEvaluationGameTime = Long.MIN_VALUE;
        lifecycle = Lifecycle.ACTIVE_VISIBLE;
    }

    private void expireIfNecessary(long currentGameTime) {
        if ((lifecycle == Lifecycle.ACTIVE_VISIBLE || lifecycle == Lifecycle.ACTIVE_AFTER_RENDER_GAP)
                && currentGameTime >= startGameTime + durationTicks) {
            lifecycle = Lifecycle.EXPIRED;
        }
    }
}
