package com.kingsandmonsters;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BannerPattern;

public final class ModBannerPatterns {
    public static final Holder<BannerPattern> OGRE_BRUTE = pattern(
            "ogre_brute", "block.kingsandmonsters.banner.ogre_brute");
    public static final Holder<BannerPattern> GRUNT_CAPTAIN = pattern(
            "grunt_captain", "block.kingsandmonsters.banner.grunt_captain");
    public static final Holder<BannerPattern> OGRE_KING = Holder.direct(
            new BannerPattern(
                    Identifier.fromNamespaceAndPath(KingsAndMonsters.MODID, "ogre_king"),
                    "block.kingsandmonsters.banner.ogre_king"));
    public static final Holder<BannerPattern> OGRE_MAGE = pattern(
            "ogre_mage", "block.kingsandmonsters.banner.ogre_mage");

    private static Holder<BannerPattern> pattern(String asset, String translationKey) {
        return Holder.direct(new BannerPattern(
                Identifier.fromNamespaceAndPath(KingsAndMonsters.MODID, asset),
                translationKey));
    }

    private ModBannerPatterns() {
    }
}
