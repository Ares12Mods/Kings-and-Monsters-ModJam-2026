package com.kingsandmonsters.entity;

import com.kingsandmonsters.Config;
import com.kingsandmonsters.ModSoundEvents;
import com.kingsandmonsters.effect.CombatEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.network.chat.Component;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import com.geckolib.animation.RawAnimation;

import org.joml.Vector3f;

/**
 * The battle-hardened captain of a Grunt-controlled outpost.
 *
 * <p>This deliberately inherits the regular Grunt without changing any goals,
 * navigation, combat behavior, attributes, or animation state. It remains a
 * distinct entity type so its visuals, spawn egg, loot, and future balancing
 * can evolve independently.</p>
 */
public class OgreGruntCaptain extends OgreGrunt {
    // Captains use 120% of the Grunt's default movement attribute (0.33264), so this
    // multiplier yields an effective pursuit speed of about 0.35, keeping the Captain slightly
    // faster and more threatening than a regular Grunt.
    private static final double CAPTAIN_PURSUIT_SPEED_MODIFIER = 0.35 / (0.2772 * 1.20);
    private static final float CAPTAIN_VOICE_PITCH_MULTIPLIER = 0.88F;
    private static final float CAPTAIN_STEP_PITCH_MULTIPLIER = 0.82F;
    private static final RawAnimation SWORD_SLASH = RawAnimation.begin().thenPlay("sword_slash");
    private static final RawAnimation SWORD_BUTT = RawAnimation.begin().thenPlay("sword_butt");
    private static final RawAnimation PUNCH = RawAnimation.begin().thenPlay("punch");
    private static final RawAnimation SWORD_SALUTE = RawAnimation.begin().thenPlay("sword_salute");
    private static final int SWORD_SLASH_ID = 3;
    private static final int SWORD_BUTT_ID = 4;
    private static final int PUNCH_ID = 5;
    private static final int SWORD_SALUTE_ID = 6;
    private static final int CAPTAIN_ATTACK_ANIMATION_TICKS = 25;
    private static final int SWORD_SALUTE_ANIMATION_TICKS = 50;
    private static final int SWORD_SALUTE_BUFF_DELAY_TICKS = 28;
    private static final int SWORD_SALUTE_BUFF_TICKS = 30 * 20;
    private static final double SWORD_SALUTE_BUFF_RADIUS = 16.0;
    /**
     * Degrees per tick the Captain may turn while one of his attack animations is playing.
     * Matches the Mage's {@code CAST_TURN_SPEED} and the Archer's
     * {@code SHOOT_VISUAL_TRACK_TURN_SPEED} so every ogre tracks at the same gentle rate.
     */
    private static final float ATTACK_TRACK_TURN_SPEED = 10.0F;
    private static final DustParticleOptions SWORD_SALUTE_RING =
            new DustParticleOptions(0x24AD2E, 1.05F);
    private int activeCaptainAttackId;
    private int swordSaluteTicks;
    private boolean swordSaluteUsed;
    private boolean swordSaluteBuffApplied;
    private boolean swordSalutePending;
    private int swordSaluteRingTicks;

    public OgreGruntCaptain(EntityType<? extends OgreGruntCaptain> type, Level level) {
        super(type, level);
        ensureCaptainTitle();
        setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        // The Captain's dedicated loot table controls rewards; prevent a second equipment roll.
        setDropChance(EquipmentSlot.MAINHAND, 0.0F);
    }

    private void ensureCaptainTitle() {
        if (!hasCustomName()) {
            setCustomName(Component.translatable("title.kingsandmonsters.grunt_captain"));
        }
        setCustomNameVisible(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return OgreGrunt.createAttributes()
                .add(Attributes.MAX_HEALTH, 100.0)
                .add(Attributes.ARMOR, 12.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.66);
    }

    @Override
    public void applyConfiguredCombatAttributes(boolean healToMax) {
        float healthBeforeGruntConfiguration = getHealth();
        super.applyConfiguredCombatAttributes(healToMax);
        if (!Config.isLoaded()) {
            return;
        }
        AttributeInstance maxHealth = getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(Config.OGRE_GRUNT_CAPTAIN_MAX_HEALTH.get());
            if (healToMax) {
                setHealth(getMaxHealth());
            } else {
                // The shared Grunt configuration temporarily lowers the maximum to the Grunt's
                // value. Restore the Captain's saved health after installing his own maximum.
                setHealth(Math.min(healthBeforeGruntConfiguration, getMaxHealth()));
            }
        }
        AttributeInstance armor = getAttribute(Attributes.ARMOR);
        if (armor != null) {
            armor.setBaseValue(Config.OGRE_GRUNT_CAPTAIN_ARMOR.get());
        }
        AttributeInstance movementSpeed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (movementSpeed != null) {
            movementSpeed.setBaseValue(Config.OGRE_GRUNT_MOVEMENT_SPEED.get() * 1.20);
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide()) {
            return;
        }


        trackTargetDuringCaptainAttack();

        if (!swordSaluteUsed && getHealth() <= getMaxHealth() * 0.5F) {
            swordSalutePending = true;
        }

        if (swordSalutePending && !hasActiveMeleeAttack()) {
            beginSwordSalute();
        }

        if (swordSaluteTicks > 0) {
            int elapsedTicks = SWORD_SALUTE_ANIMATION_TICKS - swordSaluteTicks;
            if (!swordSaluteBuffApplied && elapsedTicks >= SWORD_SALUTE_BUFF_DELAY_TICKS) {
                applySwordSaluteBuff();
            }
            swordSaluteTicks--;
            getNavigation().stop();
            setDeltaMovement(0.0, getDeltaMovement().y, 0.0);
            if (swordSaluteTicks == 0) {
                onMeleeAttackPlanFinished();
            }
        }

        if (swordSaluteRingTicks > 0) {
            spawnSwordSaluteRingStep();
            swordSaluteRingTicks--;
        }
    }

    /**
     * Keeps the Captain gently facing his target for the whole of an attack clip.
     *
     * <p>The shared melee goal only holds the ogre still (and only look-tracks through
     * {@code setLookAt}) while its attack <em>plan</em> is active, and a plan ends on its last hit
     * tick — 13/15/14 for Sword Slash/Butt/Punch against 25-tick animations. For the Captain,
     * {@code holdsMeleeApproachSpacing()} is false, so path navigation restarts during the second
     * half of the still-playing swing and {@code OgreGruntMoveControl.rotlerp} steers the body yaw
     * toward the next path node, which at contact range points sideways or behind the target. That
     * is what made the Captain finish a swing facing away and then visibly turn back.
     *
     * <p>This runs after {@code super.aiStep()}, i.e. after the move/look controls have ticked, so
     * it is the final word on yaw for the tick. It reuses the Archer's rate-limited
     * {@code Mth.rotateIfNecessary} body-turn pattern and deliberately does not touch the previous
     * -tick rotation fields, so the turn interpolates smoothly instead of snapping.</p>
     */
    private void trackTargetDuringCaptainAttack() {
        if (activeCaptainAttackId == 0) {
            return;
        }

        LivingEntity target = getTarget();
        if (target == null || !target.isAlive()) {
            return;
        }

        double xOffset = target.getX() - getX();
        double zOffset = target.getZ() - getZ();
        if (xOffset * xOffset + zOffset * zOffset < 1.0E-4) {
            return;
        }

        float desiredYaw = (float) (Mth.atan2(zOffset, xOffset) * Mth.RAD_TO_DEG) - 90.0F;
        float yaw = Mth.rotateIfNecessary(getYRot(), desiredYaw, ATTACK_TRACK_TURN_SPEED);
        setYRot(yaw);
        setYBodyRot(yaw);
        setYHeadRot(yaw);
    }

    private void beginSwordSalute() {
        swordSaluteUsed = true;
        swordSalutePending = false;
        swordSaluteBuffApplied = false;
        swordSaluteTicks = SWORD_SALUTE_ANIMATION_TICKS;
        activeCaptainAttackId = SWORD_SALUTE_ID;
        startGruntAttack(SWORD_SALUTE_ID, SWORD_SALUTE_ANIMATION_TICKS);

    }

    private void applySwordSaluteBuff() {
        swordSaluteBuffApplied = true;
        level().playSound(null, getX(), getY(), getZ(),
                ModSoundEvents.OGRE_GRUNT_CAPTAIN_SALUTE.get(), SoundSource.HOSTILE, 1.1F, 1.0F);
        for (OgreGrunt ogre : level().getEntitiesOfClass(OgreGrunt.class,
                getBoundingBox().inflate(SWORD_SALUTE_BUFF_RADIUS), OgreGrunt::isAlive)) {
            ogre.addEffect(new MobEffectInstance(MobEffects.STRENGTH,
                    SWORD_SALUTE_BUFF_TICKS, 0, false, true, true), this);
            ogre.addEffect(new MobEffectInstance(MobEffects.RESISTANCE,
                    SWORD_SALUTE_BUFF_TICKS, 0, false, true, true), this);
        }

        if (level() instanceof ServerLevel serverLevel) {
            swordSaluteRingTicks = 8;
        }
    }

    private void spawnSwordSaluteRingStep() {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }

        int completedSteps = 8 - swordSaluteRingTicks + 1;
        double radius = SWORD_SALUTE_BUFF_RADIUS * completedSteps / 8.0;
        int particleCount = 16;
        for (int index = 0; index < particleCount; index++) {
            double angle = Math.PI * 2.0 * index / particleCount;
            double x = getX() + Math.cos(angle) * radius;
            double z = getZ() + Math.sin(angle) * radius;
            double y = serverLevel.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    (int) Math.floor(x), (int) Math.floor(z)) + 0.18;
            serverLevel.sendParticles(SWORD_SALUTE_RING, x, y, z,
                    1, 0.0, 0.025, 0.0, 0.0);
        }
    }

    @Override
    public void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("SwordSaluteUsed", swordSaluteUsed);
        tag.putBoolean("SwordSaluteBuffApplied", swordSaluteBuffApplied);
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput tag) {
        super.readAdditionalSaveData(tag);
        ensureCaptainTitle();
        swordSaluteUsed = tag.getBooleanOr("SwordSaluteUsed", false);
        swordSaluteBuffApplied = tag.getBooleanOr("SwordSaluteBuffApplied", false);
    }

    @Override
    public float getVoicePitch() {
        // Keep the Grunt's natural pitch variation, shifted slightly lower for the captain.
        return super.getVoicePitch() * CAPTAIN_VOICE_PITCH_MULTIPLIER;
    }

    @Override
    protected boolean usesOnlyBiteAttack() {
        return true;
    }

    @Override
    protected double getMeleePursuitSpeedModifier() {
        return CAPTAIN_PURSUIT_SPEED_MODIFIER;
    }

    @Override
    protected double getWanderSpeedModifier() {
        // Captains use a 0.33264 default movement attribute after configuration;
        // convert the requested effective wandering speed into a goal multiplier.
        return 0.12 / (0.2772 * 1.20);
    }

    @Override
    protected MeleeAttackPlan startMeleeAttack() {
        return switch (getRandom().nextInt(5)) {
            case 0 -> startCaptainBite();
            case 1, 2 -> startCaptainAttack(SWORD_SLASH_ID, 52,
                    MeleeAttackHit.areaDamage(13, 17.0F / 10.5F, 0.0F, false));
            case 3 -> startCaptainAttack(SWORD_BUTT_ID, 56,
                    // Lower than Sword Slash's raw damage — Butt's value is the Dazed debuff, not
                    // matching Slash's hit.
                    MeleeAttackHit.areaDamage(15, 15.0F / 10.5F, 0.0F, false));
            default -> startCaptainAttack(PUNCH_ID, 50,
                    MeleeAttackHit.areaSpecial(14, 15.0F / 10.5F, 0.0F, false,
                            0.0F, 1.0, 0.0F, 0.35, 0, 0, false));
        };
    }

    @Override
    protected boolean isPerformingNonMeleeAction() {
        return swordSaluteTicks > 0;
    }

    private MeleeAttackPlan startCaptainBite() {
        activeCaptainAttackId = 2;
        return super.startMeleeAttack();
    }

    private MeleeAttackPlan startCaptainAttack(int attackId, int cooldownTicks, MeleeAttackHit hit) {
        activeCaptainAttackId = attackId;
        return startGruntAttack(attackId, cooldownTicks, hit);
    }

    @Override
    protected void applyAttackHitExtras(LivingEntity target, MeleeAttackHit hit) {
        super.applyAttackHitExtras(target, hit);
        if (activeCaptainAttackId == SWORD_BUTT_ID && !(target instanceof OgreGrunt)) {
            CombatEffects.applyDazed(target, Config.DAZED_HEAVY_ATTACK_DURATION_TICKS.get(), this);
        }
    }

    @Override
    protected void onMeleeAttackPlanFinished() {
        super.onMeleeAttackPlanFinished();
        activeCaptainAttackId = 0;
    }

    @Override
    protected double getMeleeHitReachSqr(LivingEntity target) {
        double baseReach = Math.sqrt(super.getMeleeHitReachSqr(target));
        double extraReach = switch (activeCaptainAttackId) {
            case SWORD_SLASH_ID -> 0.30;
            case PUNCH_ID -> 0.20;
            default -> 0.0;
        };
        double adjustedReach = baseReach + extraReach;
        return adjustedReach * adjustedReach;
    }

    @Override
    protected double getMeleeAttackCatchSpeed(int attackId, int activeAttackTicks) {
        if (activeCaptainAttackId == SWORD_SLASH_ID && activeAttackTicks >= 11 && activeAttackTicks <= 13) {
            return 0.03;
        }
        if (activeCaptainAttackId == PUNCH_ID && activeAttackTicks >= 11 && activeAttackTicks <= 14) {
            return 0.15;
        }
        if (activeCaptainAttackId == SWORD_BUTT_ID && activeAttackTicks >= 11 && activeAttackTicks <= 14) {
            return 0.065;
        }
        return super.getMeleeAttackCatchSpeed(attackId, activeAttackTicks);
    }

    @Override
    protected void playGruntAttackImpactSound() {
        if (activeCaptainAttackId == SWORD_SLASH_ID) {
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_SWEEP,
                    SoundSource.HOSTILE, 0.55F, 0.9F);
            return;
        }

        if (activeCaptainAttackId == PUNCH_ID) {
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_STRONG,
                    SoundSource.HOSTILE, 0.75F, 0.8F);
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.MUD_HIT,
                    SoundSource.HOSTILE, 0.35F, 0.75F);
            return;
        }

        if (activeCaptainAttackId == SWORD_BUTT_ID) {
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK,
                    SoundSource.HOSTILE, 0.65F, 0.8F);
            return;
        }

        // The inherited bite keeps its dedicated bite/eating impact cue.
        super.playGruntAttackImpactSound();
    }

    @Override
    protected boolean playsAttackImpactSoundOnMiss() {
        return true;
    }

    @Override
    protected RawAnimation getGruntAttackAnimation(int attackId) {
        return switch (attackId) {
            case SWORD_SLASH_ID -> SWORD_SLASH;
            case SWORD_BUTT_ID -> SWORD_BUTT;
            case PUNCH_ID -> PUNCH;
            case SWORD_SALUTE_ID -> SWORD_SALUTE;
            default -> super.getGruntAttackAnimation(attackId);
        };
    }

    @Override
    protected int getGruntAttackAnimationTicks(int attackId) {
        return switch (attackId) {
            case SWORD_SLASH_ID, SWORD_BUTT_ID, PUNCH_ID -> CAPTAIN_ATTACK_ANIMATION_TICKS;
            case SWORD_SALUTE_ID -> SWORD_SALUTE_ANIMATION_TICKS;
            default -> super.getGruntAttackAnimationTicks(attackId);
        };
    }

    @Override
    protected float getStepPitchMultiplier() {
        return super.getStepPitchMultiplier() * CAPTAIN_STEP_PITCH_MULTIPLIER;
    }

    @Override
    protected int getMinimumStepSoundIntervalTicks() {
        // The larger Captain lands slightly longer, deeper strides than a regular Grunt.
        return 8;
    }
}
