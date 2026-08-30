package com.kingsandmonsters.entity;

import com.kingsandmonsters.Config;
import com.kingsandmonsters.ModEntities;
import com.kingsandmonsters.ModMobEffects;
import com.kingsandmonsters.ModSoundEvents;
import com.kingsandmonsters.effect.CombatEffects;
import com.kingsandmonsters.entity.animation.SynchronizedAnimationController;
import com.kingsandmonsters.entity.animation.CanonicalOneShotState;
import com.kingsandmonsters.network.ScreenShakePayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.ChatFormatting;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.level.Level;
import net.minecraft.world.BossEvent;
import net.minecraft.world.level.block.TrialSpawnerBlock;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerState;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.state.AnimationTest;
import com.geckolib.animation.object.PlayState;
import com.geckolib.animation.RawAnimation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

public class OgreLord extends OgreGrunt {
    private static final int LORD_XP_REWARD = 700;

    @Override
    protected float getStepPitchMultiplier() {
        return 0.55F;
    }

    @Override
    protected float getStepVolumeMultiplier() {
        return 2.5F;
    }

    @Override
    protected int getMinimumStepSoundIntervalTicks() {
        return entityData.get(DATA_LORD_LOCOMOTION) == LOCOMOTION_RUN ? 10 : 12;
    }
    private static final EntityDataAccessor<Integer> DATA_LORD_ATTACK_ID =
            SynchedEntityData.defineId(OgreLord.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> DATA_LORD_ATTACK_START_TICK =
            SynchedEntityData.defineId(OgreLord.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Boolean> DATA_LORD_PHASE_TWO =
            SynchedEntityData.defineId(OgreLord.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_LORD_TRANSITIONING =
            SynchedEntityData.defineId(OgreLord.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_LORD_CLUB_IN_HAND =
            SynchedEntityData.defineId(OgreLord.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_LORD_DYING =
            SynchedEntityData.defineId(OgreLord.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_LORD_LOCOMOTION =
            SynchedEntityData.defineId(OgreLord.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_FORT_ENCOUNTER_STATE =
            SynchedEntityData.defineId(OgreLord.class, EntityDataSerializers.INT);
    private static final String PHASE_TWO_TAG = "KingsAndMonstersPhaseTwo";
    private static final String CLUB_IN_HAND_TAG = "KingsAndMonstersClubInHand";
    private static final String PHASE_TRANSITION_TICKS_TAG = "KingsAndMonstersPhaseTransitionTicks";
    private static final String FORT_ENCOUNTER_STATE_TAG = "KingsAndMonstersFortEncounterState";
    private static final String ROYAL_TRIAL_POSITIONS_TAG = "KingsAndMonstersRoyalTrialPositions";
    private static final String COMPLETED_ROYAL_TRIALS_TAG = "KingsAndMonstersCompletedRoyalTrials";
    private static final String ROYAL_DEFENCE_STARTED_TAG = "KingsAndMonstersRoyalDefenceStarted";
    private static final String WAKE_UP_TICKS_TAG = "KingsAndMonstersWakeUpTicks";
    private static final float PHASE_TWO_HEALTH_THRESHOLD = 0.5F;
    private static final int PULL_OUT_BUFF_DURATION_TICKS = 60 * 20;
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation IDLE_2 = RawAnimation.begin().thenLoop("idle2");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation COMBAT_WALK = RawAnimation.begin().thenLoop("combat_walk");
    private static final RawAnimation RUN = RawAnimation.begin().thenLoop("run");
    // The mixed capitalization below is baked into the exported animation file
    // (ogre_lord.animation.json) — GeckoLib matches these names verbatim.
    private static final RawAnimation ONE_ARM_UPSWING = RawAnimation.begin().thenPlay("lordly one arm upswing");
    private static final RawAnimation DOUBLE_STOMP = RawAnimation.begin().thenPlay("Double stomp");
    private static final RawAnimation DOUBLE_OVERHEAD_SLAM = RawAnimation.begin().thenPlay("double overhead slam");
    private static final RawAnimation ROAR = RawAnimation.begin().thenPlay("Roar");
    private static final RawAnimation SUMMON_POINT = RawAnimation.begin().thenPlay("Summon point");
    private static final RawAnimation BELLY_BUMP = RawAnimation.begin().thenPlay("Lordly belly bump");
    private static final RawAnimation DOUBLE_ARM_UPSWING = RawAnimation.begin().thenPlay("lordly double arm upswing");
    private static final RawAnimation CLUB_HEAD_THRUST = RawAnimation.begin().thenPlay("club head thrust");
    private static final RawAnimation SINGLE_LEG_STOMP = RawAnimation.begin().thenPlay("single leg stomp");
    private static final RawAnimation PULL_OUT = RawAnimation.begin().thenPlay("pull out");
    private static final RawAnimation CLUB_DOUBLE_OVERHEAD = RawAnimation.begin().thenPlay("club double overhead");
    private static final RawAnimation SINGLE_OVERHEAD_SLAM = RawAnimation.begin().thenPlay("single overhead slam");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlay("death");
    private static final RawAnimation SLEEPING = RawAnimation.begin().thenLoop("sleeping");
    private static final RawAnimation WAKE_UP = RawAnimation.begin().thenPlay("wake up animation");

    private static final int IDLE_VARIANT_TICKS = 40;
    private static final int ONE_ARM_UPSWING_ANIMATION_TICKS = 50;
    private static final int DOUBLE_STOMP_ANIMATION_TICKS = 75;
    private static final int DOUBLE_OVERHEAD_SLAM_ANIMATION_TICKS = 60;
    private static final int ROAR_ANIMATION_TICKS = 85;
    private static final int SUMMON_POINT_ANIMATION_TICKS = 50;
    // The current exported variants below are all 2.5 seconds (50 ticks).
    private static final int BELLY_BUMP_ANIMATION_TICKS = 50;
    private static final int DOUBLE_ARM_UPSWING_ANIMATION_TICKS = 50;
    private static final int CLUB_HEAD_THRUST_ANIMATION_TICKS = 50;
    private static final int SINGLE_LEG_STOMP_ANIMATION_TICKS = 50;
    private static final int PULL_OUT_ANIMATION_TICKS = 165;
    private static final int CLUB_DOUBLE_OVERHEAD_ANIMATION_TICKS = 50;
    private static final int SINGLE_OVERHEAD_SLAM_ANIMATION_TICKS = 50;
    private static final int DEATH_ANIMATION_TICKS = 50;
    // The animation is 2 seconds. Five extra ticks cover the movement
    // controller's polling interval so the ending cannot be clipped.
    private static final int WAKE_UP_ANIMATION_TICKS = 45;
    // The generalisation of the WAKE_UP padding above, and the same value as the "Lord Movement"
    // controller's transition length. SynchronizedAnimationController deliberately keeps GeckoLib
    // 5's leading transition stage inside the elapsed clock, so elapsed tick N is clip-local tick
    // N-5; every other duration constant here is the raw authored clip length, so each attack's
    // last 5 ticks were being clipped exactly as the WAKE_UP comment describes. Applied ONLY to the
    // visual one-shot, never to getAttackAnimationTicks() itself, which also drives the server-side
    // attackAnimationTicks counter gating hits, roars, summons and facing locks.
    private static final int ONE_SHOT_TRANSITION_TICKS = 5;
    private static final double FORT_WAKE_FORWARD_NUDGE = 0.20;
    private static final int FORT_WAKE_NUDGE_START_TICK = 10;
    private static final int FORT_WAKE_NUDGE_TICKS = 8;
    private static final int FORT_ENCOUNTER_NORMAL = 0;
    private static final int FORT_ENCOUNTER_SLEEPING = 1;
    private static final int FORT_ENCOUNTER_WAKING = 2;
    private static final int ROYAL_TRIAL_COUNT = 3;
    private static final double ROYAL_TRIAL_BAR_RANGE = 96.0;
    // A player may only be subscribed to one fort encounter bar. This also lets a
    // newly loaded fort evict a stale bar left by a King whose chunk unloaded
    // before its periodic range check ran.
    private static final Map<ServerPlayer, OgreLord> ROYAL_TRIAL_BAR_OWNERS = new WeakHashMap<>();
    private static final int CLUB_VISIBILITY_SWAP_TICK = 110;
    // Impact ticks below are read directly off ogre_lord.animation.json. Every King attack is authored
    // the same way: a wind-up extreme at 1.0s, a fast snap into the contact pose, then a hold on that
    // pose until ~1.75s. The first keyframe of that hold IS the visual contact frame; nothing moves
    // after it. Ticks are therefore round(contact_seconds * 20), rounding a .5 up so audio never
    // precedes contact.
    //
    // These used to carry an extra +3 ("ATTACK_SYNC_DELAY_TICKS"), plus per-attack +3/+6 hand
    // padding of the same kind, to compensate for the client starting the clip late. That lag no
    // longer exists: SynchronizedAnimationController.initializeNewAnimation seeks the newly observed
    // one-shot to (gameTime - DATA_LORD_ATTACK_START_TICK), so the clip is anchored to the exact
    // server tick the attack began. The stale padding was landing every impact sound 3-7 ticks after
    // the swing had already frozen on its hold pose.
    private static final int SUMMON_POINT_PUNCH_SOUND_TICK = 22;
    // The point pose lands at 1.1667s.
    private static final int SUMMON_POINT_EVENT_TICK = 23;
    // "pull out": right_arm/torso snap into the held ground pose at 6.75s.
    private static final int PULL_OUT_IMPACT_TICK = 135;
    // The King drops to his knees around 0.42s into the pull-out animation.
    private static final int PULL_OUT_KNEEL_HURT_SOUND_TICK = 8;
    // Raising pitch also shortens/speeds the reused hurt recording, giving this exertion its own identity.
    private static final float PULL_OUT_KNEEL_HURT_SOUND_PITCH = 1.10F;
    // "club double overhead": arms/legs/torso reach the held slam pose at 1.125s.
    // "single overhead slam": right_arm/torso reach the held slam pose at 1.125s.
    private static final int CLUB_DOUBLE_OVERHEAD_IMPACT_TICK = 23;
    private static final int SINGLE_OVERHEAD_IMPACT_TICK = 23;
    private static final int HEAVY_ATTACK_ROAR_LEAD_TICKS = 8;
    private static final int CLUB_DOUBLE_OVERHEAD_ROAR_LEAD_TICKS = HEAVY_ATTACK_ROAR_LEAD_TICKS + 2;
    private static final float PULL_OUT_ROAR_PITCH = 0.94F;
    private static final float DOUBLE_OVERHEAD_ROAR_PITCH = 0.96F;
    // The double-club wind-up should sit in the same deep register as the King's other attack grunts.
    private static final float CLUB_DOUBLE_OVERHEAD_ROAR_PITCH = 0.86F;
    private static final float SINGLE_OVERHEAD_ROAR_PITCH = 1.02F;
    private static final float HEAVY_ATTACK_ROAR_PITCH_VARIATION = 0.025F;

    // Shared recovery after one arm upswing/belly bump/double arm upswing/side smack/single leg
    // stomp — counted from attack start, so this leaves a real gap before he can swing again, not
    // back-to-back combos.
    private static final int BASIC_ATTACK_COOLDOWN_TICKS = 110;
    private static final int DOUBLE_STOMP_SWING_COOLDOWN_TICKS = 145;
    private static final int DOUBLE_OVERHEAD_SLAM_SWING_COOLDOWN_TICKS = 140;
    // Extra per-move cooldowns on top of the shared swing recovery above, so the big AOE moves
    // don't get reselected back-to-back the way the regular rotation can.
    private static final int DOUBLE_STOMP_COOLDOWN_TICKS = 300;
    private static final int DOUBLE_OVERHEAD_SLAM_COOLDOWN_TICKS = 400;
    private static final int ROAR_COOLDOWN_TICKS = 1800;
    private static final int PHASE_TWO_ROAR_COOLDOWN_TICKS = 1200;
    private static final int PHASE_TWO_FIRST_ROAR_DELAY_TICKS = 160;
    private static final int SUMMON_COOLDOWN_TICKS = 90 * 20;
    private static final int SUMMON_INITIAL_DELAY_TICKS = 600;
    private static final int SPECIAL_CONFLICT_RETRY_MIN_TICKS = 200;
    private static final int SPECIAL_CONFLICT_RETRY_RANDOM_TICKS = 101;
    // The animation opens its mouth at 1.25s and closes at the 4.25s ending frame.
    private static final int ROAR_SOUND_TICK = 25;
    private static final int ROAR_SCREEN_SHAKE_DELAY_TICKS = 5;
    private static final int ROAR_SCREEN_SHAKE_ACTIVE_TICKS = 82;
    private static final float ROAR_SCREEN_SHAKE_FREQUENCY_MULTIPLIER = 1.45F;
    private static final double ROAR_SCREEN_SHAKE_RANGE = 32.0;
    private static final float ROAR_SCREEN_SHAKE_INTENSITY = 0.55F;
    private static final int ROAR_EFFECT_TICK = 40;
    private static final int ROAR_SHOCKWAVE_START_TICK = ROAR_SOUND_TICK;
    // The roar wave crosses its full gameplay radius quickly enough to read as a
    // pressure wave while remaining visible instead of attempting real sound speed.
    private static final int ROAR_SHOCKWAVE_EXPANSION_TICKS = 40;
    private static final float ROAR_SHOCKWAVE_START_RADIUS = 1.0F;
    private static final float ROAR_SHOCKWAVE_END_RADIUS = 20.0F;
    private static final int ROAR_BUFF_DURATION_TICKS = 400;
    private static final int ROAR_WEAKNESS_DURATION_TICKS = 200;
    private static final int ROAR_SLOWNESS_REFRESH_INTERVAL_TICKS = 5;
    private static final int ROAR_SLOWNESS_REFRESH_DURATION_TICKS = 10;
    private static final double ROAR_EFFECT_RADIUS = 16.0;
    private static final int PHASE_TWO_SINGLE_LEG_STOMP_CHANCE = 7;
    private static final int GUARD_FORMATION_WEIGHT = 1;
    private static final int GRUNT_FORMATION_WEIGHT = 1;
    private static final int MAGE_FORMATION_WEIGHT = 1;
    private static final int TOTAL_SUMMON_FORMATION_WEIGHT =
            GUARD_FORMATION_WEIGHT + GRUNT_FORMATION_WEIGHT + MAGE_FORMATION_WEIGHT;
    private static final double ROYAL_COMMAND_RADIUS = 32.0;
    // Reinforcements emerge inside the king's broad collision footprint. Their
    // entity collision gives him a small physical shove while he remains planted.
    private static final double SUMMON_SPAWN_RADIUS = 0.65;
    private static final double ROAR_MAX_NUDGE_DISTANCE = 1.15;
    private static final double ROAR_MAX_HORIZONTAL_NUDGE_SPEED = 0.08;
    private static final double ROAR_NUDGE_DAMPING = 0.72;
    // Animation-aligned body sweep: forward -> slight left -> right -> forward.
    private static final int ROAR_TURN_START_TICK = 28; // 1.42s
    private static final int ROAR_LEFT_TICK = 43;       // 2.13s
    private static final int ROAR_RIGHT_TICK = 58;      // 2.88s
    private static final int ROAR_RETURN_START_TICK = 72; // 3.58s
    private static final int ROAR_FORWARD_TICK = 85;    // 4.25s
    private static final float ROAR_LEFT_YAW_OFFSET = -10.0F;
    private static final float ROAR_RIGHT_YAW_OFFSET = 15.0F;

    private static final double REGULAR_ATTACK_START_RANGE = 5.5;
    private static final float SNAP_FACE_MAX_TURN_DEGREES = 100.0F;
    // "lordly one arm upswing": right_arm reaches -187.7 at 1.1875s and holds there to 1.75s.
    private static final int ONE_ARM_UPSWING_IMPACT_DELAY_TICKS = 24;
    // "Double stomp": left foot plants at 1.125s, right foot at 2.875s.
    private static final int DOUBLE_STOMP_LEFT_IMPACT_DELAY_TICKS = 23;
    private static final int DOUBLE_STOMP_RIGHT_IMPACT_DELAY_TICKS = 58;
    // "double overhead slam" (Phase 1 only, unarmed hands): both arms/legs/torso reach the held
    // ground pose at 1.1875s.
    private static final int DOUBLE_OVERHEAD_SLAM_IMPACT_DELAY_TICKS = 24;
    // "Lordly belly bump" contacts at 1.125s; "lordly double arm upswing" and "single leg stomp"
    // both reach their held pose at 1.1667s.
    private static final int BELLY_BUMP_IMPACT_DELAY_TICKS = 23;
    private static final int DOUBLE_ARM_UPSWING_IMPACT_DELAY_TICKS = 23;
    // "club head thrust" reaches its extended pose at 1.1667s. The active window brackets that
    // extension and the impact sound sits mid-window, so the sound stays synced with the reaching
    // hitbox instead of firing after it closes.
    private static final int CLUB_HEAD_THRUST_IMPACT_DELAY_TICKS = 26;
    private static final int CLUB_HEAD_THRUST_ACTIVE_START_TICK = 23;
    private static final int CLUB_HEAD_THRUST_ACTIVE_END_TICK = 31;
    private static final double CLUB_HEAD_THRUST_START_REACH = 1.5;
    private static final double CLUB_HEAD_THRUST_END_REACH = 6.25;
    private static final double CLUB_HEAD_THRUST_RIGHT_OFFSET = -1.15;
    private static final double CLUB_HEAD_THRUST_HEAD_RADIUS = 1.65;
    private static final int SINGLE_LEG_STOMP_IMPACT_DELAY_TICKS = 23;
    // Close-pressure gate for the belly bump — punishes standing in his face and swinging away.
    private static final double BELLY_BUMP_CLOSE_PRESSURE_RANGE = 4.0;
    private static final int BELLY_BUMP_CLOSE_PRESSURE_TICKS = 28;
    private static final int BELLY_BUMP_COOLDOWN_TICKS = 400;
    // Extra cooldown on top of the low roll chance in startMeleeAttack() — was getting picked too
    // often back-to-back before this.
    private static final int DOUBLE_ARM_UPSWING_COOLDOWN_TICKS = 200;
    private static final int CLUB_DOUBLE_OVERHEAD_COOLDOWN_TICKS = 360;
    private static final int SINGLE_OVERHEAD_COOLDOWN_TICKS = 180;
    private static final int LORD_IDLE_HUFF_MIN_COOLDOWN_TICKS = 600;
    private static final int LORD_IDLE_HUFF_RANDOM_COOLDOWN_TICKS = 500;
    private static final int LORD_IDLE_HUFF_CHANCE = 80;
    private static final int LOCOMOTION_IDLE = 0;
    private static final int LOCOMOTION_WALK = 1;
    private static final int LOCOMOTION_RUN = 2;
    private static final int LOCOMOTION_COMBAT_WALK = 3;
    private static final double KING_COMBAT_WALK_SPEED = 0.90;
    private static final double COMBAT_WALK_ENTER_RANGE = 14.0;
    private static final double COMBAT_WALK_EXIT_RANGE = 16.0;
    private static final double COMBAT_WALK_MAX_VERTICAL_DIFFERENCE = 4.5;
    // Keep just a touch more breathing room so his long-reach attacks do not crowd the player.
    private static final double COMBAT_WALK_TARGET_GAP = 2.0;

    // Calibrated against fully enchanted diamond armor: 3-4 hearts per attack, max — these were
    // landing 6-7 hearts before, so everything below is roughly halved. Double stomp's two hit
    // circles overlap almost entirely at this radius (offset is only ±1 block), so a stationary
    // target usually eats both — its per-hit multiplier is cut further than the others to compensate.
    // One arm upswing is the same move as the double arm version, just redirected — same multiplier.
    private static final float ONE_ARM_UPSWING_DAMAGE_MULTIPLIER = 1.4F;
    private static final float DOUBLE_STOMP_DAMAGE_MULTIPLIER = 1.1F;
    private static final float DOUBLE_OVERHEAD_SLAM_DAMAGE_MULTIPLIER = 2.0F;
    private static final float BELLY_BUMP_DAMAGE_MULTIPLIER = 1.3F;
    private static final float DOUBLE_ARM_UPSWING_DAMAGE_MULTIPLIER = 1.4F;
    private static final float CLUB_HEAD_THRUST_DAMAGE_MULTIPLIER = 1.4F;
    private static final float SINGLE_LEG_STOMP_DAMAGE_MULTIPLIER = 1.3F;

    // 1.5x the original 7.5/9.0 block radii.
    private static final float DOUBLE_STOMP_AREA_RADIUS = 11.25F;
    private static final float DOUBLE_OVERHEAD_SLAM_AREA_RADIUS = 13.5F;
    private static final float PULL_OUT_DAMAGE_MULTIPLIER = 1.4F;
    private static final float CLUB_DOUBLE_OVERHEAD_DAMAGE_MULTIPLIER = 2.0F;
    private static final float SINGLE_OVERHEAD_DAMAGE_MULTIPLIER = 1.4F;

    // Terrain destruction on the big ground smashes — kept small ("a couple" blocks), not a crater.
    private static final int DOUBLE_STOMP_BLOCKS_DESTROYED = 2;
    private static final int DOUBLE_OVERHEAD_SLAM_BLOCKS_DESTROYED = 3;
    private static final float MAX_DESTRUCTIBLE_HARDNESS = 20.0F;

    private int idleAnimationTicks;
    private int idleVariantTicks;
    private int attackAnimationTicks;
    private int doubleStompCooldownTicks;
    private int doubleOverheadSlamCooldownTicks;
    private int bellyBumpCooldownTicks;
    private int doubleArmUpswingCooldownTicks;
    private int clubDoubleOverheadCooldownTicks;
    private int singleOverheadCooldownTicks;
    private int lordIdleHuffCooldownTicks;
    private int closePressureTicks;
    // Starts on cooldown so he can't roar the instant a fight begins.
    private int roarCooldownTicks = ROAR_COOLDOWN_TICKS;
    private int roarActiveTicks;
    private boolean roarEffectsApplied;
    private boolean roarSoundPlayed;
    private int roarScreenShakeDelayTicks;
    private int summonCooldownTicks = SUMMON_INITIAL_DELAY_TICKS;
    private int summonActiveTicks;
    private boolean summonEventFired;
    private boolean summonPunchSoundPlayed;
    private double summonLockedX;
    private double summonLockedZ;
    private float summonLockedYaw;
    private LivingEntity summonTarget;
    private double roarLockedX;
    private double roarLockedZ;
    private float roarLockedYaw;
    private int oneArmUpswingImpactSoundDelayTicks = -1;
    private int doubleStompLeftImpactSoundDelayTicks = -1;
    private int doubleStompRightImpactSoundDelayTicks = -1;
    private int doubleOverheadSlamImpactSoundDelayTicks = -1;
    private int bellyBumpImpactSoundDelayTicks = -1;
    private int doubleArmUpswingImpactSoundDelayTicks = -1;
    private int clubHeadThrustImpactSoundDelayTicks = -1;
    private int clubHeadThrustElapsedTicks;
    private double clubHeadThrustOriginX;
    private double clubHeadThrustOriginZ;
    private float clubHeadThrustYaw;
    private Vec3 previousClubHeadThrustPosition;
    private final Set<UUID> clubHeadThrustHitTargets = new HashSet<>();
    private int singleLegStompImpactSoundDelayTicks = -1;
    private int heavyAttackRoarDelayTicks = -1;
    private int heavyAttackRoarAttackId;
    private float heavyAttackRoarPitch = 1.0F;
    private boolean heavyAttackUsesOgrebloodRoar;
    private int activeLordAttackId;
    private int lastSelectedAttackId;
    private int secondLastSelectedAttackId;
    private final Set<UUID> directShockwaveWeaponHitTargets = new HashSet<>();
    private int lastAnimationTick = -1;
    private int movementAnimationGraceTicks;
    private int lastMovementGraceTick = -1;
    private final CanonicalOneShotState visualOneShot = new CanonicalOneShotState();
    private long visualDeathStartGameTime = Long.MIN_VALUE;
    private boolean openingRunAttackCompleted;
    private int phaseTransitionElapsedTicks;
    private int clubAttackElapsedTicks;
    private boolean clubAttackImpactFired;
    private double lockedSpecialX;
    private double lockedSpecialZ;
    private float lockedSpecialYaw;
    private DamageSource delayedDeathSource;
    private RawAnimation activeIdleAnimation = IDLE;
    private final List<BlockPos> royalTrialPositions = new ArrayList<>(ROYAL_TRIAL_COUNT);
    private final Set<UUID> royalDefenceIntroducedPlayers = new HashSet<>();
    private final ServerBossEvent royalTrialProgress = new ServerBossEvent(
            UUID.randomUUID(),
            royalUiText("Royal Defence 0/" + ROYAL_TRIAL_COUNT),
            BossEvent.BossBarColor.PURPLE,
            BossEvent.BossBarOverlay.PROGRESS);
    private final ServerBossEvent kingBossBar = new ServerBossEvent(
            UUID.randomUUID(),
            royalUiText("Ogre King"),
            BossEvent.BossBarColor.PURPLE,
            BossEvent.BossBarOverlay.PROGRESS);
    private int completedRoyalTrialsMask;
    private boolean royalDefenceStarted;
    private int wakeUpTicks;
    private int lastRenderedFortEncounterState = -1;

    public OgreLord(EntityType<? extends OgreLord> type, Level level) {
        super(type, level);
        avoidWaterPathfinding();
        xpReward = LORD_XP_REWARD;
        resetLordIdleHuffCooldown();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 300.0)
                .add(Attributes.ATTACK_DAMAGE, 18.0)
                // Matches OgreBrute's chase speed.
                .add(Attributes.MOVEMENT_SPEED, 0.333)
                .add(Attributes.FOLLOW_RANGE, 34.0)
                .add(Attributes.ARMOR, 20.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.ATTACK_KNOCKBACK, 2.0)
                .add(Attributes.STEP_HEIGHT, 1.3)
                .add(Attributes.SAFE_FALL_DISTANCE, 6.0)
                // See OgreGrunt#createAttributes — default 0 caps water acceleration at a tiny fixed
                // baseline regardless of requested speed, which would make applyShallowWaterSpeedFactor's
                // 50% reduction meaningless since he's already moving slower than that.
                .add(Attributes.WATER_MOVEMENT_EFFICIENCY, 1.0);
    }

    @Override
    @SuppressWarnings("deprecation") // Required 1.21.1 OverrideOnly effect hook; no replacement exists in this target.
    public boolean canBeAffected(MobEffectInstance effect) {
        // Keep the approved run pace stable by ignoring allied speed splashes.
        if (effect.getEffect().equals(MobEffects.SPEED)) {
            return false;
        }
        return super.canBeAffected(effect);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_LORD_ATTACK_ID, 0);
        builder.define(DATA_LORD_ATTACK_START_TICK, 0L);
        builder.define(DATA_LORD_PHASE_TWO, false);
        builder.define(DATA_LORD_TRANSITIONING, false);
        builder.define(DATA_LORD_CLUB_IN_HAND, false);
        builder.define(DATA_LORD_DYING, false);
        builder.define(DATA_LORD_LOCOMOTION, LOCOMOTION_IDLE);
        builder.define(DATA_FORT_ENCOUNTER_STATE, FORT_ENCOUNTER_NORMAL);
    }

    @Override
    public void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean(PHASE_TWO_TAG, isPhaseTwo());
        tag.putBoolean(CLUB_IN_HAND_TAG, isClubInHand());
        if (isPhaseTransitioning()) {
            tag.putInt(PHASE_TRANSITION_TICKS_TAG, phaseTransitionElapsedTicks);
        }
        tag.putInt(FORT_ENCOUNTER_STATE_TAG, entityData.get(DATA_FORT_ENCOUNTER_STATE));
        tag.store(ROYAL_TRIAL_POSITIONS_TAG, com.mojang.serialization.Codec.LONG.listOf(),
                royalTrialPositions.stream().map(BlockPos::asLong).toList());
        tag.putInt(COMPLETED_ROYAL_TRIALS_TAG, completedRoyalTrialsMask);
        tag.putBoolean(ROYAL_DEFENCE_STARTED_TAG, royalDefenceStarted);
        if (isWakingFromFortEncounter()) {
            tag.putInt(WAKE_UP_TICKS_TAG, wakeUpTicks);
        }
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput tag) {
        super.readAdditionalSaveData(tag);
        if (tag.getBooleanOr(PHASE_TWO_TAG, false)) {
            entityData.set(DATA_LORD_PHASE_TWO, true);
        }
        entityData.set(DATA_LORD_CLUB_IN_HAND, tag.getBooleanOr(CLUB_IN_HAND_TAG, false));
        if (tag.getInt(PHASE_TRANSITION_TICKS_TAG).isPresent()) {
            phaseTransitionElapsedTicks = Mth.clamp(tag.getIntOr(PHASE_TRANSITION_TICKS_TAG, 0), 0, PULL_OUT_ANIMATION_TICKS);
            entityData.set(DATA_LORD_PHASE_TWO, true);
            entityData.set(DATA_LORD_TRANSITIONING, phaseTransitionElapsedTicks < PULL_OUT_ANIMATION_TICKS);
            if (phaseTransitionElapsedTicks < PULL_OUT_ANIMATION_TICKS) {
                entityData.set(DATA_LORD_ATTACK_ID, 9);
                entityData.set(DATA_LORD_ATTACK_START_TICK,
                        Math.max(1L, level().getGameTime() - phaseTransitionElapsedTicks));
            }
            if (phaseTransitionElapsedTicks >= CLUB_VISIBILITY_SWAP_TICK) {
                entityData.set(DATA_LORD_CLUB_IN_HAND, true);
            }
        }
        int encounterState = Mth.clamp(tag.getIntOr(FORT_ENCOUNTER_STATE_TAG, 0),
                FORT_ENCOUNTER_NORMAL, FORT_ENCOUNTER_WAKING);
        entityData.set(DATA_FORT_ENCOUNTER_STATE, encounterState);
        royalTrialPositions.clear();
        for (long packedPos : tag.read(ROYAL_TRIAL_POSITIONS_TAG,
                com.mojang.serialization.Codec.LONG.listOf()).orElseGet(List::of)) {
            royalTrialPositions.add(BlockPos.of(packedPos));
        }
        completedRoyalTrialsMask = tag.getIntOr(COMPLETED_ROYAL_TRIALS_TAG, 0);
        royalDefenceStarted = tag.getBooleanOr(ROYAL_DEFENCE_STARTED_TAG, false);
        wakeUpTicks = Mth.clamp(tag.getIntOr(WAKE_UP_TICKS_TAG, 0), 0, WAKE_UP_ANIMATION_TICKS);
        if (encounterState == FORT_ENCOUNTER_WAKING && wakeUpTicks < WAKE_UP_ANIMATION_TICKS) {
            entityData.set(DATA_LORD_ATTACK_ID, 14);
            entityData.set(DATA_LORD_ATTACK_START_TICK,
                    Math.max(1L, level().getGameTime() - wakeUpTicks));
        }
        if (encounterState != FORT_ENCOUNTER_NORMAL) {
            setNoAi(true);
            setTarget(null);
            setAggressive(false);
        } else if (!isDyingAnimation()) {
            // Clear stale vanilla NoAI data if a save was made during the final
            // wake-state transition.
            setNoAi(false);
        }
    }

    public void setFortEncounterTrials(List<BlockPos> trialPositions) {
        royalTrialPositions.clear();
        royalTrialPositions.addAll(trialPositions.stream().limit(ROYAL_TRIAL_COUNT).toList());
        completedRoyalTrialsMask = 0;
        royalDefenceStarted = false;
        wakeUpTicks = 0;
        entityData.set(DATA_FORT_ENCOUNTER_STATE, FORT_ENCOUNTER_SLEEPING);
        entityData.set(DATA_LORD_LOCOMOTION, LOCOMOTION_IDLE);
        setNoAi(true);
        setTarget(null);
        setAggressive(false);
        getNavigation().stop();
        setDeltaMovement(Vec3.ZERO);
        royalTrialProgress.setProgress(0.0F);
    }

    @Override
    public void applyConfiguredCombatAttributes(boolean healToMax) {
        if (!Config.isLoaded()) {
            return;
        }

        setAttributeBaseValue(Attributes.MAX_HEALTH, Config.OGRE_LORD_MAX_HEALTH.get());
        setAttributeBaseValue(Attributes.ATTACK_DAMAGE, Config.OGRE_LORD_ATTACK_DAMAGE.get());
        setAttributeBaseValue(Attributes.MOVEMENT_SPEED, Config.OGRE_LORD_MOVEMENT_SPEED.get());
        setAttributeBaseValue(Attributes.FOLLOW_RANGE, Config.OGRE_LORD_FOLLOW_RANGE.get());
        setAttributeBaseValue(Attributes.ARMOR, Config.OGRE_LORD_ARMOR.get());
        applyConfiguredHealth(healToMax);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!level().isClientSide()) {
            if (isFortEncounterDormant()) {
                tickFortEncounter();
                return;
            }
            kingBossBar.setProgress(getMaxHealth() <= 0.0F ? 0.0F : getHealth() / getMaxHealth());
            // Never assert a locomotion state while a special/attack state owns the model — this
            // write is currently masked by animateLord()'s one-shot check always running first, but
            // it should not rely on that alone (attack animation ownership belongs at the write site
            // too, not just the read order).
            if ((getTarget() == null || !isAggressive())
                    && !isRoaring() && !isPhaseTransitioning() && !isDyingAnimation() && !isMidSwing()) {
                entityData.set(DATA_LORD_LOCOMOTION,
                        getNavigation().isDone() ? LOCOMOTION_IDLE : LOCOMOTION_WALK);
            }
            if (isDyingAnimation()) {
                freezeSpecialAnimationPose();
                return;
            }
            if (!isPhaseTwo()
                    && isAlive()
                    && getHealth() <= getMaxHealth() * PHASE_TWO_HEALTH_THRESHOLD) {
                enterPhaseTwo();
            }

            if (isPhaseTransitioning()) {
                tickPhaseTransition();
                return;
            }

            tickClubAttackTimeline();
            tickClubHeadThrustHitbox();

            doubleStompCooldownTicks = Math.max(doubleStompCooldownTicks - 1, 0);
            doubleOverheadSlamCooldownTicks = Math.max(doubleOverheadSlamCooldownTicks - 1, 0);
            bellyBumpCooldownTicks = Math.max(bellyBumpCooldownTicks - 1, 0);
            doubleArmUpswingCooldownTicks = Math.max(doubleArmUpswingCooldownTicks - 1, 0);
            clubDoubleOverheadCooldownTicks = Math.max(clubDoubleOverheadCooldownTicks - 1, 0);
            singleOverheadCooldownTicks = Math.max(singleOverheadCooldownTicks - 1, 0);
            roarCooldownTicks = Math.max(roarCooldownTicks - 1, 0);
            summonCooldownTicks = Math.max(summonCooldownTicks - 1, 0);
            lordIdleHuffCooldownTicks = Math.max(lordIdleHuffCooldownTicks - 1, 0);

            tickClosePressure();
            tickRoarScreenShake();
            tickRoar();
            maybeStartRoar();
            tickSummonPoint();
            maybeStartSummonPoint();
            maybePlayLordIdleHuff();
            tickDelayedImpactSounds();
        }
    }

    private void tickFortEncounter() {
        getNavigation().stop();
        setTarget(null);
        setAggressive(false);
        setDeltaMovement(Vec3.ZERO);
        entityData.set(DATA_LORD_LOCOMOTION, LOCOMOTION_IDLE);

        if (isSleepingInFort()) {
            if (tickCount % 10 == 0) {
                updateRoyalTrialProgress();
            }
        } else if (isWakingFromFortEncounter()) {
            wakeUpTicks++;
            nudgeForwardDuringFortWakeUp();
            if (wakeUpTicks >= WAKE_UP_ANIMATION_TICKS) {
                finishFortWakeUp();
            }
        }

        if (tickCount % 20 == 0) {
            updateRoyalTrialBossBarPlayers();
        }
    }

    private void nudgeForwardDuringFortWakeUp() {
        int nudgeEndTick = FORT_WAKE_NUDGE_START_TICK + FORT_WAKE_NUDGE_TICKS;
        if (wakeUpTicks < FORT_WAKE_NUDGE_START_TICK || wakeUpTicks >= nudgeEndTick) {
            return;
        }

        double distanceThisTick = FORT_WAKE_FORWARD_NUDGE / FORT_WAKE_NUDGE_TICKS;
        Vec3 wakeForward = Vec3.directionFromRotation(0.0F, getYRot()).scale(distanceThisTick);
        setPos(getX() + wakeForward.x, getY(), getZ() + wakeForward.z);
    }

    private void updateRoyalTrialProgress() {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }

        for (int index = 0; index < royalTrialPositions.size(); index++) {
            int bit = 1 << index;
            if ((completedRoyalTrialsMask & bit) != 0) {
                continue;
            }

            BlockPos trialPos = royalTrialPositions.get(index);
            if (!serverLevel.hasChunk(trialPos.getX() >> 4, trialPos.getZ() >> 4)) {
                continue;
            }
            BlockState state = serverLevel.getBlockState(trialPos);
            if (!(state.getBlock() instanceof TrialSpawnerBlock)) {
                continue;
            }
            TrialSpawnerState trialState = state.getValue(TrialSpawnerBlock.STATE);
            if (!royalDefenceStarted && trialState == TrialSpawnerState.ACTIVE) {
                royalDefenceStarted = true;
            }
            if (trialState == TrialSpawnerState.WAITING_FOR_REWARD_EJECTION
                    || trialState == TrialSpawnerState.EJECTING_REWARD
                    || trialState == TrialSpawnerState.COOLDOWN) {
                royalDefenceStarted = true;
                completedRoyalTrialsMask |= bit;
            }
        }

        int completed = Integer.bitCount(completedRoyalTrialsMask & 0b111);
        royalTrialProgress.setProgress(completed / (float) ROYAL_TRIAL_COUNT);
        royalTrialProgress.setName(royalUiText(
                "Royal Defence " + completed + "/" + ROYAL_TRIAL_COUNT));
        if (completed >= ROYAL_TRIAL_COUNT) {
            beginFortWakeUp();
        }
    }

    private void beginFortWakeUp() {
        if (!isSleepingInFort()) {
            return;
        }
        wakeUpTicks = 0;
        entityData.set(DATA_FORT_ENCOUNTER_STATE, FORT_ENCOUNTER_WAKING);
        playAttackAnimation(14);
        royalTrialProgress.setProgress(1.0F);
        showFortWakeUpTitle();
    }

    private void showFortWakeUpTitle() {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }

        double rangeSqr = ROYAL_TRIAL_BAR_RANGE * ROYAL_TRIAL_BAR_RANGE;
        for (ServerPlayer player : serverLevel.getPlayers(candidate ->
                !candidate.isSpectator() && candidate.distanceToSqr(this) <= rangeSqr)) {
            PacketDistributor.sendToPlayer(player,
                    com.kingsandmonsters.network.OgreOverlayPayload.major(
                            "THE OGRE KING AWAKENS", ""));
        }
    }

    private static Component royalUiText(String text) {
        return Component.literal(text);
    }

    private void finishFortWakeUp() {
        entityData.set(DATA_FORT_ENCOUNTER_STATE, FORT_ENCOUNTER_NORMAL);
        wakeUpTicks = WAKE_UP_ANIMATION_TICKS;
        activeLordAttackId = 0;
        attackAnimationTicks = 0;
        clearLordAttackSync();
        setNoAi(false);
        setDeltaMovement(Vec3.ZERO);
        reacquireTargetAfterWake();
        clearRoyalTrialBossBarPlayers();
        if (level() instanceof ServerLevel serverLevel) {
            double rangeSqr = ROYAL_TRIAL_BAR_RANGE * ROYAL_TRIAL_BAR_RANGE;
            for (ServerPlayer player : serverLevel.getPlayers(candidate ->
                    !candidate.isSpectator() && candidate.distanceToSqr(this) <= rangeSqr)) {
                kingBossBar.addPlayer(player);
            }
        }
    }

    private void reacquireTargetAfterWake() {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        double followRange = getAttributeValue(Attributes.FOLLOW_RANGE);
        double rangeSqr = followRange * followRange;
        Player nearest = serverLevel.getPlayers(player ->
                        !player.isCreative()
                                && !player.isSpectator()
                                && player.distanceToSqr(this) <= rangeSqr
                                && canAttack(player))
                .stream()
                .min(java.util.Comparator.comparingDouble(this::distanceToSqr))
                .orElse(null);
        if (nearest != null) {
            setTarget(nearest);
        }
    }

    private void updateRoyalTrialBossBarPlayers() {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (!royalDefenceStarted) {
            clearRoyalTrialBossBarPlayers();
            return;
        }
        double rangeSqr = ROYAL_TRIAL_BAR_RANGE * ROYAL_TRIAL_BAR_RANGE;
        List<ServerPlayer> nearbyPlayers = serverLevel.getPlayers(player ->
                !player.isSpectator() && player.distanceToSqr(this) <= rangeSqr);
        for (ServerPlayer player : List.copyOf(royalTrialProgress.getPlayers())) {
            if (!nearbyPlayers.contains(player)) {
                removeRoyalTrialBossBarPlayer(player);
            }
        }
        for (ServerPlayer player : nearbyPlayers) {
            addRoyalTrialBossBarPlayer(player);
        }
    }

    private void addRoyalTrialBossBarPlayer(ServerPlayer player) {
        OgreLord previousOwner = ROYAL_TRIAL_BAR_OWNERS.put(player, this);
        if (previousOwner != null && previousOwner != this) {
            previousOwner.royalTrialProgress.removePlayer(player);
        }
        royalTrialProgress.addPlayer(player);
        if (royalDefenceStarted
                && isSleepingInFort()
                && royalDefenceIntroducedPlayers.add(player.getUUID())) {
            PacketDistributor.sendToPlayer(player,
                    com.kingsandmonsters.network.OgreOverlayPayload.major("ROYAL DEFENCE", ""));
        }
    }

    private void removeRoyalTrialBossBarPlayer(ServerPlayer player) {
        royalTrialProgress.removePlayer(player);
        ROYAL_TRIAL_BAR_OWNERS.remove(player, this);
    }

    private void clearRoyalTrialBossBarPlayers() {
        for (ServerPlayer player : List.copyOf(royalTrialProgress.getPlayers())) {
            removeRoyalTrialBossBarPlayer(player);
        }
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        removeRoyalTrialBossBarPlayer(player);
        kingBossBar.removePlayer(player);
        super.stopSeenByPlayer(player);
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        if (!isFortEncounterDormant()) {
            kingBossBar.addPlayer(player);
        }
    }

    private boolean isFortEncounterDormant() {
        return entityData.get(DATA_FORT_ENCOUNTER_STATE) != FORT_ENCOUNTER_NORMAL;
    }

    /** True once the King has fully awakened and is a normal active combat participant. */
    public boolean isFortEncounterActive() {
        return !isFortEncounterDormant();
    }

    /** Client-visible encounter authority for music: awakening begins the encounter, sleep excludes it. */
    public boolean isFortEncounterMusicActive() {
        return entityData.get(DATA_FORT_ENCOUNTER_STATE) != FORT_ENCOUNTER_SLEEPING;
    }

    private boolean isSleepingInFort() {
        return entityData.get(DATA_FORT_ENCOUNTER_STATE) == FORT_ENCOUNTER_SLEEPING;
    }

    private boolean isWakingFromFortEncounter() {
        return entityData.get(DATA_FORT_ENCOUNTER_STATE) == FORT_ENCOUNTER_WAKING;
    }

    public boolean isPhaseTwo() {
        return entityData.get(DATA_LORD_PHASE_TWO);
    }

    public boolean isPhaseTransitioning() {
        return entityData.get(DATA_LORD_TRANSITIONING);
    }

    public boolean isClubInHand() {
        return entityData.get(DATA_LORD_CLUB_IN_HAND);
    }

    public boolean isDyingAnimation() {
        return entityData.get(DATA_LORD_DYING);
    }

    /** Synced, render-only exclusion for Sleeping, Pull-Out, and Death. */
    public boolean isProceduralHeadTrackingSuppressed() {
        return entityData.get(DATA_FORT_ENCOUNTER_STATE) == FORT_ENCOUNTER_SLEEPING
                || entityData.get(DATA_LORD_TRANSITIONING)
                || entityData.get(DATA_LORD_DYING);
    }

    private void enterPhaseTwo() {
        if (isPhaseTwo() || isDyingAnimation()) {
            return;
        }
        entityData.set(DATA_LORD_PHASE_TWO, true);
        entityData.set(DATA_LORD_TRANSITIONING, true);
        entityData.set(DATA_LORD_CLUB_IN_HAND, false);
        phaseTransitionElapsedTicks = 0;
        lockSpecialAnimationPose();
        cancelActiveMeleeAttack();
        stopRoarImmediately();
        stopSummonImmediately();
        roarCooldownTicks = Math.min(roarCooldownTicks, PHASE_TWO_FIRST_ROAR_DELAY_TICKS);
        addEffect(new MobEffectInstance(MobEffects.STRENGTH, PULL_OUT_BUFF_DURATION_TICKS, 0));
        addEffect(new MobEffectInstance(MobEffects.RESISTANCE, PULL_OUT_BUFF_DURATION_TICKS, 0));
        playAttackAnimation(9);
        scheduleHeavyAttackRoar(9, PULL_OUT_IMPACT_TICK, PULL_OUT_ROAR_PITCH, false);
    }

    private void tickPhaseTransition() {
        freezeSpecialAnimationPose();
        phaseTransitionElapsedTicks++;
        heavyAttackRoarDelayTicks = tickDelayedHeavyAttackRoar(heavyAttackRoarDelayTicks);

        if (phaseTransitionElapsedTicks == PULL_OUT_KNEEL_HURT_SOUND_TICK) {
            level().playSound(null, getX(), getY(), getZ(),
                    ModSoundEvents.OGRE_LORD_HURT.get(), SoundSource.HOSTILE,
                    1.25F, PULL_OUT_KNEEL_HURT_SOUND_PITCH);
        }
        if (phaseTransitionElapsedTicks >= CLUB_VISIBILITY_SWAP_TICK && !isClubInHand()) {
            entityData.set(DATA_LORD_CLUB_IN_HAND, true);
        }
        if (phaseTransitionElapsedTicks == PULL_OUT_IMPACT_TICK) {
            performPullOutSwing();
        }
        if (phaseTransitionElapsedTicks >= PULL_OUT_ANIMATION_TICKS) {
            entityData.set(DATA_LORD_TRANSITIONING, false);
            activeLordAttackId = 0;
            attackAnimationTicks = 0;
            clearLordAttackSync();
        }
    }

    private void tickClubAttackTimeline() {
        if (activeLordAttackId != 10 && activeLordAttackId != 11) {
            return;
        }
        freezeSpecialAnimationPose();
        clubAttackElapsedTicks++;
        int impactTick = activeLordAttackId == 10 ? CLUB_DOUBLE_OVERHEAD_IMPACT_TICK : SINGLE_OVERHEAD_IMPACT_TICK;
        if (!clubAttackImpactFired && clubAttackElapsedTicks >= impactTick) {
            clubAttackImpactFired = true;
            if (activeLordAttackId == 10) {
                performClubDoubleOverhead();
            } else {
                performSingleOverheadSlam();
            }
        }
    }

    private void lockSpecialAnimationPose() {
        lockedSpecialX = getX();
        lockedSpecialZ = getZ();
        LivingEntity target = getTarget();
        if (target != null && target.isAlive()) {
            double dx = target.getX() - getX();
            double dz = target.getZ() - getZ();
            lockedSpecialYaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
        } else {
            lockedSpecialYaw = getYRot();
        }
        freezeSpecialAnimationPose();
    }

    private void freezeSpecialAnimationPose() {
        getNavigation().stop();
        setPos(lockedSpecialX, getY(), lockedSpecialZ);
        setYRot(lockedSpecialYaw);
        setYBodyRot(lockedSpecialYaw);
        setYHeadRot(lockedSpecialYaw);
        yRotO = lockedSpecialYaw;
        yBodyRotO = lockedSpecialYaw;
        yHeadRotO = lockedSpecialYaw;
        setXxa(0.0F);
        setZza(0.0F);
        setDeltaMovement(0.0, getDeltaMovement().y, 0.0);
    }

    private void stopRoarImmediately() {
        roarActiveTicks = 0;
        roarEffectsApplied = false;
        roarSoundPlayed = false;
    }

    private void stopSummonImmediately() {
        summonActiveTicks = 0;
        summonEventFired = false;
        summonPunchSoundPlayed = false;
        summonTarget = null;
    }

    private void clearLordAttackSync() {
        if (!level().isClientSide()) {
            entityData.set(DATA_LORD_ATTACK_ID, 0);
            entityData.set(DATA_LORD_ATTACK_START_TICK, 0L);
        }
    }

    private void performPullOutSwing() {
        Vec3 forward = Vec3.directionFromRotation(0.0F, lockedSpecialYaw).normalize();
        Vec3 right = new Vec3(-forward.z, 0.0, forward.x);
        Vec3 impact = position().add(forward.scale(4.5)).add(right.scale(-0.5));
        Vec3 sideSwingDirection = forward.add(right.scale(0.7)).normalize();
        Set<UUID> hitTargets = new HashSet<>();
        List<Vec3> samples = new ArrayList<>();
        for (double radius : new double[]{3.0, 5.25, 7.5}) {
            for (int degrees = -95; degrees <= 70; degrees += 15) {
                double angle = Math.toRadians(degrees);
                samples.add(position()
                        .add(forward.scale(Math.cos(angle) * radius))
                        .add(right.scale(Math.sin(angle) * radius)));
            }
        }

        for (LivingEntity target : getClubCandidates(9.5, 6.0)) {
            Vec3 relative = target.position().subtract(position());
            double localForward = relative.dot(forward);
            boolean struckByClub = localForward >= -0.75
                    && intersectsAnySwingSample(target, samples, 2.3);
            if (!struckByClub) {
                continue;
            }
            if (hitTargets.add(target.getUUID())) {
                if (dealClubDamage(target, PULL_OUT_DAMAGE_MULTIPLIER,
                        Config.OGRE_LORD_PULL_OUT_BONUS_DAMAGE.get().floatValue())) {
                    applyHeavyClubHitEffects(target);
                    // Direct contact with the club keeps the lateral force of its side swing.
                    applyClubKnockback(target, sideSwingDirection, 3.2, 0.55);
                }
            }
        }

        playHeavyClubImpact(impact, 2);
        // Dust cloud/ring radius must match the actual ripple radius below (17.0F) — this was still
        // the pre-buff 8.5F, so the visual AOE cue read as half the size of the real 17-block hit.
        spawnClubImpactEffects(impact, 17.0F, 1.8F);
        startClubGroundRipple(impact, 17.0F, 0.55, GroundRippleKind.KING_CLUB_PULL_OUT, hitTargets);
    }

    private void performClubDoubleOverhead() {
        performOverheadClubStrike(
                15.0, 13.5, 20.0F,
                CLUB_DOUBLE_OVERHEAD_DAMAGE_MULTIPLIER,
                Config.OGRE_LORD_CLUB_DOUBLE_OVERHEAD_BONUS_DAMAGE.get().floatValue(),
                4.0, 0.8,
                2.4F, 3);
    }

    private void performSingleOverheadSlam() {
        performOverheadClubStrike(
                8.0, 3.75, 8.0F,
                SINGLE_OVERHEAD_DAMAGE_MULTIPLIER,
                Config.OGRE_LORD_SINGLE_OVERHEAD_BONUS_DAMAGE.get().floatValue(),
                3.2, 0.65,
                1.8F, 2);
    }

    private void performOverheadClubStrike(double length, double halfWidth, float rippleRadius,
                                           float damageMultiplier, float bonusDamage,
                                           double knockback, double verticalKnockback,
                                           float shake, int blocksDestroyed) {
        Vec3 forward = Vec3.directionFromRotation(0.0F, lockedSpecialYaw).normalize();
        Vec3 right = new Vec3(-forward.z, 0.0, forward.x);
        Vec3 impact = position().add(forward.scale(length * 0.62));
        double clubHeadRadius = length > 10.0 ? 3.25 : 2.75;
        AABB directImpactVolume = new AABB(
                impact.x - clubHeadRadius, getY() - 1.0, impact.z - clubHeadRadius,
                impact.x + clubHeadRadius, getY() + 6.5, impact.z + clubHeadRadius);
        Set<UUID> hitTargets = new HashSet<>();

        for (LivingEntity target : getClubCandidates(length + 2.0, 7.0)) {
            Vec3 relative = target.position().subtract(position());
            double localForward = relative.dot(forward);
            double localRight = relative.dot(right);
            boolean withinSweptCorridor = localForward >= 0.25
                    && localForward <= length
                    && Math.abs(localRight) <= halfWidth;
            boolean verticallyReachable = target.getBoundingBox().maxY >= getY() - 1.0
                    && target.getBoundingBox().minY <= getY() + 6.5;
            boolean struckByDescendingClub = target.getBoundingBox().intersects(directImpactVolume);
            if (!withinSweptCorridor || !verticallyReachable || !struckByDescendingClub
                    || !hitTargets.add(target.getUUID())) {
                continue;
            }
            // Direct club contact remains full strength. The former half-strength surrounding
            // shockwave is now delivered by the traveling server-authoritative ripple below.
            if (dealClubDamage(target, damageMultiplier, bonusDamage)) {
                applyHeavyClubHitEffects(target);
                applyVerticalShockwaveLaunch(target, verticalKnockback);
            }
        }

        playHeavyClubImpact(impact, blocksDestroyed);
        spawnClubImpactEffects(impact, rippleRadius, shake);
        startClubGroundRipple(impact, rippleRadius, verticalKnockback,
                length > 10.0 ? GroundRippleKind.KING_CLUB_DOUBLE : GroundRippleKind.KING_CLUB_SINGLE,
                hitTargets);
    }

    private void startClubGroundRipple(Vec3 impact, float radius, double verticalKnockback,
                                       GroundRippleKind kind, Set<UUID> directHits) {
        int rings = Math.max(2, (int) Math.ceil(radius / 1.25F));
        float propagationSpeed = radius / (rings * 2.0F);
        MeleeAttackHit rippleHit = MeleeAttackHit.areaSpecial(0, 0.0F, radius, false,
                        0.0F, 0.0, 0.0F, verticalKnockback, 0, 0, true)
                .withVerticalHitRange(7.0);
        startGroundRipple(impact, rippleHit,
                new GroundRippleProfile(radius, propagationSpeed, 1.125F, 0.44F,
                        kind == GroundRippleKind.KING_CLUB_DOUBLE ? 1.25F : 1.1F,
                        7.0, true, kind), directHits);
    }

    private List<LivingEntity> getClubCandidates(double horizontalRange, double verticalRange) {
        return level().getEntitiesOfClass(
                LivingEntity.class,
                getBoundingBox().inflate(horizontalRange, verticalRange, horizontalRange),
                target -> target != this
                        && target.isAlive()
                        && !(target instanceof OgreGrunt)
                        && getSensing().hasLineOfSight(target));
    }

    private boolean intersectsAnySwingSample(LivingEntity target, List<Vec3> samples, double radius) {
        double radiusSqr = radius * radius;
        AABB bounds = target.getBoundingBox();
        for (Vec3 sample : samples) {
            Vec3 clubHead = sample.add(0.0, 2.5, 0.0);
            double dx = distanceToInterval(clubHead.x, bounds.minX, bounds.maxX);
            double dy = distanceToInterval(clubHead.y, bounds.minY, bounds.maxY);
            double dz = distanceToInterval(clubHead.z, bounds.minZ, bounds.maxZ);
            if (dx * dx + dy * dy + dz * dz <= radiusSqr) {
                return true;
            }
        }
        return false;
    }

    private static double distanceToInterval(double value, double min, double max) {
        if (value < min) {
            return min - value;
        }
        return value > max ? value - max : 0.0;
    }

    private boolean dealClubDamage(LivingEntity target, float damageMultiplier, float bonusDamage) {
        float damage = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * damageMultiplier + bonusDamage;
        return level() instanceof ServerLevel serverLevel
                && target.hurtServer(serverLevel, damageSources().mobAttack(this), damage);
    }

    private void applyHeavyClubHitEffects(LivingEntity target) {
        CombatEffects.applyDazed(target, Config.DAZED_HEAVY_ATTACK_DURATION_TICKS.get(), this);
    }

    private void applyClubKnockback(LivingEntity target, Vec3 direction,
                                    double strength, double verticalStrength) {
        target.knockback(strength, -direction.x, -direction.z);
        target.push(0.0, verticalStrength, 0.0);
    }

    private void applyVerticalShockwaveLaunch(LivingEntity target, double verticalStrength) {
        Vec3 movement = target.getDeltaMovement();
        target.setDeltaMovement(movement.x, Math.max(movement.y, verticalStrength), movement.z);
        target.hurtMarked = true;
    }

    private void playHeavyClubImpact(Vec3 impact, int blocksDestroyed) {
        level().playSound(null, impact.x, impact.y, impact.z,
                SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.6F, 0.45F);
        level().playSound(null, impact.x, impact.y, impact.z,
                ModSoundEvents.OGRE_BRUTE_BELLY_SLAM_IMPACT.get(), SoundSource.HOSTILE, 1.5F, 0.65F);
        destroyNearbyBlocks(impact, blocksDestroyed);
    }

    private void spawnClubImpactEffects(Vec3 impact, float radius, float shake) {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        spawnDenseDustCloud(serverLevel, impact, radius);
        PacketDistributor.sendToPlayersNear(
                serverLevel, null, impact.x, impact.y, impact.z,
                Math.max(24.0, radius * 4.0),
                new ScreenShakePayload(impact.x, impact.y, impact.z, shake));
    }

    /**
     * The club slams' dense dust burst (cloud + poof + boundary ring), reused for the unarmed
     * Phase-1 Double Overhead Slam so a slam that size reads with the same amount of dust — that
     * attack already gets its own screenshake through the standard shared hit pipeline, so this
     * intentionally excludes it to avoid a duplicate shake packet.
     */
    private void spawnDenseDustCloud(ServerLevel serverLevel, Vec3 impact, float radius) {
        int particles = Math.max(30, (int) (radius * 9.0F));
        serverLevel.sendParticles(ParticleTypes.CLOUD,
                impact.x, impact.y + 0.25, impact.z,
                particles, radius * 0.35, 0.3, radius * 0.35, 0.05);
        serverLevel.sendParticles(ParticleTypes.POOF,
                impact.x, impact.y + 0.35, impact.z,
                particles / 2, radius * 0.2, 0.2, radius * 0.2, 0.03);

        // Draw the slam's actual AOE boundary, matching the readable ground-ring cue used by the
        // club shockwaves so players can judge the jump dodge consistently.
        int ringPoints = Math.max(32, (int) Math.ceil(radius * 8.0F));
        for (int point = 0; point < ringPoints; point++) {
            double angle = Math.PI * 2.0 * point / ringPoints;
            double ringX = impact.x + Math.cos(angle) * radius;
            double ringZ = impact.z + Math.sin(angle) * radius;
            serverLevel.sendParticles(ParticleTypes.CLOUD,
                    ringX, impact.y + 0.15, ringZ,
                    1, 0.035, 0.025, 0.035, 0.005);
        }
    }

    /**
     * Fills the gap between a shockwave's center burst and its edge ring with concentric bands of
     * decreasing density — dense right at the impact point, thinning out toward (but not replacing)
     * the existing ring. Radius here only controls how far the fill reaches; it does not draw or
     * resize the ring itself.
     */
    private void spawnGraduatedDustFill(ServerLevel serverLevel, Vec3 center, float radius) {
        double y = center.y + 0.12;
        float[] bandFractions = {0.15F, 0.35F, 0.55F, 0.75F, 0.9F};
        int[] bandCounts = {20, 16, 12, 8, 5};
        for (int band = 0; band < bandFractions.length; band++) {
            float bandRadius = radius * bandFractions[band];
            int points = bandCounts[band];
            double angleOffset = band * 0.3;
            for (int i = 0; i < points; i++) {
                double angle = Math.PI * 2.0 * i / points + angleOffset;
                double x = center.x + Math.cos(angle) * bandRadius;
                double z = center.z + Math.sin(angle) * bandRadius;
                serverLevel.sendParticles(ParticleTypes.CLOUD, x, y, z, 1, 0.05, 0.04, 0.05, 0.01);
            }
        }
    }

    private void tickClosePressure() {
        LivingEntity target = getTarget();
        if (target != null
                && target.isAlive()
                && distanceToSqr(target) <= BELLY_BUMP_CLOSE_PRESSURE_RANGE * BELLY_BUMP_CLOSE_PRESSURE_RANGE
                && getSensing().hasLineOfSight(target)) {
            closePressureTicks = Math.min(closePressureTicks + (hurtTime > 0 ? 2 : 1), BELLY_BUMP_CLOSE_PRESSURE_TICKS);
        } else {
            closePressureTicks = Math.max(closePressureTicks - 2, 0);
        }
    }

    private void maybePlayLordIdleHuff() {
        if (lordIdleHuffCooldownTicks > 0 || activeLordAttackId != 0 || isRoaring()) {
            return;
        }

        if (getRandom().nextInt(LORD_IDLE_HUFF_CHANCE) != 0) {
            return;
        }

        playLordHuff(0.9F);
        resetLordIdleHuffCooldown();
    }

    private void resetLordIdleHuffCooldown() {
        lordIdleHuffCooldownTicks = LORD_IDLE_HUFF_MIN_COOLDOWN_TICKS + getRandom().nextInt(LORD_IDLE_HUFF_RANDOM_COOLDOWN_TICKS + 1);
    }

    private void tickDelayedImpactSounds() {
        heavyAttackRoarDelayTicks = tickDelayedHeavyAttackRoar(heavyAttackRoarDelayTicks);
        oneArmUpswingImpactSoundDelayTicks = tickDelayedImpactSound(oneArmUpswingImpactSoundDelayTicks, 1, this::playLaunchImpactSound);
        doubleStompLeftImpactSoundDelayTicks = tickDelayedImpactSound(doubleStompLeftImpactSoundDelayTicks, 2, () -> playDoubleStompImpactSound(-1.0));
        doubleStompRightImpactSoundDelayTicks = tickDelayedImpactSound(doubleStompRightImpactSoundDelayTicks, 2, () -> playDoubleStompImpactSound(1.0));
        doubleOverheadSlamImpactSoundDelayTicks = tickDelayedImpactSound(doubleOverheadSlamImpactSoundDelayTicks, 3, this::playDoubleOverheadSlamImpactSound);
        bellyBumpImpactSoundDelayTicks = tickDelayedImpactSound(bellyBumpImpactSoundDelayTicks, 5, this::playBellyBumpImpactSound);
        doubleArmUpswingImpactSoundDelayTicks = tickDelayedImpactSound(doubleArmUpswingImpactSoundDelayTicks, 6, this::playLaunchImpactSound);
        clubHeadThrustImpactSoundDelayTicks = tickDelayedImpactSound(clubHeadThrustImpactSoundDelayTicks, 7, this::playClubHeadThrustImpactSound);
        singleLegStompImpactSoundDelayTicks = tickDelayedImpactSound(singleLegStompImpactSoundDelayTicks, 8, this::playSingleLegStompImpactSound);
    }

    private int tickDelayedHeavyAttackRoar(int ticks) {
        if (ticks < 0) {
            return ticks;
        }
        if (activeLordAttackId != heavyAttackRoarAttackId || attackAnimationTicks <= 0) {
            return -1;
        }
        if (ticks <= 0) {
            float variation = (getRandom().nextFloat() * 2.0F - 1.0F) * HEAVY_ATTACK_ROAR_PITCH_VARIATION;
            level().playSound(null, getX(), getY(), getZ(),
                    (heavyAttackUsesOgrebloodRoar
                            ? ModSoundEvents.OGREBLOOD_TOTEM_ACTIVATE
                            : ModSoundEvents.OGRE_LORD_OVERHEAD_GRUNT).get(), SoundSource.HOSTILE,
                    1.85F, heavyAttackRoarPitch + variation);
            return -1;
        }
        return ticks - 1;
    }

    private void scheduleHeavyAttackRoar(int attackId, int impactTick, float pitch, boolean useOgrebloodRoar) {
        scheduleHeavyAttackRoar(attackId, impactTick, pitch, useOgrebloodRoar, HEAVY_ATTACK_ROAR_LEAD_TICKS);
    }

    private void scheduleHeavyAttackRoar(int attackId, int impactTick, float pitch, boolean useOgrebloodRoar,
                                         int leadTicks) {
        heavyAttackRoarAttackId = attackId;
        heavyAttackRoarDelayTicks = Math.max(0, impactTick - leadTicks);
        heavyAttackRoarPitch = pitch;
        heavyAttackUsesOgrebloodRoar = useOgrebloodRoar;
    }

    private int tickDelayedImpactSound(int ticks, int attackId, Runnable playSound) {
        if (ticks < 0) {
            return ticks;
        }

        if (activeLordAttackId != attackId || attackAnimationTicks <= 0) {
            return -1;
        }

        if (ticks <= 0) {
            playSound.run();
            return -1;
        }

        return ticks - 1;
    }

    private void playLordHuff(float volume) {
        level().playSound(null, getX(), getY(), getZ(), ModSoundEvents.OGRE_LORD_HUFF.get(), SoundSource.HOSTILE, volume, 1.0F);
    }

    private void playDoubleStompImpactSound(double rightOffset) {
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.3F, 0.55F);
        level().playSound(null, getX(), getY(), getZ(), ModSoundEvents.OGRE_BRUTE_BELLY_SLAM_IMPACT.get(), SoundSource.HOSTILE, 1.3F, 0.85F);
        destroyNearbyBlocks(getImpactCenter(0.4, rightOffset), DOUBLE_STOMP_BLOCKS_DESTROYED);
    }

    private void playDoubleOverheadSlamImpactSound() {
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.6F, 0.45F);
        level().playSound(null, getX(), getY(), getZ(), ModSoundEvents.OGRE_BRUTE_BELLY_SLAM_IMPACT.get(), SoundSource.HOSTILE, 1.5F, 0.65F);
        destroyNearbyBlocks(getImpactCenter(1.6, 0.0), DOUBLE_OVERHEAD_SLAM_BLOCKS_DESTROYED);
    }

    private void playSingleLegStompImpactSound() {
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.1F, 0.6F);
        level().playSound(null, getX(), getY(), getZ(), ModSoundEvents.OGRE_BRUTE_BELLY_SLAM_IMPACT.get(), SoundSource.HOSTILE, 1.1F, 0.9F);
    }

    // The combo variety attacks below reuse OgreBrute's sound palette directly. Both launch-style
    // upswings (one arm and double arm) share this: a wet smack for the grab plus his recorded whoosh.
    private void playBellyBumpImpactSound() {
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.HOSTILE, 1.2F, 0.6F);
    }

    private void playLaunchImpactSound() {
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.SLIME_ATTACK, SoundSource.HOSTILE, 1.2F, 0.65F);
        level().playSound(null, getX(), getY(), getZ(), ModSoundEvents.OGRE_BRUTE_UPSWING_WHOOSH.get(), SoundSource.HOSTILE, 1.15F, 0.88F);
    }

    private void playClubHeadThrustImpactSound() {
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.15F, 0.8F);
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.IRON_GOLEM_DAMAGE, SoundSource.HOSTILE, 1.0F, 0.75F);
    }

    private Vec3 getImpactCenter(double forwardOffset, double rightOffset) {
        Vec3 forward = Vec3.directionFromRotation(0.0F, getYRot());
        Vec3 right = new Vec3(-forward.z, 0.0, forward.x);
        return position().add(forward.scale(forwardOffset)).add(right.scale(rightOffset));
    }

    private void destroyNearbyBlocks(Vec3 center, int count) {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }

        BlockPos centerPos = BlockPos.containing(center.x, center.y, center.z);
        List<BlockPos> candidates = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(centerPos.offset(-1, -1, -1), centerPos.offset(1, 1, 1))) {
            BlockState state = serverLevel.getBlockState(pos);
            if (state.isAir() || state.hasBlockEntity()) {
                continue;
            }

            float hardness = state.getDestroySpeed(serverLevel, pos);
            if (hardness < 0.0F || hardness > MAX_DESTRUCTIBLE_HARDNESS) {
                continue;
            }

            candidates.add(pos.immutable());
        }

        int destroyed = 0;
        while (destroyed < count && !candidates.isEmpty()) {
            BlockPos pos = candidates.remove(getRandom().nextInt(candidates.size()));
            serverLevel.destroyBlock(pos, false);
            destroyed++;
        }
    }

    private void maybeStartRoar() {
        // activeLordAttackId (cleared server-side by onMeleeAttackPlanFinished()) is used here
        // instead of attackAnimationTicks — the latter is only ever assigned a fresh duration and
        // never decremented on the server, so it would otherwise block every roar attempt after
        // his first swing.
        if (isSummoning()) {
            if (roarCooldownTicks <= 0) {
                roarCooldownTicks = getSpecialConflictRetryTicks();
            }
            return;
        }
        if (isPhaseTransitioning() || isDyingAnimation()
                || isRoaring() || roarCooldownTicks > 0 || activeLordAttackId != 0) {
            return;
        }

        LivingEntity target = getTarget();
        if (target == null || !target.isAlive()) {
            return;
        }

        startRoar();
    }

    private void startRoar() {
        LivingEntity target = getTarget();
        if (target != null && target.isAlive()) {
            double dx = target.getX() - getX();
            double dz = target.getZ() - getZ();
            roarLockedYaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
        } else {
            roarLockedYaw = getYRot();
        }
        roarLockedX = getX();
        roarLockedZ = getZ();
        roarActiveTicks = ROAR_ANIMATION_TICKS;
        roarCooldownTicks = isPhaseTwo() ? PHASE_TWO_ROAR_COOLDOWN_TICKS : ROAR_COOLDOWN_TICKS;
        roarEffectsApplied = false;
        roarSoundPlayed = false;
        getNavigation().stop();
        setYRot(roarLockedYaw);
        setYBodyRot(roarLockedYaw);
        setYHeadRot(roarLockedYaw);
        yRotO = roarLockedYaw;
        yBodyRotO = roarLockedYaw;
        yHeadRotO = roarLockedYaw;
        setDeltaMovement(0.0, getDeltaMovement().y, 0.0);
        playAttackAnimation(4);
    }

    private void tickRoar() {
        if (roarActiveTicks <= 0) {
            return;
        }

        getNavigation().stop();
        setXxa(0.0F);
        setZza(0.0F);

        int elapsedTicks = ROAR_ANIMATION_TICKS - roarActiveTicks;
        tickAnchoredNudgeMovement(roarLockedX, roarLockedZ);
        float roarYaw = roarLockedYaw + getRoarYawOffset(elapsedTicks);
        setYRot(roarYaw);
        setYBodyRot(roarYaw);
        setYHeadRot(roarYaw);
        yRotO = roarYaw;
        yBodyRotO = roarYaw;
        yHeadRotO = roarYaw;

        if (elapsedTicks >= ROAR_SOUND_TICK && !roarSoundPlayed) {
            level().playSound(null, getX(), getY(), getZ(), ModSoundEvents.OGRE_LORD_ROAR.get(), SoundSource.HOSTILE, 1.8F, 1.0F);
            roarSoundPlayed = true;
            startRoarScreenShake();
        }

        if (elapsedTicks >= ROAR_EFFECT_TICK && !roarEffectsApplied) {
            applyRoarEffects();
            roarEffectsApplied = true;
        }

        tickRoarSlowness(elapsedTicks);

        tickRoarShockwave(elapsedTicks);

        roarActiveTicks--;
        if (roarActiveTicks <= 0) {
            activeLordAttackId = 0;
            attackAnimationTicks = 0;
            clearLordAttackSync();
        }
    }

    private void startRoarScreenShake() {
        roarScreenShakeDelayTicks = ROAR_SCREEN_SHAKE_DELAY_TICKS;
    }

    private void tickRoarScreenShake() {
        if (roarScreenShakeDelayTicks > 0) {
            roarScreenShakeDelayTicks--;
            if (roarScreenShakeDelayTicks == 0) {
                sendContinuousRoarScreenShake();
            }
        }
    }

    private void sendContinuousRoarScreenShake() {
        if (level() instanceof ServerLevel serverLevel) {
            PacketDistributor.sendToPlayersNear(
                    serverLevel, null, getX(), getY(), getZ(), ROAR_SCREEN_SHAKE_RANGE,
                    new ScreenShakePayload(getX(), getY(), getZ(), ROAR_SCREEN_SHAKE_INTENSITY,
                            ROAR_SCREEN_SHAKE_ACTIVE_TICKS, ROAR_SCREEN_SHAKE_FREQUENCY_MULTIPLIER));
        }
    }

    private void tickAnchoredNudgeMovement(double anchorX, double anchorZ) {
        double offsetX = getX() - anchorX;
        double offsetZ = getZ() - anchorZ;
        double distanceSqr = offsetX * offsetX + offsetZ * offsetZ;
        if (distanceSqr > ROAR_MAX_NUDGE_DISTANCE * ROAR_MAX_NUDGE_DISTANCE) {
            double scale = ROAR_MAX_NUDGE_DISTANCE / Math.sqrt(distanceSqr);
            setPos(anchorX + offsetX * scale, getY(), anchorZ + offsetZ * scale);
        }

        Vec3 movement = getDeltaMovement();
        double horizontalSpeed = Math.sqrt(movement.x * movement.x + movement.z * movement.z);
        double speedScale = horizontalSpeed > ROAR_MAX_HORIZONTAL_NUDGE_SPEED
                ? ROAR_MAX_HORIZONTAL_NUDGE_SPEED / horizontalSpeed
                : 1.0;
        setDeltaMovement(
                movement.x * speedScale * ROAR_NUDGE_DAMPING,
                movement.y,
                movement.z * speedScale * ROAR_NUDGE_DAMPING);
    }

    private float getRoarYawOffset(int elapsedTicks) {
        if (elapsedTicks < ROAR_TURN_START_TICK) {
            return 0.0F;
        }
        if (elapsedTicks < ROAR_LEFT_TICK) {
            return smoothRoarYaw(elapsedTicks, ROAR_TURN_START_TICK, ROAR_LEFT_TICK,
                    0.0F, ROAR_LEFT_YAW_OFFSET);
        }
        if (elapsedTicks < ROAR_RIGHT_TICK) {
            return smoothRoarYaw(elapsedTicks, ROAR_LEFT_TICK, ROAR_RIGHT_TICK,
                    ROAR_LEFT_YAW_OFFSET, ROAR_RIGHT_YAW_OFFSET);
        }
        if (elapsedTicks < ROAR_RETURN_START_TICK) {
            return ROAR_RIGHT_YAW_OFFSET;
        }
        if (elapsedTicks < ROAR_FORWARD_TICK) {
            return smoothRoarYaw(elapsedTicks, ROAR_RETURN_START_TICK, ROAR_FORWARD_TICK,
                    ROAR_RIGHT_YAW_OFFSET, 0.0F);
        }
        return 0.0F;
    }

    private static float smoothRoarYaw(int tick, int startTick, int endTick, float startYaw, float endYaw) {
        float progress = Mth.clamp((float) (tick - startTick) / (endTick - startTick), 0.0F, 1.0F);
        float eased = progress * progress * (3.0F - 2.0F * progress);
        return Mth.lerp(eased, startYaw, endYaw);
    }

    private void tickRoarShockwave(int elapsedTicks) {
        if (!(level() instanceof ServerLevel serverLevel)
                || elapsedTicks < ROAR_SHOCKWAVE_START_TICK
                || elapsedTicks > ROAR_SHOCKWAVE_START_TICK + ROAR_SHOCKWAVE_EXPANSION_TICKS
                || (elapsedTicks - ROAR_SHOCKWAVE_START_TICK) % 2 != 0) {
            return;
        }

        float progress = Mth.clamp(
                (float) (elapsedTicks - ROAR_SHOCKWAVE_START_TICK) / ROAR_SHOCKWAVE_EXPANSION_TICKS,
                0.0F, 1.0F);
        float radius = Mth.lerp(progress, ROAR_SHOCKWAVE_START_RADIUS, ROAR_SHOCKWAVE_END_RADIUS);
        int ringPoints = Math.max(14, (int) Math.ceil(radius * 3.0F));
        double y = getY() + 0.15;

        for (int point = 0; point < ringPoints; point++) {
            double angle = Math.TAU * point / ringPoints;
            double x = getX() + Math.cos(angle) * radius;
            double z = getZ() + Math.sin(angle) * radius;
            serverLevel.sendParticles(ParticleTypes.CLOUD,
                    x, y, z,
                    1, 0.035, 0.025, 0.035, 0.005);
        }
    }

    private void applyRoarEffects() {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }

        AABB area = getBoundingBox().inflate(ROAR_EFFECT_RADIUS);
        List<LivingEntity> nearby = level().getEntitiesOfClass(
                LivingEntity.class,
                area,
                entity -> entity != this && entity.isAlive() && distanceToSqr(entity) <= ROAR_EFFECT_RADIUS * ROAR_EFFECT_RADIUS);

        for (LivingEntity entity : nearby) {
            if (entity instanceof OgreGrunt) {
                entity.addEffect(new MobEffectInstance(MobEffects.STRENGTH, ROAR_BUFF_DURATION_TICKS, 1));
                entity.addEffect(new MobEffectInstance(MobEffects.SPEED, ROAR_BUFF_DURATION_TICKS, 0));
            } else if (entity instanceof Player) {
                entity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ROAR_WEAKNESS_DURATION_TICKS, 0));
            }
        }

    }

    private void tickRoarSlowness(int elapsedTicks) {
        if (elapsedTicks < ROAR_SOUND_TICK
                || elapsedTicks % ROAR_SLOWNESS_REFRESH_INTERVAL_TICKS != 0) {
            return;
        }

        AABB area = getBoundingBox().inflate(ROAR_EFFECT_RADIUS);
        for (Player player : level().getEntitiesOfClass(Player.class, area,
                candidate -> candidate.isAlive()
                        && distanceToSqr(candidate) <= ROAR_EFFECT_RADIUS * ROAR_EFFECT_RADIUS)) {
            player.addEffect(new MobEffectInstance(
                    MobEffects.SLOWNESS,
                    ROAR_SLOWNESS_REFRESH_DURATION_TICKS,
                    1));
        }
    }

    private void performRoyalSummon(ServerLevel serverLevel) {
        LivingEntity target = summonTarget != null && summonTarget.isAlive() ? summonTarget : getTarget();
        commandNearbyOgres(target);

        int roll = getRandom().nextInt(TOTAL_SUMMON_FORMATION_WEIGHT);
        switch (selectSummonFormation(roll)) {
            case GUARDS -> spawnFormation(serverLevel, target,
                    ModEntities.OGRE_GUARD.get(), ModEntities.OGRE_GUARD.get());
            case CAPTAIN -> spawnFormation(serverLevel, target,
                    ModEntities.OGRE_GRUNT.get(), ModEntities.OGRE_GRUNT.get(),
                    ModEntities.OGRE_GRUNT_CAPTAIN.get());
            case MAGE -> spawnFormation(serverLevel, target,
                    ModEntities.OGRE_MAGE.get(), ModEntities.OGRE_ARCHER.get(),
                    ModEntities.OGRE_ARCHER.get());
        }
    }

    private static SummonFormation selectSummonFormation(int roll) {
        if (roll < GUARD_FORMATION_WEIGHT) {
            return SummonFormation.GUARDS;
        }
        if (roll < GUARD_FORMATION_WEIGHT + GRUNT_FORMATION_WEIGHT) {
            return SummonFormation.CAPTAIN;
        }
        return SummonFormation.MAGE;
    }

    @SafeVarargs
    private final void spawnFormation(ServerLevel serverLevel, LivingEntity target,
                                      EntityType<? extends OgreGrunt>... formation) {
        for (int i = 0; i < formation.length; i++) {
            double angle = getSummonSpawnAngle(i, formation.length);
            BlockPos spawnPos = findSummonSpawnPos(serverLevel, angle, formation[i]);
            if (spawnPos == null) {
                continue;
            }
            OgreGrunt ogre = formation[i].spawn(serverLevel, spawnPos, EntitySpawnReason.MOB_SUMMONED);
            if (ogre == null) {
                continue;
            }

            if (ogre instanceof OgreGuard guard) {
                guard.rollEliteSpearEnchantment();
            }
            if (target != null && target.isAlive()) {
                ogre.setRoyalCommandTarget(target);
            }
        }
    }

    private void commandNearbyOgres(@Nullable LivingEntity target) {
        if (target == null || !target.isAlive()) {
            return;
        }
        AABB commandArea = getBoundingBox().inflate(ROYAL_COMMAND_RADIUS);
        for (OgreGrunt ogre : level().getEntitiesOfClass(OgreGrunt.class, commandArea,
                ogre -> ogre != this && ogre.isAlive())) {
            ogre.setRoyalCommandTarget(target);
        }
    }

    @Nullable
    private BlockPos findSummonSpawnPos(ServerLevel serverLevel, double baseAngle,
                                        EntityType<? extends OgreGrunt> entityType) {
        for (int attempt = 0; attempt < 8; attempt++) {
            int direction = attempt % 2 == 0 ? 1 : -1;
            double angleOffset = Math.toRadians(((attempt + 1) / 2) * 18.0 * direction);
            double radius = SUMMON_SPAWN_RADIUS + attempt % 3;
            double angle = baseAngle + angleOffset;
            int x = Mth.floor(getX() + Math.cos(angle) * radius);
            int z = Mth.floor(getZ() + Math.sin(angle) * radius);
            int y = serverLevel.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos pos = new BlockPos(x, y, z);
            if (serverLevel.getBlockState(pos.below()).getCollisionShape(serverLevel, pos.below()).isEmpty()
                    || !serverLevel.getFluidState(pos).isEmpty()
                    || !serverLevel.noCollision(entityType.getDimensions().makeBoundingBox(
                            x + 0.5, y, z + 0.5))) {
                continue;
            }
            return pos;
        }
        return null;
    }

    private double getSummonSpawnAngle(int index, int formationSize) {
        return Math.toRadians(summonLockedYaw) + Math.PI / 2.0
                + Math.TAU * index / formationSize;
    }

    private enum SummonFormation { GUARDS, CAPTAIN, MAGE }

    private void maybeStartSummonPoint() {
        if (isRoaring()) {
            if (summonCooldownTicks <= 0) {
                summonCooldownTicks = getSpecialConflictRetryTicks();
            }
            return;
        }
        if (isPhaseTransitioning() || isDyingAnimation() || isSummoning()
                || summonCooldownTicks > 0 || activeLordAttackId != 0) {
            return;
        }

        LivingEntity target = getTarget();
        if (target == null || !target.isAlive()) {
            return;
        }

        summonTarget = target;
        double dx = target.getX() - getX();
        double dz = target.getZ() - getZ();
        summonLockedYaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
        summonLockedX = getX();
        summonLockedZ = getZ();
        // Drop any completed attack's lingering recovery plan before reserving the shared
        // controller. The summon itself remains immune to melee selection until it finishes.
        cancelActiveMeleeAttack();
        summonActiveTicks = SUMMON_POINT_ANIMATION_TICKS;
        summonCooldownTicks = SUMMON_COOLDOWN_TICKS;
        summonEventFired = false;
        summonPunchSoundPlayed = false;
        getNavigation().stop();
        setDeltaMovement(0.0, getDeltaMovement().y, 0.0);
        playAttackAnimation(13);
    }

    private void tickSummonPoint() {
        if (summonActiveTicks <= 0) {
            return;
        }

        getNavigation().stop();
        setXxa(0.0F);
        setZza(0.0F);
        tickAnchoredNudgeMovement(summonLockedX, summonLockedZ);
        setYRot(summonLockedYaw);
        setYBodyRot(summonLockedYaw);
        setYHeadRot(summonLockedYaw);
        yRotO = summonLockedYaw;
        yBodyRotO = summonLockedYaw;
        yHeadRotO = summonLockedYaw;

        int elapsedTicks = SUMMON_POINT_ANIMATION_TICKS - summonActiveTicks;
        if (!summonPunchSoundPlayed && elapsedTicks >= SUMMON_POINT_PUNCH_SOUND_TICK) {
            summonPunchSoundPlayed = true;
            level().playSound(null, getX(), getY(), getZ(),
                    SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.HOSTILE, 1.25F, 0.8F);
        }
        if (!summonEventFired && elapsedTicks >= SUMMON_POINT_EVENT_TICK) {
            summonEventFired = true;
            level().playSound(null, getX(), getY(), getZ(),
                    ModSoundEvents.OGRE_LORD_SUMMON_GRUNT.get(), SoundSource.HOSTILE, 1.3F, 1.0F);
            if (level() instanceof ServerLevel serverLevel) {
                performRoyalSummon(serverLevel);
            }
        }

        summonActiveTicks--;
        if (summonActiveTicks <= 0) {
            activeLordAttackId = 0;
            attackAnimationTicks = 0;
            summonTarget = null;
            clearLordAttackSync();
        }
    }

    private boolean isSummoning() {
        return summonActiveTicks > 0 || activeLordAttackId == 13 && attackAnimationTicks > 0;
    }

    @Override
    protected boolean isPerformingNonMeleeAction() {
        return isSummoning() || isRoaring() || isPhaseTransitioning() || isDyingAnimation();
    }

    private int getSpecialConflictRetryTicks() {
        return SPECIAL_CONFLICT_RETRY_MIN_TICKS
                + getRandom().nextInt(SPECIAL_CONFLICT_RETRY_RANDOM_TICKS);
    }

    public boolean isRoaring() {
        return roarActiveTicks > 0 || activeLordAttackId == 4 && attackAnimationTicks > 0;
    }

    @Override
    protected MeleeAttackPlan startMeleeAttack() {
        if (isPhaseTransitioning()) {
            return new MeleeAttackPlan(10,
                    Math.max(1, PULL_OUT_ANIMATION_TICKS - phaseTransitionElapsedTicks),
                    List.of());
        }
        if (isSummoning() || isRoaring() || isDyingAnimation()) {
            return new MeleeAttackPlan(10, List.of());
        }

        if (isPhaseTwo()) {
            return startPhaseTwoMeleeAttack();
        }

        LivingEntity target = getTarget();
        if (canReachForBigAttack(target) && doubleOverheadSlamCooldownTicks <= 0 && getRandom().nextInt(5) == 0) {
            return startDoubleOverheadSlam();
        }

        if (canReachForBigAttack(target) && doubleStompCooldownTicks <= 0 && getRandom().nextInt(5) == 0) {
            return startDoubleStomp();
        }

        if (closePressureTicks >= BELLY_BUMP_CLOSE_PRESSURE_TICKS && bellyBumpCooldownTicks <= 0) {
            return startBellyBump();
        }

        if (doubleArmUpswingCooldownTicks <= 0 && getRandom().nextInt(4) == 0) {
            return startDoubleArmUpswing();
        }

        return switch (chooseVariedNormalAttack(1, 8)) {
            case 1 -> startOneArmUpswing();
            default -> startSingleLegStomp();
        };
    }

    private MeleeAttackPlan startPhaseTwoMeleeAttack() {
        LivingEntity target = getTarget();
        // Slightly more club-favoring than before (was 1-in-8), per tuning feedback — still leaves
        // plenty of room for Double Stomp/Belly Bump/the varied pool below, not a takeover.
        if (canReachForBigAttack(target)
                && clubDoubleOverheadCooldownTicks <= 0
                && getRandom().nextInt(6) == 0) {
            return startClubDoubleOverhead();
        }
        if (canReachForBigAttack(target)
                && doubleStompCooldownTicks <= 0
                && getRandom().nextInt(4) == 0) {
            return startDoubleStomp();
        }
        if (closePressureTicks >= BELLY_BUMP_CLOSE_PRESSURE_TICKS
                && bellyBumpCooldownTicks <= 0) {
            if (getRandom().nextBoolean()) {
                return startBellyBump();
            }
            // Share Belly Bump's close-pressure window and cooldown so these
            // two punish attacks occur at the same rate instead of the bump
            // repeatedly preempting the normal phase-two pool.
            bellyBumpCooldownTicks = BELLY_BUMP_COOLDOWN_TICKS;
            closePressureTicks = 0;
            return startClubHeadThrust();
        }
        if (singleOverheadCooldownTicks <= 0) {
            if (shouldUsePhaseTwoSingleLegStomp()) {
                return startSingleLegStomp();
            }
            // Single Overhead Slam (11, the default case) listed twice so it's the most common
            // pick in this pool; Club Head Thrust still gets its normal share, plus its own
            // separate appearances via the Belly Bump alternative and the fallback pool below.
            return switch (chooseVariedNormalAttack(1, 7, 11, 11)) {
                case 1 -> startOneArmUpswing();
                case 7 -> startClubHeadThrust();
                default -> startSingleOverheadSlam();
            };
        }
        if (shouldUsePhaseTwoSingleLegStomp()) {
            return startSingleLegStomp();
        }
        return switch (chooseVariedNormalAttack(1, 7)) {
            case 1 -> startOneArmUpswing();
            default -> startClubHeadThrust();
        };
    }

    private boolean shouldUsePhaseTwoSingleLegStomp() {
        return getRandom().nextInt(PHASE_TWO_SINGLE_LEG_STOMP_CHANCE) == 0;
    }

    private int chooseVariedNormalAttack(int... attackIds) {
        List<Integer> choices = new ArrayList<>(attackIds.length);
        for (int attackId : attackIds) {
            choices.add(attackId);
        }

        // Usually suppress the immediately previous move, but leave a small repeat chance so
        // the King does not feel like he is stepping through a scripted attack playlist.
        if (choices.size() > 1 && getRandom().nextInt(5) != 0) {
            choices.remove(Integer.valueOf(lastSelectedAttackId));
        }
        // Usually avoid both recent attacks, but occasionally allow the second-last move so the
        // sequence stays organic rather than becoming a perfectly predictable fixed rotation.
        if (choices.size() > 1 && getRandom().nextInt(4) != 0) {
            choices.remove(Integer.valueOf(secondLastSelectedAttackId));
        }

        return choices.get(getRandom().nextInt(choices.size()));
    }

    private MeleeAttackPlan startClubDoubleOverhead() {
        playAttackAnimation(10);
        scheduleHeavyAttackRoar(10, CLUB_DOUBLE_OVERHEAD_IMPACT_TICK, CLUB_DOUBLE_OVERHEAD_ROAR_PITCH, true,
                CLUB_DOUBLE_OVERHEAD_ROAR_LEAD_TICKS);
        lockSpecialAnimationPose();
        clubAttackElapsedTicks = 0;
        clubAttackImpactFired = false;
        clubDoubleOverheadCooldownTicks = CLUB_DOUBLE_OVERHEAD_COOLDOWN_TICKS;
        return new MeleeAttackPlan(
                DOUBLE_OVERHEAD_SLAM_SWING_COOLDOWN_TICKS,
                CLUB_DOUBLE_OVERHEAD_ANIMATION_TICKS,
                List.of());
    }

    private MeleeAttackPlan startSingleOverheadSlam() {
        playAttackAnimation(11);
        scheduleHeavyAttackRoar(11, SINGLE_OVERHEAD_IMPACT_TICK, SINGLE_OVERHEAD_ROAR_PITCH, false);
        lockSpecialAnimationPose();
        clubAttackElapsedTicks = 0;
        clubAttackImpactFired = false;
        singleOverheadCooldownTicks = SINGLE_OVERHEAD_COOLDOWN_TICKS;
        return new MeleeAttackPlan(
                BASIC_ATTACK_COOLDOWN_TICKS,
                SINGLE_OVERHEAD_SLAM_ANIMATION_TICKS,
                List.of());
    }

    private boolean canReachForBigAttack(LivingEntity target) {
        return target != null && target.isAlive() && isWithinRegularAttackStartRange(target) && getSensing().hasLineOfSight(target);
    }

    private MeleeAttackPlan startOneArmUpswing() {
        playAttackAnimation(1);
        oneArmUpswingImpactSoundDelayTicks = ONE_ARM_UPSWING_IMPACT_DELAY_TICKS;
        boolean clubUpswing = isPhaseTwo();
        double horizontalLaunch = clubUpswing ? 0.9 : 1.8;
        double verticalLaunch = clubUpswing ? 2.2 : 1.8;
        float hitRadius = clubUpswing ? 6.5F : 5.0F;
        double forwardOffset = clubUpswing ? 1.5 : 1.0;
        double verticalHitRange = clubUpswing ? 4.5 : 3.5;
        float damageMultiplier = ONE_ARM_UPSWING_DAMAGE_MULTIPLIER;
        if (clubUpswing) {
            double baseDamage = Math.max(0.01, getAttributeValue(Attributes.ATTACK_DAMAGE));
            damageMultiplier += (float) (Config.OGRE_LORD_CLUB_UPSWING_BONUS_DAMAGE.get() / baseDamage);
        }
        // Same launch as the double arm version, but redirected: he swings with his right hand
        // across to his left, so the launch is ~45 degrees up-and-out to his left instead of
        // straight up. In phase two, the metal club tip adds damage, reach, and a stronger launch.
        // The delayed impact sound uses playLaunchImpactSound(), including the same recorded whoosh.
        return new MeleeAttackPlan(BASIC_ATTACK_COOLDOWN_TICKS, ONE_ARM_UPSWING_ANIMATION_TICKS, List.of(
                MeleeAttackHit.areaSpecial(ONE_ARM_UPSWING_IMPACT_DELAY_TICKS, damageMultiplier, hitRadius, false,
                                0.2F, horizontalLaunch, 120.0F, verticalLaunch, 0, 0, false)
                        .withAreaOffset(forwardOffset, 0.0)
                        .withDirectionalKnockback(0.3, -1.0)
                        .withVerticalHitRange(verticalHitRange)));
    }

    private MeleeAttackPlan startDoubleStomp() {
        playAttackAnimation(2);
        doubleStompCooldownTicks = DOUBLE_STOMP_COOLDOWN_TICKS;
        doubleStompLeftImpactSoundDelayTicks = DOUBLE_STOMP_LEFT_IMPACT_DELAY_TICKS;
        doubleStompRightImpactSoundDelayTicks = DOUBLE_STOMP_RIGHT_IMPACT_DELAY_TICKS;
        warmUpRippleBlockVisual();
        return new MeleeAttackPlan(DOUBLE_STOMP_SWING_COOLDOWN_TICKS, DOUBLE_STOMP_ANIMATION_TICKS, List.of(
                MeleeAttackHit.areaSpecial(DOUBLE_STOMP_LEFT_IMPACT_DELAY_TICKS, DOUBLE_STOMP_DAMAGE_MULTIPLIER, DOUBLE_STOMP_AREA_RADIUS, true,
                                0.2F, 2.2, 0.0F, 0.85, 30, 0, true)
                        .withAreaOffset(0.4, -1.0)
                        .withVerticalHitRange(4.0)
                        .withScreenShake(1.3F)
                        .withBlockRippleParticles(),
                MeleeAttackHit.areaSpecial(DOUBLE_STOMP_RIGHT_IMPACT_DELAY_TICKS, DOUBLE_STOMP_DAMAGE_MULTIPLIER, DOUBLE_STOMP_AREA_RADIUS, true,
                                0.2F, 2.2, 0.0F, 0.85, 30, 0, true)
                        .withAreaOffset(0.4, 1.0)
                        .withVerticalHitRange(4.0)
                        .withScreenShake(1.3F)
                        .withBlockRippleParticles()));
    }

    private MeleeAttackPlan startDoubleOverheadSlam() {
        playAttackAnimation(3);
        scheduleHeavyAttackRoar(3, DOUBLE_OVERHEAD_SLAM_IMPACT_DELAY_TICKS, DOUBLE_OVERHEAD_ROAR_PITCH, true);
        doubleOverheadSlamCooldownTicks = DOUBLE_OVERHEAD_SLAM_COOLDOWN_TICKS;
        doubleOverheadSlamImpactSoundDelayTicks = DOUBLE_OVERHEAD_SLAM_IMPACT_DELAY_TICKS;
        warmUpRippleBlockVisual();
        return new MeleeAttackPlan(DOUBLE_OVERHEAD_SLAM_SWING_COOLDOWN_TICKS, DOUBLE_OVERHEAD_SLAM_ANIMATION_TICKS, List.of(
                MeleeAttackHit.areaSpecial(DOUBLE_OVERHEAD_SLAM_IMPACT_DELAY_TICKS, DOUBLE_OVERHEAD_SLAM_DAMAGE_MULTIPLIER, DOUBLE_OVERHEAD_SLAM_AREA_RADIUS, true,
                                0.3F, 3.0, 0.0F, 1.3, 40, 20, true)
                        .withAreaOffset(1.6, 0.0)
                        .withVerticalHitRange(4.5)
                        .withScreenShake(2.0F)
                        .withBlockRippleParticles()));
    }

    private MeleeAttackPlan startBellyBump() {
        playAttackAnimation(5);
        bellyBumpCooldownTicks = BELLY_BUMP_COOLDOWN_TICKS;
        bellyBumpImpactSoundDelayTicks = BELLY_BUMP_IMPACT_DELAY_TICKS;
        closePressureTicks = 0;
        // Omnidirectional (coneDegrees 0 skips the facing check) — this punishes anyone crowded
        // around him, not just whoever he's currently facing.
        return new MeleeAttackPlan(BASIC_ATTACK_COOLDOWN_TICKS, BELLY_BUMP_ANIMATION_TICKS, List.of(
                MeleeAttackHit.areaSpecial(BELLY_BUMP_IMPACT_DELAY_TICKS, BELLY_BUMP_DAMAGE_MULTIPLIER, 6.0F, false,
                                0.2F, 4.5, 0.0F, 1.3, 0, 0, false)
                        .withVerticalHitRange(4.0)));
    }

    private MeleeAttackPlan startDoubleArmUpswing() {
        playAttackAnimation(6);
        doubleArmUpswingCooldownTicks = DOUBLE_ARM_UPSWING_COOLDOWN_TICKS;
        doubleArmUpswingImpactSoundDelayTicks = DOUBLE_ARM_UPSWING_IMPACT_DELAY_TICKS;
        return new MeleeAttackPlan(BASIC_ATTACK_COOLDOWN_TICKS, DOUBLE_ARM_UPSWING_ANIMATION_TICKS, List.of(
                MeleeAttackHit.areaSpecial(DOUBLE_ARM_UPSWING_IMPACT_DELAY_TICKS, DOUBLE_ARM_UPSWING_DAMAGE_MULTIPLIER, 5.5F, false,
                                0.2F, 1.5, 120.0F, 2.2, 0, 0, false)
                        .withAreaOffset(1.3, 0.0)
                        .withVerticalHitRange(3.5)));
    }

    private MeleeAttackPlan startClubHeadThrust() {
        playAttackAnimation(7);
        clubHeadThrustImpactSoundDelayTicks = CLUB_HEAD_THRUST_IMPACT_DELAY_TICKS;
        clubHeadThrustElapsedTicks = 0;
        clubHeadThrustOriginX = getX();
        clubHeadThrustOriginZ = getZ();
        clubHeadThrustYaw = getYRot();
        previousClubHeadThrustPosition = null;
        clubHeadThrustHitTargets.clear();
        return new MeleeAttackPlan(BASIC_ATTACK_COOLDOWN_TICKS, CLUB_HEAD_THRUST_ANIMATION_TICKS, List.of());
    }

    private void tickClubHeadThrustHitbox() {
        if (activeLordAttackId != 7 || attackAnimationTicks <= 0) {
            previousClubHeadThrustPosition = null;
            return;
        }

        clubHeadThrustElapsedTicks++;
        if (clubHeadThrustElapsedTicks < CLUB_HEAD_THRUST_ACTIVE_START_TICK
                || clubHeadThrustElapsedTicks > CLUB_HEAD_THRUST_ACTIVE_END_TICK) {
            return;
        }

        Vec3 forward = Vec3.directionFromRotation(0.0F, clubHeadThrustYaw).normalize();
        Vec3 right = new Vec3(-forward.z, 0.0, forward.x);
        float progress = Mth.clamp(
                (float) (clubHeadThrustElapsedTicks - CLUB_HEAD_THRUST_ACTIVE_START_TICK + 1)
                        / (CLUB_HEAD_THRUST_ACTIVE_END_TICK - CLUB_HEAD_THRUST_ACTIVE_START_TICK + 1),
                0.0F, 1.0F);
        double reach = Mth.lerp(progress, CLUB_HEAD_THRUST_START_REACH, CLUB_HEAD_THRUST_END_REACH);
        Vec3 currentClubHead = new Vec3(clubHeadThrustOriginX, getY() + 2.25, clubHeadThrustOriginZ)
                .add(forward.scale(reach))
                .add(right.scale(CLUB_HEAD_THRUST_RIGHT_OFFSET));
        Vec3 previousClubHead = previousClubHeadThrustPosition == null
                ? new Vec3(clubHeadThrustOriginX, getY() + 2.25, clubHeadThrustOriginZ)
                    .add(forward.scale(CLUB_HEAD_THRUST_START_REACH))
                    .add(right.scale(CLUB_HEAD_THRUST_RIGHT_OFFSET))
                : previousClubHeadThrustPosition;
        previousClubHeadThrustPosition = currentClubHead;

        AABB sweptVolume = new AABB(previousClubHead, currentClubHead)
                .inflate(CLUB_HEAD_THRUST_HEAD_RADIUS, 2.25, CLUB_HEAD_THRUST_HEAD_RADIUS);
        MeleeAttackHit hit = MeleeAttackHit.areaSpecial(0,
                        CLUB_HEAD_THRUST_DAMAGE_MULTIPLIER, 0.0F, false,
                        0.2F, 4.32, 65.0F, 0.40, 0, 0, false)
                .withDirectionalKnockback(1.0, -0.38);
        for (LivingEntity target : level().getEntitiesOfClass(LivingEntity.class, sweptVolume,
                candidate -> candidate != this
                        && candidate.isAlive()
                        && !(candidate instanceof OgreGrunt)
                        && candidate.getBoundingBox().intersects(sweptVolume)
                        && getSensing().hasLineOfSight(candidate))) {
            if (!clubHeadThrustHitTargets.add(target.getUUID())) {
                continue;
            }
            if (doScaledHurtTarget(target, hit.damageMultiplier(), hit.armorPierceFraction())) {
                applyAttackHitExtras(target, hit);
            }
        }
    }

    private MeleeAttackPlan startSingleLegStomp() {
        playAttackAnimation(8);
        singleLegStompImpactSoundDelayTicks = SINGLE_LEG_STOMP_IMPACT_DELAY_TICKS;
        warmUpRippleBlockVisual();
        // Smaller/regular-tier ground stomp — right leg only, so offset toward his right side like
        // the right foot of the (bigger, rarer) double stomp.
        float stompRadius = 6.5F;
        return new MeleeAttackPlan(BASIC_ATTACK_COOLDOWN_TICKS, SINGLE_LEG_STOMP_ANIMATION_TICKS, List.of(
                MeleeAttackHit.areaSpecial(SINGLE_LEG_STOMP_IMPACT_DELAY_TICKS, SINGLE_LEG_STOMP_DAMAGE_MULTIPLIER, stompRadius, true,
                                0.2F, 2.0, 0.0F, 0.6, 20, 0, true)
                        .withAreaOffset(0.4, 1.0)
                        .withVerticalHitRange(4.0)
                        .withScreenShake(1.0F)
                        .withBlockRippleParticles()));
    }

    private boolean isWithinRegularAttackStartRange(LivingEntity target) {
        return target != null
                && target.isAlive()
                && distanceToSqr(target) <= REGULAR_ATTACK_START_RANGE * REGULAR_ATTACK_START_RANGE;
    }

    @Override
    protected double getMeleeAttackReachSqr(LivingEntity target) {
        return REGULAR_ATTACK_START_RANGE * REGULAR_ATTACK_START_RANGE;
    }

    @Override
    protected boolean shouldFaceMeleeTarget(LivingEntity target) {
        return !isRoaring() && !isPhaseTransitioning() && !isDyingAnimation() && !isMidSwing();
    }

    @Override
    protected boolean shouldStayStationaryDuringMeleeAttack() {
        // Without this, the goal keeps re-evaluating movement every tick during a swing — if the
        // target steps out of range mid-attack, it kicks off a repath toward the new position,
        // which reorients his body. shouldFaceMeleeTarget() already blocks the look-based rotation
        // mid-swing; this blocks the navigation-driven one too, so he stays committed to a swing
        // instead of whipping around partway through it.
        return isRoaring() || isPhaseTransitioning() || isDyingAnimation() || isMidSwing();
    }

    @Override
    protected boolean shouldDirectlyTrackMeleeTargetWhilePursuing() {
        // Let navigation own the body heading while closing distance. The look controller takes
        // over near attack range, avoiding the two controllers pulling the King side-to-side.
        return false;
    }

    @Override
    protected boolean shouldApproachDuringMeleeRecovery() {
        return true;
    }

    @Override
    protected boolean shouldUseCombatWalk(LivingEntity target, boolean closeEnoughToAttack,
                                          int ticksUntilNextAttack, boolean currentlyCombatWalking) {
        if (target == null || !target.isAlive() || isMidSwing()
                || isPhaseTransitioning() || isRoaring() || isDyingAnimation()) {
            return false;
        }

        double horizontalX = target.getX() - getX();
        double horizontalZ = target.getZ() - getZ();
        double horizontalDistanceSqr = horizontalX * horizontalX + horizontalZ * horizontalZ;
        double threshold = currentlyCombatWalking || openingRunAttackCompleted
                ? COMBAT_WALK_EXIT_RANGE
                : COMBAT_WALK_ENTER_RANGE;
        if (horizontalDistanceSqr > threshold * threshold
                || Math.abs(target.getY() - getY()) > COMBAT_WALK_MAX_VERTICAL_DIFFERENCE) {
            openingRunAttackCompleted = false;
            return false;
        }

        return openingRunAttackCompleted;
    }

    @Override
    protected void setUsingCombatWalk(boolean usingCombatWalk) {
        if (!level().isClientSide()) {
            if (!usingCombatWalk && (getTarget() == null || !isAggressive())) {
                openingRunAttackCompleted = false;
            }
            int locomotion = usingCombatWalk
                    ? LOCOMOTION_COMBAT_WALK
                    : getTarget() != null && isAggressive()
                            ? LOCOMOTION_RUN
                            : getNavigation().isDone() ? LOCOMOTION_IDLE : LOCOMOTION_WALK;
            entityData.set(DATA_LORD_LOCOMOTION, locomotion);
        }
    }

    @Override
    protected boolean usesRunningTurnSmoothing() {
        int locomotion = entityData.get(DATA_LORD_LOCOMOTION);
        return locomotion == LOCOMOTION_RUN || locomotion == LOCOMOTION_COMBAT_WALK;
    }

    public boolean shouldClampCombatHeadRotation() {
        return isAggressive() && entityData.get(DATA_LORD_ATTACK_ID) == 0;
    }

    @Override
    protected double getCombatWalkSpeedModifier() {
        return KING_COMBAT_WALK_SPEED;
    }

    @Override
    protected boolean shouldHoldCombatSpacing(LivingEntity target) {
        double x = target.getX() - getX();
        double z = target.getZ() - getZ();
        double combinedRadius = (getBbWidth() + target.getBbWidth()) * 0.5;
        double preferredCenterDistance = combinedRadius + COMBAT_WALK_TARGET_GAP;
        return x * x + z * z <= preferredCenterDistance * preferredCenterDistance
                && Math.abs(target.getY() - getY()) <= COMBAT_WALK_MAX_VERTICAL_DIFFERENCE;
    }

    @Override
    protected boolean usesPolishedMeleePursuit() {
        return true;
    }

    @Override
    protected int getPathRecalculationTicks() {
        return 8 + getRandom().nextInt(5);
    }

    private boolean isMidSwing() {
        return activeLordAttackId != 0 && attackAnimationTicks > 0;
    }

    private boolean isAirborneShockwaveDodge(LivingEntity target) {
        return !target.onGround();
    }

    @Override
    protected void applyAttackHitExtras(LivingEntity target, MeleeAttackHit hit) {
        // Ground shockwaves launch vertically without replacing the target's horizontal motion.
        // Direct contact with a directionally moving weapon keeps its authored knockback.
        super.applyAttackHitExtras(target,
                hit.blockRippleParticles() ? hit.withoutHorizontalKnockback() : hit);
    }

    @Override
    protected void applyGroundRippleHit(LivingEntity target, MeleeAttackHit hit, GroundRippleProfile profile) {
        if (profile.kind() == GroundRippleKind.STANDARD) {
            super.applyGroundRippleHit(target, hit, profile);
            return;
        }

        float damageMultiplier;
        float bonusDamage;
        if (profile.kind() == GroundRippleKind.KING_CLUB_DOUBLE) {
            damageMultiplier = CLUB_DOUBLE_OVERHEAD_DAMAGE_MULTIPLIER * 0.5F;
            bonusDamage = Config.OGRE_LORD_CLUB_DOUBLE_OVERHEAD_BONUS_DAMAGE.get().floatValue() * 0.5F;
        } else if (profile.kind() == GroundRippleKind.KING_CLUB_SINGLE) {
            damageMultiplier = SINGLE_OVERHEAD_DAMAGE_MULTIPLIER * 0.5F;
            bonusDamage = Config.OGRE_LORD_SINGLE_OVERHEAD_BONUS_DAMAGE.get().floatValue() * 0.5F;
        } else {
            damageMultiplier = PULL_OUT_DAMAGE_MULTIPLIER * 0.5F;
            bonusDamage = Config.OGRE_LORD_PULL_OUT_BONUS_DAMAGE.get().floatValue() * 0.5F;
        }
        if (dealClubDamage(target, damageMultiplier, bonusDamage)) {
            applyHeavyClubHitEffects(target);
            applyVerticalShockwaveLaunch(target, hit.verticalKnockbackStrength());
        }
    }

    @Override
    protected void applyDirectShockwaveWeaponHits(List<LivingEntity> targets, Vec3 areaCenter,
                                                  MeleeAttackHit hit) {
        if (activeLordAttackId != 2 && activeLordAttackId != 3 && activeLordAttackId != 8) {
            return;
        }

        // The unarmed Double Overhead Slam is a big enough hit to want the same dust density as
        // the club slams — its own screenshake still comes from the standard shared hit pipeline,
        // so this only adds the extra particles, not a second shake packet.
        if (activeLordAttackId == 3 && level() instanceof ServerLevel serverLevel) {
            spawnDenseDustCloud(serverLevel, areaCenter, DOUBLE_OVERHEAD_SLAM_AREA_RADIUS);
        }

        // Double Stomp's radius (11.25) is large enough that the shared shockwave call's center
        // burst and edge ring leave a visible gap between them — Single Leg Stomp's smaller radius
        // (6.5) doesn't show this gap, which is why only it currently reads as a dense-center,
        // spread-to-the-edge cloud. This layers extra fill bands into that gap per impact without
        // touching the existing edge ring/its radius.
        if (activeLordAttackId == 2 && level() instanceof ServerLevel serverLevel) {
            spawnGraduatedDustFill(serverLevel, areaCenter, DOUBLE_STOMP_AREA_RADIUS);
        }

        // Double Stomp has two separately authored impacts. Its first ripple has completed before
        // the second foot lands, so begin fresh per-impact tracking and allow the second foot to
        // connect independently just as it did before this direct-hit audit.
        directShockwaveWeaponHitTargets.clear();

        // Match the visible attacking surface at the authored ground-impact center. Directly
        // struck targets are remembered so the traveling ripple cannot deal a duplicate hit.
        double directRadius = activeLordAttackId == 3 ? 3.25 : 2.1;
        double directTop = activeLordAttackId == 3 ? getY() + 6.5 : getY() + 3.25;
        AABB directImpactVolume = new AABB(
                areaCenter.x - directRadius, getY() - 1.0, areaCenter.z - directRadius,
                areaCenter.x + directRadius, directTop, areaCenter.z + directRadius);
        for (LivingEntity target : targets) {
            if (!target.getBoundingBox().intersects(directImpactVolume)
                    || !directShockwaveWeaponHitTargets.add(target.getUUID())) {
                continue;
            }

            float damageMultiplier = target instanceof OgreGrunt
                    ? hit.damageMultiplier() * 0.5F
                    : hit.damageMultiplier();
            if (doScaledHurtTarget(target, damageMultiplier, hit.armorPierceFraction())) {
                applyAttackHitExtras(target, hit.withoutHorizontalKnockback());
            }
        }
    }

    @Override
    protected boolean shouldDodgeRippleHit(LivingEntity target) {
        if (!isPhaseTransitioning() && activeLordAttackId != 2 && activeLordAttackId != 3 && activeLordAttackId != 8
                && activeLordAttackId != 10 && activeLordAttackId != 11) {
            return false;
        }

        // Stomps and both unarmed/club overhead slams are ground shockwaves.
        // Jumping at the right moment so the ripple passes underneath is the intended dodge.
        // Either half of the jump counts as long as the target is airborne when the wave arrives.
        return directShockwaveWeaponHitTargets.contains(target.getUUID())
                || isAirborneShockwaveDodge(target)
                || hasRecentIntentionalJump(target, 2);
    }


    @Override
    protected double getMeleePursuitSpeedModifier() {
        return 1.0;
    }

    @Override
    protected double getWanderSpeedModifier() {
        return 0.4;
    }

    @Override
    protected void snapFaceTarget(LivingEntity target) {
        // The default hard-snaps to face the target the instant an attack starts. On a giant model
        // that can whip through a huge arc in one frame if the target has circled around — cap how
        // far a single snap can turn so it reads as a quick turn instead of a spin.
        double dx = target.getX() - getX();
        double dz = target.getZ() - getZ();
        float desiredYaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
        float yaw = Mth.rotateIfNecessary(getYRot(), desiredYaw, SNAP_FACE_MAX_TURN_DEGREES);
        setYRot(yaw);
        setYBodyRot(yaw);
        setYHeadRot(yaw);
        yRotO = yaw;
        yBodyRotO = yaw;
        yHeadRotO = yaw;
    }

    @Override
    protected boolean suppressShockwaveSound() {
        // The generic explosion sound is played explicitly in playDoubleStompImpactSound()/
        // playDoubleOverheadSlamImpactSound() alongside the reused belly-slam impact — suppress the
        // shared inherited one here so it doesn't also fire and double up.
        return true;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return ModSoundEvents.OGRE_LORD_HURT.get();
    }

    @Override
    public boolean hurtServer(ServerLevel serverLevel, DamageSource source, float amount) {
        if (isFortEncounterDormant() || isDyingAnimation()) {
            return false;
        }
        if (isPhaseTransitioning()) {
            amount *= 0.5F;
        }
        if (source.getDirectEntity() instanceof AbstractArrow) {
            amount *= 0.5F;
        }

        boolean hurt = super.hurtServer(serverLevel, source, amount);
        if (hurt && isAlive() && !isPhaseTwo()
                && getHealth() <= getMaxHealth() * PHASE_TWO_HEALTH_THRESHOLD) {
            enterPhaseTwo();
        }
        return hurt;
    }

    @Override
    public void knockback(double strength, double x, double z) {
        if (isFortEncounterDormant() || isPhaseTransitioning() || isDyingAnimation()) {
            return;
        }
        super.knockback(strength, x, z);
    }

    @Override
    public boolean isPushable() {
        return !isFortEncounterDormant() && super.isPushable();
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        clearRoyalTrialBossBarPlayers();
        kingBossBar.removeAllPlayers();
        super.remove(reason);
    }

    @Override
    public void die(DamageSource source) {
        if (isDyingAnimation()) {
            return;
        }

        delayedDeathSource = source;
        entityData.set(DATA_LORD_DYING, true);
        entityData.set(DATA_LORD_TRANSITIONING, false);
        cancelActiveMeleeAttack();
        stopRoarImmediately();
        stopSummonImmediately();
        setTarget(null);
        setAggressive(false);
        lockSpecialAnimationPose();
        setNoAi(true);
        deathTime = 0;
        playAttackAnimation(12);
    }

    @Override
    protected void tickDeath() {
        if (!isDyingAnimation()) {
            super.tickDeath();
            return;
        }

        // lockedSpecialX/Z are authoritative server-only state. Freezing with them on the client
        // uses unsynchronised default coordinates and can visually teleport an interrupted death
        // toward world origin. Server position packets keep the client fixed in the correct spot.
        if (!level().isClientSide()) {
            freezeSpecialAnimationPose();
        }
        deathTime++;
        if (deathTime < DEATH_ANIMATION_TICKS) {
            return;
        }

        DamageSource source = delayedDeathSource != null
                ? delayedDeathSource
                : damageSources().genericKill();
        entityData.set(DATA_LORD_DYING, false);
        delayedDeathSource = null;
        super.die(source);
        deathTime = 19;
        super.tickDeath();
    }

    @Override
    public float getVoicePitch() {
        // Was pinned to a flat 1.0F, which made every hurt grunt sound identical. Vary it a bit
        // (and skew a little deeper, to suit his size) instead of using vanilla's default 1.0-centered range.
        return 0.85F + getRandom().nextFloat() * 0.2F;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new SynchronizedAnimationController<>(this, "Lord Movement", 5, this::animateLord));
    }

    private PlayState animateLord(AnimationTest<OgreLord> state) {
        int fortEncounterState = entityData.get(DATA_FORT_ENCOUNTER_STATE);
        lastRenderedFortEncounterState = fortEncounterState;
        if (isDyingAnimation()) {
            state.setControllerSpeed(1.0F);
            if (visualDeathStartGameTime == Long.MIN_VALUE) {
                long syncedStart = entityData.get(DATA_LORD_ATTACK_ID) == 12
                        ? entityData.get(DATA_LORD_ATTACK_START_TICK) : 0L;
                visualDeathStartGameTime = syncedStart > 0L
                        ? syncedStart : Math.max(1L, level().getGameTime() - deathTime);
            }
            visualOneShot.updateTerminal(state.controller(), 12, visualDeathStartGameTime,
                    DEATH_ANIMATION_TICKS, level().getGameTime());
            return state.setAndContinue(DEATH);
        }
        if (fortEncounterState == FORT_ENCOUNTER_SLEEPING) {
            state.setControllerSpeed(1.0F);
            return state.setAndContinue(SLEEPING);
        }
        int syncedAttackId = entityData.get(DATA_LORD_ATTACK_ID);
        long syncedAttackStartTick = entityData.get(DATA_LORD_ATTACK_START_TICK);
        int visualAttackId;
        if (entityData.get(DATA_LORD_TRANSITIONING) && syncedAttackId == 9) {
            // Pull-Out is a forced phase-transition override, exactly like death: it must cut off
            // whatever attack animation was already playing rather than wait for that animation's
            // own natural duration to elapse (the normal update() immutability rule that protects
            // every other attack from being preempted early). Same escape hatch death already uses.
            visualAttackId = visualOneShot.updateTerminal(state.controller(), 9, syncedAttackStartTick,
                    getAttackAnimationTicks(9), level().getGameTime());
        } else {
            visualAttackId = visualOneShot.update(state.controller(), syncedAttackId, syncedAttackStartTick,
                    this::getVisualAttackAnimationTicks, level().getGameTime());
        }
        if (visualAttackId > 0) {
            state.setControllerSpeed(1.0F);
            return state.setAndContinue(getAttackAnimation(visualAttackId));
        }

        int locomotion = entityData.get(DATA_LORD_LOCOMOTION);
        if (locomotion == LOCOMOTION_RUN || locomotion == LOCOMOTION_COMBAT_WALK) {
            idleAnimationTicks = 0;
            idleVariantTicks = 0;
            if (isMovingForAnimation(state)) {
                // Only scale playback speed in water — on dry land this must stay a flat 1.0F, since
                // the ratio against Attributes.MOVEMENT_SPEED doesn't land cleanly at 1.0 for his
                // normal run pace and was quietly slowing down every land chase.
                state.setControllerSpeed(isInWater() ? computeMovementAnimSpeed() : 1.0F);
                return state.setAndContinue(
                        locomotion == LOCOMOTION_COMBAT_WALK ? COMBAT_WALK : RUN);
            }
            state.setControllerSpeed(1.0F);
            return state.setAndContinue(IDLE);
        }

        if (locomotion == LOCOMOTION_WALK && isMovingForAnimation(state)) {
            idleAnimationTicks = 0;
            idleVariantTicks = 0;
            state.setControllerSpeed(isInWater() ? computeMovementAnimSpeed() : 1.0F);
            return state.setAndContinue(WALK);
        }

        state.setControllerSpeed(1.0F);
        updateIdleAnimationTimers(state);
        if (idleVariantTicks > 0) {
            return state.setAndContinue(activeIdleAnimation);
        }
        return state.setAndContinue(IDLE);
    }

    private boolean isMovingForAnimation(AnimationTest<OgreLord> state) {
        boolean moving = hasLocomotionMotion();
        if (moving) {
            movementAnimationGraceTicks = locomotionGraceTicks();
            lastMovementGraceTick = tickCount;
            return true;
        }
        if (movementAnimationGraceTicks > 0) {
            // This grace window bridges the 1-2 tick gap between the combat goal stopping
            // navigation (which zeroes the movement predicate immediately) and the synced attack
            // ID/timestamp reaching the client so the one-shot can take over. It is a *tick*
            // budget, but this predicate runs once per render frame, so decrementing it here
            // unconditionally burned all four units inside a single game tick at normal
            // framerates, leaving a hole where the mob fell through to IDLE and then re-armed to
            // a run-in-place from residual velocity before the action animation became active.
            // Gate the decrement on the game tick, the same way the idle timers already do.
            if (lastMovementGraceTick != tickCount) {
                int tickDelta = lastMovementGraceTick < 0 ? 1 : Math.max(1, tickCount - lastMovementGraceTick);
                boolean expiredUnobserved = tickDelta > movementAnimationGraceTicks;
                lastMovementGraceTick = tickCount;
                movementAnimationGraceTicks = Math.max(0, movementAnimationGraceTicks - tickDelta);
                if (expiredUnobserved) {
                    // The predicate was not evaluated at all for longer than the remaining budget
                    // (the one-shot branch returns before this method for the whole animation), so
                    // the stale grace would report "moving" for one frame right as the one-shot
                    // releases. The window really did elapse — report it as elapsed.
                    return false;
                }
            }
            return true;
        }
        lastMovementGraceTick = tickCount;
        return false;
    }

    @Override
    protected void onMeleeAttackPlanFinished() {
        boolean completedOpeningAttack = !level().isClientSide()
                && activeLordAttackId != 0
                && !isRoaring()
                && !isPhaseTransitioning()
                && !isDyingAnimation();
        if (!isRoaring() && !isPhaseTransitioning() && !isDyingAnimation()) {
            activeLordAttackId = 0;
        }
        super.onMeleeAttackPlanFinished();
        if (completedOpeningAttack) {
            openingRunAttackCompleted = true;
        }
        if (!level().isClientSide() && !isPhaseTransitioning() && !isDyingAnimation()) {
            clearLordAttackSync();
        }
    }

    private void playAttackAnimation(int attackId) {
        directShockwaveWeaponHitTargets.clear();
        if (!level().isClientSide()) {
            if (attackId >= 1 && attackId <= 11) {
                secondLastSelectedAttackId = lastSelectedAttackId;
                lastSelectedAttackId = attackId;
            }
            entityData.set(DATA_LORD_ATTACK_ID, attackId);
            entityData.set(DATA_LORD_ATTACK_START_TICK, level().getGameTime());
        }

        attackAnimationTicks = getAttackAnimationTicks(attackId);
        activeLordAttackId = attackId;
    }

    private RawAnimation getAttackAnimation(int attackId) {
        return switch (attackId) {
            case 2 -> DOUBLE_STOMP;
            case 3 -> DOUBLE_OVERHEAD_SLAM;
            case 4 -> ROAR;
            case 5 -> BELLY_BUMP;
            case 6 -> DOUBLE_ARM_UPSWING;
            case 7 -> CLUB_HEAD_THRUST;
            case 8 -> SINGLE_LEG_STOMP;
            case 9 -> PULL_OUT;
            case 10 -> CLUB_DOUBLE_OVERHEAD;
            case 11 -> SINGLE_OVERHEAD_SLAM;
            case 12 -> DEATH;
            case 13 -> SUMMON_POINT;
            case 14 -> WAKE_UP;
            default -> ONE_ARM_UPSWING;
        };
    }

    /**
     * Client-visual duration for the synchronized one-shot: the authored clip length plus the
     * controller's leading transition, which the elapsed clock includes. WAKE_UP is excluded
     * because WAKE_UP_ANIMATION_TICKS already carries that padding explicitly.
     */
    private int getVisualAttackAnimationTicks(int attackId) {
        return attackId == 14
                ? WAKE_UP_ANIMATION_TICKS
                : getAttackAnimationTicks(attackId) + ONE_SHOT_TRANSITION_TICKS;
    }

    private int getAttackAnimationTicks(int attackId) {
        return switch (attackId) {
            case 2 -> DOUBLE_STOMP_ANIMATION_TICKS;
            case 3 -> DOUBLE_OVERHEAD_SLAM_ANIMATION_TICKS;
            case 4 -> ROAR_ANIMATION_TICKS;
            case 5 -> BELLY_BUMP_ANIMATION_TICKS;
            case 6 -> DOUBLE_ARM_UPSWING_ANIMATION_TICKS;
            case 7 -> CLUB_HEAD_THRUST_ANIMATION_TICKS;
            case 8 -> SINGLE_LEG_STOMP_ANIMATION_TICKS;
            case 9 -> PULL_OUT_ANIMATION_TICKS;
            case 10 -> CLUB_DOUBLE_OVERHEAD_ANIMATION_TICKS;
            case 11 -> SINGLE_OVERHEAD_SLAM_ANIMATION_TICKS;
            case 12 -> DEATH_ANIMATION_TICKS;
            case 13 -> SUMMON_POINT_ANIMATION_TICKS;
            case 14 -> WAKE_UP_ANIMATION_TICKS;
            default -> ONE_ARM_UPSWING_ANIMATION_TICKS;
        };
    }

    private void updateIdleAnimationTimers(AnimationTest<OgreLord> state) {
        int tickDelta = updateLastAnimationTick();
        if (tickDelta == 0) {
            return;
        }

        if (idleVariantTicks > 0) {
            idleVariantTicks = Math.max(0, idleVariantTicks - tickDelta);
            if (idleVariantTicks <= 0) {
                activeIdleAnimation = IDLE;
                state.controller().reset();
            }
            return;
        }

        idleAnimationTicks += tickDelta;
        if (idleAnimationTicks >= 160 && getRandom().nextInt(180) == 0) {
            idleAnimationTicks = 0;
            idleVariantTicks = IDLE_VARIANT_TICKS;
            activeIdleAnimation = IDLE_2;
            state.controller().reset();
        }
    }

    private int updateLastAnimationTick() {
        if (lastAnimationTick == tickCount) {
            return 0;
        }

        int tickDelta = lastAnimationTick < 0 ? 1 : Math.max(1, tickCount - lastAnimationTick);
        lastAnimationTick = tickCount;
        return tickDelta;
    }
}
