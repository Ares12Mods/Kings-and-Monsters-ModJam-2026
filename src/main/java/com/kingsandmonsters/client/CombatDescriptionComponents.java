package com.kingsandmonsters.client;

import com.kingsandmonsters.Config;
import com.kingsandmonsters.ModMobEffects;
import com.kingsandmonsters.effect.BleedingEffect;
import com.kingsandmonsters.effect.BogfumeRageEvents;
import com.kingsandmonsters.effect.CrippledEffect;
import com.kingsandmonsters.effect.DazedEffect;
import com.kingsandmonsters.enchantment.ModEnchantments;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.enchantment.Enchantment;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;

/** Builds balance-aware descriptions shared by inventory tooltips and JEI reference pages. */
public final class CombatDescriptionComponents {
    private CombatDescriptionComponents() {}

    public static Component huntersSpear() {
        return Component.translatable("tooltip.kingsandmonsters.hunters_spear",
                percent(Config.HUNTERS_SPEAR_CRIPPLE_CHANCE.get()),
                seconds(Config.CRIPPLED_SPEAR_DURATION_TICKS.get()),
                percent(Config.HUNTERS_SPEAR_ARMOR_PIERCE.get()));
    }

    public static Component huntersSpearArmorPierce() {
        return Component.translatable("tooltip.kingsandmonsters.hunters_spear.armor_pierce",
                percent(Config.HUNTERS_SPEAR_ARMOR_PIERCE.get()));
    }

    public static Component huntersSpearCripple() {
        return Component.translatable("tooltip.kingsandmonsters.hunters_spear.cripple",
                percent(Config.HUNTERS_SPEAR_CRIPPLE_CHANCE.get()),
                seconds(Config.CRIPPLED_SPEAR_DURATION_TICKS.get()));
    }

    @Nullable
    public static Component enchantment(ResourceKey<Enchantment> key) {
        if (key.equals(ModEnchantments.BARBED)) {
            return Component.translatable("tooltip.kingsandmonsters.enchantment.barbed",
                    percent(Config.BARBED_BLEED_CHANCE.get()),
                    seconds(Config.BARBED_BLEED_DURATION_TICKS.get()),
                    number(Config.BARBED_BLEED_DAMAGE.get()),
                    seconds(BleedingEffect.DAMAGE_INTERVAL_TICKS));
        }
        if (key.equals(ModEnchantments.HEAVY_THROW)) {
            return Component.translatable("tooltip.kingsandmonsters.enchantment.heavy_throw",
                    number(Config.HEAVY_THROW_DAMAGE_PER_LEVEL.get()),
                    number(Config.HEAVY_THROW_KNOCKBACK_PER_LEVEL.get()),
                    percent(Config.HEAVY_THROW_SPEED_PENALTY_PER_LEVEL.get()));
        }
        if (key.equals(ModEnchantments.TYRANT)) {
            return Component.translatable("tooltip.kingsandmonsters.enchantment.tyrant",
                    number(Config.TYRANT_SEARCH_RADIUS.get()));
        }
        return null;
    }

    @Nullable
    public static Component effect(Holder<MobEffect> effect) {
        if (effect.equals(ModMobEffects.DAZED)) {
            return Component.translatable("tooltip.kingsandmonsters.effect.dazed",
                    percent(DazedEffect.MOVEMENT_SPEED_REDUCTION),
                    percent(DazedEffect.ATTACK_SPEED_REDUCTION));
        }
        if (effect.equals(ModMobEffects.CRIPPLED)) {
            return Component.translatable("tooltip.kingsandmonsters.effect.crippled",
                    percent(CrippledEffect.MOVEMENT_SPEED_REDUCTION));
        }
        if (effect.equals(ModMobEffects.BOGFUME_RAGE)) {
            return Component.translatable("tooltip.kingsandmonsters.effect.bogfume_rage",
                    percent(BogfumeRageEvents.DAMAGE_DEALT_MULTIPLIER - 1.0F),
                    percent(1.0F - BogfumeRageEvents.DAMAGE_TAKEN_MULTIPLIER));
        }
        if (effect.equals(ModMobEffects.BLEEDING)) {
            return Component.translatable(
                    "tooltip.kingsandmonsters.effect.bleeding",
                    number(Config.BARBED_BLEED_DAMAGE.get()),
                    seconds(BleedingEffect.DAMAGE_INTERVAL_TICKS));
        }
        return null;
    }

    private static String seconds(int ticks) {
        return number(ticks / 20.0) + "s";
    }

    private static String percent(double fraction) {
        return number(fraction * 100.0) + "%";
    }

    private static String number(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }
}
