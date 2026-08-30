package com.kingsandmonsters.entity;

import net.minecraft.server.level.ServerLevel;

import com.kingsandmonsters.Config;
import com.kingsandmonsters.ModItems;
import com.kingsandmonsters.ModSoundEvents;
import com.kingsandmonsters.enchantment.ModEnchantmentEffects;
import com.kingsandmonsters.enchantment.ModEnchantments;
import com.kingsandmonsters.entity.animation.SynchronizedAnimationController;
import com.kingsandmonsters.entity.animation.CanonicalOneShotState;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.state.AnimationTest;
import com.geckolib.animation.object.PlayState;
import com.geckolib.animation.RawAnimation;

import java.util.EnumSet;
import java.util.List;

public class OgreGuard extends OgreGrunt {
    private static final String ROYAL_THRONE_GUARD_TAG_PREFIX =
            "KingsAndMonstersFortResident_throne_guard_";
    private static final EntityDataAccessor<Long> DATA_GUARD_SHOT_START_TICK =
            SynchedEntityData.defineId(OgreGuard.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> DATA_GUARD_PUNCH_START_TICK =
            SynchedEntityData.defineId(OgreGuard.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Boolean> DATA_GUARD_AIMING =
            SynchedEntityData.defineId(OgreGuard.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_GUARD_COMBAT_MOVING =
            SynchedEntityData.defineId(OgreGuard.class, EntityDataSerializers.BOOLEAN);
    // Mob#getTarget() is server-only and never synced to the client, so animateMovement() (which
    // runs client-side for rendering) can't use it directly to tell whether he's aggro'd — it
    // always reads null there, which was silently forcing the WALK fallback (and skipping
    // COMBAT_WALK entirely) any time he moved while chasing a target, no matter what.
    private static final EntityDataAccessor<Boolean> DATA_GUARD_HAS_TARGET =
            SynchedEntityData.defineId(OgreGuard.class, EntityDataSerializers.BOOLEAN);
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation COMBAT_WALK = RawAnimation.begin().thenLoop("run");
    private static final RawAnimation SHOOT = RawAnimation.begin().thenPlay("throw");
    private static final RawAnimation PUNCH = RawAnimation.begin().thenPlay("Punch");
    private static final int SHOOT_ANIMATION_TICKS = 45;
    private static final int BOW_DRAW_START_TICKS = 1;
    // 34, matching the 1.21.1 build this was ported from, where the spear release reads correctly
    // in-game. It is NOT bare sync padding on top of the authored keyframe, which is why the port
    // pass that flattened it to 28 made the spear leave while the arm was still wound back:
    // "throw" holds right_arm at its wound-back extreme through 1.25s, deepens to -208.46 at
    // 1.375s and only snaps forward to -107.11 at 1.5s, and GeckoLib plays that clip behind this
    // controller's 5-tick leading transition stage (SynchronizedAnimationController seeks the
    // overall timeline, deliberately keeping that stage inside the elapsed clock). The visible
    // release therefore lands ~1.45s * 20 + 5 = 34 ticks after the synced start tick these
    // server-side counters are measured from.
    private static final int ARROW_RELEASE_TICKS = 34;
    private static final int PUNCH_ANIMATION_TICKS = 25;
    // 18, matching 1.21.1, for exactly the same reason as ARROW_RELEASE_TICKS: the authored
    // full-extension hold is 0.6667s-0.8333s local (ticks 13-17), and the leading transition
    // stage puts that at ticks 18-22 on this counter. The port pass flattened it to 15 (bare
    // local 0.75s), landing the hit before the arm had extended.
    private static final int PUNCH_IMPACT_TICKS = 18;
    // The "Guard Movement" controller is registered with a 5-tick transition, and
    // SynchronizedAnimationController deliberately keeps that leading transition stage inside the
    // elapsed clock (see setTimelineTime() there) — which is exactly why ARROW_RELEASE_TICKS and
    // PUNCH_IMPACT_TICKS are authored-local + 5. The *visual* one-shot duration has to carry the
    // same offset: the timeline is [5t transition][clip][transition], so at elapsed == 45 the
    // "throw" clip (2.25s == 45t) has only reached local tick 40, still 5 ticks from its authored
    // end. Expiring the canonical one-shot at 45 therefore cut the throw off mid-follow-through and
    // handed locomotion a hard pose jump — the residual "reset" seam at the end of the throw.
    private static final int ONE_SHOT_TRANSITION_TICKS = 5;
    // Tiny per-tick step toward the target while the swing is winding up, so he visibly closes
    // the last bit of distance as part of the animation itself instead of standing still — a
    // single upfront velocity kick read as a slide happening before the punch, not part of it.
    private static final double PUNCH_CREEP_SPEED = 0.006;
    // Brief "just noticed you" pause before he can throw his first attack against a freshly
    // (re)acquired target, so a player who respawns already within his reach doesn't get an
    // instant, zero-warning punch the very tick he re-targets them.
    private static final int ENGAGEMENT_REACTION_TICKS = 10;
    private static final int PUNCH_SOUND_LEAD_TICKS = 2;
    private static final int PUNCH_COOLDOWN_TICKS = 44;
    private static final int MIN_SHOT_COOLDOWN_TICKS = 18;
    private static final int SHOT_COOLDOWN_RANDOM_TICKS = 12;
    // Well under OgreArcher's 22.0 — the guard is a brute with a spear-throw option, not a
    // dedicated ranged specialist, so he should close in rather than engaging from way out.
    private static final double SHOOT_RANGE = 8.0;
    private static final double PUNCH_RANGE = 2.35;
    private static final double PUNCH_HIT_RANGE = 3.5;
    private static final double MELEE_ENGAGE_RANGE = 3.5;
    private static final float PUNCH_DAMAGE = 6.5F;
    private static final double PUNCH_KNOCKBACK_STRENGTH = 0.75;
    private static final float PUNCH_SOUND_VOLUME = 0.75F;
    private static final float PUNCH_SOUND_PITCH = 0.8F;
    // Health fraction (of max health) below which the guard breaks off melee to put distance
    // between himself and his target, instead of the old "retreat the instant the player gets
    // close" behavior — he's meant to be more aggressive than the archer and fight through
    // normal damage, only backing off when actually in danger.
    private static final double LOW_HEALTH_RETREAT_HEALTH_FRACTION = 0.5;
    // He stops as soon as he reaches the outer portion of spear range. The timer is only a
    // pathfinding failsafe so blocked terrain cannot leave him in the retreat state forever.
    private static final int LOW_HEALTH_RETREAT_TICKS = 100;
    private static final double LOW_HEALTH_RETREAT_DISTANCE = 4.0;
    private static final double LOW_HEALTH_RETREAT_STOP_RANGE = SHOOT_RANGE - 0.5;
    private static final double LOW_HEALTH_RETREAT_SPEED = 1.08;
    // Matches TridentItem.SHOOT_POWER so the thrown spear flies at the same speed as a player-thrown trident.
    private static final float ARROW_PROJECTILE_SPEED = 2.5F;
    private static final float ARROW_INACCURACY = 4.5F;
    private static final double ARROW_LOB_COMPENSATION = 0.14;
    // These offsets approximate where the right hand actually is at the release pose (both arms
    // thrust forward, per the "throw" animation's ~1.42s keyframe) rather than a neutral standing
    // pose — the old, smaller values were tuned against an earlier (too-early) release point and
    // no longer line up now that release happens later in the swing. This is a reasoned estimate,
    // not a measurement off the actual animated bone position, so it will likely need further
    // tuning once seen in-game.
    private static final double ARROW_SPAWN_FORWARD_OFFSET = 0.85;
    private static final double ARROW_SPAWN_SIDE_OFFSET = 0.35;
    private static final double ARROW_SPAWN_VERTICAL_OFFSET = 0.55;
    // Calibrated against full diamond armor (~1.5 hearts effective per hit) to match the grunt/brute
    // melee tuning pass — arrows hit noticeably harder on lighter armor (~2.4 hearts vs full iron).
    private static final double OGRE_ARROW_BASE_DAMAGE = 10.0;
    private static final int POISON_ARROW_ROLL = 15;
    private static final int SLOWNESS_ARROW_ROLL = 28;
    private static final int POISON_ARROW_DURATION_TICKS = 100;
    private static final int SLOWNESS_ARROW_DURATION_TICKS = 100;
    private static final float AIM_TURN_SPEED = 18.0F;
    private static final float SHOOT_VISUAL_TRACK_TURN_SPEED = 10.0F;
    private static final float MAX_SHOOT_ANGLE = 25.0F;

    private int idleAnimationTicks;
    private int rareIdleTicks;
    private int activeShotTicks;
    private int arrowReleaseTicks;
    private int activePunchTicks;
    private int punchImpactTicks;
    private int punchCooldownTicks;
    private int combatMovementAnimationTicks;
    private int lastAnimationTick = -1;
    private int movementAnimationGraceTicks;
    private int lastMovementGraceTick = -1;
    private boolean arrowReleasedThisShot;
    private boolean bowDrawStartedThisShot;
    private boolean punchHitThisPunch;
    private boolean punchImpactSoundPlayed;
    private boolean lowHealthRetreatUsed;
    private LivingEntity pendingShotTarget;
    private LivingEntity pendingPunchTarget;
    private double enchantedBowDamageBonus;
    private boolean enchantedBowHasFlame;
    private double configuredArrowBaseDamage = OGRE_ARROW_BASE_DAMAGE;
    private final CanonicalOneShotState visualOneShot = new CanonicalOneShotState();

    public OgreGuard(EntityType<? extends OgreGuard> type, Level level) {
        super(type, level);
        setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.HUNTERS_SPEAR.get()));
        setDropChance(EquipmentSlot.MAINHAND, 0.0F);
    }

    public void rollEliteSpearEnchantment() {
        if (level().isClientSide() || !getRandom().nextBoolean()) {
            return;
        }

        ItemStack spear = new ItemStack(ModItems.HUNTERS_SPEAR.get());
        switch (getRandom().nextInt(2)) {
            case 0 -> enchantSpear(spear, ModEnchantments.BARBED, 1 + getRandom().nextInt(3));
            default -> enchantSpear(spear, ModEnchantments.HEAVY_THROW, 1 + getRandom().nextInt(3));
        }
        setItemSlot(EquipmentSlot.MAINHAND, spear);
    }

    private void enchantSpear(ItemStack spear, net.minecraft.resources.ResourceKey<Enchantment> enchantment, int level) {
        Holder<Enchantment> holder = this.level().registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(enchantment);
        spear.enchant(holder, level);
    }

    @Override
    @SuppressWarnings("deprecation") // Required 1.21.1 OverrideOnly spawn hook; no replacement exists in this target.
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, net.minecraft.world.DifficultyInstance difficulty,
                                        EntitySpawnReason spawnType, @Nullable SpawnGroupData spawnGroupData) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData);
        ensureBowEquipped();
        return data;
    }

    @Override
    public void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput compound) {
        super.addAdditionalSaveData(compound);
        compound.putDouble("EnchantedBowDamageBonus", enchantedBowDamageBonus);
        compound.putBoolean("EnchantedBowHasFlame", enchantedBowHasFlame);
        compound.putBoolean("LowHealthRetreatUsed", lowHealthRetreatUsed);
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput compound) {
        super.readAdditionalSaveData(compound);
        enchantedBowDamageBonus = compound.getDoubleOr("EnchantedBowDamageBonus", 0.0);
        enchantedBowHasFlame = compound.getBooleanOr("EnchantedBowHasFlame", false);
        lowHealthRetreatUsed = compound.getBooleanOr("LowHealthRetreatUsed", false);
        ensureBowEquipped();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 70.0)
                .add(Attributes.ATTACK_DAMAGE, 7.0)
                .add(Attributes.MOVEMENT_SPEED, 0.275)
                .add(Attributes.FOLLOW_RANGE, 34.0)
                .add(Attributes.ARMOR, 11.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.65)
                .add(Attributes.ATTACK_KNOCKBACK, 0.35)
                .add(Attributes.STEP_HEIGHT, 1.0)
                .add(Attributes.SAFE_FALL_DISTANCE, 4.0);
    }

    @Override
    protected double getWanderSpeedModifier() {
        return 0.14 / 0.275;
    }

    @Override
    public void applyConfiguredCombatAttributes(boolean healToMax) {
        if (!Config.isLoaded()) {
            return;
        }

        setAttributeBaseValue(Attributes.MAX_HEALTH, Config.OGRE_GUARD_MAX_HEALTH.get());
        setAttributeBaseValue(Attributes.ATTACK_DAMAGE, Config.OGRE_GUARD_MELEE_DAMAGE.get());
        setAttributeBaseValue(Attributes.MOVEMENT_SPEED, Config.OGRE_GUARD_MOVEMENT_SPEED.get());
        setAttributeBaseValue(Attributes.FOLLOW_RANGE, Config.OGRE_GUARD_FOLLOW_RANGE.get());
        setAttributeBaseValue(Attributes.ARMOR, Config.OGRE_GUARD_ARMOR.get());
        configuredArrowBaseDamage = Config.OGRE_GUARD_SPEAR_DAMAGE.get();
        applyConfiguredHealth(healToMax);
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return ModSoundEvents.OGRE_ARCHER_HURT.get();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_GUARD_SHOT_START_TICK, 0L);
        builder.define(DATA_GUARD_PUNCH_START_TICK, 0L);
        builder.define(DATA_GUARD_AIMING, false);
        builder.define(DATA_GUARD_COMBAT_MOVING, false);
        builder.define(DATA_GUARD_HAS_TARGET, false);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!level().isClientSide()) {
            if (combatMovementAnimationTicks > 0 && --combatMovementAnimationTicks <= 0) {
                setCombatMoving(false);
            }

            entityData.set(DATA_GUARD_HAS_TARGET, getTarget() != null);
        }
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new OgreGuardShootGoal(this, 1.0));
        goalSelector.addGoal(4, new GuardSuperiorGoal(this));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, getWanderSpeedModifier()));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0f));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));

        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        registerTerritorialTargetGoal();
    }

    @Override
    protected boolean canGuardSuperiors() {
        // Fort throne guards are assigned fixed posts around the King. Letting the generic
        // superior-follow goal control them makes all three funnel into his hut and doorway.
        return entityTags().stream().noneMatch(tag -> tag.startsWith(ROYAL_THRONE_GUARD_TAG_PREFIX))
                && super.canGuardSuperiors();
    }

    private void beginBowShot(LivingEntity target) {
        ensureBowEquipped();
        setBowAiming(true);
        pendingShotTarget = target;
        activeShotTicks = SHOOT_ANIMATION_TICKS;
        arrowReleaseTicks = ARROW_RELEASE_TICKS;
        arrowReleasedThisShot = false;
        bowDrawStartedThisShot = false;
        playShootAnimation();
    }

    private void tickPendingBowShot() {
        if (pendingShotTarget == null) {
            return;
        }

        activeShotTicks--;
        int elapsedShotTicks = SHOOT_ANIMATION_TICKS - activeShotTicks;
        if (!bowDrawStartedThisShot && elapsedShotTicks >= BOW_DRAW_START_TICKS) {
            bowDrawStartedThisShot = true;
            startUsingItem(InteractionHand.MAIN_HAND);
        }

        if (!arrowReleasedThisShot && --arrowReleaseTicks <= 0) {
            shootPendingArrow();
        }

        if (activeShotTicks <= 0) {
            cancelBowShot();
        }
    }

    private void shootPendingArrow() {
        if (pendingShotTarget == null || arrowReleasedThisShot) {
            return;
        }

        ensureBowEquipped();
        arrowReleasedThisShot = true;
        if (isUsingItem()) {
            stopUsingItem();
        }

        if (level().isClientSide()) {
            return;
        }

        LivingEntity target = pendingShotTarget;
        if (!hasClearShotToTarget(target)) {
            return;
        }

        Vec3 arrowSpawn = getArrowSpawnPosition();
        double xOffset = target.getX() - arrowSpawn.x;
        double zOffset = target.getZ() - arrowSpawn.z;
        double horizontalDistance = Math.sqrt(xOffset * xOffset + zOffset * zOffset);
        double yOffset = target.getY(0.3333333333333333) - arrowSpawn.y;
        Vec3 aim = new Vec3(xOffset, yOffset + horizontalDistance * ARROW_LOB_COMPENSATION, zOffset).normalize();
        ItemStack spearStack = getMainHandItem().copyWithCount(1);
        int heavyThrowLevel = ModEnchantmentEffects.level(
                level(), spearStack, ModEnchantments.HEAVY_THROW);
        float projectileSpeed = ModEnchantmentEffects.heavyThrowSpeed(
                ARROW_PROJECTILE_SPEED, heavyThrowLevel);
        float projectileDamage = ModEnchantmentEffects.heavyThrowDamage(
                (float) configuredArrowBaseDamage, heavyThrowLevel);
        OgreSpear spear = new OgreSpear(
                level(),
                this,
                spearStack,
                arrowSpawn,
                aim.scale(projectileSpeed),
                projectileDamage,
                OgreSpear.Pickup.DISALLOWED);
        level().addFreshEntity(spear);
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.TRIDENT_THROW,
                SoundSource.HOSTILE, 2.0F, 0.82F + getRandom().nextFloat() * 0.10F);
    }

    private Vec3 getArrowSpawnPosition() {
        Vec3 forward = Vec3.directionFromRotation(0.0F, getYRot()).normalize();
        Vec3 right = new Vec3(-forward.z, 0.0, forward.x).normalize();
        return position()
                .add(0.0, getEyeHeight() - ARROW_SPAWN_VERTICAL_OFFSET, 0.0)
                .add(forward.scale(ARROW_SPAWN_FORWARD_OFFSET))
                .add(right.scale(ARROW_SPAWN_SIDE_OFFSET));
    }

    private double getVelocityAdjustedArrowDamage() {
        return Math.max(0.0, configuredArrowBaseDamage + enchantedBowDamageBonus) / ARROW_PROJECTILE_SPEED;
    }

    private boolean hasClearShotToTarget(LivingEntity target) {
        Vec3 start = getArrowSpawnPosition();
        Vec3 end = target.getEyePosition().subtract(0.0, 0.15, 0.0);
        double targetDistanceSqr = start.distanceToSqr(end);
        AABB shotLane = new AABB(start, end).inflate(0.75);
        List<OgreGrunt> alliesInLane = level().getEntitiesOfClass(
                OgreGrunt.class,
                shotLane,
                ally -> ally != this && ally.isAlive());

        for (OgreGrunt ally : alliesInLane) {
            if (ally == target) {
                continue;
            }

            var allyHit = ally.getBoundingBox().inflate(0.35).clip(start, end);
            if (allyHit.isPresent() && start.distanceToSqr(allyHit.get()) < targetDistanceSqr) {
                return false;
            }
        }

        return true;
    }

    private void ensureBowEquipped() {
        if (!getMainHandItem().is(ModItems.HUNTERS_SPEAR.get())) {
            setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.HUNTERS_SPEAR.get()));
        }
    }

    private void cancelBowShot() {
        pendingShotTarget = null;
        activeShotTicks = 0;
        arrowReleaseTicks = 0;
        arrowReleasedThisShot = false;
        bowDrawStartedThisShot = false;
        if (isUsingItem()) {
            stopUsingItem();
        }
        // Without this, DATA_GUARD_SHOT_START_TICK stays set to this shot's start tick forever.
        // Harmless while the client keeps a continuous view of this entity (its own
        // lastSyncedShotStartTick already matches), but if the client's tracking of this entity
        // is ever refreshed (e.g. it drops out of range and comes back — plausible right after a
        // death/respawn), that stale nonzero value reads as a brand new trigger and replays the
        // animation purely visually, with no real attack behind it.
        if (!level().isClientSide()) {
            entityData.set(DATA_GUARD_SHOT_START_TICK, 0L);
        }
    }

    private void setBowAiming(boolean aiming) {
        entityData.set(DATA_GUARD_AIMING, aiming);
    }

    private boolean isBowAiming() {
        return entityData.get(DATA_GUARD_AIMING);
    }

    private boolean isPreparingBowShot() {
        return pendingShotTarget != null;
    }

    public boolean shouldRenderHeldSpear() {
        // Always visible, matching vanilla Drowned: their trident stays in-hand the whole time
        // they're winding up and throwing, even though a separate thrown entity also exists.
        return true;
    }

    private void beginPunch(LivingEntity target) {
        cancelBowShot();
        plantForMelee();
        pendingPunchTarget = target;
        activePunchTicks = PUNCH_ANIMATION_TICKS;
        punchImpactTicks = PUNCH_IMPACT_TICKS;
        punchHitThisPunch = false;
        punchImpactSoundPlayed = false;
        punchCooldownTicks = PUNCH_COOLDOWN_TICKS;
        playPunchAnimation();
    }

    private void plantForMelee() {
        getNavigation().stop();
        Vec3 movement = getDeltaMovement();
        setDeltaMovement(0.0, movement.y, 0.0);
        setSpeed(0.0F);
    }

    private void creepTowardMeleeTarget(LivingEntity target) {
        Vec3 toTarget = new Vec3(target.getX() - getX(), 0.0, target.getZ() - getZ());
        if (toTarget.lengthSqr() < 1.0E-4) {
            return;
        }

        move(MoverType.SELF, toTarget.normalize().scale(PUNCH_CREEP_SPEED));
    }

    private void tickPendingPunch() {
        if (pendingPunchTarget == null) {
            return;
        }

        plantForMelee();
        if (!punchHitThisPunch) {
            creepTowardMeleeTarget(pendingPunchTarget);
        }
        activePunchTicks--;
        punchImpactTicks--;
        if (!punchImpactSoundPlayed && punchImpactTicks <= PUNCH_SOUND_LEAD_TICKS) {
            playPunchImpactSound();
        }
        if (!punchHitThisPunch && punchImpactTicks <= 0) {
            applyPunchImpact();
        }

        if (activePunchTicks <= 0) {
            cancelPunch();
        }
    }

    private void applyPunchImpact() {
        punchHitThisPunch = true;
        if (level().isClientSide() || pendingPunchTarget == null || !pendingPunchTarget.isAlive()) {
            return;
        }

        if (distanceToSqr(pendingPunchTarget) > PUNCH_HIT_RANGE * PUNCH_HIT_RANGE
                || !getSensing().hasLineOfSight(pendingPunchTarget)) {
            return;
        }

        if (!punchImpactSoundPlayed) {
            playPunchImpactSound();
        }
        if (level() instanceof ServerLevel serverLevel) {
            pendingPunchTarget.hurtServer(serverLevel, damageSources().mobAttack(this),
                    (float) getAttributeValue(Attributes.ATTACK_DAMAGE));
        }
        pendingPunchTarget.knockback(PUNCH_KNOCKBACK_STRENGTH, getX() - pendingPunchTarget.getX(), getZ() - pendingPunchTarget.getZ());
    }

    private void playPunchImpactSound() {
        if (level().isClientSide() || pendingPunchTarget == null || !pendingPunchTarget.isAlive()) {
            return;
        }

        if (distanceToSqr(pendingPunchTarget) > PUNCH_HIT_RANGE * PUNCH_HIT_RANGE
                || !getSensing().hasLineOfSight(pendingPunchTarget)) {
            return;
        }

        level().playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.HOSTILE,
                PUNCH_SOUND_VOLUME, PUNCH_SOUND_PITCH);
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.MUD_HIT, SoundSource.HOSTILE, 0.35F, 0.75F);
        punchImpactSoundPlayed = true;
    }

    private void cancelPunch() {
        pendingPunchTarget = null;
        activePunchTicks = 0;
        punchImpactTicks = 0;
        punchHitThisPunch = false;
        punchImpactSoundPlayed = false;
        // See the matching comment in cancelBowShot(): without this, a stale
        // DATA_GUARD_PUNCH_START_TICK value can replay the punch animation purely visually if the
        // client's tracking of this entity ever gets refreshed after this punch is long over.
        if (!level().isClientSide()) {
            entityData.set(DATA_GUARD_PUNCH_START_TICK, 0L);
        }
    }

    @Override
    protected void resetTransientCombatState() {
        super.resetTransientCombatState();
        // cancelBowShot()/cancelPunch() clear the server-side AI counters, but not the
        // client-visual animation timers or the synced start-tick data those animations key off
        // of — without this, a mob (re)loaded mid-swing would render mid-swing forever, since
        // nothing would ever drive those counters back down to 0 on their own.
        cancelBowShot();
        cancelPunch();
        setBowAiming(false);
        if (!level().isClientSide()) {
            entityData.set(DATA_GUARD_SHOT_START_TICK, 0L);
            entityData.set(DATA_GUARD_PUNCH_START_TICK, 0L);
        }
    }

    private boolean isPreparingPunch() {
        return pendingPunchTarget != null;
    }

    private boolean canPunch(LivingEntity target) {
        return punchCooldownTicks <= 0
                && target.isAlive()
                && distanceToSqr(target) <= PUNCH_RANGE * PUNCH_RANGE
                && getSensing().hasLineOfSight(target);
    }

    private void tickPunchCooldown() {
        if (punchCooldownTicks > 0) {
            punchCooldownTicks--;
        }
    }

    private boolean turnBodyToward(LivingEntity target) {
        return turnBodyToward(target, AIM_TURN_SPEED);
    }

    private boolean turnBodyToward(LivingEntity target, float turnSpeed) {
        double xOffset = target.getX() - getX();
        double zOffset = target.getZ() - getZ();
        float targetYaw = (float) (Mth.atan2(zOffset, xOffset) * Mth.RAD_TO_DEG) - 90.0F;
        setYRot(Mth.rotateIfNecessary(getYRot(), targetYaw, turnSpeed));
        yBodyRot = getYRot();
        yHeadRot = getYRot();
        float yawDifference = Mth.wrapDegrees(targetYaw - getYRot());
        return Math.abs(yawDifference) <= MAX_SHOOT_ANGLE;
    }

    private void visuallyTrackShotTarget(LivingEntity target) {
        getLookControl().setLookAt(target, 45.0F, 45.0F);
        turnBodyToward(target, SHOOT_VISUAL_TRACK_TURN_SPEED);
    }

    private void playShootAnimation() {
        if (!level().isClientSide()) {
            entityData.set(DATA_GUARD_SHOT_START_TICK, level().getGameTime());
        }
    }

    private void playPunchAnimation() {
        if (!level().isClientSide()) {
            entityData.set(DATA_GUARD_PUNCH_START_TICK, level().getGameTime());
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new SynchronizedAnimationController<>(this, "Guard Movement", 5, this::animateMovement));
    }

    private PlayState animateMovement(AnimationTest<OgreGuard> state) {
        long shotStart = entityData.get(DATA_GUARD_SHOT_START_TICK);
        long punchStart = entityData.get(DATA_GUARD_PUNCH_START_TICK);
        int observedType = punchStart > shotStart ? 2 : shotStart > 0L ? 1 : 0;
        long observedStart = observedType == 2 ? punchStart : shotStart;
        int visualType = visualOneShot.update(state.controller(), observedType, observedStart,
                id -> (id == 2 ? PUNCH_ANIMATION_TICKS : SHOOT_ANIMATION_TICKS)
                        + ONE_SHOT_TRANSITION_TICKS,
                level().getGameTime());
        if (visualType > 0) {
            return state.setAndContinue(visualType == 2 ? PUNCH : SHOOT);
        }

        // getTarget() is server-only and always null when this runs client-side for rendering —
        // DATA_GUARD_HAS_TARGET is the synced stand-in so the aggro'd branch actually triggers.
        if (entityData.get(DATA_GUARD_HAS_TARGET)) {
            idleAnimationTicks = 0;
            rareIdleTicks = 0;
            lastAnimationTick = tickCount;
            // isCombatMoving() is a 12-tick synced latch armed the instant the goal issues a path,
            // before the guard has actually moved. OR-ing it in let it force COMBAT_WALK on its
            // own, so the frame the throw/punch one-shot released — with the goal re-pathing on
            // that very tick — he popped into a run-in-place before settling. Same fix already
            // applied to OgreArcher: the latch may only choose WHICH clip, never whether
            // locomotion plays at all. Here COMBAT_WALK is already the only locomotion clip in the
            // aggro branch, so the real movement test is all that is needed.
            if (isMovingForAnimation(state)) {
                return state.setAndContinue(COMBAT_WALK);
            }
            return state.setAndContinue(IDLE);
        }

        if (isRunMovementState() && isMovingForAnimation(state)) {
            idleAnimationTicks = 0;
            rareIdleTicks = 0;
            lastAnimationTick = tickCount;
            return state.setAndContinue(COMBAT_WALK);
        }

        if (isMovingForAnimation(state)) {
            idleAnimationTicks = 0;
            rareIdleTicks = 0;
            lastAnimationTick = tickCount;
            return state.setAndContinue(WALK);
        }

        updateIdleAnimationTimers(state);
        return state.setAndContinue(IDLE);
    }

    private void updateIdleAnimationTimers(AnimationTest<OgreGuard> state) {
        if (lastAnimationTick == tickCount) {
            return;
        }

        int tickDelta = lastAnimationTick < 0 ? 1 : Math.max(1, tickCount - lastAnimationTick);
        lastAnimationTick = tickCount;

        if (rareIdleTicks > 0) {
            rareIdleTicks = Math.max(0, rareIdleTicks - tickDelta);
            return;
        }

        idleAnimationTicks += tickDelta;
        if (idleAnimationTicks >= 160 && getRandom().nextInt(240) == 0) {
            idleAnimationTicks = 0;
            rareIdleTicks = 40;
            state.controller().reset();
        }
    }

    /**
     * OgreGrunt#locomotionGraceTicks() only hands out the longer combat window when the synced
     * movement state is CHASING or ATTACKING. Both of those are set by the inherited grunt melee
     * goal (CHASING needs isAggressive(); ATTACKING needs activeGruntAttackId), and OgreGuard
     * replaces registerGoals() outright with OgreGuardShootGoal, which never sets either. A guard
     * closing on his target therefore reported MOVEMENT_WALKING and armed only the short 2-tick
     * window, while every ogre that keeps the melee goal — and every ogre in the 1.21.1 reference,
     * which armed a flat 4 unconditionally — got 4. That halved the bridge between "the shoot goal
     * halted him" and "the throw/punch one-shot is up", which is the pre-attack idle beat.
     *
     * <p>This only sizes the grace window; it does not make anything play locomotion on its own,
     * so it does not reintroduce the removed isCombatMoving() locomotion latch.</p>
     */
    @Override
    protected int locomotionGraceTicks() {
        return entityData.get(DATA_GUARD_HAS_TARGET)
                ? COMBAT_MOVEMENT_ANIMATION_GRACE_TICKS
                : super.locomotionGraceTicks();
    }

    private boolean isMovingForAnimation(AnimationTest<OgreGuard> state) {
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

    private boolean hasActiveCombatTarget() {
        LivingEntity target = getTarget();
        return target != null && target.isAlive();
    }

    private void markCombatMovementForAnimation() {
        combatMovementAnimationTicks = 12;
        setCombatMoving(true);
    }

    private void setCombatMoving(boolean combatMoving) {
        entityData.set(DATA_GUARD_COMBAT_MOVING, combatMoving);
    }

    private boolean isCombatMoving() {
        return entityData.get(DATA_GUARD_COMBAT_MOVING);
    }

    private static class OgreGuardShootGoal extends Goal {
        private final OgreGuard guard;
        private final double speedModifier;
        private int ticksUntilNextPathRecalculation;
        private int ticksUntilNextMeleeApproachPath;
        private int ticksUntilNextShot;
        private Vec3 retreatTargetPos;
        private int lowHealthRetreatTicks;
        private boolean lowHealthRetreating;
        private int engagementReactionTicks;
        private LivingEntity engagedTarget;
        private Vec3 lastPursuitTargetPos;

        private OgreGuardShootGoal(OgreGuard guard, double speedModifier) {
            this.guard = guard;
            this.speedModifier = speedModifier;
            this.ticksUntilNextShot = guard.getRandom().nextInt(MIN_SHOT_COOLDOWN_TICKS);
            setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = guard.getTarget();
            return target != null && target.isAlive();
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity target = guard.getTarget();
            return guard.isPreparingPunch()
                    || guard.isPreparingBowShot()
                    || target != null
                    && target.isAlive()
                    && (!(target instanceof Player player) || !player.isSpectator() && !player.isCreative())
                    && guard.isWithinHome(target.blockPosition());
        }

        @Override
        public void stop() {
            guard.getNavigation().stop();
            guard.setBowAiming(false);
            guard.cancelPunch();
            guard.cancelBowShot();
            ticksUntilNextPathRecalculation = 0;
            ticksUntilNextMeleeApproachPath = 0;
            retreatTargetPos = null;
            lowHealthRetreatTicks = 0;
            lowHealthRetreating = false;
            engagementReactionTicks = 0;
            engagedTarget = null;
            lastPursuitTargetPos = null;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity target = guard.getTarget();
            guard.tickPunchCooldown();

            if (target == null) {
                engagedTarget = null;
                guard.setBowAiming(false);
                guard.setCombatMoving(false);
                guard.tickPendingPunch();
                guard.tickPendingBowShot();
                return;
            }

            // Tracked by identity rather than relying on Goal#start(), which won't fire again if
            // this goal never actually stopped between engagements (e.g. it was still finishing
            // a punch's follow-through against the old target when a new one was acquired) — a
            // freshly (re)acquired target always gets the reaction pause, however it happened.
            if (target != engagedTarget) {
                engagedTarget = target;
                engagementReactionTicks = adjustedTickDelay(ENGAGEMENT_REACTION_TICKS);
                ticksUntilNextShot = Math.max(ticksUntilNextShot, engagementReactionTicks);
            }

            guard.getLookControl().setLookAt(target, 30.0F, 30.0F);
            double distanceToTargetSqr = guard.distanceToSqr(target);
            boolean hasLineOfSight = guard.getSensing().hasLineOfSight(target);
            boolean withinMeleeRange = distanceToTargetSqr < MELEE_ENGAGE_RANGE * MELEE_ENGAGE_RANGE;

            if (guard.isPreparingPunch()) {
                guard.setBowAiming(false);
                guard.turnBodyToward(target);
                guard.getNavigation().stop();
                guard.tickPendingPunch();
                return;
            }

            if (guard.isPreparingBowShot()) {
                guard.visuallyTrackShotTarget(target);
                guard.getNavigation().stop();
                guard.tickPendingBowShot();
                return;
            }

            engagementReactionTicks = Math.max(engagementReactionTicks - 1, 0);

            // One tactical fallback per guard lifetime. Once firing range is restored (or the
            // pathfinding failsafe expires), normal melee/ranged combat resumes permanently.
            boolean isLowHealth = guard.getHealth() <= guard.getMaxHealth() * LOW_HEALTH_RETREAT_HEALTH_FRACTION;
            if (isLowHealth && !guard.lowHealthRetreatUsed) {
                guard.lowHealthRetreatUsed = true;
                lowHealthRetreating = true;
                lowHealthRetreatTicks = adjustedTickDelay(LOW_HEALTH_RETREAT_TICKS);
                retreatTargetPos = null;
            }

            if (lowHealthRetreating) {
                boolean reachedFiringRange = distanceToTargetSqr
                        >= LOW_HEALTH_RETREAT_STOP_RANGE * LOW_HEALTH_RETREAT_STOP_RANGE;
                if (reachedFiringRange || lowHealthRetreatTicks <= 0) {
                    lowHealthRetreating = false;
                    retreatTargetPos = null;
                    guard.getNavigation().stop();
                } else {
                    guard.setBowAiming(false);
                    lowHealthRetreatTicks--;
                    ticksUntilNextMeleeApproachPath = 0;
                    tickLowHealthRetreat(target);
                    ticksUntilNextShot = Math.max(ticksUntilNextShot, adjustedTickDelay(8));
                    return;
                }
            }

            boolean inShootRange = distanceToTargetSqr <= SHOOT_RANGE * SHOOT_RANGE;

            if (withinMeleeRange) {
                guard.setBowAiming(false);
                guard.turnBodyToward(target);
                retreatTargetPos = null;
                ticksUntilNextMeleeApproachPath = Math.max(ticksUntilNextMeleeApproachPath - 1, 0);
                if (engagementReactionTicks <= 0 && guard.canPunch(target)) {
                    guard.getNavigation().stop();
                    guard.beginPunch(target);
                } else if (distanceToTargetSqr <= PUNCH_RANGE * PUNCH_RANGE) {
                    guard.getNavigation().stop();
                } else if (ticksUntilNextMeleeApproachPath <= 0 || guard.getNavigation().isDone()) {
                    guard.getNavigation().moveTo(target, speedModifier);
                    guard.markCombatMovementForAnimation();
                    ticksUntilNextMeleeApproachPath = adjustedTickDelay(4);
                }

                ticksUntilNextShot = Math.max(ticksUntilNextShot, adjustedTickDelay(12));
                return;
            }

            ticksUntilNextMeleeApproachPath = 0;
            retreatTargetPos = null;

            if (!hasLineOfSight || !inShootRange) {
                guard.setBowAiming(false);
                ticksUntilNextPathRecalculation = Math.max(ticksUntilNextPathRecalculation - 1, 0);
                // Only tear up a still-valid path once the target has actually moved (or the old
                // path ran out) — repathing straight at their exact live position every time the
                // timer ticks over, even when they've barely shifted, is what reads as zigzagging.
                Vec3 targetPos = target.position();
                boolean targetMoved = lastPursuitTargetPos == null
                        || lastPursuitTargetPos.distanceToSqr(targetPos) > 1.0;
                if (ticksUntilNextPathRecalculation <= 0
                        && (targetMoved || guard.getNavigation().isDone())) {
                    boolean pathing = guard.getNavigation().moveTo(target, speedModifier);
                    if (pathing) {
                        guard.markCombatMovementForAnimation();
                    }
                    lastPursuitTargetPos = targetPos;
                    ticksUntilNextPathRecalculation = adjustedTickDelay(10);
                }
            } else {
                guard.setBowAiming(true);
                guard.getNavigation().stop();
                lastPursuitTargetPos = null;
            }

            // Vanilla ground navigation must own body yaw while still closing distance — forcing
            // it toward the target here fights the path steering every tick (see the identical
            // fix already applied to OgreArcherShootGoal). Only take over facing once actually in
            // shoot range and about to aim/fire.
            boolean facingTarget = hasLineOfSight && inShootRange && guard.turnBodyToward(target);
            ticksUntilNextShot = Math.max(ticksUntilNextShot - 1, 0);
            if (ticksUntilNextShot <= 0
                    && hasLineOfSight
                    && inShootRange
                    && facingTarget
                    && guard.hasClearShotToTarget(target)) {
                guard.beginBowShot(target);
                ticksUntilNextShot = adjustedTickDelay(MIN_SHOT_COOLDOWN_TICKS + guard.getRandom().nextInt(SHOT_COOLDOWN_RANDOM_TICKS));
            } else if (ticksUntilNextShot <= 0 && hasLineOfSight && inShootRange && facingTarget) {
                ticksUntilNextShot = adjustedTickDelay(8);
            }
        }

        private void tickLowHealthRetreat(LivingEntity target) {
            boolean needsNewRetreatTarget = retreatTargetPos == null
                    || guard.distanceToSqr(retreatTargetPos) < 2.0
                    || guard.getNavigation().isDone();

            if (needsNewRetreatTarget) {
                Vec3 retreatPos = DefaultRandomPos.getPosAway(
                        guard,
                        (int) Math.ceil(LOW_HEALTH_RETREAT_DISTANCE),
                        4,
                        target.position());
                if (retreatPos == null) {
                    Vec3 away = guard.position().subtract(target.position());
                    if (away.horizontalDistanceSqr() < 1.0E-4) {
                        away = Vec3.directionFromRotation(0.0F, guard.getYRot());
                    }

                    Vec3 horizontalAway = new Vec3(away.x, 0.0, away.z).normalize();
                    retreatPos = guard.position().add(horizontalAway.scale(LOW_HEALTH_RETREAT_DISTANCE));
                }

                retreatTargetPos = retreatPos;
            }

            guard.getNavigation().moveTo(retreatTargetPos.x, retreatTargetPos.y, retreatTargetPos.z, LOW_HEALTH_RETREAT_SPEED);
            guard.markCombatMovementForAnimation();
        }
    }
}
