package com.kingsandmonsters.item;

import com.kingsandmonsters.ModItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class RattleboneRingEvents {
    private static final String LAST_DEATH_RATTLE_TAG = "KingsAndMonstersLastDeathRattle";
    private static final int COOLDOWN_TICKS = 6 * 20;
    private static final int SLOWNESS_TICKS = 4 * 20;
    private static final double RADIUS = 5.5;
    private static final float DAMAGE = 4.0F;

    // AOE telegraph ring: a sparse ring of particle points that rapidly expands from the wearer
    // out to the actual Death Rattle radius, using the same lightweight "points around a circle"
    // technique AND the same white ParticleTypes.CLOUD particle as the Ogre King's roar shockwave
    // (OgreLord#tickRoarShockwave) — only the radius/duration are Rattlebone-specific; the King's
    // own effect is untouched.
    private static final int RING_EXPANSION_TICKS = 4;
    private static final float RING_START_RADIUS = 0.4F;
    private static final List<ActiveRing> ACTIVE_RINGS = new ArrayList<>();

    private RattleboneRingEvents() {
    }

    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Enemy)
                || !(event.getSource().getEntity() instanceof ServerPlayer player)
                || !(player.level() instanceof ServerLevel level)
                || !hasRattleboneRing(player)) {
            return;
        }

        long gameTime = level.getGameTime();
        long lastActivation = player.getPersistentData().getLongOr(LAST_DEATH_RATTLE_TAG, 0L);
        if (lastActivation != 0L && gameTime - lastActivation < COOLDOWN_TICKS) {
            return;
        }

        // Commit the cooldown before dealing damage so shockwave kills cannot
        // recursively trigger another Death Rattle in the same tick.
        player.getPersistentData().putLong(LAST_DEATH_RATTLE_TAG, gameTime);
        LivingEntity slain = event.getEntity();

        for (LivingEntity target : level.getEntitiesOfClass(
                LivingEntity.class,
                slain.getBoundingBox().inflate(RADIUS),
                target -> target != slain
                        && target.isAlive()
                        && target instanceof Enemy
                        && target.distanceToSqr(slain) <= RADIUS * RADIUS)) {
            target.hurtServer(level, level.damageSources().playerAttack(player), DAMAGE);
            if (!target.is(Tags.EntityTypes.BOSSES)) {
                target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SLOWNESS_TICKS, 0), player);
            }
        }

        level.playSound(null, slain.blockPosition(), SoundEvents.SKELETON_STEP,
                SoundSource.PLAYERS, 1.1F, 0.65F);
        level.sendParticles(ParticleTypes.POOF,
                slain.getX(), slain.getY() + slain.getBbHeight() * 0.5, slain.getZ(),
                18, 1.2, 0.65, 1.2, 0.04);

        ACTIVE_RINGS.add(new ActiveRing(level, slain.position()));
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        if (ACTIVE_RINGS.isEmpty()) {
            return;
        }
        Iterator<ActiveRing> iterator = ACTIVE_RINGS.iterator();
        while (iterator.hasNext()) {
            if (!iterator.next().tick()) {
                iterator.remove();
            }
        }
    }

    private static boolean hasRattleboneRing(LivingEntity entity) {
        return CuriosApi.getCuriosInventory(entity)
                .map(inventory -> inventory.findFirstCurio(
                        stack -> stack.is(ModItems.RATTLEBONE_RING.get())).isPresent())
                .orElse(false);
    }

    /** One expanding telegraph ring, from {@link #RING_START_RADIUS} out to the real {@link #RADIUS}. */
    private static final class ActiveRing {
        private final ServerLevel level;
        private final Vec3 center;
        private int age;

        private ActiveRing(ServerLevel level, Vec3 center) {
            this.level = level;
            this.center = center;
        }

        private boolean tick() {
            if (age > RING_EXPANSION_TICKS) {
                return false;
            }
            float progress = Mth.clamp((float) age / RING_EXPANSION_TICKS, 0.0F, 1.0F);
            float radius = Mth.lerp(progress, RING_START_RADIUS, (float) RADIUS);
            int ringPoints = Math.max(14, (int) Math.ceil(radius * 3.0F));
            double y = center.y + 0.1;
            for (int point = 0; point < ringPoints; point++) {
                double angle = Math.TAU * point / ringPoints;
                double x = center.x + Math.cos(angle) * radius;
                double z = center.z + Math.sin(angle) * radius;
                level.sendParticles(ParticleTypes.CLOUD, x, y, z, 1, 0.02, 0.01, 0.02, 0.005);
                level.sendParticles(ParticleTypes.CLOUD, x, y + 0.5, z, 1, 0.02, 0.01, 0.02, 0.005);
            }
            age++;
            return true;
        }
    }
}
