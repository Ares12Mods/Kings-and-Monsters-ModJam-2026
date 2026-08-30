package com.kingsandmonsters;

import com.kingsandmonsters.menu.OgreMerchantBackpackMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, KingsAndMonsters.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<OgreMerchantBackpackMenu>> OGRE_MERCHANT_BACKPACK =
            MENUS.register("ogre_merchant_backpack", () -> IMenuTypeExtension.create(OgreMerchantBackpackMenu::new));

    private ModMenus() {}
}
