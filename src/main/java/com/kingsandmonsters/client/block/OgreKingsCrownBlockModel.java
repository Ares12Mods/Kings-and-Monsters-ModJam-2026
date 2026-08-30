package com.kingsandmonsters.client.block;

import com.kingsandmonsters.KingsAndMonsters;
import com.kingsandmonsters.block.entity.OgreKingsCrownBlockEntity;
import net.minecraft.resources.Identifier;
import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;

public final class OgreKingsCrownBlockModel extends GeoModel<OgreKingsCrownBlockEntity> {
    // GeckoLib 5 looks these up in its baked-resource cache by a bare logical id (root folder and
    // file suffix are stripped internally) — see OgreModel for the full explanation.
    private static final Identifier MODEL = id("block/ogre_kings_crown");
    private static final Identifier TEXTURE = id("textures/armor/ogre_kings_crown.png");
    private static final Identifier ANIMATION = id("block/ogre_kings_crown");

    @Override
    public Identifier getModelResource(GeoRenderState renderState) {
        return MODEL;
    }

    @Override
    public Identifier getTextureResource(GeoRenderState renderState) {
        return TEXTURE;
    }

    @Override
    public Identifier getAnimationResource(OgreKingsCrownBlockEntity animatable) {
        return ANIMATION;
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(KingsAndMonsters.MODID, path);
    }
}
