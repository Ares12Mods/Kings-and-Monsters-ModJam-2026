package com.kingsandmonsters.item;

import com.kingsandmonsters.Config;
import com.kingsandmonsters.ModItems;
import com.kingsandmonsters.ModMobEffects;
import com.kingsandmonsters.effect.CombatEffects;
import com.kingsandmonsters.enchantment.ModEnchantmentEffects;
import com.kingsandmonsters.enchantment.ModEnchantments;
import com.kingsandmonsters.entity.OgreSpear;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

public final class HunterSpearEvents {

    private HunterSpearEvents() {}

    // Fires after armor/enchantment reductions are already baked into the container, so we can
    // read exactly how much a hit was reduced by armor and hand a fraction of that back as bonus
    // damage — a true "ignores X% of armor" effect rather than a flat damage bonus.
    public static void onLivingDamagePre(LivingDamageEvent.Pre event) {
        if (!isHunterSpearHit(event.getSource())) {
            return;
        }

        float armorReduction = event.getContainer().getReduction(DamageContainer.Reduction.ARMOR);
        if (armorReduction <= 0.0F) {
            return;
        }

        float pierced = armorReduction * Config.HUNTERS_SPEAR_ARMOR_PIERCE.get().floatValue();
        event.setNewDamage(event.getNewDamage() + pierced);
    }

    public static void onLivingDamagePost(LivingDamageEvent.Post event) {
        if (event.getHealthDamage() <= 0.0F
                || !(event.getSource().getEntity() instanceof ServerPlayer attacker)
                || event.getSource().getDirectEntity() != attacker) {
            return;
        }

        ItemStack weapon = attacker.getMainHandItem();
        if (weapon.is(ModItems.HUNTERS_SPEAR.get())) {
            tryApplyBarbed(attacker.level(), weapon, event.getEntity(), attacker);
        }
    }

    public static void tryApplyBarbed(Level level, ItemStack weapon, LivingEntity target, LivingEntity source) {
        if (target.hasEffect(ModMobEffects.BLEEDING)) {
            return;
        }
        int barbedLevel = ModEnchantmentEffects.level(level, weapon, ModEnchantments.BARBED);
        if (barbedLevel > 0 && source.getRandom().nextDouble() < Config.BARBED_BLEED_CHANCE.get()) {
            CombatEffects.applyBleeding(target, Config.BARBED_BLEED_DURATION_TICKS.get(), source, barbedLevel - 1);
        }
    }

    private static boolean isHunterSpearHit(net.minecraft.world.damagesource.DamageSource source) {
        if (source.getDirectEntity() instanceof OgreSpear spear) {
            return spear.getWeaponItem().is(ModItems.HUNTERS_SPEAR.get());
        }

        return source.getEntity() instanceof Player player
                && source.getDirectEntity() == player
                && player.getMainHandItem().is(ModItems.HUNTERS_SPEAR.get());
    }
}
