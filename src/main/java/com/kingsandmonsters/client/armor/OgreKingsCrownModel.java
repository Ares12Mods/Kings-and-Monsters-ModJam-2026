package com.kingsandmonsters.client.armor;

import com.kingsandmonsters.KingsAndMonsters;
import com.kingsandmonsters.item.OgreKingsCrownItem;
import net.minecraft.resources.Identifier;
import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;

public class OgreKingsCrownModel extends GeoModel<OgreKingsCrownItem> {
    // GeckoLib 5 looks these up in its baked-resource cache by a bare logical id (root folder and
    // file suffix are stripped internally) — see OgreModel for the full explanation.
    private static final Identifier MODEL = id("armor/ogre_kings_crown");
    private static final Identifier TEXTURE = id("textures/armor/ogre_kings_crown.png");
    private static final Identifier ANIMATION = id("armor/ogre_kings_crown");

    @Override
    @SuppressWarnings("deprecation")
    public Identifier getModelResource(GeoRenderState renderState) {
        return MODEL;
    }

    @Override
    @SuppressWarnings("deprecation")
    public Identifier getTextureResource(GeoRenderState renderState) {
        return TEXTURE;
    }

    @Override
    public Identifier getAnimationResource(OgreKingsCrownItem animatable) {
        return ANIMATION;
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(KingsAndMonsters.MODID, path);
    }
}
