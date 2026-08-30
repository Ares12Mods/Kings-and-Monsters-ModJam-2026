package com.kingsandmonsters;

import com.google.common.collect.ImmutableMultimap;
import com.kingsandmonsters.item.BogIronArmorItem;
import com.kingsandmonsters.item.OgreKingsCrownItem;
import com.kingsandmonsters.item.OgreKingsClubItem;
import com.kingsandmonsters.item.KingsCleaverItem;
import com.kingsandmonsters.item.CurioItem;
import com.kingsandmonsters.item.IronbellyStewItem;
import com.kingsandmonsters.item.BogEyeCharmItem;
import com.kingsandmonsters.item.GoldenOgreToothItem;
import com.kingsandmonsters.item.OgreHookbladeItem;
import com.kingsandmonsters.item.CreativeDestroyerItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import com.kingsandmonsters.item.HunterSpearItem;
import com.kingsandmonsters.item.OgrebloodTotemItem;
import com.kingsandmonsters.item.OgreMerchantBackpackItem;
import com.kingsandmonsters.item.KingsTributeBagItem;
import net.minecraft.world.item.component.BundleContents;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class ModItems {

    private static Item.Properties itemProperties(String name) {
        return new Item.Properties().setId(ResourceKey.create(Registries.ITEM,
                Identifier.fromNamespaceAndPath(KingsAndMonsters.MODID, name)));
    }

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(KingsAndMonsters.MODID);

    public static final DeferredItem<Item> BOG_IRON_INGOT = ITEMS.register("bog_iron",
            () -> new Item(itemProperties("bog_iron")));

    public static final DeferredItem<Item> RAW_BOG_IRON = ITEMS.register("raw_bog_iron",
            () -> new Item(itemProperties("raw_bog_iron")));

    public static final DeferredItem<GoldenOgreToothItem> GOLDEN_OGRE_TOOTH = ITEMS.register("golden_ogre_tooth",
            () -> new GoldenOgreToothItem(itemProperties("golden_ogre_tooth")));

    public static final DeferredItem<OgrebloodTotemItem> OGREBLOOD_TOTEM = ITEMS.register("ogreblood_totem",
            () -> new OgrebloodTotemItem(itemProperties("ogreblood_totem").stacksTo(1)));

    public static final DeferredItem<OgreMerchantBackpackItem> OGRE_MERCHANT_BACKPACK = ITEMS.register("ogre_merchant_backpack",
            () -> new OgreMerchantBackpackItem(itemProperties("ogre_merchant_backpack").stacksTo(1)));

    public static final DeferredItem<KingsTributeBagItem> KINGS_TRIBUTE_BUNDLE = ITEMS.register("kings_tribute_bundle",
            () -> new KingsTributeBagItem(itemProperties("kings_tribute_bundle").stacksTo(1)
                    .component(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY)));

    public static final DeferredItem<Item> IRONBELLY_STEW = ITEMS.register("ironbelly_stew",
            () -> new IronbellyStewItem(itemProperties("ironbelly_stew")
                    .stacksTo(1)
                    .food(new FoodProperties.Builder()
                            .nutrition(8)
                            .saturationModifier(0.9F)
                            .alwaysEdible()
                            .build())));

    public static final DeferredItem<BogEyeCharmItem> BOG_EYE_CHARM = ITEMS.register("bog_eye_charm",
            () -> new BogEyeCharmItem(itemProperties("bog_eye_charm").durability(64)));

    public static final DeferredItem<Item> WARCALLER_BELL = ITEMS.register("warcaller_bell",
            () -> new CurioItem(itemProperties("warcaller_bell").stacksTo(1), ImmutableMultimap.of(), curioTooltip("Charm",
                    Component.literal("Draws the attention of nearby hostile creatures").withStyle(ChatFormatting.BLUE),
                    Component.literal("While targeted by 3+ hostiles within 16 blocks:").withStyle(ChatFormatting.GRAY),
                    Component.literal("+1 Max Heart").withStyle(ChatFormatting.BLUE),
                    Component.literal("+4 Armor").withStyle(ChatFormatting.BLUE),
                    Component.literal("Bonuses linger for 2 seconds").withStyle(ChatFormatting.DARK_GRAY)
            )));

    public static final DeferredItem<Item> RATTLEBONE_RING = ITEMS.register("rattlebone_ring",
            () -> new CurioItem(itemProperties("rattlebone_ring").stacksTo(1), ImmutableMultimap.of(), curioTooltip("Ring",
                    Component.literal("Hostile kills release a 4 damage Death Rattle").withStyle(ChatFormatting.BLUE),
                    Component.literal("Hits enemies within 5.5 blocks").withStyle(ChatFormatting.BLUE),
                    Component.literal("Applies Slowness I for 4 seconds").withStyle(ChatFormatting.BLUE),
                    Component.literal("6 second cooldown").withStyle(ChatFormatting.DARK_GRAY)
            )));

    public static final DeferredItem<BannerItem> OGRE_BRUTE_BANNER = ITEMS.register("ogre_brute_banner",
            () -> fixedBanner("ogre_brute_banner", Blocks.BLACK_BANNER, Blocks.BLACK_WALL_BANNER, ModBannerPatterns.OGRE_BRUTE));

    public static final DeferredItem<BannerItem> GRUNT_CAPTAIN_BANNER = ITEMS.register("grunt_captain_banner",
            () -> fixedBanner("grunt_captain_banner", Blocks.RED_BANNER, Blocks.RED_WALL_BANNER, ModBannerPatterns.GRUNT_CAPTAIN));

    public static final DeferredItem<BannerItem> OGRE_KINGS_BANNER = ITEMS.register("ogre_kings_banner",
            () -> fixedBanner("ogre_kings_banner", Blocks.PURPLE_BANNER, Blocks.PURPLE_WALL_BANNER, ModBannerPatterns.OGRE_KING));

    public static final DeferredItem<BannerItem> OGRE_MAGE_BANNER = ITEMS.register("ogre_mage_banner",
            () -> fixedBanner("ogre_mage_banner", Blocks.BROWN_BANNER, Blocks.BROWN_WALL_BANNER, ModBannerPatterns.OGRE_MAGE));

    private static BannerItem fixedBanner(String name, Block standing, Block wall, Holder<net.minecraft.world.level.block.entity.BannerPattern> pattern) {
        return new BannerItem(standing, wall,
                itemProperties(name).stacksTo(16).component(
                        DataComponents.BANNER_PATTERNS,
                        new BannerPatternLayers.Builder().add(pattern, DyeColor.WHITE).build()));
    }

    public static final DeferredItem<Item> OGRE_TOOTH_NECKLACE = ITEMS.register("ogre_tooth_necklace",
            () -> new CurioItem(itemProperties("ogre_tooth_necklace").stacksTo(1),
                    ImmutableMultimap.of(
                            Attributes.ATTACK_DAMAGE,
                            new AttributeModifier(
                                    Identifier.fromNamespaceAndPath(KingsAndMonsters.MODID, "ogre_tooth_necklace_damage"),
                                    1.0, AttributeModifier.Operation.ADD_VALUE)),
                    curioTooltip("Necklace",
                            Component.literal("+1 Attack Damage").withStyle(ChatFormatting.BLUE),
                            Component.literal("Attacks deal +10% knockback").withStyle(ChatFormatting.BLUE)
                    )));

    public static final DeferredItem<Item> BRUTE_HEART = ITEMS.register("brute_heart", () -> {
        ImmutableMultimap.Builder<Holder<Attribute>, AttributeModifier> builder = ImmutableMultimap.builder();
        builder.put(Attributes.MAX_HEALTH, new AttributeModifier(
                Identifier.fromNamespaceAndPath(KingsAndMonsters.MODID, "brute_heart_health"),
                4.0, AttributeModifier.Operation.ADD_VALUE));
        builder.put(Attributes.KNOCKBACK_RESISTANCE, new AttributeModifier(
                Identifier.fromNamespaceAndPath(KingsAndMonsters.MODID, "brute_heart_knockback_res"),
                0.2, AttributeModifier.Operation.ADD_VALUE));
        return new CurioItem(itemProperties("brute_heart").stacksTo(1), builder.build(), curioTooltip("Charm",
                Component.literal("+2 Max Hearts").withStyle(ChatFormatting.BLUE),
                Component.literal("+20% Knockback Resistance").withStyle(ChatFormatting.BLUE)
        ));
    });

    public static final DeferredItem<Item> BOG_IRON_BAND = ITEMS.register("bog_iron_band",
            () -> new CurioItem(itemProperties("bog_iron_band").stacksTo(1),
                    ImmutableMultimap.of(
                            Attributes.ARMOR,
                            new AttributeModifier(
                                    Identifier.fromNamespaceAndPath(KingsAndMonsters.MODID, "bog_iron_band_armor"),
                                    2.0, AttributeModifier.Operation.ADD_VALUE)),
                    curioTooltip("Ring",
                            Component.literal("+2 Armor").withStyle(ChatFormatting.BLUE),
                            Component.literal("20% chance to prevent incoming Poison").withStyle(ChatFormatting.BLUE),
                            Component.literal("Stacks with Bog Iron armor").withStyle(ChatFormatting.DARK_GRAY)
                    )));

    public static final DeferredItem<Item> BOGFUME_CHARM = ITEMS.register("bogfume_charm",
            () -> new CurioItem(itemProperties("bogfume_charm").stacksTo(1), ImmutableMultimap.of(), curioTooltip("Charm",
                    Component.literal("30% chance to inflict Poison II on melee attackers for 5s").withStyle(ChatFormatting.BLUE),
                    Component.literal("All poison you inflict deals +20% damage").withStyle(ChatFormatting.BLUE)
            )));

    public static final DeferredItem<Item> BUCKLER = ITEMS.register("buckler",
            () -> new CurioItem(itemProperties("buckler").stacksTo(1), ImmutableMultimap.of(), curioTooltip("Hands",
                    Component.literal("25% chance to block arrows").withStyle(ChatFormatting.BLUE),
                    Component.literal("Player-fired arrows deal +10% damage").withStyle(ChatFormatting.BLUE),
                    Component.literal("Does not affect thrown spears").withStyle(ChatFormatting.DARK_GRAY)
            )));

    private static List<Component> curioTooltip(String slot, Component... effects) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("tooltip.kingsandmonsters.slot", slot)
                .withStyle(ChatFormatting.YELLOW));
        lines.add(Component.empty());
        lines.add(Component.translatable("tooltip.kingsandmonsters.when_worn")
                .withStyle(ChatFormatting.GRAY));
        lines.addAll(Arrays.asList(effects));
        lines.add(Component.empty());
        lines.add(Component.translatable("tooltip.kingsandmonsters.not_stackable")
                .withStyle(ChatFormatting.BLUE));
        return List.copyOf(lines);
    }

    /** Throwable player weapon and the Ogre Guard's signature armament. */
    public static final DeferredItem<HunterSpearItem> HUNTERS_SPEAR = ITEMS.register("hunters_spear",
            () -> new HunterSpearItem(itemProperties("hunters_spear")
                    .durability(384)
                    .attributes(HunterSpearItem.createAttributes())));

    // -------------------------------------------------------------------------
    // Dev tools — test jigsaw structure spawners
    // -------------------------------------------------------------------------

    /** Creative-only Netherite Sword with attack damage/speed overridden for a guaranteed instant kill. */
    public static final DeferredItem<CreativeDestroyerItem> CREATIVE_DESTROYER = ITEMS.register("creative_destroyer",
            () -> new CreativeDestroyerItem(itemProperties("creative_destroyer")
                    .sword(ToolMaterial.NETHERITE, 3.0F, -2.4F)
                    .attributes(ItemAttributeModifiers.builder()
                            .add(Attributes.ATTACK_DAMAGE,
                                    new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, 999.0,
                                            AttributeModifier.Operation.ADD_VALUE),
                                    EquipmentSlotGroup.MAINHAND)
                            .add(Attributes.ATTACK_SPEED,
                                    new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, -2.4,
                                            AttributeModifier.Operation.ADD_VALUE),
                                    EquipmentSlotGroup.MAINHAND)
                            .build())));

    // -------------------------------------------------------------------------
    // Armor Materials
    // -------------------------------------------------------------------------

    /**
     * Bog Iron - a swamp-forged endgame material that sits between diamond
     * and netherite. Vanilla diamond and netherite share armor point values,
     * so bog iron matches that defense while landing between them on
     * durability and toughness.
     *
     * Defense:    Helmet 3 | Chestplate 8 | Leggings 6 | Boots 3
     * Durability: multiplier 35  (diamond uses 33, netherite uses 37)
     */
    private static final TagKey<Item> BOG_IRON_REPAIR_ITEMS = TagKey.create(
            Registries.ITEM, Identifier.fromNamespaceAndPath(KingsAndMonsters.MODID, "bog_iron_repair_items"));
    private static final ResourceKey<EquipmentAsset> BOG_IRON_ASSET = ResourceKey.create(
            EquipmentAssets.ROOT_ID, Identifier.fromNamespaceAndPath(KingsAndMonsters.MODID, "bog_iron"));
    private static final ResourceKey<EquipmentAsset> OGRE_KINGS_CROWN_ASSET = ResourceKey.create(
            EquipmentAssets.ROOT_ID, Identifier.fromNamespaceAndPath(KingsAndMonsters.MODID, "ogre_kings_crown"));

    public static final ArmorMaterial BOG_IRON = new ArmorMaterial(
            35,
            Map.of(ArmorType.HELMET, 3, ArmorType.CHESTPLATE, 8, ArmorType.LEGGINGS, 6,
                    ArmorType.BOOTS, 3, ArmorType.BODY, 8),
            12,
            SoundEvents.ARMOR_EQUIP_IRON,
            2.5f,
            0.05f,
            BOG_IRON_REPAIR_ITEMS,
            BOG_IRON_ASSET
    );

    /** Gold-level protection, 1 armor toughness, and the durability of a Bog Iron helmet. */
    public static final ArmorMaterial OGRE_KINGS_CROWN_MATERIAL = new ArmorMaterial(
            35,
            Map.of(ArmorType.HELMET, 2, ArmorType.CHESTPLATE, 5, ArmorType.LEGGINGS, 3,
                    ArmorType.BOOTS, 1, ArmorType.BODY, 5),
            25,
            SoundEvents.ARMOR_EQUIP_GOLD,
            1.0f,
            0.0f,
            ItemTags.REPAIRS_GOLD_ARMOR,
            OGRE_KINGS_CROWN_ASSET
    );

    // -------------------------------------------------------------------------
    // Tool Materials
    // -------------------------------------------------------------------------

    public static final ToolMaterial BOG_IRON_TOOL = new ToolMaterial(
            BlockTags.INCORRECT_FOR_DIAMOND_TOOL,
            1750,
            8.5F,
            3.25F,
            12,
            BOG_IRON_REPAIR_ITEMS);

    // -------------------------------------------------------------------------
    // Bog Iron Tools
    // -------------------------------------------------------------------------

    public static final DeferredItem<Item> BOG_IRON_SWORD = ITEMS.register("bog_iron_sword",
            () -> new Item(itemProperties("bog_iron_sword").sword(BOG_IRON_TOOL, 3.0F, -2.4F)));

    public static final DeferredItem<OgreHookbladeItem> OGRE_HOOKBLADE = ITEMS.register("ogre_hookblade",
            () -> new OgreHookbladeItem(itemProperties("ogre_hookblade")
                            .sword(BOG_IRON_TOOL, 3.25F, -2.6F)
                            .durability(650)
                            .attributes(ItemAttributeModifiers.builder()
                                    // Total damage: player base 1 + this 6.5-point modifier.
                                    .add(Attributes.ATTACK_DAMAGE,
                                            new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, 6.5,
                                                    AttributeModifier.Operation.ADD_VALUE),
                                            EquipmentSlotGroup.MAINHAND)
                                    .add(Attributes.ATTACK_SPEED,
                                            new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, -2.6,
                                                    AttributeModifier.Operation.ADD_VALUE),
                                            EquipmentSlotGroup.MAINHAND)
                                    .add(Attributes.ENTITY_INTERACTION_RANGE,
                                            new AttributeModifier(
                                                    Identifier.fromNamespaceAndPath(KingsAndMonsters.MODID, "ogre_hookblade_reach"),
                                                    0.75, AttributeModifier.Operation.ADD_VALUE),
                                            EquipmentSlotGroup.MAINHAND)
                                    .build())));

    public static final DeferredItem<Item> BOG_IRON_PICKAXE = ITEMS.register("bog_iron_pickaxe",
            () -> new Item(itemProperties("bog_iron_pickaxe").pickaxe(BOG_IRON_TOOL, 1.0F, -2.8F)));

    public static final DeferredItem<Item> BOG_IRON_AXE = ITEMS.register("bog_iron_axe",
            () -> new AxeItem(BOG_IRON_TOOL, 6.0F, -3.1F, itemProperties("bog_iron_axe")));

    public static final DeferredItem<Item> BOG_IRON_SHOVEL = ITEMS.register("bog_iron_shovel",
            () -> new ShovelItem(BOG_IRON_TOOL, 1.5F, -3.0F, itemProperties("bog_iron_shovel")));

    public static final DeferredItem<Item> BOG_IRON_HOE = ITEMS.register("bog_iron_hoe",
            () -> new HoeItem(BOG_IRON_TOOL, -2.0F, -1.0F, itemProperties("bog_iron_hoe")));

    // -------------------------------------------------------------------------
    // Bog Iron Armor
    // -------------------------------------------------------------------------

    public static final DeferredItem<BogIronArmorItem> BOG_IRON_HELMET = ITEMS.register("bog_iron_helmet",
            () -> new BogIronArmorItem(BOG_IRON, ArmorType.HELMET, itemProperties("bog_iron_helmet")));

    public static final DeferredItem<BogIronArmorItem> BOG_IRON_CHESTPLATE = ITEMS.register("bog_iron_chestplate",
            () -> new BogIronArmorItem(BOG_IRON, ArmorType.CHESTPLATE, itemProperties("bog_iron_chestplate")));

    public static final DeferredItem<BogIronArmorItem> BOG_IRON_LEGGINGS = ITEMS.register("bog_iron_leggings",
            () -> new BogIronArmorItem(BOG_IRON, ArmorType.LEGGINGS, itemProperties("bog_iron_leggings")));

    public static final DeferredItem<BogIronArmorItem> BOG_IRON_BOOTS = ITEMS.register("bog_iron_boots",
            () -> new BogIronArmorItem(BOG_IRON, ArmorType.BOOTS, itemProperties("bog_iron_boots")));

    public static final DeferredItem<OgreKingsCrownItem> OGRE_KINGS_CROWN = ITEMS.register("ogre_kings_crown",
            () -> new OgreKingsCrownItem(OGRE_KINGS_CROWN_MATERIAL, itemProperties("ogre_kings_crown")));

    public static final DeferredItem<OgreKingsClubItem> OGRE_KINGS_CLUB = ITEMS.register("ogre_kings_club",
            () -> new OgreKingsClubItem(itemProperties("ogre_kings_club")
                    .durability(1600)
                    .attributes(ItemAttributeModifiers.builder()
                            // Total held damage 12: player base 1 plus this 11-point modifier.
                            .add(Attributes.ATTACK_DAMAGE,
                                    new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, 11.0,
                                            AttributeModifier.Operation.ADD_VALUE),
                                    EquipmentSlotGroup.MAINHAND)
                            // Total vanilla attack speed 0.9 from the player's base 4.0.
                            .add(Attributes.ATTACK_SPEED,
                                    new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, -3.1,
                                            AttributeModifier.Operation.ADD_VALUE),
                                    EquipmentSlotGroup.MAINHAND)
                            .build())));

    // -------------------------------------------------------------------------
    // Spawn Eggs
    // -------------------------------------------------------------------------

    /** Dark mossy-green body, muddy brown spots. */
    public static final DeferredItem<SpawnEggItem> OGRE_GRUNT_SPAWN_EGG = ITEMS.register(
            "ogre_grunt_spawn_egg",
            () -> new SpawnEggItem(itemProperties("ogre_grunt_spawn_egg").spawnEgg(ModEntities.OGRE_GRUNT.get())));

    /** Mossy-green hide with an iron-gray accent for the captain's armor. */
    public static final DeferredItem<SpawnEggItem> OGRE_GRUNT_CAPTAIN_SPAWN_EGG = ITEMS.register(
            "ogre_grunt_captain_spawn_egg",
            () -> new SpawnEggItem(itemProperties("ogre_grunt_captain_spawn_egg").spawnEgg(ModEntities.OGRE_GRUNT_CAPTAIN.get())));

    public static final DeferredItem<SpawnEggItem> OGRE_MAGE_SPAWN_EGG = ITEMS.register(
            "ogre_mage_spawn_egg",
            () -> new SpawnEggItem(itemProperties("ogre_mage_spawn_egg").spawnEgg(ModEntities.OGRE_MAGE.get())));

    public static final DeferredItem<SpawnEggItem> OGRE_BRUTE_SPAWN_EGG = ITEMS.register(
            "ogre_brute_spawn_egg",
            () -> new SpawnEggItem(itemProperties("ogre_brute_spawn_egg").spawnEgg(ModEntities.OGRE_BRUTE.get())));

    public static final DeferredItem<SpawnEggItem> OGRE_ARCHER_SPAWN_EGG = ITEMS.register(
            "ogre_archer_spawn_egg",
            () -> new SpawnEggItem(itemProperties("ogre_archer_spawn_egg").spawnEgg(ModEntities.OGRE_ARCHER.get())));

    public static final DeferredItem<SpawnEggItem> OGRE_GUARD_SPAWN_EGG = ITEMS.register(
            "ogre_guard_spawn_egg",
            () -> new SpawnEggItem(itemProperties("ogre_guard_spawn_egg").spawnEgg(ModEntities.OGRE_GUARD.get())));

    public static final DeferredItem<SpawnEggItem> OGRE_MERCHANT_SPAWN_EGG = ITEMS.register(
            "ogre_merchant_spawn_egg",
            () -> new SpawnEggItem(itemProperties("ogre_merchant_spawn_egg").spawnEgg(ModEntities.OGRE_MERCHANT.get())));

    public static final DeferredItem<SpawnEggItem> OGRE_LORD_SPAWN_EGG = ITEMS.register(
            "ogre_lord_spawn_egg",
            () -> new SpawnEggItem(itemProperties("ogre_lord_spawn_egg").spawnEgg(ModEntities.OGRE_LORD.get())));

    // The Ogre Lord's weapon — a two-handed greatsword, clearly the strongest weapon in the mod
    // and noticeably better than the Bog Iron tier across the board.
    public static final ToolMaterial KINGS_CLEAVER_TOOL = new ToolMaterial(
            BlockTags.INCORRECT_FOR_DIAMOND_TOOL,
            2200,
            9.0F,
            6.0F,
            15,
            BOG_IRON_REPAIR_ITEMS);

    private static final Identifier KINGS_CLEAVER_BLOCK_REACH_ID =
            Identifier.fromNamespaceAndPath(KingsAndMonsters.MODID, "kings_cleaver_block_reach");

    public static final DeferredItem<KingsCleaverItem> KINGS_CLEAVER = ITEMS.register("kings_cleaver",
            () -> new KingsCleaverItem(itemProperties("kings_cleaver")
                    .sword(KINGS_CLEAVER_TOOL, 3.0F, -3.0F)
                    .attributes(
                    ItemAttributeModifiers.builder()
                            // Total held damage is 10: player base 1 plus this 9-point modifier.
                            .add(Attributes.ATTACK_DAMAGE,
                                    new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, 9.0, AttributeModifier.Operation.ADD_VALUE),
                                    EquipmentSlotGroup.MAINHAND)
                            // Heavy and slow — a greatsword swing, not a quick poke.
                            .add(Attributes.ATTACK_SPEED,
                                    new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, -3.0, AttributeModifier.Operation.ADD_VALUE),
                                    EquipmentSlotGroup.MAINHAND)
                            .add(Attributes.BLOCK_INTERACTION_RANGE,
                                    new AttributeModifier(KINGS_CLEAVER_BLOCK_REACH_ID, 1.0, AttributeModifier.Operation.ADD_VALUE),
                                    EquipmentSlotGroup.MAINHAND)
                            .build())));

}
