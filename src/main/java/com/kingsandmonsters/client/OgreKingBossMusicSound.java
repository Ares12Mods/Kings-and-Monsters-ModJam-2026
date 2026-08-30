package com.kingsandmonsters.client;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/** One streamed movement of the score whose lifetime and fade are owned by the encounter controller. */
final class OgreKingBossMusicSound extends AbstractTickableSoundInstance {
    private static final float FADE_STEP = 1.0F / 50.0F;
    // The sparse opening is not compatible with the full death cadence, so the
    // first intro exit is held until the melody has developed at measure 8.
    // The remaining intro exit is four measures later; once A begins,
    // two-measure downbeats keep combat death response under about 4.2 seconds.
    private static final int[] OPENING_VERSE_END_TICKS = {
            337, 505,
            589, 674, 758, 842, 926, 1011, 1095, 1179,
            1263, 1347, 1432, 1516, 1600, 1684, 1768, 1853,
            1937, 2021, 2105, 2189, 2274, 2358, 2442, 2526,
            2611, 2695, 2779, 2863, 2947, 3032, 3116, 3200
    };
    private static final int[] COMBAT_VERSE_END_TICKS = {
            84, 168, 253, 337, 421, 505, 589, 674,
            758, 842, 926, 1011, 1095, 1179, 1263, 1347,
            1432, 1516, 1600, 1684, 1768, 1853, 1937, 2021,
            2105, 2189, 2274, 2358, 2442, 2526, 2611, 2695
    };
    private final BossMusicController.MusicPhase phase;
    private boolean fadingOut;
    private int ticksPlayed;

    OgreKingBossMusicSound(SoundEvent soundEvent, BossMusicController.MusicPhase phase, boolean loop) {
        super(soundEvent, SoundSource.MUSIC, RandomSource.create());
        this.phase = phase;
        looping = loop;
        delay = 0;
        relative = true;
        attenuation = Attenuation.NONE;
        volume = 1.0F;
        pitch = 1.0F;
    }

    void fadeOut() {
        fadingOut = true;
    }

    void cancelFade() {
        fadingOut = false;
        volume = 1.0F;
    }

    BossMusicController.MusicPhase phase() {
        return phase;
    }

    boolean hasHadTimeToStart() {
        return ticksPlayed > 2;
    }

    boolean isLoopingMovement() {
        return looping;
    }

    int nextCombatVerseEndTick() {
        int[] boundaries = phase == BossMusicController.MusicPhase.INTRO
                ? OPENING_VERSE_END_TICKS
                : COMBAT_VERSE_END_TICKS;
        int position = phase == BossMusicController.MusicPhase.COMBAT
                ? ticksPlayed % COMBAT_VERSE_END_TICKS[COMBAT_VERSE_END_TICKS.length - 1]
                : ticksPlayed;
        for (int boundary : boundaries) {
            if (boundary > position) {
                return ticksPlayed + boundary - position;
            }
        }
        return ticksPlayed;
    }

    int ticksPlayed() {
        return ticksPlayed;
    }

    @Override
    public void tick() {
        ticksPlayed++;
        if (fadingOut) {
            volume = Math.max(0.0F, volume - FADE_STEP);
            if (volume <= 0.0F) {
                stop();
            }
        }
    }
}
