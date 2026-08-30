package com.kingsandmonsters.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

public class BogfumeRageEffect extends MobEffect {
    private static final int BOGFUME_GREEN = 0x6fa51f;

    public BogfumeRageEffect() {
        super(MobEffectCategory.BENEFICIAL, BOGFUME_GREEN);
    }
}
