package com.kingsandmonsters.entity.client;

import com.geckolib.cache.model.GeoBone;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.layer.builtin.BlockAndItemGeoLayer;
import com.geckolib.util.RenderUtil;
import com.kingsandmonsters.entity.OgreGuard;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.item.ItemDisplayContext;
import java.util.List;

public class OgreGuardRenderer extends KingsGeoEntityRenderer<OgreGuard> {
    public OgreGuardRenderer(EntityRendererProvider.Context context) {
        super(context, new OgreGuardModel());
        shadowRadius = 0.55f;
        withRenderLayer(new GuardSpearLayer(context, this));
    }
    private static final class GuardSpearLayer extends BlockAndItemGeoLayer<OgreGuard, Void, OgreEntityRenderState> {
        GuardSpearLayer(EntityRendererProvider.Context context, GeoEntityRenderer<OgreGuard, OgreEntityRenderState> renderer) { super(context, renderer); }
        protected List<RenderData> getRelevantBones(OgreGuard guard, Void ignored, OgreEntityRenderState state, float partialTick) {
            var stack = guard.getMainHandItem();
            return !guard.shouldRenderHeldSpear() || stack.isEmpty() ? List.of() : List.of(RenderData.item(
                    "right_hand_item", ItemDisplayContext.NONE,
                    RenderUtil.createRenderStateForItem(stack, itemModelResolver, ItemDisplayContext.NONE, guard)));
        }
        public void addRenderData(OgreGuard guard, Void ignored, OgreEntityRenderState state, float partialTick) {
            List<RenderData> data = getRelevantBones(guard, ignored, state, partialTick);
            if (!data.isEmpty()) state.addGeckolibData(CONTENTS, data);
        }
        protected void submitItemStackRender(PoseStack pose, GeoBone bone, ItemStackRenderState item,
                ItemDisplayContext context, OgreEntityRenderState state, SubmitNodeCollector collector, int light) {
            pose.translate(0, 0, 0.15f);
            pose.mulPose(Axis.XP.rotationDegrees(90));
            pose.mulPose(Axis.YP.rotationDegrees(90));
            pose.mulPose(Axis.ZP.rotationDegrees(45));
            pose.scale(1.4f, 1.4f, 0.8f);
            super.submitItemStackRender(pose, bone, item, context, state, collector, light);
        }
    }
}
