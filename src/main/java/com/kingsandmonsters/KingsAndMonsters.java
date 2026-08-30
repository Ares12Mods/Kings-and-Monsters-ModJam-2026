package com.kingsandmonsters;

import com.kingsandmonsters.advancement.RoyalAdvancementEvents;
import com.kingsandmonsters.client.ClientEvents;
import com.kingsandmonsters.effect.BogfumeRageEvents;
import com.kingsandmonsters.effect.CombatEffectEvents;
import com.kingsandmonsters.enchantment.ModEnchantmentEffects;
import com.kingsandmonsters.item.ArtifactEvents;
import com.kingsandmonsters.item.HunterSpearEvents;
import com.kingsandmonsters.item.KingsCleaverEvents;
import com.kingsandmonsters.item.OgreKingsClubEvents;
import com.kingsandmonsters.item.OgreKingBannerEvents;
import com.kingsandmonsters.item.RattleboneRingEvents;
import com.kingsandmonsters.item.WarcallerBellEvents;
import com.kingsandmonsters.item.OgreHookbladeEvents;
import com.kingsandmonsters.item.CreativeDestroyerEvents;
import com.kingsandmonsters.item.OgrebloodTotemEvents;
import com.kingsandmonsters.entity.OgreArcher;
import com.kingsandmonsters.entity.OgreBrute;
import com.kingsandmonsters.entity.OgreGrunt;
import com.kingsandmonsters.entity.OgreBruteCombatEvents;
import com.kingsandmonsters.entity.OgreDamageCapEvents;
import com.kingsandmonsters.entity.OgreLord;
import com.kingsandmonsters.entity.OgreLordCombatEvents;
import com.kingsandmonsters.entity.OgreMage;
import com.kingsandmonsters.network.ModNetwork;
import com.kingsandmonsters.tribute.BogIronArmorEffects;
import com.kingsandmonsters.tribute.BogIronProgression;
import com.kingsandmonsters.tribute.AngerHudSync;
import com.kingsandmonsters.tribute.OutpostEvents;
import com.kingsandmonsters.tribute.OutpostPopulationManager;
import com.kingsandmonsters.tribute.PatrolManager;
import com.kingsandmonsters.tribute.TributeManager;
import com.kingsandmonsters.world.ModFeatures;
import com.kingsandmonsters.world.ModStructures;
import com.kingsandmonsters.world.ArcherTrialSpawnerActivation;
import com.kingsandmonsters.world.FortVegetationCleaner;
import com.kingsandmonsters.world.FortPopulationSpawner;
import com.kingsandmonsters.world.FortMapTrades;
import com.kingsandmonsters.world.OutpostVegetationCleaner;
import com.kingsandmonsters.world.OgreMerchantSpawnManager;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

@Mod(KingsAndMonsters.MODID)
public class KingsAndMonsters {

    public static final String MODID = "kingsandmonsters";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> KINGS_AND_MONSTERS_TAB =
            CREATIVE_MODE_TABS.register("kings_and_monsters_tab", () ->
                    CreativeModeTab.builder()
                            .title(Component.translatable("itemGroup.kingsandmonsters"))
                            .withTabsBefore(CreativeModeTabs.COMBAT)
                            .icon(() -> ModItems.OGRE_KINGS_CROWN.get().getDefaultInstance())
                            .displayItems((parameters, output) -> {
                                // Materials
                                output.accept(ModItems.BOG_IRON_INGOT.get());
                                output.accept(ModItems.RAW_BOG_IRON.get());
                                // Ogre merchant stock
                                output.accept(ModItems.IRONBELLY_STEW.get());
                                output.accept(ModItems.GOLDEN_OGRE_TOOTH.get());
                                output.accept(ModItems.OGREBLOOD_TOTEM.get());
                                output.accept(ModItems.OGRE_MERCHANT_BACKPACK.get());
                                output.accept(ModItems.KINGS_TRIBUTE_BUNDLE.get());
                                output.accept(ModItems.BOG_EYE_CHARM.get());
                                // Armor
                                output.accept(ModItems.BOG_IRON_HELMET.get());
                                output.accept(ModItems.BOG_IRON_CHESTPLATE.get());
                                output.accept(ModItems.BOG_IRON_LEGGINGS.get());
                                output.accept(ModItems.BOG_IRON_BOOTS.get());
                                // Tools
                                output.accept(ModItems.BOG_IRON_SWORD.get());
                                output.accept(ModItems.BOG_IRON_PICKAXE.get());
                                output.accept(ModItems.BOG_IRON_AXE.get());
                                output.accept(ModItems.BOG_IRON_SHOVEL.get());
                                output.accept(ModItems.BOG_IRON_HOE.get());
                                output.accept(ModItems.HUNTERS_SPEAR.get());
                                output.accept(ModItems.OGRE_KINGS_CROWN.get());
                                output.accept(ModItems.OGRE_KINGS_CLUB.get());
                                output.accept(ModItems.KINGS_CLEAVER.get());
                                output.accept(ModItems.OGRE_HOOKBLADE.get());
                                output.accept(ModItems.OGRE_BRUTE_BANNER.get());
                                output.accept(ModItems.GRUNT_CAPTAIN_BANNER.get());
                                output.accept(ModItems.OGRE_KINGS_BANNER.get());
                                output.accept(ModItems.OGRE_MAGE_BANNER.get());
                                // Blocks
                                output.accept(ModBlocks.BOG_IRON_ORE_ITEM.get());
                                output.accept(ModBlocks.DEEPSLATE_BOG_IRON_ORE_ITEM.get());
                                output.accept(ModBlocks.BOG_IRON_BLOCK_ITEM.get());
                                output.accept(ModBlocks.RAW_BOG_IRON_BLOCK_ITEM.get());
                                // Spawn Eggs
                                output.accept(ModItems.OGRE_GRUNT_SPAWN_EGG.get());
                                output.accept(ModItems.OGRE_GRUNT_CAPTAIN_SPAWN_EGG.get());
                                output.accept(ModItems.OGRE_MAGE_SPAWN_EGG.get());
                                output.accept(ModItems.OGRE_BRUTE_SPAWN_EGG.get());
                                output.accept(ModItems.OGRE_ARCHER_SPAWN_EGG.get());
                                output.accept(ModItems.OGRE_GUARD_SPAWN_EGG.get());
                                output.accept(ModItems.OGRE_MERCHANT_SPAWN_EGG.get());
                                output.accept(ModItems.OGRE_LORD_SPAWN_EGG.get());
                                // Dev tools
                                output.accept(ModItems.CREATIVE_DESTROYER.get());
                            })
                            .build());

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> KINGS_AND_MONSTERS_ARTIFACTS_TAB =
            CREATIVE_MODE_TABS.register("kings_and_monsters_artifacts_tab", () ->
                    CreativeModeTab.builder()
                            .title(Component.translatable("itemGroup.kingsandmonsters.artifacts"))
                            .withTabsBefore(KINGS_AND_MONSTERS_TAB.getKey())
                            .icon(() -> ModItems.OGRE_TOOTH_NECKLACE.get().getDefaultInstance())
                            .displayItems((parameters, output) -> {
                                output.accept(ModItems.OGRE_TOOTH_NECKLACE.get());
                                output.accept(ModItems.BRUTE_HEART.get());
                                output.accept(ModItems.BOG_IRON_BAND.get());
                                output.accept(ModItems.BOGFUME_CHARM.get());
                                output.accept(ModItems.BUCKLER.get());
                                output.accept(ModItems.WARCALLER_BELL.get());
                                output.accept(ModItems.RATTLEBONE_RING.get());
                            })
                            .build());

    public KingsAndMonsters(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            modEventBus.addListener((FMLClientSetupEvent e) -> {
                LOGGER.info("Kings and Monsters client setup");
                e.enqueueWork(ClientEvents::registerItemProperties);
                ClientEvents.registerClientRuntimeEvents();
            });
            modEventBus.addListener(ClientEvents::registerRenderers);
            modEventBus.addListener(ClientEvents::registerLayerDefinitions);
            modEventBus.addListener(ClientEvents::registerGuiLayers);
            modEventBus.addListener(ClientEvents::registerKeyMappings);
            modEventBus.addListener(ClientEvents::registerMenuScreens);
            modEventBus.addListener(com.kingsandmonsters.client.KingsRenderTypes::registerPipelines);
        }
        modEventBus.addListener(ModNetwork::register);

        ModBlocks.BLOCKS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITY_TYPES.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModMenus.MENUS.register(modEventBus);
        ModEntities.ENTITY_TYPES.register(modEventBus);
        ModMobEffects.MOB_EFFECTS.register(modEventBus);
        ModSoundEvents.SOUND_EVENTS.register(modEventBus);
        ModLootFunctions.LOOT_FUNCTIONS.register(modEventBus);
        ModFeatures.FEATURES.register(modEventBus);
        ModStructures.STRUCTURE_TYPES.register(modEventBus);
        ModStructures.STRUCTURE_PROCESSOR_TYPES.register(modEventBus);
        ModStructures.STRUCTURE_PLACEMENT_TYPES.register(modEventBus);
        modEventBus.addListener(ModEntities::registerAttributes);
        CREATIVE_MODE_TABS.register(modEventBus);

        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.addListener(BogIronArmorEffects::onMobEffectApplicable);
        NeoForge.EVENT_BUS.addListener(BogIronProgression::onLivingDeath);
        NeoForge.EVENT_BUS.addListener(BogIronProgression::onBlockBreak);
        NeoForge.EVENT_BUS.addListener(BogfumeRageEvents::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(CombatEffectEvents::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(ModEnchantmentEffects::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(ArtifactEvents::onKnockBack);
        NeoForge.EVENT_BUS.addListener(ArtifactEvents::onEntityJoinLevel);
        NeoForge.EVENT_BUS.addListener(ArtifactEvents::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(ArtifactEvents::onLivingDamagePost);
        NeoForge.EVENT_BUS.addListener(ArtifactEvents::onMobEffectAdded);
        NeoForge.EVENT_BUS.addListener(KingsCleaverEvents::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(KingsCleaverEvents::onLivingDeath);
        NeoForge.EVENT_BUS.addListener(RoyalAdvancementEvents::onLivingDeath);
        NeoForge.EVENT_BUS.addListener(OgreKingsClubEvents::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(OgreKingBannerEvents::onBlockDrops);
        NeoForge.EVENT_BUS.addListener(RattleboneRingEvents::onLivingDeath);
        NeoForge.EVENT_BUS.addListener(RattleboneRingEvents::onServerTick);
        NeoForge.EVENT_BUS.addListener(OgrebloodTotemEvents::onLivingDeath);
        NeoForge.EVENT_BUS.addListener(OgrebloodTotemEvents::onServerTick);
        NeoForge.EVENT_BUS.addListener(WarcallerBellEvents::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(OgreHookbladeEvents::onLivingDamagePost);
        NeoForge.EVENT_BUS.addListener(CreativeDestroyerEvents::onAttackEntity);
        NeoForge.EVENT_BUS.addListener(PatrolManager::onServerTick);
        NeoForge.EVENT_BUS.addListener(OutpostPopulationManager::onServerTick);
        NeoForge.EVENT_BUS.addListener(FortVegetationCleaner::onServerTick);
        NeoForge.EVENT_BUS.addListener(FortPopulationSpawner::onServerTick);
        NeoForge.EVENT_BUS.addListener(OutpostVegetationCleaner::onServerTick);
        NeoForge.EVENT_BUS.addListener(OgreMerchantSpawnManager::onServerTick);
        NeoForge.EVENT_BUS.addListener(ArcherTrialSpawnerActivation::onChunkLoad);
        NeoForge.EVENT_BUS.addListener(OutpostEvents::onTributeChestOpened);
        NeoForge.EVENT_BUS.addListener(OutpostEvents::onOgreKilled);
        NeoForge.EVENT_BUS.addListener(OgreBruteCombatEvents::onShieldBlock);
        NeoForge.EVENT_BUS.addListener(OgreLordCombatEvents::onShieldBlock);
        NeoForge.EVENT_BUS.addListener(OgreDamageCapEvents::onLivingDamagePre);
        NeoForge.EVENT_BUS.addListener(com.kingsandmonsters.entity.OgreRallyEvents::onLivingDamagePost);
        NeoForge.EVENT_BUS.addListener(com.kingsandmonsters.entity.RippleJumpTracker::onLivingJump);
        NeoForge.EVENT_BUS.addListener(HunterSpearEvents::onLivingDamagePre);
        NeoForge.EVENT_BUS.addListener(HunterSpearEvents::onLivingDamagePost);
        NeoForge.EVENT_BUS.addListener(AngerHudSync::onServerTick);
        NeoForge.EVENT_BUS.addListener(com.kingsandmonsters.tribute.StructureDiscoveryManager::onServerTick);
        NeoForge.EVENT_BUS.addListener(AngerHudSync::onPlayerLoggedOut);

        // Some values are needed while server resources and villager trades are
        // assembled, before per-world SERVER configs become available. Load the
        // server-authoritative settings at startup so those hooks can read them.
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC, "kingsandmonsters-server.toml");
        modContainer.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC, "kingsandmonsters-client.toml");
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("Kings and Monsters common setup");
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("Kings and Monsters server starting");
        TributeManager.attach(event.getServer());
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        TributeManager.detach();
        AngerHudSync.reset();
        OgreKingsClubEvents.reset();
        OgrebloodTotemEvents.reset();
        FortPopulationSpawner.reset();
        FortVegetationCleaner.reset();
        OutpostVegetationCleaner.reset();
        OgreMerchantSpawnManager.reset();
        com.kingsandmonsters.world.WorldgenSavedDataQueue.reset();
        com.kingsandmonsters.tribute.StructureDiscoveryManager.reset();
    }

    @SubscribeEvent
    public void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof OgreGrunt ogreGrunt) {
            ogreGrunt.applyConfiguredCombatAttributes(!event.loadedFromDisk());
            if (!event.loadedFromDisk() && !event.getLevel().isClientSide()) {
                playOgreEntranceHuff(ogreGrunt);
            }
        }
    }

    private void playOgreEntranceHuff(OgreGrunt ogre) {
        if (!(ogre instanceof OgreMage) && !(ogre instanceof OgreBrute) && !(ogre instanceof OgreLord)) {
            return;
        }

        SoundEvent sound = ModSoundEvents.OGRE_GRUNT_HUFF.get();
        float volume = 0.85F;
        float pitch = 0.9F;

        if (ogre instanceof OgreMage) {
            sound = ModSoundEvents.OGRE_MAGE_HUFF.get();
            volume = 0.8F;
            pitch = 0.82F;
        } else if (ogre instanceof OgreBrute) {
            volume = 1.0F;
            pitch = 0.68F;
        } else if (ogre instanceof OgreLord) {
            sound = ModSoundEvents.OGRE_LORD_HUFF.get();
            volume = 1.15F;
            pitch = 1.0F;
        }

        ogre.level().playSound(null, ogre.getX(), ogre.getY(), ogre.getZ(), sound, SoundSource.HOSTILE, volume, pitch);
    }
}
