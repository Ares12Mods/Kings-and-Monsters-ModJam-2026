package com.kingsandmonsters.client.item;

import com.kingsandmonsters.KingsAndMonsters;
import com.kingsandmonsters.item.OgreKingsClubItem;
import net.minecraft.resources.Identifier;
import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;

public final class OgreKingsClubModel extends GeoModel<OgreKingsClubItem> {
    // GeckoLib 5 looks these up in its baked-resource cache by a bare logical id (root folder and
    // file suffix are stripped internally) — see OgreModel for the full explanation.
    private static final Identifier MODEL = id("item/ogre_kings_club");
    private static final Identifier TEXTURE = id("textures/item/ogre_kings_club.png");
    private static final Identifier ANIMATION = id("item/ogre_kings_club");

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
    public Identifier getAnimationResource(OgreKingsClubItem animatable) {
        return ANIMATION;
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(KingsAndMonsters.MODID, path);
    }
}
