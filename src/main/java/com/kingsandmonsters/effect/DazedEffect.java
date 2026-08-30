package com.kingsandmonsters.effect;

import com.kingsandmonsters.KingsAndMonsters;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** A heavy-impact debuff. Attribute modifiers are synchronized by vanilla. */
public final class DazedEffect extends MobEffect {
    public static final double MOVEMENT_SPEED_REDUCTION = 0.20;
    public static final double ATTACK_SPEED_REDUCTION = 0.15;

    public DazedEffect() {
        super(MobEffectCategory.HARMFUL, 0xD6B46A);
        addAttributeModifier(Attributes.MOVEMENT_SPEED, id("movement_speed"), -MOVEMENT_SPEED_REDUCTION,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        addAttributeModifier(Attributes.ATTACK_SPEED, id("attack_speed"), -ATTACK_SPEED_REDUCTION,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    private static Identifier id(String name) {
        return Identifier.fromNamespaceAndPath(KingsAndMonsters.MODID, "dazed_" + name);
    }
}
