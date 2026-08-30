package com.kingsandmonsters.tribute;

import com.kingsandmonsters.ModItems;
import com.kingsandmonsters.entity.OgreGrunt;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;

public final class BogIronArmorEffects {
    private BogIronArmorEffects() {
    }

    public static void onMobEffectApplicable(MobEffectEvent.Applicable event) {
        if (event.getEntity() instanceof OgreGrunt && isOgreDebuff(event)) {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
            return;
        }

        if (event.getEffectInstance().getEffect().equals(MobEffects.POISON)) {
            float blockChance = 0.0f;
            LivingEntity entity = event.getEntity();
            if (entity.hasEffect(com.kingsandmonsters.ModMobEffects.POISON_RESISTANCE)) {
                event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
                return;
            }
            if (entity.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.BOG_IRON_HELMET.get()))     blockChance += 0.15f;
            if (entity.getItemBySlot(EquipmentSlot.CHEST).is(ModItems.BOG_IRON_CHESTPLATE.get())) blockChance += 0.15f;
            if (entity.getItemBySlot(EquipmentSlot.LEGS).is(ModItems.BOG_IRON_LEGGINGS.get()))   blockChance += 0.15f;
            if (entity.getItemBySlot(EquipmentSlot.FEET).is(ModItems.BOG_IRON_BOOTS.get()))      blockChance += 0.15f;
            if (hasBogIronBand(entity)) blockChance += 0.2f;
            if (blockChance > 0 && entity.getRandom().nextFloat() < blockChance) {
                event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
            }
        }
    }

    private static boolean isOgreDebuff(MobEffectEvent.Applicable event) {
        return event.getEffectInstance().getEffect().equals(MobEffects.POISON)
                || event.getEffectInstance().getEffect().equals(MobEffects.SLOWNESS)
                || event.getEffectInstance().getEffect().equals(MobEffects.WEAKNESS);
    }

    private static boolean hasBogIronBand(LivingEntity entity) {
        return top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(entity)
                .map(h -> h.findFirstCurio(stack -> stack.is(ModItems.BOG_IRON_BAND.get())).isPresent())
                .orElse(false);
    }
}
