package com.kingsandmonsters.client;

import com.kingsandmonsters.network.GroundRipplePayload;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Draws cached copies of surface blocks with frame-interpolated vertical offsets. The actual
 * level is never edited and the server remains solely responsible for ripple damage and launch.
 */
public final class GroundRippleRenderer {
    private static final List<ActiveRipple> ACTIVE = new ArrayList<>();
    private static final double MAX_RENDER_DISTANCE_SQR = 56.0 * 56.0;
    private static final float MIN_VISIBLE_LIFT = 0.012F;

    // Centralized motion tuning. The payload's authored lift is scaled against the former King
    // value so weaker ripples (notably the Brute's) retain their relative visual strength.
    private static final float MAX_VISUAL_LIFT = 0.32F;
    private static final float REFERENCE_KING_VISUAL_LIFT = 0.44F;
    private static final float RISE_FRACTION = 0.38F;
    private static final float CREST_DURATION_FRACTION = 0.04F;
    private static final float SETTLE_DURATION_FRACTION = 1.0F - RISE_FRACTION - CREST_DURATION_FRACTION;
    // One server ripple ring is authored per 1.25 blocks. Matching that spacing here makes whole
    // sections of terrain buckle together instead of exposing a continuous water-like wave on
    // the larger King and Brute radii. Y movement remains fully partial-tick interpolated.
    private static final float RADIAL_DELAY_GROUP_SIZE_BLOCKS = 1.25F;
    private static final float RADIAL_DELAY_GROUPING_STRENGTH = 1.0F;

    private GroundRippleRenderer() {
    }

    public static void start(GroundRipplePayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }

        List<SurfaceSample> samples = sampleSurface(level, payload);
        if (!samples.isEmpty()) {
            ACTIVE.add(new ActiveRipple(level, payload, level.getGameTime(), samples));
        }
    }

    public static void render(SubmitCustomGeometryEvent event) {
        if (ACTIVE.isEmpty()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            ACTIVE.clear();
            return;
        }

        float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        PoseStack poseStack = event.getPoseStack();

        Iterator<ActiveRipple> iterator = ACTIVE.iterator();
        while (iterator.hasNext()) {
            ActiveRipple ripple = iterator.next();
            if (ripple.level != level) {
                iterator.remove();
                continue;
            }
            float age = level.getGameTime() - ripple.startGameTick + partialTick;
            if (age > ripple.totalDuration()) {
                iterator.remove();
                continue;
            }
            if (camera.distanceToSqr(ripple.center()) > MAX_RENDER_DISTANCE_SQR) {
                continue;
            }

            for (SurfaceSample sample : ripple.samples) {
                float localAge = age - sample.delayTicks;
                if (localAge <= 0.0F || localAge >= ripple.payload.blockDurationTicks()) {
                    continue;
                }

                float progress = Mth.clamp(localAge / ripple.payload.blockDurationTicks(), 0.0F, 1.0F);
                float lift = impactLift(progress) * scaledMaxLift(ripple.payload.maxLift());
                if (lift < MIN_VISIBLE_LIFT) {
                    continue;
                }

                BlockPos pos = sample.pos;
                AABB bounds = new AABB(pos).move(0.0, lift, 0.0);
                if (!event.getLevelRenderState().cameraRenderState.cullFrustum.isVisible(bounds)) {
                    continue;
                }

                poseStack.pushPose();
                poseStack.translate(pos.getX() - camera.x, pos.getY() - camera.y + lift, pos.getZ() - camera.z);
                event.getSubmitNodeCollector().submitMultiLayerBlockModel(poseStack, sample.parts,
                        sample.hasTranslucency, sample.tints, sample.packedLight,
                        OverlayTexture.NO_OVERLAY, 0);
                poseStack.popPose();
            }
        }

    }

    private static List<SurfaceSample> sampleSurface(ClientLevel level, GroundRipplePayload payload) {
        List<SurfaceSample> samples = new ArrayList<>();
        BlockColors blockColors = Minecraft.getInstance().getBlockColors();
        BlockStateModelSet stateModelSet = Minecraft.getInstance().getModelManager().getBlockStateModelSet();
        int minX = Mth.floor(payload.x() - payload.radius());
        int maxX = Mth.floor(payload.x() + payload.radius());
        int minZ = Mth.floor(payload.z() - payload.radius());
        int maxZ = Mth.floor(payload.z() + payload.radius());
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                double dx = x + 0.5 - payload.x();
                double dz = z + 0.5 - payload.z();
                double distance = Math.sqrt(dx * dx + dz * dz);
                if (distance > payload.radius() + 0.35 || !level.hasChunk(x >> 4, z >> 4)) {
                    continue;
                }

                BlockPos surface = findSurface(level, cursor, x, z, payload.y());
                if (surface == null) {
                    continue;
                }
                BlockState state = level.getBlockState(surface);
                if (state.getRenderShape() != RenderShape.MODEL || state.hasBlockEntity()) {
                    continue;
                }

                // Use the real world-facing block model + tint pipeline (BlockColors sampling the
                // actual level/pos, exactly like chunk rendering does) instead of GeckoLib/vanilla's
                // "display" model resolver — that resolver has no Level/BlockPos to sample biome tint
                // from at all, which is what produced flat black terrain here.
                BlockStateModel model = stateModelSet.get(state);
                List<BlockStateModelPart> parts = new ArrayList<>();
                model.collectParts(level, surface, state, RandomSource.create(state.getSeed(surface)), parts);
                if (parts.isEmpty()) {
                    continue;
                }

                List<BlockTintSource> tintSources = blockColors.getTintSources(state);
                int[] tints = new int[tintSources.size()];
                for (int i = 0; i < tintSources.size(); i++) {
                    tints[i] = tintSources.get(i).colorInWorld(state, level, surface);
                }

                boolean hasTranslucency = model.hasMaterialFlag(level, surface, state, 1);
                // This submit path applies one light value to every quad. Sampling inside the
                // opaque surface block commonly returns zero sky light; renderBatched previously
                // obtained adjacent face light automatically. Use the exposed block above.
                BlockPos exposedLightPos = surface.above();
                int packedLight = net.minecraft.util.LightCoordsUtil.pack(
                        level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, exposedLightPos),
                        level.getBrightness(net.minecraft.world.level.LightLayer.SKY, exposedLightPos));
                float groupedDistance = groupedRadialDistance((float) distance);
                float delayTicks = distance < 0.55 ? 0.0F : groupedDistance / payload.propagationSpeed();
                samples.add(new SurfaceSample(surface, List.copyOf(parts), tints, hasTranslucency,
                        packedLight, distance, delayTicks));
            }
        }
        return samples;
    }

    private static float scaledMaxLift(float authoredLift) {
        return Math.min(MAX_VISUAL_LIFT,
                authoredLift * (MAX_VISUAL_LIFT / REFERENCE_KING_VISUAL_LIFT));
    }

    /** Quick smooth rise, a very short rounded crest, then a firm but smooth settle. */
    private static float impactLift(float progress) {
        if (progress < RISE_FRACTION) {
            return smoothStep(progress / RISE_FRACTION);
        }

        float crestEnd = RISE_FRACTION + CREST_DURATION_FRACTION;
        if (progress < crestEnd) {
            float crestProgress = (progress - RISE_FRACTION) / CREST_DURATION_FRACTION;
            return Mth.lerp(smoothStep(crestProgress), 1.0F, 0.97F);
        }

        float settleProgress = Mth.clamp((progress - crestEnd) / SETTLE_DURATION_FRACTION, 0.0F, 1.0F);
        // Feeding a fractional-power clock into smoothstep makes the earth leave the crest
        // decisively, then ease into rest without a visible snap at high frame rates.
        float firmSettle = smoothStep((float) Math.pow(settleProgress, 0.65));
        return 0.97F * (1.0F - firmSettle);
    }

    private static float smoothStep(float value) {
        float clamped = Mth.clamp(value, 0.0F, 1.0F);
        return clamped * clamped * (3.0F - 2.0F * clamped);
    }

    private static float groupedRadialDistance(float distance) {
        float bandCenter = Math.round(distance / RADIAL_DELAY_GROUP_SIZE_BLOCKS)
                * RADIAL_DELAY_GROUP_SIZE_BLOCKS;
        return Mth.lerp(RADIAL_DELAY_GROUPING_STRENGTH, distance, bandCenter);
    }

    private static BlockPos findSurface(ClientLevel level, BlockPos.MutableBlockPos cursor, int x, int z, double y) {
        cursor.set(x, Mth.floor(y + 2.0), z);
        for (int i = 0; i < 12; i++) {
            BlockState state = level.getBlockState(cursor);
            if (!state.isAir() && level.getBlockState(cursor.above()).isAir()) {
                return cursor.immutable();
            }
            cursor.move(0, -1, 0);
        }
        return null;
    }

    private record SurfaceSample(BlockPos pos, List<BlockStateModelPart> parts, int[] tints,
                                 boolean hasTranslucency, int packedLight,
                                 double distanceFromCenter, float delayTicks) {
    }

    private record ActiveRipple(ClientLevel level, GroundRipplePayload payload, long startGameTick,
                                List<SurfaceSample> samples) {
        private Vec3 center() {
            return new Vec3(payload.x(), payload.y(), payload.z());
        }

        private float totalDuration() {
            return payload.radius() / payload.propagationSpeed() + payload.blockDurationTicks() + 1.0F;
        }
    }
}
