package com.kingsandmonsters.client;

import com.kingsandmonsters.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

public final class OgrebloodTotemActivation {
    private OgrebloodTotemActivation() {
    }

    public static void show() {
        Minecraft.getInstance().gameRenderer.displayItemActivation(
                new ItemStack(ModItems.OGREBLOOD_TOTEM.get()));
    }
}
