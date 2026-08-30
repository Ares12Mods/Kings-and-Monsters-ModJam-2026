package com.kingsandmonsters.item;

import com.kingsandmonsters.ModItems;
import com.kingsandmonsters.ModSoundEvents;
import com.kingsandmonsters.network.OgrebloodTotemActivationPayload;
import com.kingsandmonsters.network.ScreenShakePayload;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class OgrebloodTotemEvents {
    private static final float REVIVE_HEALTH = 4.0F;
    private static final double SHOCKWAVE_RADIUS = 8.0;
    private static final double HORIZONTAL_KNOCKBACK = 2.0;
    private static final double VERTICAL_LAUNCH = 0.45;
    private static final int RESISTANCE_TICKS = 5 * 20;
    private static final int STRENGTH_TICKS = 8 * 20;
    private static final int REGENERATION_TICKS = 5 * 20;
    private static final int SHOCKWAVE_SLOWNESS_TICKS = 5 * 20;
    private static final int SHOCKWAVE_EXPANSION_TICKS = 8;
    private static final double SHOCKWAVE_START_RADIUS = 0.6;
    private static final DustParticleOptions BOG_GREEN =
            new DustParticleOptions(0x387A1F, 1.35F);
    private static final DustParticleOptions OGRE_GREEN =
            new DustParticleOptions(0x6BAD29, 1.05F);
    private static final List<ActiveShockwave> ACTIVE_SHOCKWAVES = new ArrayList<>();

    private OgrebloodTotemEvents() {
    }

    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity saved = event.getEntity();
        if (!(saved.level() instanceof ServerLevel level)) {
            return;
        }

        InteractionHand hand = findHeldTotem(saved);
        if (hand == null) {
            return;
        }

        ItemStack heldTotem = saved.getItemInHand(hand);
        event.setCanceled(true);
        heldTotem.shrink(1);
        saved.setHealth(Math.min(REVIVE_HEALTH, saved.getMaxHealth()));
        saved.clearFire();
        saved.addEffect(new MobEffectInstance(MobEffects.RESISTANCE,
                RESISTANCE_TICKS, 1, false, true, true));
        saved.addEffect(new MobEffectInstance(MobEffects.STRENGTH,
                STRENGTH_TICKS, 0, false, true, true));
        saved.addEffect(new MobEffectInstance(MobEffects.REGENERATION,
                REGENERATION_TICKS, 1, false, true, true));

        if (saved instanceof ServerPlayer player) {
            CriteriaTriggers.USED_TOTEM.trigger(player, new ItemStack(ModItems.OGREBLOOD_TOTEM.get()));
            player.awardStat(Stats.ITEM_USED.get(ModItems.OGREBLOOD_TOTEM.get()));
            PacketDistributor.sendToPlayer(player, new OgrebloodTotemActivationPayload());
        }

        LivingEntity lethalAttacker = event.getSource().getEntity() instanceof LivingEntity living ? living : null;
        ACTIVE_SHOCKWAVES.add(new ActiveShockwave(level, saved, lethalAttacker, saved.position()));
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING,
                saved.getX(), saved.getY() + saved.getBbHeight() * 0.55, saved.getZ(),
                36, 0.55, 0.7, 0.55, 0.2);
        level.playSound(null, saved.blockPosition(), ModSoundEvents.OGREBLOOD_TOTEM_ACTIVATE.get(),
                SoundSource.PLAYERS, 1.8F, 1.0F);
        level.playSound(null, saved.blockPosition(), SoundEvents.TOTEM_USE,
                SoundSource.PLAYERS, 1.0F, 1.0F);
        PacketDistributor.sendToPlayersNear(level, null,
                saved.getX(), saved.getY(), saved.getZ(), 16.0,
                new ScreenShakePayload(saved.getX(), saved.getY(), saved.getZ(),
                        0.32F, 10, 1.6F));
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        Iterator<ActiveShockwave> iterator = ACTIVE_SHOCKWAVES.iterator();
        while (iterator.hasNext()) {
            ActiveShockwave shockwave = iterator.next();
            if (!shockwave.tick()) {
                iterator.remove();
            }
        }
    }

    public static void reset() {
        ACTIVE_SHOCKWAVES.clear();
    }

    private static InteractionHand findHeldTotem(LivingEntity entity) {
        if (entity.getMainHandItem().is(ModItems.OGREBLOOD_TOTEM.get())) {
            return InteractionHand.MAIN_HAND;
        }
        if (entity.getOffhandItem().is(ModItems.OGREBLOOD_TOTEM.get())) {
            return InteractionHand.OFF_HAND;
        }
        return null;
    }

    private static void repelHostilesAtWavefront(ActiveShockwave shockwave,
                                                  double previousRadius, double radius) {
        ServerLevel level = shockwave.level;
        LivingEntity saved = shockwave.saved;
        AABB area = new AABB(shockwave.origin, shockwave.origin).inflate(radius + 1.0, 4.0, radius + 1.0);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area,
                target -> isValidTarget(saved, target, shockwave.lethalAttacker))) {
            if (!shockwave.hitTargets.add(target.getUUID())) {
                continue;
            }
            double dx = target.getX() - shockwave.origin.x;
            double dz = target.getZ() - shockwave.origin.z;
            double distance = Math.sqrt(dx * dx + dz * dz);
            if (distance > radius + 0.8 || distance < Math.max(0.0, previousRadius - 0.8)) {
                shockwave.hitTargets.remove(target.getUUID());
                continue;
            }
            if (dx * dx + dz * dz < 0.0001) {
                double angle = saved.getRandom().nextDouble() * Math.TAU;
                dx = Math.cos(angle);
                dz = Math.sin(angle);
            }
            Vec3 velocity = target.getDeltaMovement();
            double inverseDistance = 1.0 / Math.sqrt(dx * dx + dz * dz);
            target.setDeltaMovement(
                    velocity.x * 0.5 + dx * inverseDistance * HORIZONTAL_KNOCKBACK,
                    Math.max(velocity.y, VERTICAL_LAUNCH),
                    velocity.z * 0.5 + dz * inverseDistance * HORIZONTAL_KNOCKBACK);
            if (target instanceof Mob) {
                target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS,
                        SHOCKWAVE_SLOWNESS_TICKS, 1, false, true, true), saved);
            }
            target.hurtMarked = true;
        }
    }

    private static boolean isValidTarget(LivingEntity saved, LivingEntity target,
                                         LivingEntity lethalAttacker) {
        if (target == saved || !target.isAlive() || saved.isAlliedTo(target)
                || target instanceof TamableAnimal tameable && tameable.isTame()) {
            return false;
        }
        if (target instanceof Player) {
            return target == lethalAttacker;
        }
        return target instanceof Enemy || target instanceof Mob mob && mob.getTarget() == saved;
    }

    private static void spawnShockwaveRing(ServerLevel level, Vec3 center, double radius) {
        double y = center.y + 0.15;
        int ringPoints = Math.max(20, (int) Math.ceil(radius * 6.0));
        for (int point = 0; point < ringPoints; point++) {
            double angle = Math.TAU * point / ringPoints;
            double x = center.x + Math.cos(angle) * radius;
            double z = center.z + Math.sin(angle) * radius;
            level.sendParticles(ParticleTypes.CLOUD, x, y, z,
                    1, 0.025, 0.018, 0.025, 0.003);
            level.sendParticles(point % 2 == 0 ? BOG_GREEN : OGRE_GREEN, x, y + 0.035, z,
                    1, 0.018, 0.018, 0.018, 0.0);
            if (point % 4 == 0) {
                level.sendParticles(ParticleTypes.WITCH, x, y + 0.07, z,
                        1, 0.015, 0.025, 0.015, 0.0);
            }
        }
    }

    private static final class ActiveShockwave {
        private final ServerLevel level;
        private final LivingEntity saved;
        private final LivingEntity lethalAttacker;
        private final Vec3 origin;
        private final Set<UUID> hitTargets = new HashSet<>();
        private int age;

        private ActiveShockwave(ServerLevel level, LivingEntity saved,
                                LivingEntity lethalAttacker, Vec3 origin) {
            this.level = level;
            this.saved = saved;
            this.lethalAttacker = lethalAttacker;
            this.origin = origin;
        }

        private boolean tick() {
            if (!saved.isAlive() || age > SHOCKWAVE_EXPANSION_TICKS) {
                return false;
            }
            double previousProgress = Math.max(0.0, (age - 1.0) / SHOCKWAVE_EXPANSION_TICKS);
            double progress = Math.min(1.0, (double) age / SHOCKWAVE_EXPANSION_TICKS);
            double previousRadius = SHOCKWAVE_START_RADIUS
                    + (SHOCKWAVE_RADIUS - SHOCKWAVE_START_RADIUS) * previousProgress;
            double radius = SHOCKWAVE_START_RADIUS
                    + (SHOCKWAVE_RADIUS - SHOCKWAVE_START_RADIUS) * progress;
            spawnShockwaveRing(level, origin, radius);
            repelHostilesAtWavefront(this, previousRadius, radius);
            age++;
            return true;
        }
    }
}
