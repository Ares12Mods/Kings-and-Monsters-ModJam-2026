package com.kingsandmonsters.client.armor;

import com.kingsandmonsters.item.OgreKingsCrownItem;
import com.geckolib.renderer.GeoArmorRenderer;

public class OgreKingsCrownRenderer extends GeoArmorRenderer<OgreKingsCrownItem, net.minecraft.client.renderer.entity.state.HumanoidRenderState> {
    public OgreKingsCrownRenderer() {
        super(new OgreKingsCrownModel());
    }
}
