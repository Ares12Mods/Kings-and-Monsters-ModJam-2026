package com.kingsandmonsters.entity;

import com.kingsandmonsters.ModEntities;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.List;
import java.util.UUID;

public class BogfumeBolt extends Entity {
    private static final int MAX_LIFETIME_TICKS = 60;
    // Calibrated to match the archer's arrow (~1.5 hearts vs full diamond, ~2.4 vs full iron).
    private static final float DAMAGE = 11.0F;
    private static final float OGRE_ALLY_DAMAGE_MULTIPLIER = 0.5F;
    private static final double HIT_RADIUS = 0.45;
    private static final double HOMING_STRENGTH = 0.08;
    private static final double PROJECTILE_SPEED = 0.95;
    private static final int POISON_DURATION_TICKS = 80;
    private static final DustParticleOptions GREEN_MAGIC =
            new DustParticleOptions(0x1FE62E, 1.35F);

    private LivingEntity owner;
    private UUID ownerUuid;
    private LivingEntity trackingTarget;
    private UUID trackingTargetUuid;

    public BogfumeBolt(EntityType<? extends BogfumeBolt> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    private BogfumeBolt(Level level, LivingEntity owner, LivingEntity target, Vec3 position, Vec3 velocity) {
        this(ModEntities.BOGFUME_BOLT.get(), level);
        this.owner = owner;
        this.ownerUuid = owner.getUUID();
        this.trackingTarget = target;
        this.trackingTargetUuid = target.getUUID();
        setPos(position.x, position.y, position.z);
        setDeltaMovement(velocity);
    }

    public static BogfumeBolt shoot(ServerLevel level, LivingEntity owner, LivingEntity target, Vec3 position) {
        Vec3 aim = target.getEyePosition().subtract(position).normalize();
        BogfumeBolt bolt = new BogfumeBolt(level, owner, target, position, aim.scale(PROJECTILE_SPEED));
        level.addFreshEntity(bolt);
        return bolt;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    public void tick() {
        super.tick();
        if (tickCount > MAX_LIFETIME_TICKS) {
            discard();
            return;
        }

        if (level() instanceof ServerLevel) {
            applyHoming();
        }

        Vec3 movement = getDeltaMovement();
        if (level() instanceof ServerLevel serverLevel) {
            if (resolveCollision(serverLevel, movement)) {
                return;
            }
        }

        move(MoverType.SELF, movement);
        if (level() instanceof ServerLevel serverLevel) {
            spawnTrail(serverLevel);
        }
    }

    private void applyHoming() {
        LivingEntity target = getTrackingTarget();
        if (target == null || !target.isAlive()) {
            return;
        }
        Vec3 velocity = getDeltaMovement();
        double speed = velocity.length();
        Vec3 toTarget = target.getEyePosition().subtract(position()).normalize();
        Vec3 newDir = velocity.normalize().lerp(toTarget, HOMING_STRENGTH).normalize();
        setDeltaMovement(newDir.scale(speed));
    }

    private void spawnTrail(ServerLevel serverLevel) {
        // A sparse dust trail clears visually much faster than the previous
        // every-tick dust plus long-lived happy-villager particles.
        if (tickCount % 2 == 0) {
            serverLevel.sendParticles(GREEN_MAGIC, getX(), getY(), getZ(), 1, 0.015, 0.015, 0.015, 0.001);
        }
        if (tickCount % 3 == 0) {
            Vec3 trailPos = position().subtract(getDeltaMovement().normalize().scale(0.2));
            serverLevel.sendParticles(ParticleTypes.SMOKE, trailPos.x, trailPos.y, trailPos.z, 1, 0.01, 0.01, 0.01, 0.001);
        }
    }

    private boolean resolveCollision(ServerLevel serverLevel, Vec3 movement) {
        Vec3 start = position();
        Vec3 end = start.add(movement);
        BlockHitResult blockHit = level().clip(new ClipContext(
                start,
                end,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                this));
        double nearestDistanceSqr = blockHit.getType() == HitResult.Type.MISS
                ? Double.POSITIVE_INFINITY
                : start.distanceToSqr(blockHit.getLocation());
        Vec3 impactPosition = blockHit.getType() == HitResult.Type.MISS ? null : blockHit.getLocation();
        LivingEntity hitEntity = null;

        AABB sweptArea = getBoundingBox().expandTowards(movement).inflate(HIT_RADIUS);
        List<LivingEntity> targets = level().getEntitiesOfClass(
                LivingEntity.class,
                sweptArea,
                target -> target.isAlive() && target != getOwnerEntity());
        for (LivingEntity target : targets) {
            var intersection = target.getBoundingBox().inflate(HIT_RADIUS).clip(start, end);
            if (intersection.isEmpty()) {
                continue;
            }

            double distanceSqr = start.distanceToSqr(intersection.get());
            if (distanceSqr < nearestDistanceSqr) {
                nearestDistanceSqr = distanceSqr;
                impactPosition = intersection.get();
                hitEntity = target;
            }
        }

        if (impactPosition == null) {
            return false;
        }

        setPos(impactPosition.x, impactPosition.y, impactPosition.z);
        if (hitEntity != null) {
            LivingEntity owner = getOwnerEntity();
            float damage = hitEntity instanceof OgreGrunt
                    ? DAMAGE * OGRE_ALLY_DAMAGE_MULTIPLIER
                    : DAMAGE;
            hitEntity.hurtServer(serverLevel, damageSources().indirectMagic(this, owner), damage);
            hitEntity.addEffect(new MobEffectInstance(MobEffects.POISON, POISON_DURATION_TICKS, 0), owner);
        }
        spawnImpact(serverLevel);
        discard();
        return true;
    }

    private void spawnImpact(ServerLevel serverLevel) {
        serverLevel.sendParticles(GREEN_MAGIC, getX(), getY(), getZ(), 20, 0.14, 0.14, 0.14, 0.03);
        serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY(), getZ(), 8, 0.1, 0.1, 0.1, 0.0);
    }

    public LivingEntity getOwnerEntity() {
        if (owner != null && owner.isAlive()) {
            return owner;
        }
        if (ownerUuid != null && level() instanceof ServerLevel serverLevel && serverLevel.getEntity(ownerUuid) instanceof LivingEntity livingOwner) {
            owner = livingOwner;
            return owner;
        }
        return null;
    }

    private LivingEntity getTrackingTarget() {
        if (trackingTarget != null && trackingTarget.isAlive()) {
            return trackingTarget;
        }
        if (trackingTargetUuid != null && level() instanceof ServerLevel serverLevel && serverLevel.getEntity(trackingTargetUuid) instanceof LivingEntity livingTarget) {
            trackingTarget = livingTarget;
            return trackingTarget;
        }
        return null;
    }

    @Override
    protected void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput tag) {
        ownerUuid = tag.read("Owner", UUIDUtil.CODEC).orElse(null);
        trackingTargetUuid = tag.read("TrackingTarget", UUIDUtil.CODEC).orElse(null);
    }

    @Override
    protected void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput tag) {
        if (ownerUuid != null) {
            tag.store("Owner", UUIDUtil.CODEC, ownerUuid);
        }
        if (trackingTargetUuid != null) {
            tag.store("TrackingTarget", UUIDUtil.CODEC, trackingTargetUuid);
        }
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 4096.0;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false;
    }
}
