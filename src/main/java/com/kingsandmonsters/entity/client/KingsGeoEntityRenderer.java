package com.kingsandmonsters.entity.client;

import com.geckolib.animatable.GeoAnimatable;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.model.GeoModel;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.RenderPassInfo;
import com.kingsandmonsters.client.KingsRenderTypes;
import com.kingsandmonsters.entity.OgreArcher;
import com.kingsandmonsters.entity.OgreBrute;
import com.kingsandmonsters.entity.OgreLord;
import com.kingsandmonsters.entity.OgreMage;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.WeakHashMap;

public class KingsGeoEntityRenderer<T extends LivingEntity & GeoAnimatable>
        extends GeoEntityRenderer<T, OgreEntityRenderState> {
    private static final float MAX_HEAD_YAW = 40.0F;
    // Sign convention, verified against GeckoLib 5: DataTickets.ENTITY_PITCH == LivingEntityRenderState#xRot
    // == entity.getXRot(partialTick), where POSITIVE is looking DOWN, and GeckoLib's own canonical head
    // hookup (DefaultAnimations#hardcodedHeadRotation) applies it as bone.rotX = -pitch. So in the clamp
    // below the UPPER bound is the downward limit and the LOWER bound is the upward limit.
    //
    // The 1.21.1 reference clamped GeckoLib 4's EntityModelData#headPitch, which that version already
    // hands over pre-negated (GeoEntityRenderer passes `-headPitch`). Looking down therefore produced a
    // NEGATIVE value there, so its per-mob getMaximumDownwardHeadPitch() overrides only ever bounded the
    // upward look and downward was always pinned to the shared upward constant — which is why editing
    // those per-mob "downward" numbers changed nothing on screen. Keep these two bounds separate and
    // explicitly named so that mistake cannot come back.
    private static final float MAX_UPWARD_HEAD_PITCH = 22.5F;
    private static final float DEFAULT_MAX_DOWNWARD_HEAD_PITCH = 12.0F;
    private static final float TRACKING_RESPONSE_PER_TICK = 0.18F;
    private static final float TRACKING_FADE_TICKS = 6.0F;
    private final Map<T, TrackingPose> trackingPoses = new WeakHashMap<>();

    public KingsGeoEntityRenderer(EntityRendererProvider.Context context, GeoModel<T> model) {
        super(context, model);
    }

    @Override
    public OgreEntityRenderState createRenderState(T entity, Void relatedObject) {
        return new OgreEntityRenderState();
    }

    @Override
    public RenderType getRenderType(OgreEntityRenderState renderState, Identifier texture) {
        return KingsRenderTypes.entityCutout(texture);
    }

    @Override
    public void extractRenderState(T entity, OgreEntityRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);

        boolean suppressed = entity instanceof OgreArcher archer && archer.isProceduralHeadTrackingSuppressed()
                || entity instanceof OgreBrute brute && brute.isProceduralHeadTrackingSuppressed()
                || entity instanceof OgreMage mage && mage.isProceduralHeadTrackingSuppressed()
                || entity instanceof OgreLord lord && lord.isProceduralHeadTrackingSuppressed();
        float renderTime = entity.tickCount + partialTick;
        TrackingPose pose = trackingPoses.computeIfAbsent(entity,
                ignored -> new TrackingPose(renderTime, suppressed ? 0.0F : 1.0F));
        float elapsed = Mth.clamp(renderTime - pose.lastRenderTime, 0.0F, TRACKING_FADE_TICKS);
        float smoothing = 1.0F - (float)Math.pow(1.0F - TRACKING_RESPONSE_PER_TICK, Math.min(elapsed, 1.0F));
        pose.yaw = Mth.rotLerp(smoothing, pose.yaw, Mth.clamp(state.yRot, -MAX_HEAD_YAW, MAX_HEAD_YAW));
        pose.pitch = Mth.lerp(smoothing, pose.pitch,
                Mth.clamp(state.xRot, -MAX_UPWARD_HEAD_PITCH, maximumDownwardHeadPitch(entity)));
        float weightStep = elapsed / TRACKING_FADE_TICKS;
        pose.weight = Mth.clamp(pose.weight + (suppressed ? -weightStep : weightStep), 0.0F, 1.0F);
        pose.lastRenderTime = renderTime;

        state.proceduralHeadYaw = pose.yaw;
        state.proceduralHeadPitch = pose.pitch;
        state.proceduralHeadTrackingWeight = smoothStep(pose.weight);
        state.maximumDownwardHeadPitch = maximumDownwardHeadPitch(entity);
        state.clampCombatHeadRotation = entity instanceof OgreLord lord && lord.shouldClampCombatHeadRotation();
    }

    @Override
    public void adjustModelBonesForRender(RenderPassInfo<OgreEntityRenderState> renderPassInfo,
                                          BoneSnapshots snapshots) {
        super.adjustModelBonesForRender(renderPassInfo, snapshots);
        OgreEntityRenderState state = renderPassInfo.renderState();
        snapshots.get("head").ifPresent(head -> {
            head.setRotY(head.getRotY() - state.proceduralHeadYaw * Mth.DEG_TO_RAD
                    * state.proceduralHeadTrackingWeight);
            head.setRotX(head.getRotX() - state.proceduralHeadPitch * Mth.DEG_TO_RAD
                    * state.proceduralHeadTrackingWeight);
            if (state.clampCombatHeadRotation) {
                float limit = state.maximumDownwardHeadPitch * Mth.DEG_TO_RAD;
                head.setRotX(Mth.clamp(head.getRotX(), -limit, limit));
            }
        });
    }

    /**
     * Maximum downward head pitch in degrees, per mob. Taller/thicker-necked ogres tuck their chin into
     * their chest at a much smaller angle than the shorter ones, so the limit tightens with size:
     * King (tallest) &lt; Brute &lt; Mage &lt; Grunt/Captain. These only bound the DOWNWARD half of the
     * range — upward tracking stays on MAX_UPWARD_HEAD_PITCH for every type. Subclass order matters here
     * (every ogre below extends OgreGrunt), so the most specific types must be tested first.
     */
    private static float maximumDownwardHeadPitch(LivingEntity entity) {
        if (entity instanceof OgreLord) return 1.0F;
        if (entity instanceof OgreBrute) return 6.0F;
        if (entity instanceof OgreMage) return 9.0F;
        return DEFAULT_MAX_DOWNWARD_HEAD_PITCH;
    }

    private static float smoothStep(float value) {
        return value * value * (3.0F - 2.0F * value);
    }

    private static final class TrackingPose {
        private float yaw;
        private float pitch;
        private float weight;
        private float lastRenderTime;

        private TrackingPose(float renderTime, float weight) {
            this.lastRenderTime = renderTime;
            this.weight = weight;
        }
    }

}
