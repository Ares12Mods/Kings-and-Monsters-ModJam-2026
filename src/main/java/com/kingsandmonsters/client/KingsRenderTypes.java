package com.kingsandmonsters.client;

import com.kingsandmonsters.KingsAndMonsters;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;

import java.util.function.Function;

/**
 * Entity render types that retain lightmap-based world lighting without the
 * directional per-face multiplier added to Minecraft's 26.1 entity pipelines.
 */
public final class KingsRenderTypes {
    private static final RenderPipeline ENTITY_CUTOUT = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(KingsAndMonsters.MODID, "pipeline/entity_cutout"))
            .withShaderDefine("ALPHA_CUTOUT", 0.1F)
            .withSampler("Sampler1")
            .withCull(false)
            .build();
    private static final RenderPipeline ENTITY_TRANSLUCENT = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(KingsAndMonsters.MODID, "pipeline/entity_translucent"))
            .withShaderDefine("ALPHA_CUTOUT", 0.1F)
            .withSampler("Sampler1")
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withCull(false)
            .build();

    private static final Function<Identifier, RenderType> CUTOUT = Util.memoize(texture -> RenderType.create(
            "kingsandmonsters_entity_cutout",
            RenderSetup.builder(ENTITY_CUTOUT)
                    .withTexture("Sampler0", texture)
                    .useLightmap()
                    .useOverlay()
                    .affectsCrumbling()
                    .setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE)
                    .createRenderSetup()));
    private static final Function<Identifier, RenderType> TRANSLUCENT = Util.memoize(texture -> RenderType.create(
            "kingsandmonsters_entity_translucent",
            RenderSetup.builder(ENTITY_TRANSLUCENT)
                    .withTexture("Sampler0", texture)
                    .useLightmap()
                    .useOverlay()
                    .affectsCrumbling()
                    .sortOnUpload()
                    .setOutline(RenderSetup.OutlineProperty.NONE)
                    .createRenderSetup()));

    private KingsRenderTypes() {
    }

    public static void registerPipelines(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(ENTITY_CUTOUT);
        event.registerPipeline(ENTITY_TRANSLUCENT);
    }

    public static RenderType entityCutout(Identifier texture) {
        return CUTOUT.apply(texture);
    }

    public static RenderType entityTranslucent(Identifier texture) {
        return TRANSLUCENT.apply(texture);
    }
}
