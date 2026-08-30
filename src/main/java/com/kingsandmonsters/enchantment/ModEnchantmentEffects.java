package com.kingsandmonsters.enchantment;

import com.kingsandmonsters.Config;
import com.kingsandmonsters.ModItems;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/** Java-side mechanics for effects that cannot be expressed by vanilla enchantment components. */
public final class ModEnchantmentEffects {
    private ModEnchantmentEffects() {}

    public static int level(Level level, ItemStack stack, ResourceKey<Enchantment> key) {
        var enchantment = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
        return stack.getEnchantmentLevel(enchantment);
    }

    public static float heavyThrowDamage(float baseDamage, int level) {
        return baseDamage + (float) (Config.HEAVY_THROW_DAMAGE_PER_LEVEL.get() * level);
    }

    public static float heavyThrowSpeed(float baseSpeed, int level) {
        double multiplier = Math.max(0.35, 1.0 - Config.HEAVY_THROW_SPEED_PENALTY_PER_LEVEL.get() * level);
        return (float) (baseSpeed * multiplier);
    }

    public static double heavyThrowKnockback(int level) {
        return Config.HEAVY_THROW_KNOCKBACK_PER_LEVEL.get() * level;
    }

    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        Entity sourceEntity = event.getSource().getEntity();
        if (!(sourceEntity instanceof Player attacker) || event.getSource().getDirectEntity() != attacker) {
            return;
        }

        ItemStack weapon = attacker.getMainHandItem();
        LivingEntity target = event.getEntity();
        if (weapon.is(ModItems.KINGS_CLEAVER.get()) || weapon.is(ModItems.OGRE_KINGS_CLUB.get())) {
            int tyrantLevel = level(attacker.level(), weapon, ModEnchantments.TYRANT);
            if (tyrantLevel > 0) {
                double radius = Config.TYRANT_SEARCH_RADIUS.get();
                AABB area = attacker.getBoundingBox().inflate(radius);
                int hostiles = attacker.level().getEntitiesOfClass(Monster.class, area,
                        mob -> mob.isAlive() && mob != target).size();
                double damagePerHostile = switch (tyrantLevel) {
                    case 1 -> 0.5;
                    case 2 -> 0.75;
                    default -> 1.0;
                };
                int hostileCap = switch (tyrantLevel) {
                    case 1 -> 4;
                    case 2 -> 5;
                    default -> 6;
                };
                double bonus = Math.min(hostiles, hostileCap) * damagePerHostile;
                event.setAmount(event.getAmount() + (float) bonus);
            }
        }
    }
}
