package com.kingsandmonsters.entity.client;

import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.RenderPassInfo;
import com.kingsandmonsters.entity.OgreLord;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

public class OgreLordRenderer extends KingsGeoEntityRenderer<OgreLord> {
    // Preview scale: 5 blocks tall instead of roughly 6. Restore to 1.0F to reverse.
    private static final float OGRE_KING_SCALE = 5.0F / 6.0F;

    public OgreLordRenderer(EntityRendererProvider.Context context) {
        super(context, new OgreLordModel());
        this.shadowRadius = 1.3f;
        withScale(OGRE_KING_SCALE);
        // The King's Cleaver render layer is intentionally omitted; he does not carry or use it.
    }

    @Override
    protected float getDeathMaxRotation(com.geckolib.renderer.base.GeoRenderState state) {
        // The custom death animation already performs the King's full-body fall.
        return state instanceof OgreEntityRenderState ogreState && ogreState.kingDyingAnimation
                ? 0.0F : super.getDeathMaxRotation(state);
    }

    @Override
    public void extractRenderState(OgreLord lord, OgreEntityRenderState state, float partialTick) {
        super.extractRenderState(lord, state, partialTick);
        state.kingDyingAnimation = lord.isDyingAnimation();
        // Back-mounted while sleeping, awakening, throughout Phase 1, and before the existing
        // Phase 2 draw/swap tick. Only the synchronized hand flag moves it into the hand.
        state.clubRenderState = lord.isClubInHand()
                ? OgreEntityRenderState.ClubRenderState.HAND
                : OgreEntityRenderState.ClubRenderState.BACK;
    }

    @Override
    public void adjustModelBonesForRender(RenderPassInfo<OgreEntityRenderState> renderPassInfo,
                                          BoneSnapshots snapshots) {
        super.adjustModelBonesForRender(renderPassInfo, snapshots);
        OgreEntityRenderState state = renderPassInfo.renderState();
        boolean handVisible = state.clubRenderState == OgreEntityRenderState.ClubRenderState.HAND;
        boolean backVisible = state.clubRenderState == OgreEntityRenderState.ClubRenderState.BACK;

        // The club heads live in child bones. Reset both flags on both hierarchy levels every
        // render pass so hiding one placement cannot leave fragments or poison the next state.
        setClubBoneVisibility(snapshots, "club_hand", handVisible);
        setClubBoneVisibility(snapshots, "bat", handVisible);
        setClubBoneVisibility(snapshots, "club_back", backVisible);
        setClubBoneVisibility(snapshots, "bat2", backVisible);
    }

    private static void setClubBoneVisibility(BoneSnapshots snapshots, String boneName, boolean visible) {
        snapshots.get(boneName).ifPresent(bone -> bone
                .skipRender(!visible)
                .skipChildrenRender(!visible));
    }
}
