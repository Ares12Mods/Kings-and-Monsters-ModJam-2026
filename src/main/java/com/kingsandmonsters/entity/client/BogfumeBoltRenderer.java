package com.kingsandmonsters.entity.client;

import com.kingsandmonsters.KingsAndMonsters;
import com.kingsandmonsters.entity.BogfumeBolt;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;

public class BogfumeBoltRenderer extends EntityRenderer<BogfumeBolt, EntityRenderState> {

    public BogfumeBoltRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    public EntityRenderState createRenderState() {
        return new EntityRenderState();
    }
}
