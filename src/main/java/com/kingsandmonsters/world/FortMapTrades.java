package com.kingsandmonsters.world;

import com.kingsandmonsters.KingsAndMonsters;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.structure.Structure;

public final class FortMapTrades {
    public static final TagKey<Structure> FORT_MAP_DESTINATIONS = TagKey.create(
            net.minecraft.core.registries.Registries.STRUCTURE,
            Identifier.fromNamespaceAndPath(
                    KingsAndMonsters.MODID, "on_ogre_fort_maps"));

    private FortMapTrades() {
    }

}
