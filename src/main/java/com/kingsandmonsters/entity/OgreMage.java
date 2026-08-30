package com.kingsandmonsters.entity;

import com.kingsandmonsters.Config;
import com.kingsandmonsters.ModEntities;
import com.kingsandmonsters.ModSoundEvents;
import com.kingsandmonsters.entity.animation.SynchronizedAnimationController;
import com.kingsandmonsters.entity.animation.CanonicalOneShotState;
import com.kingsandmonsters.world.OgreTrialSpawnerProcessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntitySpawnReason;
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
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.TrialSpawnerBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.object.LoopType;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.state.AnimationTest;
import com.geckolib.animation.object.PlayState;
import com.geckolib.animation.RawAnimation;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class OgreMage extends OgreGrunt {
    private static final int AMBIENT_HUFF_MIN_COOLDOWN_TICKS = 600;
    private static final int AMBIENT_HUFF_RANDOM_COOLDOWN_TICKS = 500;
    private static final int AMBIENT_HUFF_CHANCE = 80;
    private static final int LEVEL_5_XP_REWARD = 55;
    private static final EntityDataAccessor<Long> DATA_MAGE_CAST_START_TICK =
            SynchedEntityData.defineId(OgreMage.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> DATA_MAGE_CAST_ID =
            SynchedEntityData.defineId(OgreMage.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_MAGE_RETREATING =
            SynchedEntityData.defineId(OgreMage.class, EntityDataSerializers.BOOLEAN);
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation RARE_IDLE = RawAnimation.begin().then("idle2", LoopType.PLAY_ONCE);
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation RUN = RawAnimation.begin().thenLoop("run");
    // These identifiers intentionally match the current Blockbench export,
    // including capitalization and the trailing space in "Bolt ".
    private static final RawAnimation CHANNEL = RawAnimation.begin().thenPlay("Channel animation");
    private static final RawAnimation TOSS_ANIMATION = RawAnimation.begin().thenPlay("toss");
    private static final RawAnimation MAGIC_PUSH_ANIMATION = RawAnimation.begin().thenPlay("Magic push");
    private static final RawAnimation BOLT_ANIMATION = RawAnimation.begin().thenPlay("Bolt ");
    private static final RawAnimation SMACK_ANIMATION = RawAnimation.begin().thenPlay("Smack");
    private static final RawAnimation DRINK_ANIMATION = RawAnimation.begin().thenPlay("drink");
    // Matches the "Mage Movement" controller's transition length. SynchronizedAnimationController
    // deliberately keeps GeckoLib 5's leading transition stage inside the elapsed clock, so elapsed
    // tick N is clip-local tick N-5 and expiring the visual one-shot at CastType.animationTicks
    // (which equals each authored clip's raw length) cut its last 5 ticks off. Applied ONLY at the
    // animation call site: CastType.animationTicks also drives the server-side activeCastTicks
    // counter and every release timing, which must stay exactly as tuned.
    private static final int ONE_SHOT_TRANSITION_TICKS = 5;
    private static final int FOG_COOLDOWN_MIN_TICKS = 220;
    private static final int FOG_COOLDOWN_RANDOM_TICKS = 80;
    private static final int BOLT_COOLDOWN_TICKS = 60;
    private static final double BOLT_RANGE = 20.0;
    private static final double BOLT_RELEASE_HEIGHT_OFFSET = 1.5;
    private static final double BOLT_RELEASE_FORWARD_OFFSET = 1.0;
    private static final double BOLT_RELEASE_SIDE_OFFSET = 1.22;
    private static final int POTION_COOLDOWN_TICKS = 80;
    private static final double POTION_RANGE = 6.0;
    private static final double SUPPORT_RANGE = POTION_RANGE;
    private static final double POTION_RELEASE_SIDE_OFFSET = -1.10;
    private static final double POTION_RELEASE_FORWARD_OFFSET = 0.4;
    private static final double POTION_RELEASE_HEIGHT_OFFSET = 1.28;
    private static final float POTION_THROW_SPEED = 1.15F;
    private static final double POTION_THROW_ARC_PER_BLOCK = 0.18;
    private static final float POTION_THROW_INACCURACY = 2.0F;
    private static final int MAGIC_PUSH_COOLDOWN_TICKS = 260;
    private static final double MAGIC_PUSH_RANGE = 5.75;
    private static final int MAGIC_PUSH_PRESSURE_TICKS = 28;
    private static final double MAGIC_PUSH_CONE_DOT = 0.68;
    // Utility knockback spell — kept light (~1 heart vs diamond) since its value is the push, not damage.
    private static final float MAGIC_PUSH_DAMAGE = 8.0F;
    private static final double MAGIC_PUSH_KNOCKBACK_STRENGTH = 4.675;
    private static final double MAGIC_PUSH_VERTICAL_LIFT = 1.35;
    private static final double SMACK_RANGE = 3.35;
    // Close-range melee fallback. Kept below the Mage's magic/control kit in raw damage — his
    // threat should come from Bogfume Bolt/poison, not this fallback swing.
    private static final float SMACK_DAMAGE = 14.5F;
    private static final double SMACK_KNOCKBACK_STRENGTH = 0.55;
    private static final int SMACK_POISON_TICKS = 80;
    // Extra cooldown beyond the cast's own animation time, since the smack hits hard enough now
    // that letting it chain rapidly (e.g. repeatedly during the post-push melee window) is too fast.
    private static final int SMACK_COOLDOWN_TICKS = 25;
    private static final float LOW_HEALTH_RETREAT_RATIO = 0.35F;
    private static final double RETREAT_RANGE = 7.0;
    private static final double RETREAT_DISTANCE = 7.0;
    // Base movement remains 0.189 for calm wandering. This navigation multiplier produces
    // approximately the archer's 0.288 movement pace only while the mage is in combat.
    private static final double COMBAT_SPEED_MODIFIER = 0.288 / 0.189;
    private static final double RETREAT_SPEED = COMBAT_SPEED_MODIFIER;
    private static final int RETREAT_REPATH_TICKS = 24;
    // The locomotion grace window is OgreGrunt#MOVEMENT_ANIMATION_GRACE_TICKS, shared by every ogre.
    private static final float CAST_TURN_SPEED = 10.0F;
    private static final float MAGIC_PUSH_TRACK_TURN_SPEED = 85.0F;
    private static final double CAST_RANGE = 18.0;
    private static final int POTION_PROJECTILE_SPAWN_DELAY_TICKS = 2;
    private static final int POISON_FOG_CIRCLE_DELAY_TICKS = 4;
    private static final float POISON_FOG_CHANNEL_SOUND_VOLUME = 2.5F;
    private static final int HEAL_DRINK_COOLDOWN_TICKS = 1200;
    private static final int HEAL_DRINK_SOUND_START_TICKS = 10;
    private static final int HEAL_DRINK_SOUND_INTERVAL_TICKS = 4;
    private static final float HEAL_DRINK_MAX_HEALTH_FRACTION = 0.25F;
    private static final String ROYAL_SQUAD_GRUNTS_SPAWNED_TAG = "KingsAndMonstersRoyalSquadGruntsSpawned";
    private static final String ROYAL_SQUAD_ARCHERS_SPAWNED_TAG = "KingsAndMonstersRoyalSquadArchersSpawned";
    private static final int ROYAL_SQUAD_GRUNT_COUNT = 2;
    private static final int ROYAL_SQUAD_ARCHER_COUNT = 1;

    private int idleAnimationTicks;
    private int rareIdleTicks;
    private int castAnimationTicks;
    private int activeCastTicks;
    private int castReleaseTicks;
    private int healDrinkCooldownTicks;
    private int ambientHuffCooldownTicks = AMBIENT_HUFF_MIN_COOLDOWN_TICKS;
    private int lastAnimationTick = -1;
    private int movementAnimationGraceTicks;
    private int lastMovementGraceTick = -1;
    private boolean castReleased;
    private boolean startingCastAnimation;
    private RawAnimation activeCastAnimation = CHANNEL;
    private MageCast pendingCast;
    private MageCast queuedPotionThrow;
    private int queuedPotionThrowTicks;
    private PoisonFogCloud pendingPoisonCloud;
    private final CanonicalOneShotState visualOneShot = new CanonicalOneShotState();
    private boolean royalTrialSquadLeader;
    private int royalSquadGruntsSpawned;
    private int royalSquadArchersSpawned;

    public OgreMage(EntityType<? extends OgreMage> type, Level level) {
        super(type, level);
        ensureCaptainTitle();
        avoidWaterPathfinding();
        xpReward = LEVEL_5_XP_REWARD;
    }

    private void ensureCaptainTitle() {
        if (!hasCustomName()) {
            setCustomName(Component.translatable("title.kingsandmonsters.mage_captain"));
        }
        setCustomNameVisible(true);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!level().isClientSide()) {
            tickAmbientHuff();
            deployRoyalTrialSquadIfNeeded();
            healDrinkCooldownTicks = Math.max(healDrinkCooldownTicks - 1, 0);
            tickQueuedPotionThrow();
        }
    }

    private void tickAmbientHuff() {
        if (ambientHuffCooldownTicks > 0) {
            ambientHuffCooldownTicks--;
            return;
        }
        if (getTarget() != null || isAggressive() || pendingCast != null || castAnimationTicks > 0
                || getRandom().nextInt(AMBIENT_HUFF_CHANCE) != 0) {
            return;
        }

        level().playSound(null, getX(), getY(), getZ(),
                ModSoundEvents.OGRE_MAGE_HUFF.get(), SoundSource.HOSTILE, 0.8F, 0.82F);
        ambientHuffCooldownTicks = AMBIENT_HUFF_MIN_COOLDOWN_TICKS
                + getRandom().nextInt(AMBIENT_HUFF_RANDOM_COOLDOWN_TICKS + 1);
    }

    @Override
    public void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean(OgreTrialSpawnerProcessor.ROYAL_MAGE_SQUAD_TAG, royalTrialSquadLeader);
        tag.putInt(ROYAL_SQUAD_GRUNTS_SPAWNED_TAG, royalSquadGruntsSpawned);
        tag.putInt(ROYAL_SQUAD_ARCHERS_SPAWNED_TAG, royalSquadArchersSpawned);
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput tag) {
        super.readAdditionalSaveData(tag);
        ensureCaptainTitle();
        royalTrialSquadLeader = tag.getBooleanOr(OgreTrialSpawnerProcessor.ROYAL_MAGE_SQUAD_TAG, false);
        royalSquadGruntsSpawned = Mth.clamp(
                tag.getIntOr(ROYAL_SQUAD_GRUNTS_SPAWNED_TAG, 0), 0, ROYAL_SQUAD_GRUNT_COUNT);
        royalSquadArchersSpawned = Mth.clamp(
                tag.getIntOr(ROYAL_SQUAD_ARCHERS_SPAWNED_TAG, 0), 0, ROYAL_SQUAD_ARCHER_COUNT);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 100.0)
                .add(Attributes.ATTACK_DAMAGE, 6.0)
                .add(Attributes.MOVEMENT_SPEED, 0.189)
                .add(Attributes.FOLLOW_RANGE, 30.0)
                .add(Attributes.ARMOR, 10.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.65)
                .add(Attributes.ATTACK_KNOCKBACK, 0.45)
                .add(Attributes.STEP_HEIGHT, 1.0)
                .add(Attributes.SAFE_FALL_DISTANCE, 4.0)
                // See OgreGrunt#createAttributes — default 0 caps water acceleration at a tiny fixed
                // baseline regardless of requested speed, which would make applyShallowWaterSpeedFactor's
                // 50% reduction meaningless since he's already moving slower than that.
                .add(Attributes.WATER_MOVEMENT_EFFICIENCY, 1.0);
    }

    private void deployRoyalTrialSquadIfNeeded() {
        if (!royalTrialSquadLeader
                || royalSquadGruntsSpawned >= ROYAL_SQUAD_GRUNT_COUNT
                && royalSquadArchersSpawned >= ROYAL_SQUAD_ARCHER_COUNT
                || !(level() instanceof ServerLevel serverLevel)) {
            return;
        }

        TrialSpawnerBlockEntity owner = findOwningTrialSpawner(serverLevel);
        if (owner == null) {
            return;
        }

        while (royalSquadGruntsSpawned < ROYAL_SQUAD_GRUNT_COUNT) {
            if (!spawnTrackedSquadMember(serverLevel, owner, ModEntities.OGRE_GRUNT.get())) {
                break;
            }
            royalSquadGruntsSpawned++;
        }
        while (royalSquadArchersSpawned < ROYAL_SQUAD_ARCHER_COUNT) {
            if (!spawnTrackedSquadMember(serverLevel, owner, ModEntities.OGRE_ARCHER.get())) {
                break;
            }
            royalSquadArchersSpawned++;
        }
    }

    public boolean isRoyalTrialSquadLeader() {
        return royalTrialSquadLeader;
    }

    private TrialSpawnerBlockEntity findOwningTrialSpawner(ServerLevel level) {
        BlockPos center = blockPosition();
        for (BlockPos candidate : BlockPos.betweenClosed(
                center.offset(-6, -3, -6), center.offset(6, 3, 6))) {
            if (level.getBlockEntity(candidate) instanceof TrialSpawnerBlockEntity spawner
                    && isTrackedBySpawner(level, spawner, getUUID())) {
                return spawner;
            }
        }
        return null;
    }

    private boolean spawnTrackedSquadMember(
            ServerLevel level,
            TrialSpawnerBlockEntity owner,
            EntityType<? extends OgreGrunt> entityType) {
        OgreGrunt member = entityType.create(level, EntitySpawnReason.EVENT);
        if (member == null) {
            return false;
        }

        BlockPos spawnerPos = owner.getBlockPos();
        for (int attempt = 0; attempt < 20; attempt++) {
            double x = spawnerPos.getX() + 0.5 + (getRandom().nextDouble() - getRandom().nextDouble()) * 4.5;
            double y = spawnerPos.getY() + getRandom().nextInt(3) - 1;
            double z = spawnerPos.getZ() + 0.5 + (getRandom().nextDouble() - getRandom().nextDouble()) * 4.5;
            member.snapTo(x, y, z, getRandom().nextFloat() * 360.0F, 0.0F);
            if (!level.noCollision(member) || !member.checkSpawnObstruction(level)) {
                continue;
            }

            EventHooks.finalizeMobSpawnSpawner(
                    member,
                    level,
                    level.getCurrentDifficultyAt(member.blockPosition()),
                    EntitySpawnReason.TRIAL_SPAWNER,
                    null,
                    owner.getTrialSpawner(),
                    true);
            member.setPersistenceRequired();
            if (!level.addFreshEntity(member)) {
                return false;
            }
            addTrackedMob(level, owner, member.getUUID());
            owner.getTrialSpawner().markUpdated();
            return true;
        }
        return false;
    }

    private static boolean isTrackedBySpawner(
            ServerLevel level,
            TrialSpawnerBlockEntity spawner,
            java.util.UUID entityId) {
        CompoundTag savedData = spawner.saveWithoutMetadata(level.registryAccess());
        return savedData.read("current_mobs", UUIDUtil.CODEC_SET)
                .orElseGet(Set::of).contains(entityId);
    }

    private static void addTrackedMob(
            ServerLevel level,
            TrialSpawnerBlockEntity spawner,
            java.util.UUID entityId) {
        CompoundTag savedData = spawner.saveWithoutMetadata(level.registryAccess());
        Set<java.util.UUID> trackedMobs = new HashSet<>(savedData.read("current_mobs", UUIDUtil.CODEC_SET)
                .orElseGet(Set::of));
        trackedMobs.add(entityId);
        savedData.store("current_mobs", UUIDUtil.CODEC_SET, trackedMobs);
        spawner.loadCustomOnly(net.minecraft.world.level.storage.TagValueInput.create(
                net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess(), savedData));
    }

    @Override
    public void applyConfiguredCombatAttributes(boolean healToMax) {
        if (!Config.isLoaded()) {
            return;
        }

        setAttributeBaseValue(Attributes.MAX_HEALTH, Config.OGRE_MAGE_MAX_HEALTH.get());
        setAttributeBaseValue(Attributes.ATTACK_DAMAGE, Config.OGRE_MAGE_ATTACK_DAMAGE.get());
        setAttributeBaseValue(Attributes.MOVEMENT_SPEED, Config.OGRE_MAGE_MOVEMENT_SPEED.get());
        setAttributeBaseValue(Attributes.FOLLOW_RANGE, Config.OGRE_MAGE_FOLLOW_RANGE.get());
        setAttributeBaseValue(Attributes.ARMOR, Config.OGRE_MAGE_ARMOR.get());
        applyConfiguredHealth(healToMax);
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return ModSoundEvents.OGRE_MAGE_HURT.get();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_MAGE_CAST_START_TICK, 0L);
        builder.define(DATA_MAGE_CAST_ID, 0);
        builder.define(DATA_MAGE_RETREATING, false);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new OgreMageCombatGoal(this, COMBAT_SPEED_MODIFIER));
        goalSelector.addGoal(4, new GuardSuperiorGoal(this));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0f));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));

        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        registerTerritorialTargetGoal();
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new SynchronizedAnimationController<>(this, "Mage Movement", 5, this::animateMage));
    }

    public ItemStack getVisiblePotionStack() {
        return getItemBySlot(EquipmentSlot.OFFHAND);
    }

    private void beginPoisonFogCast(LivingEntity target) {
        faceToward(target);
        beginCast(new MageCast(CastType.POISON_FOG, target));
        warnTargetedPlayer(target);
        // The warning circle is delayed slightly so it begins after the channel animation
        // is visibly underway and its warning phase ends closer to the animation's finish.
    }

    private void warnTargetedPlayer(LivingEntity target) {
        if (target instanceof ServerPlayer player) {
            player.sendOverlayMessage(Component.translatable("message.kingsandmonsters.ogre_shaman_targeting"));
        }
    }

    private void beginMagicPush(LivingEntity target) {
        faceToward(target);
        beginCast(new MageCast(CastType.MAGIC_PUSH, target));
    }

    private void beginBoltCast(LivingEntity target) {
        faceToward(target);
        beginCast(new MageCast(CastType.BOLT, target));
    }

    private void beginPotionThrow(MageCast cast) {
        faceToward(cast.target());
        beginCast(cast);
    }

    private void beginSmack(LivingEntity target) {
        faceToward(target);
        beginCast(new MageCast(CastType.SMACK, target));
    }

    private void beginHealingDrink() {
        ItemStack healingPotion = PotionContents.createItemStack(Items.POTION, Potions.HEALING);
        beginCast(new MageCast(CastType.HEAL_DRINK, this, healingPotion));
        healDrinkCooldownTicks = HEAL_DRINK_COOLDOWN_TICKS;
    }

    private void beginCast(MageCast cast) {
        pendingCast = cast;
        activeCastTicks = cast.type().animationTicks;
        castReleaseTicks = cast.type().releaseTicks;
        castReleased = false;
        setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        if (!cast.stack().isEmpty()) {
            setItemSlot(EquipmentSlot.OFFHAND, singlePotionStack(cast.stack()));
        }
        playCastAnimation(cast.type());
    }

    private void tickPendingCast() {
        if (pendingCast == null) {
            return;
        }

        if (pendingCast.type() != CastType.HEAL_DRINK) {
            float turnSpeed = pendingCast.type() == CastType.MAGIC_PUSH && !castReleased
                    ? MAGIC_PUSH_TRACK_TURN_SPEED
                    : CAST_TURN_SPEED;
            turnToward(pendingCast.target(), turnSpeed);
        }
        activeCastTicks--;
        int castElapsed = pendingCast.type().animationTicks - activeCastTicks;
        if (pendingCast.type() == CastType.POISON_FOG
                && castElapsed == POISON_FOG_CIRCLE_DELAY_TICKS) {
            spawnPendingPoisonCloud(pendingCast.target());
            level().playSound(
                    null,
                    getX(),
                    getY(),
                    getZ(),
                    SoundEvents.EVOKER_PREPARE_ATTACK,
                    SoundSource.HOSTILE,
                    POISON_FOG_CHANNEL_SOUND_VOLUME,
                    0.78F);
        }
        if (pendingCast.type() == CastType.HEAL_DRINK
                && castElapsed >= HEAL_DRINK_SOUND_START_TICKS
                && castElapsed < pendingCast.type().releaseTicks
                && (castElapsed - HEAL_DRINK_SOUND_START_TICKS) % HEAL_DRINK_SOUND_INTERVAL_TICKS == 0) {
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_DRINK, SoundSource.HOSTILE, 0.8F, 0.9F);
        }
        if (!castReleased && --castReleaseTicks <= 0) {
            releasePendingCast();
        }

        if (activeCastTicks <= 0) {
            pendingCast = null;
            pendingPoisonCloud = null;
            activeCastTicks = 0;
            castReleaseTicks = 0;
            castReleased = false;
            setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            clearSyncedCastAnimation();
        }
    }

    private void releasePendingCast() {
        if (pendingCast == null || castReleased) {
            return;
        }

        castReleased = true;
        if (pendingCast.type() == CastType.POTION) {
            // Both ally buffs and hostile debuffs use this identical release path:
            // hide the held bottle at the authored release pose, then allow two
            // synchronization ticks before spawning the thrown bottle from that hand.
            setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            queuedPotionThrow = pendingCast;
            queuedPotionThrowTicks = POTION_PROJECTILE_SPAWN_DELAY_TICKS;
        } else if (pendingCast.type() == CastType.BOLT) {
            shootBogfumeBolt();
        } else if (pendingCast.type() == CastType.SMACK) {
            applySmackImpact();
        } else if (pendingCast.type() == CastType.MAGIC_PUSH) {
            applyMagicPushImpact();
        } else if (pendingCast.type() == CastType.HEAL_DRINK) {
            setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            heal(getMaxHealth() * HEAL_DRINK_MAX_HEALTH_FRACTION);
        }
    }

    private void tickQueuedPotionThrow() {
        if (queuedPotionThrow == null || --queuedPotionThrowTicks > 0) {
            return;
        }

        throwPotion(queuedPotionThrow);
        queuedPotionThrow = null;
        queuedPotionThrowTicks = 0;
    }

    private void throwPotion(MageCast cast) {
        if (cast == null || cast.stack().isEmpty() || level().isClientSide() || !cast.target().isAlive()) {
            return;
        }

        Vec3 releasePos = getPotionReleasePosition();
        ThrownSplashPotion potion = new ThrownSplashPotion(level(), this, singlePotionStack(cast.stack()));
        potion.setPos(releasePos.x, releasePos.y, releasePos.z);

        double xOffset = cast.target().getX() - releasePos.x;
        double zOffset = cast.target().getZ() - releasePos.z;
        double horizontalDistance = Math.sqrt(xOffset * xOffset + zOffset * zOffset);
        double yOffset = cast.target().getY(0.4) - releasePos.y;
        potion.shoot(
                xOffset,
                yOffset + horizontalDistance * POTION_THROW_ARC_PER_BLOCK,
                zOffset,
                POTION_THROW_SPEED,
                POTION_THROW_INACCURACY);
        level().addFreshEntity(potion);
    }

    private Vec3 getPotionReleasePosition() {
        Vec3 forward = Vec3.directionFromRotation(0.0F, yBodyRot);
        Vec3 left = new Vec3(-forward.z(), 0.0, forward.x());
        return position()
                .add(left.scale(POTION_RELEASE_SIDE_OFFSET))
                .add(forward.scale(POTION_RELEASE_FORWARD_OFFSET))
                .add(0.0, POTION_RELEASE_HEIGHT_OFFSET, 0.0);
    }

    private java.util.Optional<MageCast> choosePotionThrowInRange(LivingEntity hostileTarget) {
        java.util.Optional<MageCast> supportCast = chooseSupportPotion();
        if (supportCast.isPresent()) {
            return supportCast;
        }

        if (distanceToSqr(hostileTarget) <= POTION_RANGE * POTION_RANGE) {
            return java.util.Optional.of(chooseDebuffPotion(hostileTarget));
        }

        return java.util.Optional.empty();
    }

    private java.util.Optional<MageCast> chooseSupportPotion() {
        java.util.List<MageCast> supportOptions = new java.util.ArrayList<>();
        java.util.List<OgreGrunt> allies = level().getEntitiesOfClass(
                OgreGrunt.class,
                getBoundingBox().inflate(SUPPORT_RANGE),
                ogre -> ogre != this && ogre.isAlive());

        for (OgreGrunt ally : allies) {
            if (ally.isOnFire() && !ally.hasEffect(MobEffects.FIRE_RESISTANCE)) {
                supportOptions.add(new MageCast(CastType.POTION, ally, createCustomSplashPotion(MobEffects.FIRE_RESISTANCE, 600, 0)));
            }

            if (!ally.hasEffect(MobEffects.STRENGTH)) {
                supportOptions.add(new MageCast(CastType.POTION, ally, createCustomSplashPotion(MobEffects.STRENGTH, 600, 0)));
            }

            if (!(ally instanceof OgreLord) && !ally.hasEffect(MobEffects.SPEED)) {
                supportOptions.add(new MageCast(CastType.POTION, ally, createCustomSplashPotion(MobEffects.SPEED, 600, 0)));
            }

            // Regeneration is a rarer heal-up, only considered for allies who are actually hurt and
            // don't already have it — the low roll chance (vs. the other buffs above, which are
            // added unconditionally when eligible) is what keeps it uncommon.
            if (ally.getHealth() < ally.getMaxHealth() * 0.7F
                    && !ally.hasEffect(MobEffects.REGENERATION)
                    && getRandom().nextInt(6) == 0) {
                supportOptions.add(new MageCast(CastType.POTION, ally, createCustomSplashPotion(MobEffects.REGENERATION, 200, 1)));
            }
        }

        if (supportOptions.isEmpty()) {
            return java.util.Optional.empty();
        }

        return java.util.Optional.of(supportOptions.get(getRandom().nextInt(supportOptions.size())));
    }

    private MageCast chooseDebuffPotion(LivingEntity target) {
        ItemStack potion;
        if (!target.hasEffect(MobEffects.WEAKNESS) && getRandom().nextInt(10) == 0) {
            potion = createCustomSplashPotion(MobEffects.WEAKNESS, 300, 0);
        } else {
            potion = getRandom().nextBoolean()
                    ? createCustomSplashPotion(MobEffects.POISON, 200, 1)
                    : createCustomSplashPotion(MobEffects.SLOWNESS, 30 * 20, 0);
        }
        return new MageCast(CastType.POTION, target, potion);
    }

    private ItemStack createCustomSplashPotion(Holder<MobEffect> effect, int durationTicks, int amplifier) {
        ItemStack stack = new ItemStack(Items.SPLASH_POTION);
        PotionContents contents = PotionContents.EMPTY.withEffectAdded(new MobEffectInstance(effect, durationTicks, amplifier));
        stack.set(DataComponents.POTION_CONTENTS, contents);
        return stack;
    }

    private ItemStack singlePotionStack(ItemStack stack) {
        ItemStack copy = stack.copy();
        copy.setCount(1);
        return copy;
    }

    private void applySmackImpact() {
        MageCast cast = pendingCast;
        if (cast == null || level().isClientSide() || !cast.target().isAlive()) {
            return;
        }

        // The swing sound is tied to the authored attack timeline and must play regardless of
        // whether the target is still close enough / in sight for the hit to actually land.
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.HOSTILE, 0.65F, 0.75F);

        LivingEntity target = cast.target();
        if (distanceToSqr(target) > SMACK_RANGE * SMACK_RANGE || !getSensing().hasLineOfSight(target)) {
            return;
        }

        if (level() instanceof ServerLevel serverLevel) {
            target.hurtServer(serverLevel, damageSources().mobAttack(this), SMACK_DAMAGE);
        }
        target.addEffect(new MobEffectInstance(MobEffects.POISON, SMACK_POISON_TICKS, 0), this);
        target.knockback(SMACK_KNOCKBACK_STRENGTH, getX() - target.getX(), getZ() - target.getZ());
    }

    private void shootBogfumeBolt() {
        MageCast cast = pendingCast;
        if (cast == null || level().isClientSide() || !cast.target().isAlive()) {
            return;
        }

        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }

        Vec3 origin = getBoltReleasePosition();
        if (!hasClearBoltToTarget(cast.target())) {
            return;
        }

        // A tighter, higher version of the Magic Push cast sound distinguishes the focused bolt.
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.EVOKER_CAST_SPELL,
                SoundSource.HOSTILE, 2.0F, 1.22F);
        DustParticleOptions greenBoltRelease =
                new DustParticleOptions(0x1FE62E, 1.25F);
        serverLevel.sendParticles(greenBoltRelease, origin.x, origin.y, origin.z, 10, 0.1, 0.1, 0.1, 0.02);
        BogfumeBolt.shoot(serverLevel, this, cast.target(), origin);
    }

    private Vec3 getBoltReleasePosition() {
        Vec3 forward = Vec3.directionFromRotation(0.0F, getYRot()).normalize();
        Vec3 right = new Vec3(-forward.z, 0.0, forward.x).normalize();
        return position()
                .add(0.0, BOLT_RELEASE_HEIGHT_OFFSET, 0.0)
                .add(forward.scale(BOLT_RELEASE_FORWARD_OFFSET))
                .add(right.scale(BOLT_RELEASE_SIDE_OFFSET));
    }

    private boolean hasClearBoltToTarget(LivingEntity target) {
        Vec3 start = getBoltReleasePosition();
        Vec3 end = target.getEyePosition();
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

            var allyHit = ally.getBoundingBox().inflate(0.45).clip(start, end);
            if (allyHit.isPresent() && start.distanceToSqr(allyHit.get()) < targetDistanceSqr) {
                return false;
            }
        }

        return true;
    }

    private void applyMagicPushImpact() {
        MageCast cast = pendingCast;
        if (cast == null || level().isClientSide()) {
            return;
        }

        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }

        Vec3 forward = Vec3.directionFromRotation(0.0F, getYRot()).normalize();
        Vec3 origin = position().add(forward.scale(1.2)).add(0.0, 1.0, 0.0);
        spawnMagicPushParticles(serverLevel, origin);
        // Volume only. The push's own event and pitch are unchanged and still match the 1.21.1
        // reference; 0.9F simply reads as inaudible next to the Mage's other cast sounds (the
        // poison-fog channel plays at 2.5F) once a fight is noisy.
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.HOSTILE, 1.6F, 0.75F);

        AABB area = getBoundingBox().inflate(MAGIC_PUSH_RANGE, 2.5, MAGIC_PUSH_RANGE);
        List<LivingEntity> targets = level().getEntitiesOfClass(
                LivingEntity.class,
                area,
                target -> target != this && target.isAlive() && !(target instanceof OgreGrunt));

        for (LivingEntity target : targets) {
            Vec3 toTarget = target.position().subtract(position());
            Vec3 horizontalToTarget = new Vec3(toTarget.x, 0.0, toTarget.z);
            double horizontalDistanceSqr = horizontalToTarget.lengthSqr();
            if (horizontalDistanceSqr < 0.0001 || horizontalDistanceSqr > MAGIC_PUSH_RANGE * MAGIC_PUSH_RANGE) {
                continue;
            }

            Vec3 direction = horizontalToTarget.normalize();
            if (direction.dot(forward) < MAGIC_PUSH_CONE_DOT || !getSensing().hasLineOfSight(target)) {
                continue;
            }

            target.hurtServer(serverLevel, damageSources().mobAttack(this), MAGIC_PUSH_DAMAGE);
            Vec3 movement = target.getDeltaMovement();
            target.setDeltaMovement(
                    direction.x * MAGIC_PUSH_KNOCKBACK_STRENGTH,
                    Math.max(movement.y, MAGIC_PUSH_VERTICAL_LIFT),
                    direction.z * MAGIC_PUSH_KNOCKBACK_STRENGTH);
            target.hurtMarked = true;
        }
    }

    private void spawnMagicPushParticles(ServerLevel serverLevel, Vec3 origin) {
        var greenDust = new DustParticleOptions(0x1AD926, 1.6f);
        serverLevel.sendParticles(greenDust, origin.x, origin.y, origin.z, 32, 0.42, 0.42, 0.42, 0.22);
        serverLevel.sendParticles(ParticleTypes.WITCH, origin.x, origin.y, origin.z, 30, 0.38, 0.38, 0.38, 0.16);
        serverLevel.sendParticles(ParticleTypes.EXPLOSION, origin.x, origin.y, origin.z, 3, 0.14, 0.14, 0.14, 0.0);
        serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, origin.x, origin.y, origin.z, 10, 0.32, 0.38, 0.32, 0.02);
        serverLevel.sendParticles(ParticleTypes.SMOKE, origin.x, origin.y, origin.z, 22, 0.28, 0.28, 0.28, 0.05);
        serverLevel.sendParticles(ParticleTypes.END_ROD, origin.x, origin.y, origin.z, 14, 0.5, 0.38, 0.5, 0.14);
        serverLevel.sendParticles(ParticleTypes.POOF, origin.x, origin.y, origin.z, 14, 0.24, 0.22, 0.24, 0.08);
    }

    private void spawnPendingPoisonCloud(LivingEntity target) {
        if (level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            pendingPoisonCloud = PoisonFogCloud.spawn(serverLevel, target.getX(), target.getY(), target.getZ());
        }
    }

    private void cancelCast() {
        if (!castReleased && pendingPoisonCloud != null && pendingPoisonCloud.isAlive()) {
            pendingPoisonCloud.discard();
        }
        pendingCast = null;
        pendingPoisonCloud = null;
        activeCastTicks = 0;
        castReleaseTicks = 0;
        castReleased = false;
        setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        clearSyncedCastAnimation();
    }

    private boolean isCasting() {
        return pendingCast != null;
    }

    private void turnToward(LivingEntity target, float turnSpeed) {
        if (target == null || !target.isAlive()) {
            return;
        }

        float targetYaw = getYawToward(target);
        setYRot(Mth.rotateIfNecessary(getYRot(), targetYaw, turnSpeed));
        yBodyRot = getYRot();
        yHeadRot = getYRot();
        getLookControl().setLookAt(target, turnSpeed, turnSpeed);
    }

    private void faceToward(LivingEntity target) {
        turnToward(target, CAST_TURN_SPEED);
    }

    private float getYawToward(LivingEntity target) {
        double xOffset = target.getX() - getX();
        double zOffset = target.getZ() - getZ();
        return (float) (Mth.atan2(zOffset, xOffset) * Mth.RAD_TO_DEG) - 90.0F;
    }

    public void playCastAnimation(CastType type) {
        if (!level().isClientSide()) {
            entityData.set(DATA_MAGE_CAST_ID, type.id);
            entityData.set(DATA_MAGE_CAST_START_TICK, level().getGameTime());
        }

        activeCastAnimation = type.animation;
        castAnimationTicks = type.animationTicks;
        startingCastAnimation = true;
    }

    /** Synced, render-only exclusion for Magic Push. */
    public boolean isProceduralHeadTrackingSuppressed() {
        return entityData.get(DATA_MAGE_CAST_ID) == CastType.MAGIC_PUSH.id;
    }

    private PlayState animateMage(AnimationTest<OgreMage> state) {
        int visualCastId = visualOneShot.update(state.controller(),
                entityData.get(DATA_MAGE_CAST_ID), entityData.get(DATA_MAGE_CAST_START_TICK),
                id -> CastType.byId(id).animationTicks + ONE_SHOT_TRANSITION_TICKS,
                level().getGameTime());
        if (visualCastId > 0) {
            return state.setAndContinue(CastType.byId(visualCastId).animation);
        }

        if (shouldUseRunAnimation()) {
            idleAnimationTicks = 0;
            rareIdleTicks = 0;
            lastAnimationTick = tickCount;
            // Follow actual movement — being "aggressive" doesn't mean currently moving (e.g. stopped
            // to cast), so without this check he'd show RUN even while stationary between casts.
            if (isMovingForAnimation(state)) {
                return state.setAndContinue(RUN);
            }
            return state.setAndContinue(IDLE);
        }

        if (isMovingForAnimation(state)) {
            idleAnimationTicks = 0;
            rareIdleTicks = 0;
            lastAnimationTick = tickCount;
            return state.setAndContinue(WALK);
        }

        updateIdleAnimationTimers(state);
        if (rareIdleTicks > 0 || state.isCurrentAnimation(RARE_IDLE) && !state.controller().hasAnimationFinished()) {
            return state.setAndContinue(RARE_IDLE);
        }
        return state.setAndContinue(IDLE);
    }

    private boolean isMovingForAnimation(AnimationTest<OgreMage> state) {
        boolean moving = hasLocomotionMotion();
        if (moving) {
            movementAnimationGraceTicks = locomotionGraceTicks();
            lastMovementGraceTick = tickCount;
            return true;
        }
        if (movementAnimationGraceTicks > 0) {
            // This grace window exists to bridge the 1-2 tick gap between the combat goal stopping
            // navigation (which zeroes the movement predicate immediately) and the synced cast
            // ID/timestamp reaching the client so the one-shot can take over. It is a *tick* budget,
            // but this predicate is evaluated once per render frame, so decrementing it here
            // unconditionally burned all four units inside a single game tick at normal framerates.
            // That left a real hole where the Mage fell through to IDLE and then re-armed to a
            // run-in-place from residual velocity before the cast animation became active. Gate the
            // decrement on the game tick — the same way updateCastAnimationTimer and
            // updateIdleAnimationTimers already do — so the bridge is a genuine four ticks and is
            // no longer framerate-dependent.
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

    private boolean shouldUseRunAnimation() {
        // isAggressive() is synced (set by the combat goal); getTarget() is server-only
        // and always null on the client, which left clients stuck on WALK while approaching.
        return isAggressive() || isRetreatingForAnimation() || isRunMovementState();
    }

    private void setRetreatingForAnimation(boolean retreating) {
        entityData.set(DATA_MAGE_RETREATING, retreating);
    }

    private boolean isRetreatingForAnimation() {
        return entityData.get(DATA_MAGE_RETREATING);
    }

    private void clearSyncedCastAnimation() {
        if (!level().isClientSide()) {
            entityData.set(DATA_MAGE_CAST_ID, 0);
            entityData.set(DATA_MAGE_CAST_START_TICK, 0L);
        }
    }

    private boolean isCastAnimationPlaying(AnimationTest<OgreMage> state) {
        return castAnimationTicks > 0;
    }

    private boolean isCurrentCastAnimation(AnimationTest<OgreMage> state) {
        return state.isCurrentAnimation(CHANNEL)
                || state.isCurrentAnimation(TOSS_ANIMATION)
                || state.isCurrentAnimation(MAGIC_PUSH_ANIMATION)
                || state.isCurrentAnimation(BOLT_ANIMATION)
                || state.isCurrentAnimation(SMACK_ANIMATION)
                || state.isCurrentAnimation(DRINK_ANIMATION);
    }

    private void updateCastAnimationTimer(AnimationTest<OgreMage> state) {
        if (lastAnimationTick == tickCount) {
            return;
        }

        int tickDelta = lastAnimationTick < 0 ? 1 : Math.max(1, tickCount - lastAnimationTick);
        lastAnimationTick = tickCount;
        castAnimationTicks = Math.max(0, castAnimationTicks - tickDelta);
        idleAnimationTicks = 0;
        rareIdleTicks = 0;
    }

    private void updateIdleAnimationTimers(AnimationTest<OgreMage> state) {
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

    private record MageCast(CastType type, LivingEntity target, ItemStack stack) {
        private MageCast(CastType type, LivingEntity target) {
            this(type, target, ItemStack.EMPTY);
        }
    }

    private enum CastType {
        POISON_FOG(1, CHANNEL, 50, 40),
        MAGIC_PUSH(2, MAGIC_PUSH_ANIMATION, 40, 21),
        BOLT(3, BOLT_ANIMATION, 40, 20),
        SMACK(4, SMACK_ANIMATION, 25, 15),
        // Keep the held bottle through tick 20, then remove it and create the projectile from the
        // authored hand-side release position on that exact same tick.
        POTION(5, TOSS_ANIMATION, 25, 20),
        // 2.375s authored clip; consume at 2.16s (about tick 43).
        HEAL_DRINK(6, DRINK_ANIMATION, 48, 43);

        private final int id;
        private final RawAnimation animation;
        private final int animationTicks;
        private final int releaseTicks;

        CastType(int id, RawAnimation animation, int animationTicks, int releaseTicks) {
            this.id = id;
            this.animation = animation;
            this.animationTicks = animationTicks;
            this.releaseTicks = releaseTicks;
        }

        private static CastType byId(int id) {
            for (CastType type : values()) {
                if (type.id == id) {
                    return type;
                }
            }
            return POISON_FOG;
        }
    }

    private static class OgreMageCombatGoal extends Goal {
        private final OgreMage mage;
        private final double speedModifier;
        private int ticksUntilNextPathRecalculation;
        private int ticksUntilNextFog;
        private int ticksUntilNextPotion;
        private int ticksUntilNextBolt;
        private int ticksUntilNextMagicPush;
        private int ticksUntilNextSmack;
        private int closePressureTicks;
        private int retreatCommitTicks;
        private int lineOfSightTicks;
        private Vec3 retreatTargetPos;
        private Vec3 lastPursuitTargetPos;

        private OgreMageCombatGoal(OgreMage mage, double speedModifier) {
            this.mage = mage;
            this.speedModifier = speedModifier;
            this.ticksUntilNextFog = FOG_COOLDOWN_MIN_TICKS / 2 + mage.getRandom().nextInt(FOG_COOLDOWN_MIN_TICKS / 2);
            this.ticksUntilNextPotion = mage.getRandom().nextInt(POTION_COOLDOWN_TICKS);
            this.ticksUntilNextBolt = mage.getRandom().nextInt(BOLT_COOLDOWN_TICKS);
            this.ticksUntilNextMagicPush = mage.getRandom().nextInt(MAGIC_PUSH_COOLDOWN_TICKS / 2);
            setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = mage.getTarget();
            return target != null && target.isAlive();
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity target = mage.getTarget();
            return mage.isCasting()
                    || target != null
                    && target.isAlive()
                    && (!(target instanceof Player player) || !player.isSpectator() && !player.isCreative())
                    && mage.isWithinHome(target.blockPosition());
        }

        @Override
        public void start() {
            mage.setAggressive(true);
        }

        @Override
        public void stop() {
            mage.setAggressive(false);
            mage.getNavigation().stop();
            mage.setRetreatingForAnimation(false);
            mage.cancelCast();
            ticksUntilNextPathRecalculation = 0;
            closePressureTicks = 0;
            ticksUntilNextSmack = 0;
            retreatCommitTicks = 0;
            retreatTargetPos = null;
            lineOfSightTicks = 0;
            lastPursuitTargetPos = null;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity target = mage.getTarget();
            if (target == null) {
                mage.tickPendingCast();
                return;
            }

            if (mage.isCasting()) {
                mage.setRetreatingForAnimation(false);
                mage.getNavigation().stop();
                mage.tickPendingCast();
                return;
            }

            double distanceToTargetSqr = mage.distanceToSqr(target);
            boolean hasLineOfSight = mage.getSensing().hasLineOfSight(target);
            boolean inCastRange = distanceToTargetSqr <= CAST_RANGE * CAST_RANGE;
            boolean inBoltRange = distanceToTargetSqr <= BOLT_RANGE * BOLT_RANGE;
            boolean inMagicPushRange = distanceToTargetSqr <= MAGIC_PUSH_RANGE * MAGIC_PUSH_RANGE;
            boolean inSmackRange = distanceToTargetSqr <= SMACK_RANGE * SMACK_RANGE;
            boolean targetTooClose = distanceToTargetSqr < RETREAT_RANGE * RETREAT_RANGE;
            boolean lowHealth = mage.getHealth() <= mage.getMaxHealth() * LOW_HEALTH_RETREAT_RATIO;
            lineOfSightTicks = hasLineOfSight
                    ? Math.min(lineOfSightTicks + 1, 20)
                    : Math.max(lineOfSightTicks - 1, -20);

            ticksUntilNextSmack = Math.max(ticksUntilNextSmack - 1, 0);

            ticksUntilNextPotion = Math.max(ticksUntilNextPotion - 1, 0);
            ticksUntilNextBolt = Math.max(ticksUntilNextBolt - 1, 0);
            ticksUntilNextMagicPush = Math.max(ticksUntilNextMagicPush - 1, 0);
            if (inMagicPushRange && hasLineOfSight) {
                closePressureTicks = Math.min(closePressureTicks + 1, adjustedTickDelay(MAGIC_PUSH_PRESSURE_TICKS));
            } else {
                closePressureTicks = Math.max(closePressureTicks - 2, 0);
            }

            if (ticksUntilNextSmack <= 0
                    && hasLineOfSight
                    && shouldStandAndSmack(inSmackRange, lowHealth)) {
                mage.getNavigation().stop();
                mage.setRetreatingForAnimation(false);
                mage.beginSmack(target);
                ticksUntilNextSmack = adjustedTickDelay(SMACK_COOLDOWN_TICKS);
                retreatTargetPos = null;
                return;
            }

            if (ticksUntilNextMagicPush <= 0
                    && closePressureTicks >= adjustedTickDelay(MAGIC_PUSH_PRESSURE_TICKS)
                    && inMagicPushRange
                    && hasLineOfSight) {
                mage.getNavigation().stop();
                mage.setRetreatingForAnimation(false);
                mage.beginMagicPush(target);
                ticksUntilNextMagicPush = adjustedTickDelay(MAGIC_PUSH_COOLDOWN_TICKS);
                closePressureTicks = 0;
                ticksUntilNextFog = Math.max(ticksUntilNextFog, adjustedTickDelay(30));
                retreatTargetPos = null;
                return;
            }

            if (lowHealth && mage.healDrinkCooldownTicks <= 0) {
                mage.getNavigation().stop();
                mage.setRetreatingForAnimation(false);
                mage.beginHealingDrink();
                retreatTargetPos = null;
                return;
            }

            if (ticksUntilNextPotion <= 0) {
                java.util.Optional<MageCast> potionCast = mage.choosePotionThrowInRange(target);
                if (potionCast.isPresent()
                        && mage.getSensing().hasLineOfSight(potionCast.get().target())) {
                    mage.getNavigation().stop();
                    mage.setRetreatingForAnimation(false);
                    mage.beginPotionThrow(potionCast.get());
                    ticksUntilNextPotion = adjustedTickDelay(POTION_COOLDOWN_TICKS);
                    ticksUntilNextBolt = Math.max(ticksUntilNextBolt, adjustedTickDelay(20));
                    retreatTargetPos = null;
                    return;
                }
            }

            if (targetTooClose && hasLineOfSight) {
                tickRetreat(target);
                return;
            }

            if (ticksUntilNextBolt <= 0
                    && hasLineOfSight
                    && inBoltRange
                    && mage.hasClearBoltToTarget(target)) {
                mage.getNavigation().stop();
                mage.setRetreatingForAnimation(false);
                mage.beginBoltCast(target);
                ticksUntilNextBolt = adjustedTickDelay(BOLT_COOLDOWN_TICKS);
                return;
            }

            if (!hasLineOfSight || !inCastRange || lineOfSightTicks < 5) {
                tickRangedApproach(target);
            } else {
                mage.getNavigation().stop();
                mage.setRetreatingForAnimation(false);
                mage.getLookControl().setLookAt(target, 30.0F, 30.0F);
            }

            ticksUntilNextFog = Math.max(ticksUntilNextFog - 1, 0);

            if (ticksUntilNextFog <= 0 && hasLineOfSight && inCastRange) {
                mage.beginPoisonFogCast(target);
                mage.setRetreatingForAnimation(false);
                ticksUntilNextFog = adjustedTickDelay(FOG_COOLDOWN_MIN_TICKS + mage.getRandom().nextInt(FOG_COOLDOWN_RANDOM_TICKS));
                ticksUntilNextMagicPush = Math.max(ticksUntilNextMagicPush, adjustedTickDelay(30));
            }
        }

        private void tickRangedApproach(LivingEntity target) {
            mage.setRetreatingForAnimation(false);
            ticksUntilNextPathRecalculation = Math.max(ticksUntilNextPathRecalculation - 1, 0);

            Vec3 targetPos = target.position();
            boolean targetMoved = lastPursuitTargetPos == null
                    || lastPursuitTargetPos.distanceToSqr(targetPos) > 1.0;
            if (ticksUntilNextPathRecalculation > 0
                    && !mage.getNavigation().isDone()
                    && !targetMoved) {
                return;
            }

            boolean pathing = mage.getNavigation().moveTo(
                    target,
                    mage.applyShallowWaterSpeedFactor(speedModifier));
            lastPursuitTargetPos = targetPos;
            ticksUntilNextPathRecalculation = adjustedTickDelay(
                    pathing ? 8 + mage.getRandom().nextInt(5) : 4);
        }

        private void tickRetreat(LivingEntity target) {
            mage.setRetreatingForAnimation(true);
            retreatCommitTicks = Math.max(retreatCommitTicks - 1, 0);
            boolean needsNewRetreatTarget = retreatTargetPos == null
                    || retreatCommitTicks <= 0
                    || mage.distanceToSqr(retreatTargetPos) < 2.0
                    || mage.getNavigation().isDone();

            if (needsNewRetreatTarget) {
                Vec3 retreatPos = DefaultRandomPos.getPosAway(
                        mage,
                        (int) Math.ceil(RETREAT_DISTANCE),
                        4,
                        target.position());
                if (retreatPos == null) {
                    Vec3 away = mage.position().subtract(target.position());
                    if (away.horizontalDistanceSqr() < 1.0E-4) {
                        away = Vec3.directionFromRotation(0.0F, mage.getYRot());
                    }

                    Vec3 horizontalAway = new Vec3(away.x, 0.0, away.z).normalize();
                    retreatPos = mage.position().add(horizontalAway.scale(RETREAT_DISTANCE));
                }

                retreatTargetPos = retreatPos;
                retreatCommitTicks = adjustedTickDelay(RETREAT_REPATH_TICKS);
                mage.getNavigation().moveTo(
                        retreatTargetPos.x,
                        retreatTargetPos.y,
                        retreatTargetPos.z,
                        RETREAT_SPEED);
            }
        }

        private boolean shouldStandAndSmack(boolean inSmackRange, boolean lowHealth) {
            // A healthy mage will defend himself whenever the player commits to melee.
            // The animation time plus SMACK_COOLDOWN_TICKS still leaves a fair gap
            // between hits; low-health mages prioritize drinking their heal instead.
            return inSmackRange && !lowHealth;
        }

    }
}
