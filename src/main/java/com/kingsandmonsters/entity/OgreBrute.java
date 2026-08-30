package com.kingsandmonsters.entity;

import com.kingsandmonsters.Config;
import com.kingsandmonsters.ModMobEffects;
import com.kingsandmonsters.effect.CombatEffects;
import com.kingsandmonsters.ModSoundEvents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.kingsandmonsters.entity.animation.SynchronizedAnimationController;
import com.kingsandmonsters.entity.animation.CanonicalOneShotState;
import com.geckolib.animation.state.AnimationTest;
import com.geckolib.animation.object.PlayState;
import com.geckolib.animation.RawAnimation;

import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class OgreBrute extends OgreGrunt {
    private static final String TANTRUM_MILESTONE_QUEUED_TAG = "TemperTantrumMilestoneQueued";
    private static final String TANTRUM_MILESTONE_CONSUMED_TAG = "TemperTantrumMilestoneConsumed";
    private static final int AMBIENT_HUFF_MIN_COOLDOWN_TICKS = 600;
    private static final int AMBIENT_HUFF_RANDOM_COOLDOWN_TICKS = 500;
    private static final int AMBIENT_HUFF_CHANCE = 80;
    private static final int LEVEL_15_XP_REWARD = 315;

    @Override
    protected float getStepPitchMultiplier() {
        return 0.72F;
    }

    @Override
    protected float getStepVolumeMultiplier() {
        return 1.9F;
    }

    @Override
    protected int getMinimumStepSoundIntervalTicks() {
        return 8;
    }
    private static final EntityDataAccessor<Integer> DATA_BRUTE_ATTACK_ID =
            SynchedEntityData.defineId(OgreBrute.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> DATA_BRUTE_ATTACK_START_TICK =
            SynchedEntityData.defineId(OgreBrute.class, EntityDataSerializers.LONG);
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation IDLE_2 = RawAnimation.begin().thenLoop("idle2");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation RUN = RawAnimation.begin().thenLoop("run");
    private static final RawAnimation ATTACK = RawAnimation.begin().thenPlay("attack");
    private static final RawAnimation SIDE_SWIPE = RawAnimation.begin().thenPlay("side_swipe");
    private static final RawAnimation UPSWING_LAUNCH = RawAnimation.begin().thenPlay("upswing_launch");
    private static final RawAnimation BELLY_BUMP = RawAnimation.begin().thenPlay("belly_bump");
    private static final RawAnimation BELLY_SLAM = RawAnimation.begin().thenPlay("belly_slam");
    private static final RawAnimation TEMPER_TANTRUM = RawAnimation.begin().thenPlay("temper_tantrum");
    private static final int IDLE_VARIANT_TICKS = 40;
    private static final int WALK_CYCLE_TICKS = 40;
    private static final int WALK_VARIANT_MIN_CYCLES = 3;
    private static final int ATTACK_ANIMATION_TICKS = 40;
    // Matches the "Brute Movement" controller's transition length. SynchronizedAnimationController
    // deliberately keeps GeckoLib 5's leading transition stage inside the elapsed clock, so elapsed
    // tick N is clip-local tick N-5 and expiring the visual one-shot at the raw clip length cut its
    // last 5 ticks off. Applied ONLY at the animation call site: getAttackAnimationTicks() also
    // feeds the server-side attackAnimationTicks counter that gates hits, dodges and facing locks,
    // so that method must keep returning the true gameplay duration.
    private static final int ONE_SHOT_TRANSITION_TICKS = 5;
    // ogre_brute.animation.json authors "side_swipe" at 1.25s. This was 30 ticks, five ticks past
    // the end of the clip, so every side swipe froze on its last frame before releasing back to
    // locomotion — the attack visibly stalling and then cutting away part-played.
    private static final int SIDE_SWIPE_ANIMATION_TICKS = 25;
    private static final int UPSWING_LAUNCH_ANIMATION_TICKS = 35;
    private static final int BELLY_BUMP_ANIMATION_TICKS = 30;
    // jump smash animation_length grew from 2.75s to 3.25s (a uniform +0.5s
    // windup added before everything else, confirmed keyframe-by-keyframe),
    // so every tick offset tied to its timeline below shifts by +10 ticks.
    private static final int BELLY_SLAM_ANIMATION_TICKS = 65;
    private static final int TEMPER_TANTRUM_ANIMATION_TICKS = 105;
    // Authored contact keyframes: 1.4167s, 2.1667s, 3.1667s.
    private static final int TEMPER_TANTRUM_FIRST_IMPACT_TICK = 28;
    private static final int TEMPER_TANTRUM_SECOND_IMPACT_TICK = 43;
    private static final int TEMPER_TANTRUM_FINAL_IMPACT_TICK = 63;
    private static final double TEMPER_TANTRUM_HAND_FORWARD_OFFSET = 1.65;
    private static final double TEMPER_TANTRUM_HAND_SIDE_OFFSET = 1.15;
    private static final double TEMPER_TANTRUM_FINAL_FORWARD_OFFSET = 1.8;
    private static final double TEMPER_TANTRUM_MINI_DIRECT_HALF_WIDTH = 1.25;
    private static final double TEMPER_TANTRUM_FINAL_DIRECT_HALF_WIDTH = 1.9;
    private static final double TEMPER_TANTRUM_DIRECT_HEIGHT = 3.1;
    private static final double TEMPER_TANTRUM_MINI_VERTICAL_LAUNCH = 0.25;
    private static final double TEMPER_TANTRUM_FINAL_VERTICAL_LAUNCH = 1.38;
    private static final int RIPPLE_JUMP_GRACE_TICKS = 2;
    private static final int FORCED_MINI_LAUNCH_DODGE_SUPPRESSION_TICKS = 24;
    // Impact ticks below are read off ogre_brute.animation.json: "attack" reaches its punch extreme
    // at 1.2083s, "side_swipe" at 0.5417s, "upswing_launch" at 0.9167s, "belly_bump" at 0.9167s and
    // "belly_slam" at 1.875s (times 20 ticks/s = 24.2, 10.8, 18.3, 18.3 and 37.5 ticks).
    //
    // These used to carry an extra +3 ("ATTACK_SYNC_DELAY_TICKS") to compensate for the client
    // starting the clip late. That lag no longer exists: SynchronizedAnimationController seeks a
    // newly observed one-shot to the exact server-synced start tick, so the stale padding was
    // landing every impact sound/hit ~3 ticks after the swing had already reached contact.
    private static final int PUNCH_IMPACT_SOUND_DELAY_TICKS = 23;
    private static final int SIDE_SWIPE_IMPACT_SOUND_DELAY_TICKS = 10;
    private static final int UPSWING_IMPACT_DELAY_TICKS = 18;
    private static final int UPSWING_WHOOSH_SOUND_DELAY_TICKS = UPSWING_IMPACT_DELAY_TICKS;
    private static final int BELLY_SLAM_GRUNT_SOUND_DELAY_TICKS = 23;
    private static final int BELLY_SLAM_IMPACT_SOUND_DELAY_TICKS = 38;
    private static final int BELLY_BUMP_COOLDOWN_TICKS = 180;
    private static final int BELLY_SLAM_COOLDOWN_TICKS = 600;
    private static final int TEMPER_TANTRUM_COOLDOWN_TICKS = 560;
    private static final int TEMPER_TANTRUM_WHINE_DELAY_TICKS = 5;
    private static final int BELLY_BUMP_CLOSE_PRESSURE_TICKS = 40;
    private static final double BELLY_BUMP_CLOSE_PRESSURE_RANGE = 3.65;
    private static final double REGULAR_ATTACK_START_RANGE = 3.7;
    private static final double BELLY_SLAM_RANGE = 7.0;
    private static final double BELLY_SLAM_MIN_RANGE = 3.25;
    private static final double TEMPER_TANTRUM_START_RANGE = 6.25;
    private static final double TEMPER_TANTRUM_SLOWNESS_RADIUS = 7.0;
    private static final double ATTACK_AOE_SCALE = 1.2;
    private static final double ATTACK_KNOCKBACK_SCALE = 1.2;
    private static final int PUNCH_RECOVERY_TICKS = 50;
    private static final int SIDE_SWIPE_RECOVERY_TICKS = 48;
    private static final int UPSWING_RECOVERY_TICKS = 48;
    private static final int BELLY_BUMP_RECOVERY_TICKS = 62;
    private static final int SIDE_SWIPE_ADVANCE_DELAY_TICKS = 11;
    private static final int SIDE_SWIPE_ADVANCE_TICKS = 3;
    private static final double SIDE_SWIPE_ADVANCE_SPEED = 0.045;
    private static final int PUNCH_ADVANCE_DELAY_TICKS = 24;
    private static final int PUNCH_ADVANCE_TICKS = 2;
    private static final double PUNCH_ADVANCE_SPEED = 0.45;
    private static final int PUNCH_TRACKING_TICKS = PUNCH_ADVANCE_DELAY_TICKS - 1;
    private static final float PUNCH_TRACKING_MAX_TURN_PER_TICK = 1.5F;
    // Multipliers against the 11.0 base ATTACK_DAMAGE attribute. Diamond armor's reduction curve eats most
    // flat damage, so these are calibrated against full diamond armor (20 armor / 8 toughness, no pierce).
    // Deliberately scaled down (~0.65x of the old values) so he reads as noticeably weaker than OgreLord,
    // whose moves land 3-4 hearts vs the same armor: punch ~= 3.2 hearts effective, side swipe/upswing/
    // belly bump ~= 2.3 hearts effective.
    private static final float PUNCH_DAMAGE_MULTIPLIER = 1.6F;
    private static final float SIDE_SWIPE_DAMAGE_MULTIPLIER = 1.2F;
    private static final float UPSWING_LAUNCH_DAMAGE_MULTIPLIER = 1.2F;
    private static final float BELLY_BUMP_DAMAGE_MULTIPLIER = 1.2F;
    // Temper tantrum's mini slams (15% pierce) ~= 1 heart effective vs full diamond each;
    // the final big slam (35% pierce) ~= 2 hearts effective vs full diamond.
    private static final float TEMPER_TANTRUM_MINI_SLAM_DAMAGE_MULTIPLIER = 0.5F;
    private static final float TEMPER_TANTRUM_BIG_SLAM_DAMAGE_MULTIPLIER = 0.65F;
    private static final float TEMPER_TANTRUM_MINI_DIRECT_DAMAGE_MULTIPLIER = 1.0F;
    private static final float TEMPER_TANTRUM_FINAL_DIRECT_DAMAGE_MULTIPLIER = 1.3F;
    // Belly slam (the jump attack) — also scaled down from the old 2.25F, to stay in line with the rest.
    private static final float BELLY_SLAM_DAMAGE_MULTIPLIER = 1.5F;

    private int idleAnimationTicks;
    private int idleVariantTicks;
    private int attackAnimationTicks;
    private int bellyBumpCooldownTicks;
    private int bellySlamCooldownTicks;
    private int temperTantrumCooldownTicks;
    private int bellySlamTakeoffDelayTicks;
    private int bellySlamFlightTicks;
    private int bellySlamFacingLockTicks;
    private int temperTantrumWhineDelayTicks = -1;
    private int punchImpactSoundDelayTicks = -1;
    private int sideSwipeImpactSoundDelayTicks = -1;
    private int upswingWhooshSoundDelayTicks = -1;
    private int bellySlamGruntSoundDelayTicks = -1;
    private int bellySlamImpactSoundDelayTicks = -1;
    private int activeBruteAttackId;
    private int closePressureTicks;
    private int ambientHuffCooldownTicks = AMBIENT_HUFF_MIN_COOLDOWN_TICKS;
    private int lastAnimationTick = -1;
    private int movementAnimationGraceTicks;
    private int lastMovementGraceTick = -1;
    private final CanonicalOneShotState visualOneShot = new CanonicalOneShotState();
    private RawAnimation activeIdleAnimation = IDLE;
    private LivingEntity bellySlamTarget;
    private Vec3 bellySlamDirection = Vec3.ZERO;
    private Vec3 sideSwipeAdvanceDirection = Vec3.ZERO;
    private int sideSwipeAdvanceDelayTicks;
    private float bellySlamYaw;
    private int sideSwipeAdvanceTicks;
    private Vec3 punchAdvanceDirection = Vec3.ZERO;
    private int punchAdvanceDelayTicks;
    private int punchAdvanceTicks;
    private int punchTrackingTicks;
    private final Set<UUID> pendingTemperTantrumRippleExclusions = new HashSet<>();
    private boolean temperTantrumMilestoneQueued;
    private boolean temperTantrumMilestoneConsumed;

    public OgreBrute(EntityType<? extends OgreBrute> type, Level level) {
        super(type, level);
        ensureCaptainTitle();
        avoidWaterPathfinding();
        xpReward = LEVEL_15_XP_REWARD;
    }

    private void ensureCaptainTitle() {
        if (!hasCustomName()) {
            setCustomName(Component.translatable("title.kingsandmonsters.brute_captain"));
        }
        setCustomNameVisible(true);
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput tag) {
        super.readAdditionalSaveData(tag);
        temperTantrumMilestoneQueued = tag.getBooleanOr(TANTRUM_MILESTONE_QUEUED_TAG, false);
        temperTantrumMilestoneConsumed = tag.getBooleanOr(TANTRUM_MILESTONE_CONSUMED_TAG, false);
        ensureCaptainTitle();
    }

    @Override
    public void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean(TANTRUM_MILESTONE_QUEUED_TAG, temperTantrumMilestoneQueued);
        tag.putBoolean(TANTRUM_MILESTONE_CONSUMED_TAG, temperTantrumMilestoneConsumed);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 120.0)
                .add(Attributes.ATTACK_DAMAGE, 11.0)
                .add(Attributes.MOVEMENT_SPEED, 0.325)
                .add(Attributes.FOLLOW_RANGE, 30.0)
                .add(Attributes.ARMOR, 18.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.9)
                .add(Attributes.ATTACK_KNOCKBACK, 1.5)
                .add(Attributes.STEP_HEIGHT, 1.2)
                .add(Attributes.SAFE_FALL_DISTANCE, 5.0)
                // See OgreGrunt#createAttributes — default 0 caps water acceleration at a tiny fixed
                // baseline regardless of requested speed, which would make applyShallowWaterSpeedFactor's
                // 50% reduction meaningless since he's already moving slower than that.
                .add(Attributes.WATER_MOVEMENT_EFFICIENCY, 1.0);
    }

    @Override
    public boolean hurtServer(ServerLevel serverLevel, DamageSource source, float amount) {
        // Retain a small piece of the removed defensive phase as a passive trait:
        // every projectile hit independently has an exact one-in-three chance to
        // be rejected. Returning false also lets arrows use their normal failed-hit
        // response instead of embedding in and damaging the brute.
        if (!level().isClientSide()
                && source.is(DamageTypeTags.IS_PROJECTILE)
                && getRandom().nextInt(3) == 0) {
            level().playSound(
                    null,
                    getX(), getY() + getBbHeight() * 0.55, getZ(),
                    SoundEvents.SHIELD_BLOCK,
                    SoundSource.HOSTILE,
                    1.0F,
                    0.65F + getRandom().nextFloat() * 0.1F);
            return false;
        }

        return super.hurtServer(serverLevel, source, amount);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_BRUTE_ATTACK_ID, 0);
        builder.define(DATA_BRUTE_ATTACK_START_TICK, 0L);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!level().isClientSide()) {
            if (!temperTantrumMilestoneConsumed && getHealth() <= getMaxHealth() * 0.5F) {
                temperTantrumMilestoneQueued = true;
            }
            tickAmbientHuff();
            bellyBumpCooldownTicks = Math.max(bellyBumpCooldownTicks - 1, 0);
            bellySlamCooldownTicks = Math.max(bellySlamCooldownTicks - 1, 0);
            temperTantrumCooldownTicks = Math.max(temperTantrumCooldownTicks - 1, 0);
            tickTemperTantrumWhineSound();
            tickTemperTantrumSlowness();
            tickDelayedImpactSounds();
            tickBellySlamFlight();
            tickBellySlamFacingLock();
            tickSideSwipeAdvance();
            tickPunchTracking();
            tickPunchAdvance();

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
    }

    private void tickAmbientHuff() {
        if (ambientHuffCooldownTicks > 0) {
            ambientHuffCooldownTicks--;
            return;
        }
        if (getTarget() != null || isAggressive() || activeBruteAttackId != 0
                || getRandom().nextInt(AMBIENT_HUFF_CHANCE) != 0) {
            return;
        }

        level().playSound(null, getX(), getY(), getZ(),
                ModSoundEvents.OGRE_GRUNT_HUFF.get(), SoundSource.HOSTILE, 1.0F, 0.68F);
        ambientHuffCooldownTicks = AMBIENT_HUFF_MIN_COOLDOWN_TICKS
                + getRandom().nextInt(AMBIENT_HUFF_RANDOM_COOLDOWN_TICKS + 1);
    }

    @Override
    public void applyConfiguredCombatAttributes(boolean healToMax) {
        if (!Config.isLoaded()) {
            return;
        }

        setAttributeBaseValue(Attributes.MAX_HEALTH, Config.OGRE_BRUTE_MAX_HEALTH.get());
        setAttributeBaseValue(Attributes.ATTACK_DAMAGE, Config.OGRE_BRUTE_ATTACK_DAMAGE.get());
        setAttributeBaseValue(Attributes.MOVEMENT_SPEED, Config.OGRE_BRUTE_MOVEMENT_SPEED.get());
        setAttributeBaseValue(Attributes.FOLLOW_RANGE, Config.OGRE_BRUTE_FOLLOW_RANGE.get());
        setAttributeBaseValue(Attributes.ARMOR, Config.OGRE_BRUTE_ARMOR.get());
        applyConfiguredHealth(healToMax);
    }

    @Override
    protected void applyAttackHitExtras(LivingEntity target, MeleeAttackHit hit) {
        super.applyAttackHitExtras(target, hit);
        // Attack ids 3 and 4 are Belly Slam and Temper Tantrum respectively.
        if ((activeBruteAttackId == 3 || activeBruteAttackId == 4) && !(target instanceof OgreGrunt)) {
            CombatEffects.applyDazed(target, Config.DAZED_HEAVY_ATTACK_DURATION_TICKS.get(), this);
        }
    }

    private void beginPunchAdvance() {
        punchAdvanceDelayTicks = PUNCH_ADVANCE_DELAY_TICKS;
        punchAdvanceTicks = PUNCH_ADVANCE_TICKS;
        punchAdvanceDirection = Vec3.ZERO;
    }

    private void tickPunchAdvance() {
        if (activeBruteAttackId != 1) {
            punchAdvanceDelayTicks = 0;
            punchAdvanceTicks = 0;
            punchAdvanceDirection = Vec3.ZERO;
            return;
        }
        if (punchAdvanceDelayTicks > 0) {
            punchAdvanceDelayTicks--;
            if (punchAdvanceDelayTicks == 0) {
                punchAdvanceDirection = Vec3.directionFromRotation(0.0F, getYRot()).normalize();
            }
            return;
        }
        if (punchAdvanceTicks <= 0) {
            return;
        }
        punchAdvanceTicks--;
        Vec3 movement = getDeltaMovement();
        setDeltaMovement(punchAdvanceDirection.x * PUNCH_ADVANCE_SPEED,
                movement.y, punchAdvanceDirection.z * PUNCH_ADVANCE_SPEED);
    }

    private void tickPunchTracking() {
        if (activeBruteAttackId != 1 || punchTrackingTicks <= 0) {
            punchTrackingTicks = 0;
            return;
        }
        punchTrackingTicks--;
        LivingEntity target = getTarget();
        if (target == null) {
            return;
        }
        double dx = target.getX() - getX();
        double dz = target.getZ() - getZ();
        if (dx * dx + dz * dz < 1.0E-4) {
            return;
        }
        float desiredYaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
        float turn = Mth.clamp(Mth.wrapDegrees(desiredYaw - getYRot()),
                -PUNCH_TRACKING_MAX_TURN_PER_TICK, PUNCH_TRACKING_MAX_TURN_PER_TICK);
        float newYaw = getYRot() + turn;
        setYRot(newYaw);
        setYBodyRot(newYaw);
        setYHeadRot(newYaw);
    }

    private void beginSideSwipeAdvance() {
        sideSwipeAdvanceDirection = Vec3.directionFromRotation(0.0F, getYRot()).normalize();
        sideSwipeAdvanceDelayTicks = SIDE_SWIPE_ADVANCE_DELAY_TICKS;
        sideSwipeAdvanceTicks = SIDE_SWIPE_ADVANCE_TICKS;
    }

    private void tickSideSwipeAdvance() {
        if (activeBruteAttackId != 5) {
            sideSwipeAdvanceDelayTicks = 0;
            sideSwipeAdvanceTicks = 0;
            sideSwipeAdvanceDirection = Vec3.ZERO;
            return;
        }
        if (sideSwipeAdvanceDelayTicks > 0) {
            sideSwipeAdvanceDelayTicks--;
            return;
        }
        if (sideSwipeAdvanceTicks <= 0) {
            return;
        }
        sideSwipeAdvanceTicks--;
        Vec3 movement = getDeltaMovement();
        setDeltaMovement(
                sideSwipeAdvanceDirection.x * SIDE_SWIPE_ADVANCE_SPEED,
                movement.y,
                sideSwipeAdvanceDirection.z * SIDE_SWIPE_ADVANCE_SPEED);
    }

    @Override
    protected MeleeAttackPlan startMeleeAttack() {
        LivingEntity target = getTarget();
        if (canStartTemperTantrum(target)) {
            temperTantrumMilestoneQueued = false;
            temperTantrumMilestoneConsumed = true;
            playAttackAnimation(4);
            temperTantrumCooldownTicks = TEMPER_TANTRUM_COOLDOWN_TICKS;
            temperTantrumWhineDelayTicks = TEMPER_TANTRUM_WHINE_DELAY_TICKS;
            closePressureTicks = 0;
            getNavigation().stop();
            setDeltaMovement(0.0, getDeltaMovement().y, 0.0);
            warmUpRippleBlockVisual();
            return new MeleeAttackPlan(125, TEMPER_TANTRUM_ANIMATION_TICKS, List.of(
                    MeleeAttackHit.areaSpecial(TEMPER_TANTRUM_FIRST_IMPACT_TICK, TEMPER_TANTRUM_MINI_SLAM_DAMAGE_MULTIPLIER, (float) (6.0 * ATTACK_AOE_SCALE), true,
                                    0.15F, 0.0, 0.0F, TEMPER_TANTRUM_MINI_VERTICAL_LAUNCH, 0, 0, true)
                            .withAreaOffset(TEMPER_TANTRUM_HAND_FORWARD_OFFSET, -TEMPER_TANTRUM_HAND_SIDE_OFFSET)
                            .withVerticalHitRange(5.0)
                            .withScreenShake(1.05F)
                            .withBlockRippleParticles(),
                    MeleeAttackHit.areaSpecial(TEMPER_TANTRUM_SECOND_IMPACT_TICK, TEMPER_TANTRUM_MINI_SLAM_DAMAGE_MULTIPLIER, (float) (6.0 * ATTACK_AOE_SCALE), true,
                                    0.15F, 0.0, 0.0F, TEMPER_TANTRUM_MINI_VERTICAL_LAUNCH, 0, 0, true)
                            .withAreaOffset(TEMPER_TANTRUM_HAND_FORWARD_OFFSET, TEMPER_TANTRUM_HAND_SIDE_OFFSET)
                            .withVerticalHitRange(5.0)
                            .withScreenShake(1.05F)
                            .withBlockRippleParticles(),
                    MeleeAttackHit.areaSpecial(TEMPER_TANTRUM_FINAL_IMPACT_TICK, TEMPER_TANTRUM_BIG_SLAM_DAMAGE_MULTIPLIER, (float) (11.0 * ATTACK_AOE_SCALE), true,
                                    0.35F, 0.0, 0.0F, TEMPER_TANTRUM_FINAL_VERTICAL_LAUNCH, 60, 0, true)
                            .withAreaOffset(TEMPER_TANTRUM_FINAL_FORWARD_OFFSET, 0.0)
                            .withVerticalHitRange(5.5)
                            .withScreenShake(1.65F)
                            .withBlockRippleParticles()));
        }

        if (canStartBellySlam(target)) {
            return startBellySlamAttack(target);
        }

        if (bellyBumpCooldownTicks <= 0 && closePressureTicks >= BELLY_BUMP_CLOSE_PRESSURE_TICKS) {
            playAttackAnimation(2);
            bellyBumpCooldownTicks = BELLY_BUMP_COOLDOWN_TICKS;
            closePressureTicks = 0;
            return new MeleeAttackPlan(BELLY_BUMP_RECOVERY_TICKS, BELLY_BUMP_ANIMATION_TICKS, List.of(MeleeAttackHit.areaSpecial(18, BELLY_BUMP_DAMAGE_MULTIPLIER, (float) (4.0 * ATTACK_AOE_SCALE), true,
                    0.0F, 3.2 * ATTACK_KNOCKBACK_SCALE, 110.0F, 1.38, 0, 0, false)));
        }

        return switch (getRandom().nextInt(7)) {
            case 0, 1 -> {
                playAttackAnimation(1);
                punchImpactSoundDelayTicks = PUNCH_IMPACT_SOUND_DELAY_TICKS;
                beginPunchAdvance();
                punchTrackingTicks = PUNCH_TRACKING_TICKS;
                yield new MeleeAttackPlan(PUNCH_RECOVERY_TICKS, ATTACK_ANIMATION_TICKS, List.of(
                        MeleeAttackHit.areaSpecial(24, PUNCH_DAMAGE_MULTIPLIER, (float) (3.25 * ATTACK_AOE_SCALE), true,
                                0.0F, 3.3 * ATTACK_KNOCKBACK_SCALE, 75.0F, 0.36, 0, 0, false)
                                .withAreaOffset(1.05, 0.0)
                                .withDirectionalKnockback(1.0, 0.0)
                                .withVerticalHitRange(3.0)));
            }
            case 2, 3, 4 -> {
                playAttackAnimation(5);
                beginSideSwipeAdvance();
                sideSwipeImpactSoundDelayTicks = SIDE_SWIPE_IMPACT_SOUND_DELAY_TICKS;
                yield new MeleeAttackPlan(SIDE_SWIPE_RECOVERY_TICKS, SIDE_SWIPE_ANIMATION_TICKS, List.of(
                        MeleeAttackHit.areaSpecial(11, SIDE_SWIPE_DAMAGE_MULTIPLIER, (float) (3.2 * ATTACK_AOE_SCALE), true,
                                0.0F, 2.65 * ATTACK_KNOCKBACK_SCALE, 125.0F, 0.66, 0, 0, false)
                                .withAreaOffset(1.15, 0.0)
                                .withDirectionalKnockback(1.0, -1.0)
                                .withVerticalHitRange(3.0)));
            }
            default -> {
                playAttackAnimation(6);
                upswingWhooshSoundDelayTicks = UPSWING_WHOOSH_SOUND_DELAY_TICKS;
                yield new MeleeAttackPlan(UPSWING_RECOVERY_TICKS, UPSWING_LAUNCH_ANIMATION_TICKS, List.of(
                        MeleeAttackHit.areaSpecial(UPSWING_IMPACT_DELAY_TICKS, UPSWING_LAUNCH_DAMAGE_MULTIPLIER, (float) (3.5 * ATTACK_AOE_SCALE), true,
                                0.0F, 0.65 * ATTACK_KNOCKBACK_SCALE, 110.0F, 1.65, 0, 0, false)
                                .withAreaOffset(1.0, 0.0)
                                .withVerticalHitRange(3.5)));
            }
        };
    }

    private MeleeAttackPlan startBellySlamAttack(LivingEntity target) {
        if (target == null) {
            return new MeleeAttackPlan(6, List.of());
        }

        playAttackAnimation(3);
        bellySlamCooldownTicks = BELLY_SLAM_COOLDOWN_TICKS;
        bellySlamTarget = target;
        lockBellySlamDirection(target);
        // Windup lengthened by 10 ticks (0.5s) along with the rest of the animation timeline.
        bellySlamTakeoffDelayTicks = 24;
        bellySlamFlightTicks = 14;
        bellySlamFacingLockTicks = BELLY_SLAM_ANIMATION_TICKS;
        bellySlamGruntSoundDelayTicks = BELLY_SLAM_GRUNT_SOUND_DELAY_TICKS;
        bellySlamImpactSoundDelayTicks = BELLY_SLAM_IMPACT_SOUND_DELAY_TICKS;
        closePressureTicks = 0;
        // Explicit duration matching BELLY_SLAM_ANIMATION_TICKS — without it, the plan's active
        // duration defaults to the hit's delay (41 ticks), letting the goal reclaim navigation
        // control mid-flight while tickBellySlamFlight() is still independently driving velocity,
        // which looks like the attack getting cancelled/interrupted.
        return new MeleeAttackPlan(85, BELLY_SLAM_ANIMATION_TICKS, List.of(MeleeAttackHit.areaSpecial(38, BELLY_SLAM_DAMAGE_MULTIPLIER, (float) (4.4 * ATTACK_AOE_SCALE), true,
                0.3F, 1.4 * ATTACK_KNOCKBACK_SCALE, 0.0F, 0.0, 100, 0, true).withVerticalHitRange(5.0)));
    }

    @Override
    protected double getMeleeAttackReachSqr(LivingEntity target) {
        // Never stop early at the optional belly-slam range. If its random roll failed, that
        // previously left the brute staring from several blocks away through an empty cooldown.
        return REGULAR_ATTACK_START_RANGE * REGULAR_ATTACK_START_RANGE;
    }

    @Override
    protected boolean shouldFaceMeleeTarget(LivingEntity target) {
        // Lock facing for the whole swing once an attack is triggered — letting the look control
        // keep rotating toward the target's current position mid-swing turns the body away from
        // where the hit was actually aimed, causing whiffs and an "angled" look on impact.
        boolean midSwing = activeBruteAttackId != 0 && attackAnimationTicks > 0;
        return bellySlamFacingLockTicks <= 0 && !isTemperTantrumActive() && !midSwing;
    }

    @Override
    protected boolean shouldDirectlyTrackMeleeTargetWhilePursuing() {
        // Let navigation own facing during the approach, just like the grunt. Direct tracking
        // resumes near attack range, preventing look control from fighting path steering.
        return false;
    }

    @Override
    protected boolean shouldStayStationaryDuringMeleeAttack() {
        // Punch, Belly Slam, and Side Swipe drive their own authored motion.
        return activeBruteAttackId != 0
                && activeBruteAttackId != 1
                && activeBruteAttackId != 3
                && activeBruteAttackId != 5
                && attackAnimationTicks > 0;
    }

    @Override
    protected boolean shouldSuspendNavigationDuringMeleeAttack() {
        // These moves supply their own velocity, so block path navigation without zeroing motion.
        return (activeBruteAttackId == 1 || activeBruteAttackId == 3 || activeBruteAttackId == 5)
                && attackAnimationTicks > 0;
    }

    @Override
    protected double getMeleePursuitSpeedModifier() {
        return 1.0;
    }

    @Override
    protected double getWanderSpeedModifier() {
        // Navigation goals take a multiplier; this yields an effective wandering speed
        // of 0.12 against the Brute's default 0.325 movement attribute.
        return 0.12 / 0.325;
    }

    @Override
    protected int getPathRecalculationTicks() {
        // The brute is wider and faster than a grunt, so the grunt's 4–7 tick repath cadence
        // produces visible left-right corrections. A short extra commitment smooths his heading
        // without changing the underlying vanilla path navigation.
        return 8 + getRandom().nextInt(4);
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        if (shouldSuppressReactiveSounds()) {
            return null;
        }

        return ModSoundEvents.OGRE_BRUTE_HURT.get();
    }

    private boolean shouldSuppressReactiveSounds() {
        return isTemperTantrumActive()
                || activeBruteAttackId == 3 && attackAnimationTicks > 0;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new SynchronizedAnimationController<>(this, "Brute Movement", 5, this::animateBrute));
    }

    private PlayState animateBrute(AnimationTest<OgreBrute> state) {
        int visualAttackId = visualOneShot.update(state.controller(),
                entityData.get(DATA_BRUTE_ATTACK_ID), entityData.get(DATA_BRUTE_ATTACK_START_TICK),
                // Every attack's registered duration is padded by the controller's transition
                // length so the clip is allowed to finish its authored recovery before locomotion
                // takes over (see ONE_SHOT_TRANSITION_TICKS). "upswing_launch" (id 6) is the one
                // exception: UPSWING_LAUNCH_ANIMATION_TICKS (35) was already tuned ~1 tick past its
                // raw authored length (34.17), so adding the full transition padding on top would
                // overshoot and hold the final pose noticeably longer than intended.
                id -> id == 6 ? getAttackAnimationTicks(id) : getAttackAnimationTicks(id) + ONE_SHOT_TRANSITION_TICKS,
                level().getGameTime());
        if (visualAttackId > 0) {
            return state.setAndContinue(getAttackAnimation(visualAttackId));
        }

        // isAggressive() is a synced data flag (set by the inherited melee goal), unlike getTarget()
        // which is server-only and always null on the client — using target here left clients stuck on WALK.
        if (shouldUseRunAnimation()) {
            idleAnimationTicks = 0;
            idleVariantTicks = 0;
            // Follow actual movement rather than recomputing target distance on the client.
            if (isMovingForAnimation(state)) {
                return state.setAndContinue(RUN);
            }
            return state.setAndContinue(IDLE);
        }

        if (isMovingForAnimation(state)) {
            idleAnimationTicks = 0;
            idleVariantTicks = 0;
            return state.setAndContinue(WALK);
        }

        updateIdleAnimationTimers(state);
        if (idleVariantTicks > 0) {
            return state.setAndContinue(activeIdleAnimation);
        }
        return state.setAndContinue(IDLE);
    }

    private boolean isMovingForAnimation(AnimationTest<OgreBrute> state) {
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

    private boolean canStartTemperTantrum(LivingEntity target) {
        if (!canReachForTemperTantrum(target)) {
            return false;
        }

        return temperTantrumMilestoneQueued;
    }

    private boolean canReachForTemperTantrum(LivingEntity target) {
        return temperTantrumCooldownTicks <= 0
                && !isTemperTantrumActive()
                && target != null
                && target.isAlive()
                && distanceToSqr(target) <= TEMPER_TANTRUM_START_RANGE * TEMPER_TANTRUM_START_RANGE
                && getSensing().hasLineOfSight(target);
    }

    public boolean isTemperTantrumActive() {
        return activeBruteAttackId == 4 && attackAnimationTicks > 0;
    }

    /** Synced, render-only exclusion for Jump Slam and Temper Tantrum. */
    public boolean isProceduralHeadTrackingSuppressed() {
        int attackId = entityData.get(DATA_BRUTE_ATTACK_ID);
        return attackId == 3 || attackId == 4;
    }

    @Override
    protected boolean shouldDodgeRippleHit(LivingEntity target) {
        if (!isTemperTantrumActive()) {
            return false;
        }

        // Temper tantrum is an unblockable ground slam — the only way to avoid it is to jump
        // at the right moment so the shockwave passes underneath.
        boolean intentionalJump = hasRecentIntentionalJump(target, RIPPLE_JUMP_GRACE_TICKS);
        boolean airborneFromMiniImpact = RippleJumpTracker.wasRecentlyForcedUpward(
                target, FORCED_MINI_LAUNCH_DODGE_SUPPRESSION_TICKS);
        return intentionalJump || !target.onGround() && !airborneFromMiniImpact;
    }

    @Override
    protected void applyGroundRippleHit(LivingEntity target, MeleeAttackHit hit, GroundRippleProfile profile) {
        boolean ogreAlly = target instanceof OgreGrunt;
        float multiplier = ogreAlly ? hit.damageMultiplier() * 0.5F : hit.damageMultiplier();
        if (doScaledHurtTarget(target, multiplier, hit.armorPierceFraction())) {
            if (isTemperTantrumActive() && hit.delayTicks() != TEMPER_TANTRUM_FINAL_IMPACT_TICK) {
                RippleJumpTracker.recordForcedRippleLaunch(target);
            }
            applyAttackHitExtras(target, hit);
        }
    }

    @Override
    protected void applyDirectShockwaveWeaponHits(List<LivingEntity> targets, Vec3 areaCenter,
                                                  MeleeAttackHit hit) {
        pendingTemperTantrumRippleExclusions.clear();
        if (!isTemperTantrumActive()) {
            return;
        }

        boolean finalImpact = hit.delayTicks() == TEMPER_TANTRUM_FINAL_IMPACT_TICK;
        double halfWidth = finalImpact
                ? TEMPER_TANTRUM_FINAL_DIRECT_HALF_WIDTH
                : TEMPER_TANTRUM_MINI_DIRECT_HALF_WIDTH;
        AABB handImpact = new AABB(
                areaCenter.x - halfWidth, getY() - 0.75, areaCenter.z - halfWidth,
                areaCenter.x + halfWidth, getY() + TEMPER_TANTRUM_DIRECT_HEIGHT, areaCenter.z + halfWidth);
        for (LivingEntity target : targets) {
            if (!target.getBoundingBox().intersects(handImpact)) {
                continue;
            }
            pendingTemperTantrumRippleExclusions.add(target.getUUID());
            float directMultiplier = finalImpact
                    ? TEMPER_TANTRUM_FINAL_DIRECT_DAMAGE_MULTIPLIER
                    : TEMPER_TANTRUM_MINI_DIRECT_DAMAGE_MULTIPLIER;
            float multiplier = target instanceof OgreGrunt ? directMultiplier * 0.5F : directMultiplier;
            if (doScaledHurtTarget(target, multiplier, hit.armorPierceFraction())) {
                if (!finalImpact) {
                    RippleJumpTracker.recordForcedRippleLaunch(target);
                }
                applyAttackHitExtras(target, hit.withoutHorizontalKnockback());
            }
        }
    }

    @Override
    protected Set<UUID> consumeDirectShockwaveRippleExclusions() {
        Set<UUID> exclusions = Set.copyOf(pendingTemperTantrumRippleExclusions);
        pendingTemperTantrumRippleExclusions.clear();
        return exclusions;
    }

    private void tickTemperTantrumSlowness() {
        if (!isTemperTantrumActive()) {
            return;
        }

        List<LivingEntity> nearbyPlayers = level().getEntitiesOfClass(
                LivingEntity.class,
                getBoundingBox().inflate(TEMPER_TANTRUM_SLOWNESS_RADIUS),
                entity -> entity instanceof Player && entity.isAlive());

        for (LivingEntity player : nearbyPlayers) {
            player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 1, false, true));
        }
    }

    private void tickTemperTantrumWhineSound() {
        if (temperTantrumWhineDelayTicks < 0) {
            return;
        }

        if (!isTemperTantrumActive()) {
            temperTantrumWhineDelayTicks = -1;
            return;
        }

        if (temperTantrumWhineDelayTicks-- <= 0) {
            level().playSound(
                    null,
                    getX(),
                    getY(),
                    getZ(),
                    ModSoundEvents.OGRE_BRUTE_TANTRUM_WHINE.get(),
                    SoundSource.HOSTILE,
                    1.5F,
                    1.0F);
            temperTantrumWhineDelayTicks = -1;
        }
    }

    private void tickDelayedImpactSounds() {
        punchImpactSoundDelayTicks = tickDelayedImpactSound(
                punchImpactSoundDelayTicks,
                1,
                SoundEvents.PLAYER_ATTACK_STRONG,
                1.1F,
                0.74F);
        sideSwipeImpactSoundDelayTicks = tickDelayedImpactSound(
                sideSwipeImpactSoundDelayTicks,
                5,
                SoundEvents.PLAYER_ATTACK_STRONG,
                1.2F,
                0.72F);
        upswingWhooshSoundDelayTicks = tickDelayedImpactSound(
                upswingWhooshSoundDelayTicks,
                6,
                ModSoundEvents.OGRE_BRUTE_UPSWING_WHOOSH.get(),
                1.15F,
                0.88F);
        bellySlamImpactSoundDelayTicks = tickDelayedImpactSound(
                bellySlamImpactSoundDelayTicks,
                3,
                ModSoundEvents.OGRE_BRUTE_BELLY_SLAM_IMPACT.get(),
                1.65F,
                0.76F);
        bellySlamGruntSoundDelayTicks = tickDelayedImpactSound(
                bellySlamGruntSoundDelayTicks,
                3,
                ModSoundEvents.OGRE_BRUTE_BELLY_SLAM_GRUNT.get(),
                1.2F,
                0.84F);
    }

    private int tickDelayedImpactSound(int ticks, int attackId, SoundEvent sound, float volume, float pitch) {
        if (ticks < 0) {
            return ticks;
        }

        if (activeBruteAttackId != attackId || attackAnimationTicks <= 0) {
            return -1;
        }

        if (ticks <= 0) {
            level().playSound(null, getX(), getY(), getZ(), sound, SoundSource.HOSTILE, volume, pitch);
            return -1;
        }

        return ticks - 1;
    }

    @Override
    protected void onMeleeAttackPlanFinished() {
        activeBruteAttackId = 0;
        super.onMeleeAttackPlanFinished();
        if (!level().isClientSide()) {
            entityData.set(DATA_BRUTE_ATTACK_ID, 0);
            entityData.set(DATA_BRUTE_ATTACK_START_TICK, 0L);
        }
    }

    private boolean canStartBellySlam(LivingEntity target) {
        if (!canReachForBellySlam(target)) {
            return false;
        }

        double distanceSqr = distanceToSqr(target);
        if (distanceSqr >= BELLY_SLAM_MIN_RANGE * BELLY_SLAM_MIN_RANGE) {
            return getRandom().nextInt(3) == 0;
        }

        return getRandom().nextInt(12) == 0;
    }

    private boolean canReachForBellySlam(LivingEntity target) {
        return bellySlamCooldownTicks <= 0
                && target != null
                && target.isAlive()
                && distanceToSqr(target) <= BELLY_SLAM_RANGE * BELLY_SLAM_RANGE
                && getSensing().hasLineOfSight(target);
    }

    private boolean isWithinRegularAttackStartRange(LivingEntity target) {
        return target != null
                && target.isAlive()
                && distanceToSqr(target) <= REGULAR_ATTACK_START_RANGE * REGULAR_ATTACK_START_RANGE;
    }

    private void tickBellySlamFlight() {
        if (bellySlamTakeoffDelayTicks > 0) {
            bellySlamTakeoffDelayTicks--;
            faceBellySlamDirection();
            return;
        }

        if (bellySlamFlightTicks <= 0) {
            bellySlamTarget = null;
            bellySlamDirection = Vec3.ZERO;
            return;
        }

        bellySlamFlightTicks--;
        if (bellySlamDirection.lengthSqr() < 1.0E-4) {
            return;
        }

        faceBellySlamDirection();
        pushBellySlamBlockers();
        Vec3 movement = getDeltaMovement();
        double verticalVelocity = getBellySlamVerticalVelocity();
        setDeltaMovement(bellySlamDirection.x * 0.32, verticalVelocity, bellySlamDirection.z * 0.32);
        hurtMarked = true;
    }

    private void tickBellySlamFacingLock() {
        if (bellySlamFacingLockTicks <= 0) {
            return;
        }

        bellySlamFacingLockTicks--;
        faceBellySlamDirection();
    }

    private double getBellySlamVerticalVelocity() {
        if (bellySlamFlightTicks > 8) {
            return 0.42;
        }

        if (bellySlamFlightTicks > 3) {
            return 0.06;
        }

        return -0.82;
    }

    private void pushBellySlamBlockers() {
        List<LivingEntity> blockers = level().getEntitiesOfClass(
                LivingEntity.class,
                getBoundingBox().inflate(0.95, 0.65, 0.95),
                entity -> entity != this && entity.isAlive());

        for (LivingEntity blocker : blockers) {
            Vec3 away = blocker.position().subtract(position());
            Vec3 horizontalAway = new Vec3(away.x, 0.0, away.z);
            if (horizontalAway.lengthSqr() < 1.0E-4) {
                horizontalAway = bellySlamDirection;
            }

            Vec3 direction = horizontalAway.normalize();
            Vec3 movement = blocker.getDeltaMovement();
            blocker.setDeltaMovement(direction.x * 0.55, Math.max(movement.y, 0.25), direction.z * 0.55);
            blocker.hurtMarked = true;
        }
    }

    private void lockBellySlamDirection(LivingEntity target) {
        Vec3 towardTarget = target.position().subtract(position());
        Vec3 horizontal = new Vec3(towardTarget.x, 0.0, towardTarget.z);
        if (horizontal.lengthSqr() < 1.0E-4) {
            horizontal = Vec3.directionFromRotation(0.0F, getYRot());
        }

        bellySlamDirection = horizontal.normalize();
        bellySlamYaw = (float) (Math.atan2(bellySlamDirection.z, bellySlamDirection.x) * 180.0 / Math.PI) - 90.0F;
        faceBellySlamDirection();
    }

    private void faceBellySlamDirection() {
        setYRot(bellySlamYaw);
        yBodyRot = bellySlamYaw;
        yHeadRot = bellySlamYaw;
    }

    private void playAttackAnimation(int attackId) {
        if (!level().isClientSide()) {
            entityData.set(DATA_BRUTE_ATTACK_ID, attackId);
            entityData.set(DATA_BRUTE_ATTACK_START_TICK, level().getGameTime());
        }

        attackAnimationTicks = getAttackAnimationTicks(attackId);
        activeBruteAttackId = attackId;
    }

    // Only Belly Slam (3, its own timed exertion grunt) and Temper Tantrum (4, its own
    // tantrum whine five ticks in) have a vocalization. Every other attack — punch (1),
    // belly bump (2), side swipe (5), and upswing launch (6) — is silent apart from the
    // wet impact sound already handled by tickDelayedImpactSound(s).

    private RawAnimation getAttackAnimation(int attackId) {
        return switch (attackId) {
            case 2 -> BELLY_BUMP;
            case 3 -> BELLY_SLAM;
            case 4 -> TEMPER_TANTRUM;
            case 5 -> SIDE_SWIPE;
            case 6 -> UPSWING_LAUNCH;
            default -> ATTACK;
        };
    }

    private int getAttackAnimationTicks(int attackId) {
        return switch (attackId) {
            case 2 -> BELLY_BUMP_ANIMATION_TICKS;
            case 3 -> BELLY_SLAM_ANIMATION_TICKS;
            case 4 -> TEMPER_TANTRUM_ANIMATION_TICKS;
            case 5 -> SIDE_SWIPE_ANIMATION_TICKS;
            case 6 -> UPSWING_LAUNCH_ANIMATION_TICKS;
            default -> ATTACK_ANIMATION_TICKS;
        };
    }

    private boolean shouldUseRunAnimation() {
        return isRunMovementState() || isAggressive();
    }

    private void updateIdleAnimationTimers(AnimationTest<OgreBrute> state) {
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
