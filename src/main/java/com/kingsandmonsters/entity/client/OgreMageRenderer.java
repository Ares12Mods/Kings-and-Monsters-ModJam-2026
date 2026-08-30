package com.kingsandmonsters.entity.client;

import com.geckolib.cache.model.GeoBone;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.layer.builtin.BlockAndItemGeoLayer;
import com.geckolib.util.RenderUtil;
import com.kingsandmonsters.entity.OgreMage;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.item.ItemDisplayContext;
import java.util.List;

public class OgreMageRenderer extends KingsGeoEntityRenderer<OgreMage> {
    public OgreMageRenderer(EntityRendererProvider.Context context) {
        super(context, new OgreMageModel());
        shadowRadius = 0.65f;
        withRenderLayer(new MagePotionLayer(context, this));
    }

    private static final class MagePotionLayer extends BlockAndItemGeoLayer<OgreMage, Void, OgreEntityRenderState> {
        MagePotionLayer(EntityRendererProvider.Context context, GeoEntityRenderer<OgreMage, OgreEntityRenderState> renderer) { super(context, renderer); }
        protected List<RenderData> getRelevantBones(OgreMage mage, Void ignored, OgreEntityRenderState state, float partialTick) {
            var stack = mage.getVisiblePotionStack();
            return stack.isEmpty() ? List.of() : List.of(RenderData.item("left_hand_item", ItemDisplayContext.NONE,
                    RenderUtil.createRenderStateForItem(stack, itemModelResolver, ItemDisplayContext.NONE, mage)));
        }
        public void addRenderData(OgreMage mage, Void ignored, OgreEntityRenderState state, float partialTick) {
            List<RenderData> data = getRelevantBones(mage, ignored, state, partialTick);
            if (!data.isEmpty()) state.addGeckolibData(CONTENTS, data);
        }
        protected void submitItemStackRender(PoseStack pose, GeoBone bone, ItemStackRenderState item,
                ItemDisplayContext context, OgreEntityRenderState state, SubmitNodeCollector collector, int light) {
            pose.scale(0.8f, 0.8f, 0.8f);
            super.submitItemStackRender(pose, bone, item, context, state, collector, light);
        }
    }
}
