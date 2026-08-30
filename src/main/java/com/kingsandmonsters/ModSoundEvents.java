package com.kingsandmonsters;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModSoundEvents {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, KingsAndMonsters.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> OGRE_BRUTE_TANTRUM_WHINE =
            register("entity.ogre.brute.tantrum_whine");
    public static final DeferredHolder<SoundEvent, SoundEvent> OGRE_BRUTE_BELLY_SLAM_IMPACT =
            register("entity.ogre.brute.belly_slam_impact");
    public static final DeferredHolder<SoundEvent, SoundEvent> OGRE_BRUTE_BELLY_SLAM_GRUNT =
            register("entity.ogre.brute.belly_slam_grunt");
    public static final DeferredHolder<SoundEvent, SoundEvent> OGRE_BRUTE_UPSWING_WHOOSH =
            register("entity.ogre.brute.upswing_whoosh");
    public static final DeferredHolder<SoundEvent, SoundEvent> OGRE_BRUTE_HURT =
            register("entity.ogre.brute.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> OGRE_BRUTE_ATTACK_HEAVY =
            register("entity.ogre.brute.attack_heavy");
    public static final DeferredHolder<SoundEvent, SoundEvent> OGRE_BRUTE_ATTACK_PUNCH =
            register("entity.ogre.brute.attack_punch");
    public static final DeferredHolder<SoundEvent, SoundEvent> OGRE_BRUTE_ATTACK_BODY =
            register("entity.ogre.brute.attack_body");
    public static final DeferredHolder<SoundEvent, SoundEvent> OGRE_GRUNT_HURT =
            register("entity.ogre.grunt.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> OGRE_ARCHER_HURT =
            register("entity.ogre.archer.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> OGRE_MAGE_HURT =
            register("entity.ogre.mage.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> OGRE_LORD_HURT =
            register("entity.ogre_lord.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> OGRE_LORD_ROAR =
            register("entity.ogre_lord.roar");
    public static final DeferredHolder<SoundEvent, SoundEvent> OGRE_LORD_OVERHEAD_GRUNT =
            register("entity.ogre_lord.overhead_grunt");
    public static final DeferredHolder<SoundEvent, SoundEvent> OGRE_LORD_SUMMON_GRUNT =
            register("entity.ogre_lord.summon_grunt");
    public static final DeferredHolder<SoundEvent, SoundEvent> OGRE_LORD_HUFF =
            register("entity.ogre_lord.huff");
    public static final DeferredHolder<SoundEvent, SoundEvent> OGRE_GRUNT_HUFF =
            register("entity.ogre.grunt.huff");
    public static final DeferredHolder<SoundEvent, SoundEvent> OGRE_MAGE_HUFF =
            register("entity.ogre.mage.huff");
    public static final DeferredHolder<SoundEvent, SoundEvent> OGRE_GRUNT_CAPTAIN_SALUTE =
            register("entity.ogre.grunt_captain.salute");
    public static final DeferredHolder<SoundEvent, SoundEvent> OGREBLOOD_TOTEM_ACTIVATE =
            register("item.ogreblood_totem.activate");
    public static final DeferredHolder<SoundEvent, SoundEvent> OGRE_MERCHANT_HURT =
            register("entity.ogre_merchant.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> OGRE_MERCHANT_TRADE =
            register("entity.ogre_merchant.trade");
    public static final DeferredHolder<SoundEvent, SoundEvent> OGRE_KING_BOSS_INTRO =
            register("music.ogre_king_boss_intro");
    public static final DeferredHolder<SoundEvent, SoundEvent> OGRE_KING_BOSS_COMBAT =
            register("music.ogre_king_boss_combat");
    public static final DeferredHolder<SoundEvent, SoundEvent> OGRE_KING_BOSS_DEATH =
            register("music.ogre_king_boss_death");

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(
                Identifier.fromNamespaceAndPath(KingsAndMonsters.MODID, name)));
    }
}
