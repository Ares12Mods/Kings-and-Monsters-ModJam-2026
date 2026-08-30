package com.kingsandmonsters.compat.jei;

import com.kingsandmonsters.KingsAndMonsters;
import com.kingsandmonsters.ModBlocks;
import com.kingsandmonsters.ModItems;
import com.kingsandmonsters.client.CombatDescriptionComponents;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.List;

@JeiPlugin
public class KingsAndMonstersJeiPlugin implements IModPlugin {
    private static final Identifier PLUGIN_ID = id("jei_plugin");

    @Override
    public Identifier getPluginUid() {
        return PLUGIN_ID;
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addItemStackInfo(new ItemStack(ModItems.HUNTERS_SPEAR.get()),
                Component.translatable("jei.kingsandmonsters.hunters_spear.description"),
                CombatDescriptionComponents.huntersSpear());
        registration.addItemStackInfo(new ItemStack(ModItems.KINGS_CLEAVER.get()),
                Component.translatable("jei.kingsandmonsters.kings_cleaver.description"));
        registration.addItemStackInfo(new ItemStack(ModItems.OGRE_KINGS_CROWN.get()),
                Component.translatable("jei.kingsandmonsters.ogre_kings_crown.description"));
        registration.addItemStackInfo(new ItemStack(ModItems.OGRE_KINGS_CLUB.get()),
                Component.translatable("jei.kingsandmonsters.ogre_kings_club.description"));
        registration.addItemStackInfo(new ItemStack(ModItems.OGRE_TOOTH_NECKLACE.get()),
                Component.translatable("jei.kingsandmonsters.ogre_tooth_necklace.description"));
        registration.addItemStackInfo(new ItemStack(ModItems.BRUTE_HEART.get()),
                Component.translatable("jei.kingsandmonsters.brute_heart.description"));
        registration.addItemStackInfo(new ItemStack(ModItems.BOG_IRON_BAND.get()),
                Component.translatable("jei.kingsandmonsters.bog_iron_band.description"));
        registration.addItemStackInfo(new ItemStack(ModItems.BOGFUME_CHARM.get()),
                Component.translatable("jei.kingsandmonsters.bogfume_charm.description"));
        registration.addItemStackInfo(new ItemStack(ModItems.BUCKLER.get()),
                Component.translatable("jei.kingsandmonsters.buckler.description"));

    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        // The internal bog-oak block is not survival content.
        jeiRuntime.getIngredientManager().removeIngredientsAtRuntime(
                VanillaTypes.ITEM_STACK,
                List.of(new ItemStack(ModBlocks.BOG_OAK_LOG_ITEM.get())));
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(KingsAndMonsters.MODID, path);
    }
}
