package com.kingsandmonsters;

import com.kingsandmonsters.effect.BogfumeRageEffect;
import com.kingsandmonsters.effect.CrippledEffect;
import com.kingsandmonsters.effect.DazedEffect;
import com.kingsandmonsters.effect.BleedingEffect;
import com.kingsandmonsters.effect.PoisonResistanceEffect;
import com.kingsandmonsters.effect.WarcalledEffect;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModMobEffects {
    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, KingsAndMonsters.MODID);

    public static final Holder<MobEffect> BOGFUME_RAGE = MOB_EFFECTS.register(
            "bogfume_rage",
            BogfumeRageEffect::new);

    public static final Holder<MobEffect> DAZED = MOB_EFFECTS.register("dazed", DazedEffect::new);
    public static final Holder<MobEffect> CRIPPLED = MOB_EFFECTS.register("crippled", CrippledEffect::new);
    public static final Holder<MobEffect> BLEEDING = MOB_EFFECTS.register("bleeding", BleedingEffect::new);
    public static final Holder<MobEffect> POISON_RESISTANCE = MOB_EFFECTS.register(
            "poison_resistance", PoisonResistanceEffect::new);
    public static final Holder<MobEffect> WARCALLED = MOB_EFFECTS.register("warcalled", WarcalledEffect::new);
}
