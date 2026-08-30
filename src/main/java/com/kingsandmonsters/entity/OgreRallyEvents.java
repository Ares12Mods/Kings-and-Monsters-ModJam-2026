package com.kingsandmonsters.entity;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Event-driven ally-defense/rally system: when an Ogre (or the Merchant, via his escort) is hurt
 * by a valid non-allied living attacker, nearby Ogres are encouraged to target that attacker.
 * This is intentionally the only hook — no per-tick scanning, no persistent rally state, no
 * pathfinding of its own. Once a target is assigned, ordinary Ogre AI (goals, animation,
 * pathfinding) takes over exactly as it already does for any other target.
 */
public final class OgreRallyEvents {
    private static final double OGRE_RALLY_RADIUS = 24.0;
    private static final double OGRE_ELITE_RALLY_RADIUS = 32.0;
    private static final double OGRE_KING_RALLY_RADIUS = 48.0;
    private static final int OGRE_RALLY_COOLDOWN_TICKS = 20;

    private static final Map<UUID, Long> LAST_RALLY_GAME_TIME = new HashMap<>();

    private OgreRallyEvents() {
    }

    public static void onLivingDamagePost(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity victim) || victim.level().isClientSide()
                || event.getHealthDamage() <= 0.0F) {
            return;
        }

        double radius = rallyRadiusFor(victim);
        if (radius <= 0.0) {
            // Not an Ogre-faction victim, or a sleeping/waking King — no rally event at all.
            return;
        }

        LivingEntity attacker = resolveAttacker(event.getSource());
        if (attacker == null || attacker == victim || !attacker.isAlive()
                || attacker instanceof Player
                || attacker instanceof OgreGrunt || attacker instanceof OgreMerchant) {
            // Ignore environmental damage, friendly fire, and players: player-caused aggro is
            // already handled by the existing faction-anger/HurtByTargetGoal systems and must
            // not be duplicated here.
            return;
        }

        if (onRallyCooldown(victim)) {
            return;
        }

        boolean elevatedPriority = radius != OGRE_RALLY_RADIUS;
        AABB area = victim.getBoundingBox().inflate(radius);
        for (OgreGrunt ally : victim.level().getEntitiesOfClass(OgreGrunt.class, area,
                candidate -> candidate != victim && candidate.isAlive() && !(candidate instanceof OgreLord))) {
            if (!ally.canAttack(attacker)) {
                continue;
            }
            if (shouldRally(ally, attacker, elevatedPriority)) {
                ally.setTarget(attacker);
            }
        }
    }

    /**
     * Radius for the victim's rally tier, or {@code -1} if this victim shouldn't trigger a rally
     * at all (not Ogre-faction, or the King is still sleeping/waking and cannot meaningfully be
     * harmed — there is nothing to "defend" yet).
     */
    private static double rallyRadiusFor(LivingEntity victim) {
        if (victim instanceof OgreLord lord) {
            return lord.isFortEncounterActive() ? OGRE_KING_RALLY_RADIUS : -1.0;
        }
        if (victim instanceof OgreGruntCaptain || victim instanceof OgreBrute || victim instanceof OgreMage) {
            return OGRE_ELITE_RALLY_RADIUS;
        }
        if (victim instanceof OgreGrunt || victim instanceof OgreMerchant) {
            return OGRE_RALLY_RADIUS;
        }
        return -1.0;
    }

    private static boolean shouldRally(OgreGrunt ally, LivingEntity attacker, boolean elevatedPriority) {
        LivingEntity currentTarget = ally.getTarget();
        if (currentTarget == null || !currentTarget.isAlive()) {
            return true;
        }
        if (currentTarget == attacker) {
            return false;
        }
        if (currentTarget instanceof Player) {
            // Never destabilize ongoing player combat, even for an awakened King — a random
            // mob tapping an ally is not reason enough to abandon the major threat.
            return false;
        }
        // Already fighting some other non-player mob: normal-tier allies finish that fight, but
        // an attacked elite or an active King is important enough to pull them off it.
        return elevatedPriority;
    }

    /** Resolves the actual living entity responsible for the damage, unwrapping projectiles. */
    private static LivingEntity resolveAttacker(DamageSource source) {
        Entity causing = source.getEntity();
        if (causing instanceof LivingEntity living) {
            return living;
        }
        Entity direct = source.getDirectEntity();
        if (direct instanceof Projectile projectile && projectile.getOwner() instanceof LivingEntity owner) {
            return owner;
        }
        if (direct instanceof LivingEntity directLiving) {
            return directLiving;
        }
        return null;
    }

    private static boolean onRallyCooldown(LivingEntity victim) {
        long now = victim.level().getGameTime();
        Long last = LAST_RALLY_GAME_TIME.get(victim.getUUID());
        if (last != null && now - last < OGRE_RALLY_COOLDOWN_TICKS) {
            return true;
        }
        LAST_RALLY_GAME_TIME.put(victim.getUUID(), now);
        return false;
    }
}
