package com.kingsandmonsters.client.armor;

import com.kingsandmonsters.KingsAndMonsters;
import com.kingsandmonsters.item.BogIronArmorItem;
import net.minecraft.resources.Identifier;
import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.GeoArmorRenderer;
import net.minecraft.world.entity.EquipmentSlot;

public class BogIronArmorModel extends GeoModel<BogIronArmorItem> {
    // GeckoLib 5 looks these up in its baked-resource cache by a bare logical id (root folder and
    // file suffix are stripped internally) — see OgreModel for the full explanation.
    private static final Identifier LAYER_1_MODEL = id("armor/bog_iron_layer_1");
    private static final Identifier LAYER_2_MODEL = id("armor/bog_iron_layer_2");
    private static final Identifier LAYER_1_TEXTURE = id("textures/armor/bog_iron_layer_1.png");
    private static final Identifier LAYER_2_TEXTURE = id("textures/armor/bog_iron_layer_2.png");
    private static final Identifier ANIMATION = id("armor/bog_iron");

    @Override
    @SuppressWarnings("deprecation")
    public Identifier getModelResource(GeoRenderState renderState) {
        return usesLeggingsModel(renderState) ? LAYER_2_MODEL : LAYER_1_MODEL;
    }

    @Override
    @SuppressWarnings("deprecation")
    public Identifier getTextureResource(GeoRenderState renderState) {
        return usesLeggingsModel(renderState) ? LAYER_2_TEXTURE : LAYER_1_TEXTURE;
    }

    @Override
    public Identifier getAnimationResource(BogIronArmorItem animatable) {
        return ANIMATION;
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(KingsAndMonsters.MODID, path);
    }

    private static boolean usesLeggingsModel(GeoRenderState renderState) {
        return renderState.getOrDefaultGeckolibData(GeoArmorRenderer.CURRENT_SLOT, EquipmentSlot.CHEST)
                == EquipmentSlot.LEGS;
    }
}
