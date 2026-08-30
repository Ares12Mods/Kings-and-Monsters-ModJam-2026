package com.kingsandmonsters.item;

import com.kingsandmonsters.ModItems;
import com.kingsandmonsters.entity.OgreSpear;
import com.kingsandmonsters.mixin.AbstractArrowAccessor;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import top.theillusivec4.curios.api.CuriosApi;

public final class ArtifactEvents {
    private static final float NECKLACE_KNOCKBACK_BONUS = 0.1F;
    private static final float CHARM_POISON_REFLECT_CHANCE = 0.30F;
    private static final float CHARM_POISON_DAMAGE_MULTIPLIER = 1.2F;
    private static final int CHARM_POISON_DURATION_TICKS = 100;
    private static final String CHARM_POISON_DAMAGE_UNTIL_TAG =
            "KingsAndMonstersCharmPoisonDamageUntil";
    private static final float BUCKLER_ARROW_BLOCK_CHANCE = 0.25F;
    private static final double BUCKLER_ARROW_DAMAGE_MULTIPLIER = 1.10;

    private ArtifactEvents() {}

    public static void onKnockBack(LivingKnockBackEvent event) {
        LivingEntity attacker = event.getEntity().getLastHurtByMob();
        if (attacker != null && hasCurio(attacker, ModItems.OGRE_TOOTH_NECKLACE.get())) {
            event.setStrength(event.getStrength() + event.getStrength() * NECKLACE_KNOCKBACK_BONUS);
        }
    }

    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()
                || !(event.getEntity() instanceof AbstractArrow arrow)
                || arrow instanceof OgreSpear
                || !(arrow.getOwner() instanceof Player player)
                || !hasCurio(player, ModItems.BUCKLER.get())) {
            return;
        }

        arrow.setBaseDamage(((AbstractArrowAccessor) arrow).kingsandmonsters$getBaseDamage()
                * BUCKLER_ARROW_DAMAGE_MULTIPLIER);
    }

    public static void onMobEffectAdded(MobEffectEvent.Added event) {
        MobEffectInstance effect = event.getEffectInstance();
        if (!effect.is(MobEffects.POISON)) {
            return;
        }

        Entity effectSource = event.getEffectSource();
        LivingEntity poisonOwner = effectSource instanceof LivingEntity livingSource
                ? livingSource
                : effectSource instanceof Projectile projectile
                        && projectile.getOwner() instanceof LivingEntity livingOwner
                                ? livingOwner
                                : null;
        if (poisonOwner == null || !hasCurio(poisonOwner, ModItems.BOGFUME_CHARM.get())) {
            return;
        }

        event.getEntity().getPersistentData().putLong(
                CHARM_POISON_DAMAGE_UNTIL_TAG,
                event.getEntity().level().getGameTime() + effect.getDuration());
    }

    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();

        // Poison applied by the Bogfume Charm deals 20% more damage for that proc's duration.
        if (event.getSource().is(DamageTypes.MAGIC)
                && victim.hasEffect(MobEffects.POISON)
                && victim.getPersistentData().getLongOr(CHARM_POISON_DAMAGE_UNTIL_TAG, 0L)
                >= victim.level().getGameTime()) {
            event.setAmount(event.getAmount() * CHARM_POISON_DAMAGE_MULTIPLIER);
        }

        // Buckler: 25% chance to block incoming arrows
        if (hasCurio(victim, ModItems.BUCKLER.get())
                && event.getSource().getDirectEntity() instanceof AbstractArrow
                && !(event.getSource().getDirectEntity() instanceof OgreSpear)
                && victim.getRandom().nextFloat() < BUCKLER_ARROW_BLOCK_CHANCE) {
            event.setCanceled(true);
            return;
        }

    }

    public static void onLivingDamagePost(LivingDamageEvent.Post event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()
                || event.getHealthDamage() <= 0.0F
                || !hasCurio(victim, ModItems.BOGFUME_CHARM.get())) {
            return;
        }

        // The production tooltip defines melee retaliation. Projectile sources have a
        // different direct entity and therefore intentionally do not proc the charm.
        LivingEntity attacker = event.getSource().getEntity() instanceof LivingEntity living
                ? living
                : null;
        if (attacker != null
                && attacker != victim
                && event.getSource().getDirectEntity() == attacker
                && victim.getRandom().nextFloat() < CHARM_POISON_REFLECT_CHANCE) {
            attacker.addEffect(
                    new MobEffectInstance(MobEffects.POISON, CHARM_POISON_DURATION_TICKS, 1),
                    victim);
        }
    }

    private static boolean hasCurio(LivingEntity entity, Item item) {
        return CuriosApi.getCuriosInventory(entity)
                .map(h -> h.findFirstCurio(stack -> stack.is(item)).isPresent())
                .orElse(false);
    }
}
