package com.kingsandmonsters.item;

import com.kingsandmonsters.ModSoundEvents;
import com.kingsandmonsters.network.ScreenShakePayload;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class OgreKingsClubEvents {
    public static final int COOLDOWN_TICKS = 15 * 20;
    private static final int IMPACT_DELAY_TICKS = 1;
    private static final float SLAM_DAMAGE = 15.0F;
    private static final double SLAM_RADIUS = 4.5;
    private static final double VERTICAL_LAUNCH = 0.65;
    private static final Map<UUID, PendingSlam> PENDING_SLAMS = new HashMap<>();

    private OgreKingsClubEvents() {
    }

    public static void reset() {
        PENDING_SLAMS.clear();
    }

    public static void beginSlam(ServerPlayer player) {
        PENDING_SLAMS.put(player.getUUID(), new PendingSlam(IMPACT_DELAY_TICKS));
    }

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        PendingSlam slam = PENDING_SLAMS.get(player.getUUID());
        if (slam == null || --slam.ticksRemaining > 0) {
            return;
        }

        PENDING_SLAMS.remove(player.getUUID());
        if (player.isAlive()) {
            impact(player);
        }
    }

    private static void impact(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        Vec3 look = player.getLookAngle();
        Vec3 horizontal = new Vec3(look.x, 0.0, look.z);
        if (horizontal.lengthSqr() < 1.0E-4) {
            horizontal = Vec3.directionFromRotation(0.0F, player.getYRot());
        }
        Vec3 center = player.position().add(horizontal.normalize().scale(2.0));

        AABB area = new AABB(
                center.x - SLAM_RADIUS, center.y - 1.5, center.z - SLAM_RADIUS,
                center.x + SLAM_RADIUS, center.y + 3.5, center.z + SLAM_RADIUS);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area,
                target -> target != player && target.isAlive()
                        && player.canAttack(target)
                        && horizontalDistanceSqr(center, target) <= SLAM_RADIUS * SLAM_RADIUS)) {
            if (target.hurtServer(level, player.damageSources().playerAttack(player), SLAM_DAMAGE)) {
                Vec3 movement = target.getDeltaMovement();
                target.setDeltaMovement(movement.x, Math.max(movement.y, VERTICAL_LAUNCH), movement.z);
                target.hurtMarked = true;
            }
        }

        level.playSound(null, center.x, center.y, center.z,
                SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.35F, 0.55F);
        level.playSound(null, center.x, center.y, center.z,
                ModSoundEvents.OGRE_BRUTE_BELLY_SLAM_IMPACT.get(), SoundSource.PLAYERS, 1.25F, 0.72F);
        level.sendParticles(ParticleTypes.CLOUD, center.x, center.y + 0.2, center.z,
                44, 1.4, 0.2, 1.4, 0.04);
        level.sendParticles(ParticleTypes.POOF, center.x, center.y + 0.3, center.z,
                24, 0.9, 0.15, 0.9, 0.025);
        spawnRing(level, center);
        PacketDistributor.sendToPlayersNear(level, null, center.x, center.y, center.z, 24.0,
                new ScreenShakePayload(center.x, center.y, center.z, 1.2F));
    }

    private static void spawnRing(ServerLevel level, Vec3 center) {
        int points = 40;
        for (int point = 0; point < points; point++) {
            double angle = Math.PI * 2.0 * point / points;
            level.sendParticles(ParticleTypes.CLOUD,
                    center.x + Math.cos(angle) * SLAM_RADIUS,
                    center.y + 0.12,
                    center.z + Math.sin(angle) * SLAM_RADIUS,
                    1, 0.025, 0.02, 0.025, 0.003);
        }
    }

    private static double horizontalDistanceSqr(Vec3 center, LivingEntity target) {
        double x = target.getX() - center.x;
        double z = target.getZ() - center.z;
        return x * x + z * z;
    }

    private static final class PendingSlam {
        private int ticksRemaining;

        private PendingSlam(int ticksRemaining) {
            this.ticksRemaining = ticksRemaining;
        }
    }
}
