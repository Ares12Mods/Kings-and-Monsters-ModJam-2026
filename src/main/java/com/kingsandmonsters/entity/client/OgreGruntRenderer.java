package com.kingsandmonsters.entity.client;

import com.kingsandmonsters.entity.OgreGrunt;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import com.geckolib.renderer.GeoEntityRenderer;

public class OgreGruntRenderer extends KingsGeoEntityRenderer<OgreGrunt> {

    public OgreGruntRenderer(EntityRendererProvider.Context context) {
        super(context, new OgreGruntModel());
        this.shadowRadius = 0.7f;
    }
}
