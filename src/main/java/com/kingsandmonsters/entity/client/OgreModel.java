package com.kingsandmonsters.entity.client;

import com.kingsandmonsters.KingsAndMonsters;
import net.minecraft.resources.Identifier;
import com.geckolib.animatable.GeoAnimatable;
import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;

abstract class OgreModel<T extends GeoAnimatable> extends GeoModel<T> {
    private final Identifier modelResource;
    private final Identifier textureResource;
    private final Identifier animationResource;

    protected OgreModel(String name) {
        // GeckoLib 5's resource cache keys models/animations by a bare logical id: the file is loaded
        // from assets/<ns>/geckolib/(models|animations)/<path>.(geo|animation).json, but the cache key
        // strips that root folder and the file suffix, so getModelResource/getAnimationResource must
        // return just "<path>" (no geckolib/ prefix, no .geo.json/.animation.json suffix) or the lookup
        // misses. See GeckoLibResources#stripPrefixAndSuffix / BakedAnimationCache#stripLegacyPath.
        this.modelResource = Identifier.fromNamespaceAndPath(KingsAndMonsters.MODID, name);
        this.textureResource = Identifier.fromNamespaceAndPath(KingsAndMonsters.MODID, "textures/entity/" + name + ".png");
        this.animationResource = Identifier.fromNamespaceAndPath(KingsAndMonsters.MODID, name);
    }

    @Override
    @SuppressWarnings("deprecation")
    public Identifier getModelResource(GeoRenderState renderState) {
        return modelResource;
    }

    @Override
    @SuppressWarnings("deprecation")
    public Identifier getTextureResource(GeoRenderState renderState) {
        return textureResource;
    }

    @Override
    public Identifier getAnimationResource(T animatable) {
        return animationResource;
    }
}
