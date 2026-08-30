package com.kingsandmonsters.entity;

import com.kingsandmonsters.effect.BogfumeRageEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.List;

public class PoisonFogCloud extends Entity {
    public static final int WARNING_TICKS = 40;
    public static final int ACTIVE_TICKS = 100;
    public static final int DISSIPATION_TICKS = 18;
    public static final float RADIUS = 3.2F;
    public static final float VISUAL_RING_RADIUS = RADIUS;
    // Sustained zone damage (ticks every second while standing in it), kept modest per-tick
    // (~0.7 hearts vs diamond) since it accumulates over the cloud's full duration.
    private static final float DAMAGE_PER_TICK = 6.0F;
    private static final int DAMAGE_INTERVAL_TICKS = 20;
    private static final int POISON_DURATION_TICKS = 60;

    private int age;

    public PoisonFogCloud(EntityType<? extends PoisonFogCloud> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public boolean isWarningPhase() {
        return age <= WARNING_TICKS;
    }

    public boolean isDissipating() {
        return age > WARNING_TICKS + ACTIVE_TICKS;
    }

    public float getVisualProgress(float partialTick) {
        int phaseStart = isWarningPhase() ? 0 : WARNING_TICKS;
        int phaseDuration = isWarningPhase() ? WARNING_TICKS : ACTIVE_TICKS;
        return Math.clamp((age + partialTick - phaseStart) / phaseDuration, 0.0F, 1.0F);
    }

    public static PoisonFogCloud spawn(ServerLevel level, double x, double y, double z) {
        PoisonFogCloud cloud = new PoisonFogCloud(com.kingsandmonsters.ModEntities.POISON_FOG_CLOUD.get(), level);
        cloud.setPos(x, findGroundY(level, x, y, z), z);
        level.addFreshEntity(cloud);
        return cloud;
    }

    private static double findGroundY(ServerLevel level, double x, double y, double z) {
        BlockPos.MutableBlockPos pos = BlockPos.containing(x, y, z).mutable();
        for (int i = 0; i < 6; i++) {
            BlockState state = level.getBlockState(pos);
            BlockState belowState = level.getBlockState(pos.below());
            if (state.getCollisionShape(level, pos).isEmpty()
                    && !belowState.getCollisionShape(level, pos.below()).isEmpty()) {
                return pos.getY() + 0.05;
            }
            pos.move(0, -1, 0);
        }
        return y + 0.05;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    public void tick() {
        super.tick();
        age++;

        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }

        if (age <= WARNING_TICKS) {
            spawnWarningParticles(serverLevel);
        } else if (!isDissipating()) {
            spawnActiveParticles(serverLevel);
            applyActiveEffects();
        }

        if (age >= WARNING_TICKS + ACTIVE_TICKS + DISSIPATION_TICKS) {
            discard();
        }
    }

    private void spawnWarningParticles(ServerLevel level) {
        if (age % 2 == 0) {
            spawnCircle(level, ParticleMode.WARNING);
        }
        if (age % 5 == 0) {
            level.sendParticles(
                    ParticleTypes.HAPPY_VILLAGER,
                    getX() + (random.nextDouble() - 0.5) * RADIUS * 1.4,
                    getY() + 0.2,
                    getZ() + (random.nextDouble() - 0.5) * RADIUS * 1.4,
                    3,
                    0.08,
                    0.12,
                    0.08,
                    0.01);
        }
    }

    private void spawnActiveParticles(ServerLevel level) {
        if (age % 2 == 0) {
            spawnCircle(level, ParticleMode.ACTIVE);
        }
        level.sendParticles(
                ParticleTypes.HAPPY_VILLAGER,
                getX(),
                getY() + 0.65,
                getZ(),
                5,
                RADIUS * 0.45,
                0.45,
                RADIUS * 0.45,
                0.02);
        if (age % 3 == 0) {
            level.sendParticles(
                    ParticleTypes.WITCH,
                    getX(),
                    getY() + 0.35,
                    getZ(),
                    6,
                    RADIUS * 0.35,
                    0.25,
                    RADIUS * 0.35,
                    0.01);
        }
    }

    private void spawnCircle(ServerLevel level, ParticleMode mode) {
        int points = mode == ParticleMode.WARNING ? 18 : 24;
        double y = getY() + 0.08;
        for (int i = 0; i < points; i++) {
            double angle = (Math.PI * 2.0 * i / points) + age * mode.spinSpeed;
            double x = getX() + Math.cos(angle) * VISUAL_RING_RADIUS;
            double z = getZ() + Math.sin(angle) * VISUAL_RING_RADIUS;
            level.sendParticles(mode.particleType, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private void applyActiveEffects() {
        ServerLevel serverLevel = (ServerLevel) level();
        AABB area = getBoundingBox().inflate(RADIUS, 1.4, RADIUS);
        List<LivingEntity> targets = level().getEntitiesOfClass(
                LivingEntity.class,
                area,
                entity -> entity.isAlive()
                        && distanceToSqr(entity) <= RADIUS * RADIUS
                        && Math.abs(entity.getY() - getY()) <= 2.25);

        for (LivingEntity target : targets) {
            if (target instanceof OgreGrunt ogre) {
                BogfumeRageEvents.applyToOgre(ogre);
                continue;
            }

            target.addEffect(new MobEffectInstance(MobEffects.POISON, POISON_DURATION_TICKS, 1));
            if (age % DAMAGE_INTERVAL_TICKS == 0) {
                target.hurtServer(serverLevel, damageSources().magic(), DAMAGE_PER_TICK);
            }
        }
    }

    @Override
    protected void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput compound) {
        age = compound.getIntOr("Age", 0);
    }

    @Override
    protected void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput compound) {
        compound.putInt("Age", age);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false;
    }

    private enum ParticleMode {
        WARNING(ParticleTypes.WITCH, 0.03),
        ACTIVE(ParticleTypes.WITCH, 0.06);

        private final net.minecraft.core.particles.SimpleParticleType particleType;
        private final double spinSpeed;

        ParticleMode(net.minecraft.core.particles.SimpleParticleType particleType, double spinSpeed) {
            this.particleType = particleType;
            this.spinSpeed = spinSpeed;
        }
    }
}
