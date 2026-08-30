package com.kingsandmonsters.client;

import com.kingsandmonsters.ModSoundEvents;
import com.kingsandmonsters.entity.OgreLord;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.Comparator;

/** Central per-client owner for encounter music. Rendering and animation state never participate. */
public final class BossMusicController {
    private static final double ENTRY_RADIUS = 96.0;
    private static final double EXIT_RADIUS = 112.0;

    private static OgreKingBossMusicSound activeSound;
    private static ClientLevel activeLevel;
    private static boolean deathEndingPending;
    private static int deathEndingStartTick;

    enum MusicPhase {
        INTRO,
        COMBAT,
        DEATH
    }

    private BossMusicController() {}

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        Player player = minecraft.player;

        if (level == null || player == null || activeLevel != null && activeLevel != level) {
            stopImmediately(minecraft);
        }
        activeLevel = level;
        if (level == null || player == null || !player.isAlive()) {
            beginFade();
            suppressVanillaMusicWhileOwned(minecraft);
            clearFinished(minecraft);
            return;
        }

        double radius = activeSound == null ? ENTRY_RADIUS : EXIT_RADIUS;
        OgreLord nearbyDyingKing = level.getEntitiesOfClass(
                        OgreLord.class,
                        new AABB(player.blockPosition()).inflate(EXIT_RADIUS),
                        king -> !king.isRemoved()
                                && king.isDyingAnimation()
                                && king.distanceToSqr(player) <= EXIT_RADIUS * EXIT_RADIUS)
                .stream()
                .min(Comparator.comparingDouble(king -> king.distanceToSqr(player)))
                .orElse(null);

        OgreLord eligibleKing = level.getEntitiesOfClass(
                        OgreLord.class,
                        new AABB(player.blockPosition()).inflate(radius),
                        king -> king.isAlive()
                                && !king.isRemoved()
                                && !king.isDyingAnimation()
                                && king.isFortEncounterMusicActive()
                                && king.distanceToSqr(player) <= radius * radius)
                .stream()
                .min(Comparator.comparingDouble(king -> king.distanceToSqr(player)))
                .orElse(null);

        if (nearbyDyingKing != null && activeSound != null) {
            queueDeathEnding(minecraft);
        } else if (deathEndingPending) {
            advanceQueuedDeathEnding(minecraft);
        } else if (activeSound != null && activeSound.phase() == MusicPhase.DEATH) {
            clearFinished(minecraft);
        } else if (eligibleKing != null) {
            ensurePlaying(minecraft);
        } else {
            beginFade();
        }
        suppressVanillaMusicWhileOwned(minecraft);
        clearFinished(minecraft);
    }

    private static void ensurePlaying(Minecraft minecraft) {
        if (activeSound != null
                && activeSound.phase() == MusicPhase.INTRO
                && playbackFinished(minecraft)) {
            play(minecraft, MusicPhase.COMBAT);
            return;
        }
        if (activeSound != null && !activeSound.isStopped()) {
            activeSound.cancelFade();
            return;
        }
        play(minecraft, MusicPhase.INTRO);
    }

    private static void queueDeathEnding(Minecraft minecraft) {
        if (activeSound.phase() == MusicPhase.DEATH) {
            return;
        }
        if (!deathEndingPending) {
            deathEndingPending = true;
            deathEndingStartTick = activeSound.nextCombatVerseEndTick();
        }
        advanceQueuedDeathEnding(minecraft);
    }

    private static void advanceQueuedDeathEnding(Minecraft minecraft) {
        if (activeSound == null) {
            deathEndingPending = false;
            return;
        }
        if (activeSound.ticksPlayed() >= deathEndingStartTick || playbackFinished(minecraft)) {
            minecraft.getSoundManager().stop(activeSound);
            deathEndingPending = false;
            play(minecraft, MusicPhase.DEATH);
        }
    }

    private static void play(Minecraft minecraft, MusicPhase phase) {
        activeSound = switch (phase) {
            case INTRO -> new OgreKingBossMusicSound(
                    ModSoundEvents.OGRE_KING_BOSS_INTRO.get(), phase, false);
            case COMBAT -> new OgreKingBossMusicSound(
                    ModSoundEvents.OGRE_KING_BOSS_COMBAT.get(), phase, true);
            case DEATH -> new OgreKingBossMusicSound(
                    ModSoundEvents.OGRE_KING_BOSS_DEATH.get(), phase, false);
        };
        minecraft.getSoundManager().play(activeSound);
    }

    private static void beginFade() {
        if (activeSound != null) {
            activeSound.fadeOut();
        }
    }

    private static void suppressVanillaMusicWhileOwned(Minecraft minecraft) {
        if (activeSound != null && !activeSound.isStopped()) {
            minecraft.getMusicManager().stopPlaying();
        }
    }

    private static void clearFinished(Minecraft minecraft) {
        if (activeSound != null && (activeSound.isStopped() || playbackFinished(minecraft))) {
            minecraft.getSoundManager().stop(activeSound);
            activeSound = null;
        }
    }

    private static boolean playbackFinished(Minecraft minecraft) {
        return activeSound != null
                && !activeSound.isLoopingMovement()
                && activeSound.hasHadTimeToStart()
                && !minecraft.getSoundManager().isActive(activeSound);
    }

    private static void stopImmediately(Minecraft minecraft) {
        if (activeSound != null) {
            minecraft.getSoundManager().stop(activeSound);
            activeSound = null;
        }
        deathEndingPending = false;
        activeLevel = null;
    }
}
