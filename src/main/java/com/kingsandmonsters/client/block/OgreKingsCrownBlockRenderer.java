package com.kingsandmonsters.client.block;

import com.kingsandmonsters.block.entity.OgreKingsCrownBlockEntity;
import com.geckolib.renderer.GeoBlockRenderer;

public final class OgreKingsCrownBlockRenderer extends GeoBlockRenderer<OgreKingsCrownBlockEntity, net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState> {
    public OgreKingsCrownBlockRenderer(net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context context) {
        super(context, new OgreKingsCrownBlockModel());
    }
}
