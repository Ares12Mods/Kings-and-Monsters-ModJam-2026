package com.kingsandmonsters.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * Presentation-only marker shown while the Warcaller Bell's passive bonus is active. Grants
 * nothing of its own — the +1 Max Heart / +4 Armor bonus is applied and removed separately by
 * {@code WarcallerBellEvents}, in lockstep with this effect, so the icon and the real bonus never
 * disagree.
 */
public final class WarcalledEffect extends MobEffect {
    public WarcalledEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xC9A227);
    }
}
