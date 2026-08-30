package com.kingsandmonsters.entity.client;

import com.kingsandmonsters.entity.OgreGruntCaptain;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import com.geckolib.cache.model.GeoBone;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.layer.builtin.BlockAndItemGeoLayer;
import com.geckolib.util.RenderUtil;
import java.util.List;

public class OgreGruntCaptainRenderer extends KingsGeoEntityRenderer<OgreGruntCaptain> {
    private static final float CAPTAIN_SCALE = 1.05F;

    public OgreGruntCaptainRenderer(EntityRendererProvider.Context context) {
        super(context, new OgreGruntCaptainModel());
        this.shadowRadius = 0.74F;
        withScale(CAPTAIN_SCALE);
        withRenderLayer(new CaptainSwordLayer(context, this));
    }

    private static final class CaptainSwordLayer extends BlockAndItemGeoLayer<OgreGruntCaptain, Void, OgreEntityRenderState> {
        private CaptainSwordLayer(EntityRendererProvider.Context context, GeoEntityRenderer<OgreGruntCaptain, OgreEntityRenderState> renderer) {
            super(context, renderer);
        }

        @Override
        protected List<RenderData> getRelevantBones(OgreGruntCaptain captain, Void ignored, OgreEntityRenderState state, float partialTick) {
            var stack = captain.getMainHandItem();
            return stack.isEmpty() ? List.of() : List.of(RenderData.item("right_hand_item",
                    ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, RenderUtil.createRenderStateForItem(stack,
                            itemModelResolver, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, captain)));
        }

        @Override
        public void addRenderData(OgreGruntCaptain captain, Void ignored, OgreEntityRenderState state, float partialTick) {
            List<RenderData> data = getRelevantBones(captain, ignored, state, partialTick);
            if (!data.isEmpty()) state.addGeckolibData(CONTENTS, data);
        }

        @Override
        protected void submitItemStackRender(PoseStack poseStack, GeoBone bone, ItemStackRenderState item,
                ItemDisplayContext context, OgreEntityRenderState state, SubmitNodeCollector collector, int light) {
            // The vanilla hand transform points the blade down this model's long forearm.
            // Roll it forward at the wrist so the sword projects anteriorly instead.
            poseStack.translate(0.0F, 0.0F, -0.12F);
            poseStack.mulPose(Axis.XP.rotationDegrees(-80.0F));
            poseStack.scale(1.75F, 1.75F, 1.75F);
            super.submitItemStackRender(poseStack, bone, item, context, state, collector, light);
        }
    }
}
