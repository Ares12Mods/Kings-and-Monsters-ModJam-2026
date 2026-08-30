package com.kingsandmonsters.client;

import com.kingsandmonsters.KingsAndMonsters;
import com.kingsandmonsters.ClientConfig;
import com.kingsandmonsters.ModItems;
import com.kingsandmonsters.ModBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.neoforged.neoforge.client.event.GatherEffectScreenTooltipsEvent;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Adds concise mechanics and a distinct bog-iron presentation to ogre relic tooltips. */
public final class ClientTooltipEvents {
    private static final FontDescription OGRE_FONT = new FontDescription.Resource(
            Identifier.fromNamespaceAndPath(KingsAndMonsters.MODID, "ogre"));

    // Bog Iron equipment palette: dark bog + moss + mud + tarnished iron, deliberately less
    // saturated and less "magical" than the Ogre artifact palette below. Reuses the same
    // renderer/architecture as every other tier — only these color constants are new.
    private static final int BOG_IRON_EQUIPMENT_NAME_COLOR = 0x9C8A56; // tarnished brass / muted gold
    private static final int BOG_IRON_EQUIPMENT_GLYPH_COLOR = 0x5C6E43; // dull moss
    // Same dark green used elsewhere for weapon/tool stat lines (e.g. the vanilla "total attack"
    // color already seen on the Iron Maw/Ogre King's Club) — kept uniform with other items rather
    // than a Bog-Iron-specific teal, per explicit feedback that the teal read as washed-out gray.
    private static final int BOG_IRON_EQUIPMENT_STAT_COLOR = 0x00AA00;
    private static final int BOG_IRON_EQUIPMENT_FLAVOR_COLOR = 0x3A5F35; // moss/fern green, darker than relic flavor

    private ClientTooltipEvents() {}

    public static void onItemTooltip(ItemTooltipEvent event) {
        if (!ClientConfig.showCustomTooltips()) return;
        boolean ogreTooltip = isOgreRelic(event.getItemStack());
        boolean bogIronEquipment = isBogIronEquipment(event.getItemStack());
        boolean addedHeader = false;

        addBogIronMaterialLore(event);
        addMerchantLore(event);
        addSurvivalItemLore(event);

        if (event.getItemStack().is(ModItems.HUNTERS_SPEAR.get())) {
            event.getToolTip().add(Component.empty());
            event.getToolTip().add(Component.translatable("tooltip.kingsandmonsters.when_used")
                    .withStyle(ChatFormatting.GRAY));
            event.getToolTip().add(CombatDescriptionComponents.huntersSpearArmorPierce()
                    .copy().withStyle(ChatFormatting.BLUE));
            event.getToolTip().add(Component.empty());
            event.getToolTip().add(Component.translatable("tooltip.kingsandmonsters.when_thrown")
                    .withStyle(ChatFormatting.GRAY));
            event.getToolTip().add(CombatDescriptionComponents.huntersSpearCripple()
                    .copy().withStyle(ChatFormatting.BLUE));
            addedHeader = true;
        }

        for (var enchantment : EnchantmentHelper.getEnchantmentsForCrafting(event.getItemStack()).keySet()) {
            var key = enchantment.unwrapKey().orElse(null);
            Component description = key == null ? null : CombatDescriptionComponents.enchantment(key);
            if (description != null) {
                if (!addedHeader) {
                    event.getToolTip().add(Component.empty());
                    event.getToolTip().add(Component.translatable("tooltip.kingsandmonsters.when_used")
                            .withStyle(ChatFormatting.GRAY));
                    addedHeader = true;
                }
                event.getToolTip().add(description.copy().withStyle(ChatFormatting.GRAY));
                event.getToolTip().add(Component.translatable("tooltip.kingsandmonsters.not_stackable")
                        .withStyle(ChatFormatting.BLUE));
            }
        }

        PotionContents contents = event.getItemStack().get(DataComponents.POTION_CONTENTS);
        if (contents != null) {
            for (var effect : contents.getAllEffects()) {
                Component description = CombatDescriptionComponents.effect(effect.getEffect());
                if (description != null) {
                    if (!addedHeader) {
                        event.getToolTip().add(Component.translatable("tooltip.kingsandmonsters.header")
                                .withStyle(ChatFormatting.DARK_GREEN));
                        addedHeader = true;
                    }
                    event.getToolTip().add(description.copy().withStyle(ChatFormatting.GRAY));
                }
            }
        }

        if (isMerchantWare(event.getItemStack())) {
            removeRedundantAdvancedDetails(event);
            styleMerchantTooltip(event);
        } else if (bogIronEquipment) {
            removeRedundantAdvancedDetails(event);
            styleBogIronEquipmentTooltip(event);
        } else if (ogreTooltip) {
            removeRedundantAdvancedDetails(event);
            styleOgreTooltip(event);
        }
    }

    private static void addMerchantLore(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.is(ModItems.OGRE_MERCHANT_BACKPACK.get())) {
            event.getToolTip().add(Component.literal("A merchant's hoard, carried on your back.")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC));
        }
    }

    private static void addSurvivalItemLore(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        String lore = null;

        if (stack.is(ModItems.OGREBLOOD_TOTEM.get())) lore = "An Ogre Mage's crude answer to the Totem of Undying.";
        else if (stack.is(ModItems.BOG_EYE_CHARM.get())) lore = "It twitches toward treasure no living eye has claimed.";
        else if (stack.is(ModItems.WARCALLER_BELL.get())) lore = "Grunt Captains ring it when a camp has chosen its prey.";
        else if (stack.is(ModItems.RATTLEBONE_RING.get())) lore = "Ogre Mages bind finger bones into rings that celebrate every death.";
        else if (stack.is(ModItems.OGRE_TOOTH_NECKLACE.get())) lore = "Grunts wear their trophies where every rival can count them.";
        else if (stack.is(ModItems.BRUTE_HEART.get())) lore = "It still beats with the stubborn fury of its former owner.";
        else if (stack.is(ModItems.BOG_IRON_BAND.get())) lore = "Crude bog iron, hammered into a ward against swamp venom.";
        else if (stack.is(ModItems.BOGFUME_CHARM.get())) lore = "An Ogre Mage's bottled marsh spite seeps from every crack.";
        else if (stack.is(ModItems.BUCKLER.get())) lore = "Ogre Archers prize a shield small enough to never spoil their aim.";
        else if (stack.is(ModItems.HUNTERS_SPEAR.get())) lore = "Ogre Guards trust the barbs to finish what the point begins.";
        else if (stack.is(ModItems.OGRE_HOOKBLADE.get())) lore = "A merchant's answer to anyone foolish enough to flee with his gold.";
        else if (stack.is(ModItems.KINGS_CLEAVER.get())) lore = "An old Ogre King taught the Maw to hunger for every life it takes.";
        else if (stack.is(ModItems.OGRE_KINGS_CLUB.get())) lore = "The King's verdict, delivered with both hands.";
        else if (stack.is(ModItems.OGRE_KINGS_CROWN.get())) lore = "Among ogres, a crown is not inherited. It is survived.";
        else if (stack.is(ModItems.OGRE_BRUTE_BANNER.get())) lore = "A warning that strength, not mercy, rules this camp.";
        else if (stack.is(ModItems.GRUNT_CAPTAIN_BANNER.get())) lore = "The mark of an ogre trusted to keep lesser brutes in line.";
        else if (stack.is(ModItems.OGRE_MAGE_BANNER.get())) lore = "Bog fumes and old curses gather beneath this standard.";
        else if (stack.is(ModItems.OGRE_KINGS_BANNER.get())) lore = "Where this banner stands, all tribute belongs to the King.";
        else if (stack.is(ModItems.BOG_IRON_SWORD.get())) lore = "Bog-stained steel, balanced for a fighter smaller than an ogre.";
        else if (stack.is(ModItems.BOG_IRON_PICKAXE.get())) lore = "Its edge bites stone with the patience of a sinking mire.";
        else if (stack.is(ModItems.BOG_IRON_AXE.get())) lore = "Made for timber, though ogres rarely distinguish wood from bone.";
        else if (stack.is(ModItems.BOG_IRON_SHOVEL.get())) lore = "A broad swamp-forged blade for mud, graves, and stubborn ground.";
        else if (stack.is(ModItems.BOG_IRON_HOE.get())) lore = "Even ogres know a fed camp begins with broken earth.";
        else if (stack.is(ModItems.BOG_IRON_HELMET.get())) lore = "The bog's dull metal keeps both rain and blades from the skull.";
        else if (stack.is(ModItems.BOG_IRON_CHESTPLATE.get())) lore = "Heavy plates shaped after the armor of Ogre warbands.";
        else if (stack.is(ModItems.BOG_IRON_LEGGINGS.get())) lore = "Forged to wade through reeds, mud, and the press of battle.";
        else if (stack.is(ModItems.BOG_IRON_BOOTS.get())) lore = "Each step carries the weight of the swamp with it.";
        else if (stack.is(ModBlocks.BOG_IRON_BLOCK_ITEM.get())) lore = "A hoard of bog metal compressed into one sullen mass.";
        else if (stack.is(ModBlocks.RAW_BOG_IRON_BLOCK_ITEM.get())) lore = "Mud and metal packed together before the forge can judge them.";
        else if (stack.is(ModBlocks.BOG_OAK_LOG_ITEM.get())) lore = "Dark timber hardened by years of stagnant water and Ogre smoke.";

        if (lore != null) {
            event.getToolTip().add(Component.empty());
            event.getToolTip().add(Component.literal(lore)
                    .withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.ITALIC));
        }
    }

    private static void addBogIronMaterialLore(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.is(ModBlocks.BOG_IRON_ORE_ITEM.get())
                || stack.is(ModBlocks.DEEPSLATE_BOG_IRON_ORE_ITEM.get())) {
            addLore(event,
                    "Swamp magic has seeped into the vein, leaving its iron potent but impure.",
                    "Only the ogres seem able to draw clean silver from it.");
        } else if (stack.is(ModItems.RAW_BOG_IRON.get())) {
            addLore(event,
                    "Bog corruption clings to every muddy fragment.",
                    "The secret that cleanses it into ogre-silver remains closely guarded.");
        } else if (stack.is(ModItems.BOG_IRON_INGOT.get())) {
            addLore(event,
                    "Smelted into shape, though stagnant magic still stains the metal.",
                    "It lacks the bright purity of the silver armor worn by ogres.");
        } else if (stack.is(ModBlocks.RAW_BOG_IRON_BLOCK_ITEM.get())) {
            addLore(event,
                    "A dense cache of tainted ore with its bog magic concentrated.",
                    "No outsider has reproduced the ogres' method of cleansing it.");
        } else if (stack.is(ModBlocks.BOG_IRON_BLOCK_ITEM.get())) {
            addLore(event,
                    "Refined, yet traces of the bog's corruption remain within.",
                    "Ogre silverwork proves the metal can somehow be purified further.");
        }
    }

    private static void addLore(ItemTooltipEvent event, String firstLine, String secondLine) {
        event.getToolTip().add(Component.empty());
        event.getToolTip().add(Component.literal(firstLine)
                .withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.ITALIC));
        event.getToolTip().add(Component.literal(secondLine)
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }

    private static void removeRedundantAdvancedDetails(ItemTooltipEvent event) {
        int itemTagsHeader = -1;
        for (int index = 0; index < event.getToolTip().size(); index++) {
            String text = event.getToolTip().get(index).getString().trim();
            if (text.startsWith("Item Tags")) {
                itemTagsHeader = index;
                break;
            }
        }
        if (itemTagsHeader >= 0) {
            event.getToolTip().subList(itemTagsHeader, event.getToolTip().size()).clear();
        }

        // Advanced attributes append their arithmetic to the useful final value on the same line,
        // for example "12 Attack Damage [1 + 11]". Keep the result and remove only that suffix.
        for (int index = 0; index < event.getToolTip().size(); index++) {
            Component line = event.getToolTip().get(index);
            String text = line.getString();
            String cleaned = text.replaceFirst("\\s+\\[[^\\]]+\\]$", "");
            if (!cleaned.equals(text)) {
                event.getToolTip().set(index, Component.literal(cleaned)
                        .withStyle(ChatFormatting.DARK_GREEN));
            }
        }
    }

    private static void styleOgreTooltip(ItemTooltipEvent event) {
        boolean royal = isRoyalOgreRelic(event.getItemStack());
        boolean weapon = isOgreWeaponRelic(event.getItemStack());
        for (int i = 0; i < event.getToolTip().size(); i++) {
            Component line = event.getToolTip().get(i);
            event.getToolTip().set(i, line.copy().withStyle(style -> style.withFont(OGRE_FONT)));
        }
        if (!event.getToolTip().isEmpty()) {
            Component name = event.getToolTip().getFirst();
            event.getToolTip().set(0, Component.literal("\u25C6 ")
                    .withStyle(royal ? ChatFormatting.DARK_PURPLE
                            : ChatFormatting.DARK_GREEN)
                    .append(name.copy().withStyle(style -> style
                            .withFont(OGRE_FONT)
                            .withColor(royal ? 0xFFD45A : weapon ? 0xB7C0BA : 0xC6A85B))));
        }
    }

    private static void styleMerchantTooltip(ItemTooltipEvent event) {
        for (int i = 0; i < event.getToolTip().size(); i++) {
            Component line = event.getToolTip().get(i);
            event.getToolTip().set(i, line.copy().withStyle(style -> style.withFont(OGRE_FONT)));
        }
        if (!event.getToolTip().isEmpty()) {
            Component name = event.getToolTip().getFirst();
            event.getToolTip().set(0, Component.literal("\u2726 ")
                    .withStyle(style -> style.withFont(OGRE_FONT).withColor(0xB87516))
                    .append(name.copy().withStyle(style -> style
                            .withFont(OGRE_FONT)
                            .withColor(0xFFD45A))));
        }
    }

    /**
     * Dark, mossy, muddy presentation for the Bog Iron equipment set — reuses the exact same
     * renderer/font/spacing as {@link #styleOgreTooltip}, only the colors differ. Vanilla's
     * positive-attribute blue and this mod's flavor-lore dark green are recolored in place so
     * the swamp-forged palette reaches every line, not just the item name.
     */
    private static void styleBogIronEquipmentTooltip(ItemTooltipEvent event) {
        int vanillaStatColor = ChatFormatting.BLUE.getColor();
        // Vanilla renders both the "total" attack damage/speed line (e.g. sword/tool stats) and
        // this mod's italic flavor lore in the same DARK_GREEN — the italic flag is what tells
        // them apart, since only the flavor line is italic.
        int vanillaDarkGreen = ChatFormatting.DARK_GREEN.getColor();
        for (int i = 0; i < event.getToolTip().size(); i++) {
            Component line = event.getToolTip().get(i);
            var existingColor = line.getStyle().getColor();
            Integer color = existingColor != null ? existingColor.getValue() : null;
            boolean italic = line.getStyle().isItalic();
            event.getToolTip().set(i, line.copy().withStyle(style -> {
                style = style.withFont(OGRE_FONT);
                if (color != null && color == vanillaDarkGreen && italic) {
                    style = style.withColor(BOG_IRON_EQUIPMENT_FLAVOR_COLOR);
                } else if (color != null && (color == vanillaStatColor || color == vanillaDarkGreen)) {
                    style = style.withColor(BOG_IRON_EQUIPMENT_STAT_COLOR);
                }
                return style;
            }));
        }
        if (!event.getToolTip().isEmpty()) {
            Component name = event.getToolTip().getFirst();
            event.getToolTip().set(0, Component.literal("❖ ")
                    .withStyle(style -> style.withFont(OGRE_FONT).withColor(BOG_IRON_EQUIPMENT_GLYPH_COLOR))
                    .append(name.copy().withStyle(style -> style
                            .withFont(OGRE_FONT)
                            .withColor(BOG_IRON_EQUIPMENT_NAME_COLOR))));
        }
    }

    private static void applyLegacyTooltipColors(LegacyTooltipColorEvent event) {
        if (!ClientConfig.showCustomTooltips()) return;
        if (isMerchantWare(event.getItemStack())) {
            // Ogre greed: near-black coin leather with an aged amber-to-rich-gold rim.
            event.setBackgroundStart(0xF0181006);
            event.setBackgroundEnd(0xF02A1A08);
            event.setBorderStart(0xFF9A5A12);
            event.setBorderEnd(0xFFFFD45A);
            return;
        }
        if (isBogIronEquipment(event.getItemStack())) {
            // Dark earth / bog-black background — noticeably darker and less saturated than the
            // Ogre relic tier below, so the King-gated gear reads as rugged swamp-forged
            // equipment rather than a magical trophy.
            event.setBackgroundStart(0xF0141A10);
            event.setBackgroundEnd(0xF01D170C);
            // Deep moss green fading into muddy olive/brown — no gold, unlike the relic tiers.
            event.setBorderStart(0xFF3C5227);
            event.setBorderEnd(0xFF6B5A2E);
            return;
        }
        if (!isOgreRelic(event.getItemStack())) {
            return;
        }
        if (isRoyalOgreRelic(event.getItemStack())) {
            event.setBackgroundStart(0xF0181024);
            event.setBackgroundEnd(0xF0281638);
            event.setBorderStart(0xFF7D3FB2);
            event.setBorderEnd(0xFFFFC83D);
            return;
        }
        if (isOgreWeaponRelic(event.getItemStack())) {
            // Deep forest-black fading into bog green, edged with muted forged iron.
            event.setBackgroundStart(0xF0121915);
            event.setBackgroundEnd(0xF01A241C);
            event.setBorderStart(0xFF294B34);
            event.setBorderEnd(0xFF8E9992);
            return;
        }
        // Near-black bog iron with a subtle peat-brown gradient.
        event.setBackgroundStart(0xF01A2118);
        event.setBackgroundEnd(0xF0261B14);
        // Moss at the top fading into tarnished ogre gold.
        event.setBorderStart(0xFF557A32);
        event.setBorderEnd(0xFFC09A43);
    }

    private interface LegacyTooltipColorEvent {
        ItemStack getItemStack();
        void setBackgroundStart(int color);
        void setBackgroundEnd(int color);
        void setBorderStart(int color);
        void setBorderEnd(int color);
    }

    private static boolean isMerchantWare(ItemStack stack) {
        return stack.is(ModItems.GOLDEN_OGRE_TOOTH.get())
                || stack.is(ModItems.IRONBELLY_STEW.get())
                || stack.is(ModItems.OGREBLOOD_TOTEM.get())
                || stack.is(ModItems.OGRE_MERCHANT_BACKPACK.get())
                || stack.is(ModItems.KINGS_TRIBUTE_BUNDLE.get())
                || stack.is(ModItems.BOG_EYE_CHARM.get())
                || stack.is(ModItems.OGRE_HOOKBLADE.get())
                || stack.is(ModItems.RATTLEBONE_RING.get())
                || stack.is(ModItems.WARCALLER_BELL.get());
    }

    private static boolean isOgreRelic(ItemStack stack) {
        if (stack.is(ModItems.OGRE_TOOTH_NECKLACE.get())
                || stack.is(ModItems.BRUTE_HEART.get())
                || stack.is(ModItems.BOG_IRON_BAND.get())
                || stack.is(ModItems.BOGFUME_CHARM.get())
                || stack.is(ModItems.BUCKLER.get())
                || stack.is(ModItems.WARCALLER_BELL.get())
                || stack.is(ModItems.RATTLEBONE_RING.get())
                || stack.is(ModItems.HUNTERS_SPEAR.get())
                || stack.is(ModItems.OGRE_KINGS_CROWN.get())
                || stack.is(ModItems.KINGS_CLEAVER.get())
                || stack.is(ModItems.OGRE_KINGS_CLUB.get())) {
            return true;
        }
        return EnchantmentHelper.getEnchantmentsForCrafting(stack).keySet().stream()
                .map(holder -> holder.unwrapKey().orElse(null))
                .anyMatch(key -> key != null && KingsAndMonsters.MODID.equals(key.identifier().getNamespace()));
    }

    private static boolean isRoyalOgreRelic(ItemStack stack) {
        return stack.is(ModItems.OGRE_KINGS_CROWN.get())
                || stack.is(ModItems.OGRE_KINGS_CLUB.get());
    }

    private static boolean isOgreWeaponRelic(ItemStack stack) {
        return stack.is(ModItems.KINGS_CLEAVER.get())
                || stack.is(ModItems.HUNTERS_SPEAR.get());
    }

    /**
     * The King-gated Bog Iron tool/armor set — deliberately its own presentation tier, distinct
     * from Ogre artifacts (e.g. the Bog Iron Band, which despite its name is an artifact/ring and
     * stays in {@link #isOgreRelic}). Matched by item identity, never by translation/registry name.
     */
    private static boolean isBogIronEquipment(ItemStack stack) {
        return stack.is(ModItems.BOG_IRON_SWORD.get())
                || stack.is(ModItems.BOG_IRON_PICKAXE.get())
                || stack.is(ModItems.BOG_IRON_AXE.get())
                || stack.is(ModItems.BOG_IRON_SHOVEL.get())
                || stack.is(ModItems.BOG_IRON_HOE.get())
                || stack.is(ModItems.BOG_IRON_HELMET.get())
                || stack.is(ModItems.BOG_IRON_CHESTPLATE.get())
                || stack.is(ModItems.BOG_IRON_LEGGINGS.get())
                || stack.is(ModItems.BOG_IRON_BOOTS.get());
    }

    public static void onEffectScreenTooltip(GatherEffectScreenTooltipsEvent event) {
        if (!ClientConfig.showCustomTooltips()) return;
        Component description = CombatDescriptionComponents.effect(event.getEffectInstance().getEffect());
        if (description != null) {
            event.getTooltip().add(description.copy().withStyle(ChatFormatting.GRAY));
        }
    }
}
