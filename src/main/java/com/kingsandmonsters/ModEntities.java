package com.kingsandmonsters;

import com.kingsandmonsters.entity.OgreGrunt;
import com.kingsandmonsters.entity.OgreGruntCaptain;
import com.kingsandmonsters.entity.OgreArcher;
import com.kingsandmonsters.entity.OgreBrute;
import com.kingsandmonsters.entity.OgreLord;
import com.kingsandmonsters.entity.OgreMage;
import com.kingsandmonsters.entity.OgreMerchant;
import com.kingsandmonsters.entity.OgreGuard;
import com.kingsandmonsters.entity.OgreSpear;
import com.kingsandmonsters.entity.BogfumeBolt;
import com.kingsandmonsters.entity.PoisonFogCloud;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModEntities {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, KingsAndMonsters.MODID);

    // ------------------------------------------------------------------ //
    //  Entities                                                            //
    // ------------------------------------------------------------------ //

    public static final DeferredHolder<EntityType<?>, EntityType<OgreGrunt>> OGRE_GRUNT =
            ENTITY_TYPES.register("ogre_grunt", () -> EntityType.Builder
                    .<OgreGrunt>of(OgreGrunt::new, MobCategory.MONSTER)
                    .sized(1.2f, 2.8f)
                    .build(key("ogre_grunt")));

    public static final DeferredHolder<EntityType<?>, EntityType<OgreGruntCaptain>> OGRE_GRUNT_CAPTAIN =
            ENTITY_TYPES.register("ogre_grunt_captain", () -> EntityType.Builder
                    .<OgreGruntCaptain>of(OgreGruntCaptain::new, MobCategory.MONSTER)
                    .sized(1.2f, 2.8f)
                    .build(key("ogre_grunt_captain")));

    public static final DeferredHolder<EntityType<?>, EntityType<OgreMage>> OGRE_MAGE =
            ENTITY_TYPES.register("ogre_mage", () -> EntityType.Builder
                    .<OgreMage>of(OgreMage::new, MobCategory.MONSTER)
                    // The physical torso/head reaches roughly 29 model pixels (1.82 blocks).
                    // Ears, staff, and other lateral decoration do not require head clearance.
                    .sized(1.25f, 1.9f)
                    .build(key("ogre_mage")));

    public static final DeferredHolder<EntityType<?>, EntityType<OgreBrute>> OGRE_BRUTE =
            ENTITY_TYPES.register("ogre_brute", () -> EntityType.Builder
                    .<OgreBrute>of(OgreBrute::new, MobCategory.MONSTER)
                    .sized(1.7f, 3.4f)
                    .build(key("ogre_brute")));

    public static final DeferredHolder<EntityType<?>, EntityType<OgreArcher>> OGRE_ARCHER =
            ENTITY_TYPES.register("ogre_archer", () -> EntityType.Builder
                    .<OgreArcher>of(OgreArcher::new, MobCategory.MONSTER)
                    .sized(0.95f, 2.45f)
                    .build(key("ogre_archer")));

    public static final DeferredHolder<EntityType<?>, EntityType<OgreGuard>> OGRE_GUARD =
            ENTITY_TYPES.register("ogre_guard", () -> EntityType.Builder
                    .<OgreGuard>of(OgreGuard::new, MobCategory.MONSTER)
                    // The physical legs, torso, and head span 28 model pixels (1.75 blocks).
                    // The held spear is decorative equipment and is not part of collision.
                    .sized(1.05f, 1.85f)
                    .build(key("ogre_guard")));

    public static final DeferredHolder<EntityType<?>, EntityType<OgreMerchant>> OGRE_MERCHANT =
            ENTITY_TYPES.register("ogre_merchant", () -> EntityType.Builder
                    .<OgreMerchant>of(OgreMerchant::new, MobCategory.CREATURE)
                    .sized(1.4F, 2.65F)
                    .build(key("ogre_merchant")));

    public static final DeferredHolder<EntityType<?>, EntityType<OgreSpear>> OGRE_SPEAR =
            ENTITY_TYPES.register("ogre_spear", () -> EntityType.Builder
                    .<OgreSpear>of(OgreSpear::new, MobCategory.MISC)
                    .sized(0.35f, 0.35f)
                    .clientTrackingRange(64)
                    .updateInterval(1)
                    .build(key("ogre_spear")));

    public static final DeferredHolder<EntityType<?>, EntityType<OgreLord>> OGRE_LORD =
            ENTITY_TYPES.register("ogre_lord", () -> EntityType.Builder
                    .<OgreLord>of(OgreLord::new, MobCategory.MONSTER)
                    // Measured off the actual model bounds (~5.4 wide arm-to-arm, ~2.9 deep, ~6.1
                    // tall) and sized the same way OgreBrute is: width tracks the front-to-back
                    // depth (not the full arm span, which would make him too easy to hit from the
                    // side), height at ~92% of full model height. The old 2.3/4.6 undersized both,
                    // most noticeably height — his head/crown was above the hittable box entirely.
                    // Previewed at 5/6 scale alongside OgreLordRenderer. Restore to
                    // 3.3f, 5.6f if the visual scale is reverted to 1.0F.
                    .sized(2.75f, 4.67f)
                    .build(key("ogre_lord")));

    public static final DeferredHolder<EntityType<?>, EntityType<PoisonFogCloud>> POISON_FOG_CLOUD =
            ENTITY_TYPES.register("poison_fog_cloud", () -> EntityType.Builder
                    .<PoisonFogCloud>of(PoisonFogCloud::new, MobCategory.MISC)
                    .sized(PoisonFogCloud.RADIUS * 2.0F, 0.25F)
                    .clientTrackingRange(64)
                    .updateInterval(2)
                    .build(key("poison_fog_cloud")));

    public static final DeferredHolder<EntityType<?>, EntityType<BogfumeBolt>> BOGFUME_BOLT =
            ENTITY_TYPES.register("bogfume_bolt", () -> EntityType.Builder
                    .<BogfumeBolt>of(BogfumeBolt::new, MobCategory.MISC)
                    .sized(0.35F, 0.35F)
                    .clientTrackingRange(64)
                    .updateInterval(1)
                    .build(key("bogfume_bolt")));

    private static ResourceKey<EntityType<?>> key(String path) {
        return ResourceKey.create(Registries.ENTITY_TYPE,
                Identifier.fromNamespaceAndPath(KingsAndMonsters.MODID, path));
    }

    // ------------------------------------------------------------------ //
    //  Attribute registration (called from KingsAndMonsters constructor)  //
    // ------------------------------------------------------------------ //

    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(OGRE_GRUNT.get(), OgreGrunt.createAttributes().build());
        event.put(OGRE_GRUNT_CAPTAIN.get(), OgreGruntCaptain.createAttributes().build());
        event.put(OGRE_MAGE.get(), OgreMage.createAttributes().build());
        event.put(OGRE_BRUTE.get(), OgreBrute.createAttributes().build());
        event.put(OGRE_ARCHER.get(), OgreArcher.createAttributes().build());
        event.put(OGRE_GUARD.get(), OgreGuard.createAttributes().build());
        event.put(OGRE_MERCHANT.get(), OgreMerchant.createAttributes().build());
        event.put(OGRE_LORD.get(), OgreLord.createAttributes().build());
    }
}
