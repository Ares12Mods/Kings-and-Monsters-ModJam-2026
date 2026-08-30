package com.kingsandmonsters.entity;

import com.kingsandmonsters.ModEntities;
import com.kingsandmonsters.ModItems;
import com.kingsandmonsters.Config;
import com.kingsandmonsters.effect.CombatEffects;
import com.kingsandmonsters.enchantment.ModEnchantmentEffects;
import com.kingsandmonsters.enchantment.ModEnchantments;
import com.kingsandmonsters.item.HunterSpearEvents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Thrown ogre guard spear. Built on {@link AbstractArrow} (the same base as vanilla's
 * {@code ThrownTrident}) instead of a simple throwable so it gets real arrow physics for
 * free: it sticks into whatever it hits and falls under gravity like a trident, and it reads
 * any Loyalty enchantment off its pickup item to fly back to its owner exactly like a trident
 * does. It just carries this mod's spear art instead of the vanilla trident model/item.
 * Implements {@link ItemSupplier} so it can render through {@code ThrownItemRenderer} as its
 * flat 2D item icon (like a snowball/egg) rather than vanilla's full 3D trident model.
 */
public class OgreSpear extends AbstractArrow implements ItemSupplier {
    private static final EntityDataAccessor<Byte> ID_LOYALTY = SynchedEntityData.defineId(OgreSpear.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> ID_STABLE_POST_HIT_ROTATION =
            SynchedEntityData.defineId(OgreSpear.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> ID_POST_HIT_YAW =
            SynchedEntityData.defineId(OgreSpear.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> ID_POST_HIT_PITCH =
            SynchedEntityData.defineId(OgreSpear.class, EntityDataSerializers.FLOAT);

    private float damage = 10.0F;
    private boolean dealtDamage;
    private int clientSideReturnTickCount;

    public OgreSpear(EntityType<? extends OgreSpear> type, Level level) {
        super(type, level);
    }

    public OgreSpear(Level level, LivingEntity owner, Vec3 position, Vec3 velocity, double damage) {
        this(level, owner, new ItemStack(ModItems.HUNTERS_SPEAR.get()), position, velocity, damage, Pickup.DISALLOWED);
    }

    public OgreSpear(Level level, LivingEntity owner, ItemStack spearStack, Vec3 position, Vec3 velocity,
                     double damage, Pickup pickup) {
        super(ModEntities.OGRE_SPEAR.get(), position.x, position.y, position.z, level, spearStack.copy(), null);
        this.damage = (float) damage;
        this.pickup = pickup;
        setOwner(owner);
        setDeltaMovement(velocity);
        // AbstractArrow#tick() smoothly lerps xRot/yRot toward the velocity direction every
        // tick instead of snapping instantly, so without this the spear spawns facing whatever
        // default rotation the entity started at (visually pointing back toward the thrower)
        // and only gradually rotates tip-first over the next several ticks. Seed the rotation
        // to match the initial velocity immediately so there is nothing left to visibly lerp.
        float initialYaw = (float) (Mth.atan2(velocity.x, velocity.z) * (180.0 / Math.PI));
        float initialPitch = (float) (Mth.atan2(velocity.y, velocity.horizontalDistance()) * (180.0 / Math.PI));
        setYRot(initialYaw);
        setXRot(initialPitch);
        yRotO = initialYaw;
        xRotO = initialPitch;
        this.entityData.set(ID_LOYALTY, getLoyaltyFromItem(getPickupItemStackOrigin()));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ID_LOYALTY, (byte) 0);
        builder.define(ID_STABLE_POST_HIT_ROTATION, false);
        builder.define(ID_POST_HIT_YAW, 0.0F);
        builder.define(ID_POST_HIT_PITCH, 0.0F);
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return new ItemStack(ModItems.HUNTERS_SPEAR.get());
    }

    @Override
    protected double getDefaultGravity() {
        return 0.05;
    }

    @Override
    protected SoundEvent getDefaultHitGroundSoundEvent() {
        return SoundEvents.TRIDENT_HIT_GROUND;
    }

    @Override
    public ItemStack getItem() {
        return getPickupItemStackOrigin();
    }

    @Override
    public ItemStack getWeaponItem() {
        // The spear is the weapon itself (no separate launcher item), same as ThrownTrident.
        return getPickupItemStackOrigin();
    }

    public boolean hasStablePostHitRotation() {
        return this.entityData.get(ID_STABLE_POST_HIT_ROTATION)
                && this.entityData.get(ID_LOYALTY) <= 0;
    }

    public float getStablePostHitYaw() {
        return this.entityData.get(ID_POST_HIT_YAW);
    }

    public float getStablePostHitPitch() {
        return this.entityData.get(ID_POST_HIT_PITCH);
    }

    @Override
    public void tick() {
        if (this.inGroundTime > 4) {
            this.dealtDamage = true;
        }

        // Mirrors ThrownTrident.tick()'s return-to-owner homing, driven by whatever Loyalty
        // level (if any) is on the pickup item's enchantments.
        Entity owner = getOwner();
        int loyalty = this.entityData.get(ID_LOYALTY);
        if (loyalty > 0 && (dealtDamage || isNoPhysics()) && owner != null) {
            if (!isAcceptableReturnOwner()) {
                discard();
            } else {
                setNoPhysics(true);
                Vec3 toOwner = owner.getEyePosition().subtract(position());
                setPosRaw(getX(), getY() + toOwner.y * 0.015 * loyalty, getZ());
                if (level().isClientSide()) {
                    yOld = getY();
                }

                double acceleration = 0.05 * loyalty;
                setDeltaMovement(getDeltaMovement().scale(0.95).add(toOwner.normalize().scale(acceleration)));
                if (clientSideReturnTickCount == 0) {
                    playSound(SoundEvents.TRIDENT_RETURN, 10.0F, 1.0F);
                }

                clientSideReturnTickCount++;
            }
        }

        super.tick();
    }

    private boolean isAcceptableReturnOwner() {
        Entity owner = getOwner();
        return owner != null && owner.isAlive() && !(owner instanceof ServerPlayer player && player.isSpectator());
    }

    @Nullable
    @Override
    protected EntityHitResult findHitEntity(Vec3 startVec, Vec3 endVec) {
        return dealtDamage ? null : super.findHitEntity(startVec, endVec);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        Entity target = result.getEntity();
        Entity owner = getOwner();
        if (target instanceof OgreGrunt && owner instanceof OgreGrunt) {
            return;
        }

        float appliedDamage = damage;
        DamageSource damageSource = damageSources().thrown(this, owner != null ? owner : this);
        if (level() instanceof ServerLevel serverLevel) {
            appliedDamage = EnchantmentHelper.modifyDamage(serverLevel, getWeaponItem(), target, damageSource, appliedDamage);
        }

        dealtDamage = true;
        if (owner instanceof Player && this.entityData.get(ID_LOYALTY) <= 0) {
            this.entityData.set(ID_STABLE_POST_HIT_ROTATION, true);
            this.entityData.set(ID_POST_HIT_YAW, getYRot());
            this.entityData.set(ID_POST_HIT_PITCH, getXRot());
        }
        if (level() instanceof ServerLevel serverLevelForDamage
                && target.hurtServer(serverLevelForDamage, damageSource, appliedDamage)) {
            if (level() instanceof ServerLevel serverLevel) {
                EnchantmentHelper.doPostAttackEffectsWithItemSource(serverLevel, target, damageSource, getWeaponItem());
            }

            if (target instanceof LivingEntity livingTarget) {
                doKnockback(livingTarget, damageSource);
                int heavyThrow = ModEnchantmentEffects.level(level(), getWeaponItem(), ModEnchantments.HEAVY_THROW);
                double bonusKnockback = ModEnchantmentEffects.heavyThrowKnockback(heavyThrow);
                if (bonusKnockback > 0.0 && owner instanceof LivingEntity livingOwner) {
                    livingTarget.knockback(bonusKnockback,
                            livingOwner.getX() - livingTarget.getX(), livingOwner.getZ() - livingTarget.getZ());
                }
                boolean innateCrippleProc = random.nextDouble()
                        < Config.HUNTERS_SPEAR_CRIPPLE_CHANCE.get();
                if (innateCrippleProc) {
                    CombatEffects.applyCrippled(livingTarget, Config.CRIPPLED_SPEAR_DURATION_TICKS.get(),
                            owner instanceof LivingEntity livingOwner ? livingOwner : livingTarget);
                }
                LivingEntity effectSource = owner instanceof LivingEntity livingOwner ? livingOwner : livingTarget;
                HunterSpearEvents.tryApplyBarbed(level(), getWeaponItem(), livingTarget, effectSource);
                doPostHurtEffects(livingTarget);
            }
        }

        setDeltaMovement(getDeltaMovement().multiply(-0.01, -0.1, -0.01));
        playSound(SoundEvents.TRIDENT_HIT, 1.0F, 1.0F);
    }

    private byte getLoyaltyFromItem(ItemStack stack) {
        return level() instanceof ServerLevel serverLevel
                ? (byte) Mth.clamp(EnchantmentHelper.getTridentReturnToOwnerAcceleration(serverLevel, stack, this), 0, 127)
                : 0;
    }

    @Override
    public void tickDespawn() {
        int loyalty = this.entityData.get(ID_LOYALTY);
        if (pickup != Pickup.ALLOWED || loyalty <= 0) {
            super.tickDespawn();
        }
    }

    @Override
    public void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("Damage", damage);
        tag.putBoolean("DealtDamage", dealtDamage);
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput tag) {
        super.readAdditionalSaveData(tag);
        damage = tag.getFloatOr("Damage", 0.0F);
        dealtDamage = tag.getBooleanOr("DealtDamage", false);
        this.entityData.set(ID_LOYALTY, getLoyaltyFromItem(getPickupItemStackOrigin()));
    }
}
