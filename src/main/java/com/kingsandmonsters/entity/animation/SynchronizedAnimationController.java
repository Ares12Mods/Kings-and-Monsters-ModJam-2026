package com.kingsandmonsters.entity.animation;

import com.geckolib.animatable.GeoAnimatable;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;

/**
 * GeckoLib controller that can begin a newly selected one-shot at an authoritative elapsed tick.
 * GeckoLib normally resets a newly observed animation to tick zero; this adjusts that reset's
 * clock origin so entities first rendered mid-action do not replay history from the beginning.
 */
public final class SynchronizedAnimationController<T extends GeoAnimatable> extends AnimationController<T> {
    private double pendingElapsedTick = -1.0;
    private double pendingGapElapsedTick = -1.0;
    private long consumedEventStartTick = Long.MIN_VALUE;
    private int consumedEventType = Integer.MIN_VALUE;
    private long lastEvaluationGameTick = Long.MIN_VALUE;

    public SynchronizedAnimationController(T animatable, String name, int transitionTicks,
                                           AnimationStateHandler<T> handler) {
        super(name, transitionTicks, handler);
    }

    private boolean consumeAndSeek(int eventType, long eventStartTick, double elapsedTick) {
        if (eventType == consumedEventType && eventStartTick == consumedEventStartTick) {
            return false;
        }
        consumedEventType = eventType;
        consumedEventStartTick = eventStartTick;
        pendingGapElapsedTick = -1.0;
        pendingElapsedTick = Math.max(0.0, elapsedTick);
        // Drop ONLY the timeline, never reset().
        //
        // All this needs to do is force initializeNewAnimation() to run so the override below can
        // seek the fresh timeline; checkControllerState() gates that on
        // "animationPoint == null || timeline == null || the raw animation changed", so nulling the
        // timeline alone is sufficient and is the least destructive way to do it.
        //
        // reset() (the GeckoLib 4 port of this used forceAnimationReset(), which only raised a
        // "restart the clock" flag and left the pose state alone) additionally nulls animationPoint
        // and transitionFromPoint. Those two ARE the blend source: initializeNewAnimation() feeds
        // the surviving animationPoint into timeline.createAnimationPoint(time, previousPoint, ...)
        // precisely so the timeline's leading transition stage has a pose to crossfade FROM. With
        // them nulled the new clip had nothing to blend against, so the leading transition rendered
        // as a hard snap onto the attack's first frame — the run clip appearing to be cut dead the
        // instant an attack committed, most visible on the King, whose run pose is the furthest from
        // any attack's opening pose. Keeping them makes that stage a real crossfade again.
        //
        // Deliberately NOT touching playState here either: reset() set it to STOP, which makes the
        // next checkControllerState() see wasStopped and take the branch that forces timelineTime
        // back to 0 — undoing this class's whole reason for existing.
        //
        // The seek in initializeNewAnimation() is unchanged, so the elapsed clock still includes the
        // leading transition and every release-tick constant tuned against that stays valid.
        this.timeline = null;
        return true;
    }

    @Override
    protected void initializeNewAnimation(T animatable, GeoRenderState renderState, GeoModel<T> geoModel,
                                          double previousAnimationSpeed, int previousTransitionTicks) {
        super.initializeNewAnimation(animatable, renderState, geoModel,
                previousAnimationSpeed, previousTransitionTicks);
        if (pendingElapsedTick >= 0.0) {
            // GeckoLib 5 timelines use seconds; the authoritative entity timestamps are game ticks.
            // Passing raw ticks here advances clips 20x too far and makes their sampled pose jump.
            //
            // Seek the OVERALL timeline, not the animation stage. GeckoLib 5 builds the timeline as
            // [transition][animation][transition], where the leading transition is
            // getTransitionTicks()/20 seconds long. setAnimationTime() resolves onto the animation
            // stage's startTime, which silently skips that leading transition and starts the clip
            // getTransitionTicks() ticks earlier than the authoritative event timestamp says it
            // should. GeckoLib 4 (the 1.21.1 reference) consumed the transition out of the same
            // elapsed clock, and every Java release-tick constant in this mod is tuned against that
            // behaviour, so skipping it desynchronises every synchronized one-shot by the
            // controller's transition length. setTimelineTime() keeps the transition in the clock.
            setTimelineTime(pendingElapsedTick / 20.0);
            pendingElapsedTick = -1.0;
        }
    }


    /**
     * Reconciles a retained one-shot against absolute world time. Continuous evaluations leave
     * GeckoLib alone; the first evaluation after a render gap seeks an active event forward once,
     * while an expired event is discarded once so locomotion can take over immediately.
     */
    public static boolean revalidateEvent(AnimationController<?> controller, int eventType,
                                          long eventStartTick, int durationTicks,
                                          long currentGameTick) {
        if (eventStartTick == Long.MIN_VALUE) return false;
        double elapsed = Math.max(0L, currentGameTick - eventStartTick);
        if (!(controller instanceof SynchronizedAnimationController<?> synchronizedController)) {
            return elapsed < durationTicks;
        }

        boolean sameEvent = eventType == synchronizedController.consumedEventType
                && eventStartTick == synchronizedController.consumedEventStartTick;
        boolean renderGap = sameEvent
                && synchronizedController.lastEvaluationGameTick != Long.MIN_VALUE
                && currentGameTick - synchronizedController.lastEvaluationGameTick > 2L;
        synchronizedController.lastEvaluationGameTick = currentGameTick;

        if (elapsed >= durationTicks) {
            if (sameEvent) {
                synchronizedController.pendingElapsedTick = -1.0;
                synchronizedController.pendingGapElapsedTick = -1.0;
            }
            return false;
        }

        if (!sameEvent || renderGap) {
            if (renderGap) {
                synchronizedController.seekActiveEventOnce(elapsed);
            } else {
                synchronizedController.consumeAndSeek(eventType, eventStartTick, elapsed);
            }
        }
        return true;
    }

    private void seekActiveEventOnce(double elapsedTick) {
        // This is still the same running event. Rebase its clock for one evaluation only; resetting
        // the identical clip here can leave GeckoLib's transition state waiting indefinitely.
        pendingGapElapsedTick = Math.max(0.0, elapsedTick);
        // Same overall-timeline clock as initializeNewAnimation: the leading transition stage is
        // part of the elapsed time, so a catch-up seek must not resolve onto the animation stage.
        setTimelineTime(pendingGapElapsedTick / 20.0);
        pendingGapElapsedTick = -1.0;
    }
}
