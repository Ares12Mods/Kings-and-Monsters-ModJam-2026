package com.kingsandmonsters.entity.client;

import com.geckolib.cache.model.GeoBone;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.layer.builtin.BlockAndItemGeoLayer;
import com.geckolib.util.RenderUtil;
import com.kingsandmonsters.entity.OgreArcher;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.item.ItemDisplayContext;
import java.util.List;

public class OgreArcherRenderer extends KingsGeoEntityRenderer<OgreArcher> {
    public OgreArcherRenderer(EntityRendererProvider.Context context) {
        super(context, new OgreArcherModel());
        shadowRadius = 0.55f;
        withRenderLayer(new ArcherBowLayer(context, this));
    }

    private static final class ArcherBowLayer extends BlockAndItemGeoLayer<OgreArcher, Void, OgreEntityRenderState> {
        ArcherBowLayer(EntityRendererProvider.Context context, GeoEntityRenderer<OgreArcher, OgreEntityRenderState> renderer) {
            super(context, renderer);
        }

        protected List<RenderData> getRelevantBones(OgreArcher archer, Void ignored, OgreEntityRenderState state, float partialTick) {
            return archer.getMainHandItem().isEmpty() ? List.of() : List.of(RenderData.item("right_hand_item",
                    ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, RenderUtil.createRenderStateForItem(
                            archer.getMainHandItem(), itemModelResolver, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, archer)));
        }

        public void addRenderData(OgreArcher archer, Void ignored, OgreEntityRenderState state, float partialTick) {
            List<RenderData> data = getRelevantBones(archer, ignored, state, partialTick);
            if (!data.isEmpty()) state.addGeckolibData(CONTENTS, data);
        }

        protected void submitItemStackRender(PoseStack pose, GeoBone bone, ItemStackRenderState item,
                ItemDisplayContext context, OgreEntityRenderState state, SubmitNodeCollector collector, int light) {
            pose.mulPose(Axis.XP.rotationDegrees(-78));
            pose.mulPose(Axis.ZP.rotationDegrees(174));
            pose.scale(1.05f, 1.05f, 1.05f);
            super.submitItemStackRender(pose, bone, item, context, state, collector, light);
        }
    }
}
