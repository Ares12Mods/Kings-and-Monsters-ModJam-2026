package com.kingsandmonsters.entity.client;

import com.kingsandmonsters.entity.OgreSpear;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.ThrownItemRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;

/**
 * Renders the flying spear as its own flat 2D item art (unlike vanilla's 3D-modelled
 * {@code ThrownTridentRenderer}) but oriented like a trident/arrow in flight — tip pointing
 * along the direction of travel — rather than using vanilla's {@code ThrownItemRenderer}, which
 * just billboards flat items to always face the camera (fine for a snowball, wrong for a spear).
 */
public class OgreSpearRenderer extends EntityRenderer<OgreSpear, OgreSpearRenderer.State> {
    // Keep the flying weapon the same physical size as the spear held by the Ogre Guard.
    private static final float SCALE = 1.4F;

    private final ItemModelResolver itemModelResolver;

    public OgreSpearRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemModelResolver = context.getItemModelResolver();
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        // Same yaw/pitch-from-velocity formula ThrownTridentRenderer and vanilla arrows use to
        // point a shaft-shaped model along the direction of travel.
        poseStack.mulPose(Axis.YP.rotationDegrees(state.yRot - 90.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(state.xRot + 90.0F));
        // The spear texture is a diagonal generated-item icon (grip bottom-left, tip top-right,
        // ~45 degrees) — same correction used for the held pose in OgreGuardRenderer — so this
        // must run first (it's the last mulPose call, applied first to the raw sprite) to turn
        // that diagonal into a shaft pointing along local Y before the travel-direction rotations
        // above carry it the rest of the way. 225 (45 + 180), not 45: the yaw/pitch formula above
        // expects the model's tip at -Y, not +Y as it was for the held pose — confirmed backwards
        // (grip leading, tip trailing) in testing, so this is flipped from the held-pose value.
        poseStack.mulPose(Axis.ZP.rotationDegrees(225.0F));
        // Use the same slim-depth profile as the player-held and guard-held spear.
        poseStack.scale(SCALE, SCALE, 0.8F);
        state.item.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(OgreSpear entity, State state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.yRot = entity.hasStablePostHitRotation() ? entity.getStablePostHitYaw() : entity.getYRot(partialTicks);
        state.xRot = entity.hasStablePostHitRotation() ? entity.getStablePostHitPitch() : entity.getXRot(partialTicks);
        itemModelResolver.updateForNonLiving(state.item, entity.getItem(), ItemDisplayContext.NONE, entity);
    }

    public static final class State extends ThrownItemRenderState {
        float yRot;
        float xRot;
    }
}
