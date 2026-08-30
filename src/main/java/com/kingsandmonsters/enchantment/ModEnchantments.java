package com.kingsandmonsters.enchantment;

import com.kingsandmonsters.KingsAndMonsters;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.enchantment.Enchantment;

/** Keys for Minecraft 1.21's data-driven enchantment registry. */
public final class ModEnchantments {
    public static final ResourceKey<Enchantment> BARBED = key("barbed");
    public static final ResourceKey<Enchantment> HEAVY_THROW = key("heavy_throw");
    public static final ResourceKey<Enchantment> TYRANT = key("tyrant");

    private ModEnchantments() {}

    private static ResourceKey<Enchantment> key(String path) {
        return ResourceKey.create(Registries.ENCHANTMENT,
                Identifier.fromNamespaceAndPath(KingsAndMonsters.MODID, path));
    }
}

