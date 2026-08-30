package com.kingsandmonsters.client;

import com.kingsandmonsters.network.OgreOverlayPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayDeque;
import java.util.Queue;

/** Runtime state and renderer for temporary Kings & Monsters event text. */
public final class OgreOverlayState {
    private static OgreOverlayPayload active;
    private static int age;
    private static final Queue<OgreOverlayPayload> queued = new ArrayDeque<>();

    private OgreOverlayState() {}

    public static void show(OgreOverlayPayload payload) {
        if (active == null) {
            active = payload;
            age = 0;
        } else if (queued.size() < 4) {
            queued.offer(payload);
        }
    }

    public static void tick() {
        if (Minecraft.getInstance().isPaused()) {
            return;
        }
        if (active == null) {
            active = queued.poll();
            age = 0;
            return;
        }
        age++;
        if (age >= totalDuration(active)) {
            // End at zero opacity and leave one clean frame before advancing the
            // queue. This prevents the outgoing notification from sharing its
            // removal frame with a newly reset notification.
            active = null;
            age = 0;
        }
    }

    public static void render(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker deltaTracker) {
        if (active == null || Minecraft.getInstance().options.hideGui) {
            return;
        }

        float partialAge = Math.min(
                age + deltaTracker.getGameTimeDeltaPartialTick(false),
                totalDuration(active));
        float alpha = Mth.clamp(alpha(active, partialAge), 0.0F, 1.0F);
        int packedAlpha = Math.clamp(Math.round(alpha * 255.0F), 0, 255);
        // Minecraft's font renderer treats colors whose top six alpha bits are
        // all zero as legacy RGB and silently makes them fully opaque. Never
        // submit those final 0-3 alpha values or the fade flashes at its end.
        if (packedAlpha < 4) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        int centerY = active.emphasis() == OgreOverlayPayload.Emphasis.MAJOR_EVENT
                ? Math.max(62, Math.round(screenHeight * 0.31F))
                : Math.max(54, Math.round(screenHeight * 0.25F));
        int color = (packedAlpha << 24)
                | (active.emphasis() == OgreOverlayPayload.Emphasis.MAJOR_EVENT ? 0xD5A84B : 0xC8B889);

        Component title = styled(active.title());
        drawCenteredScaled(graphics, minecraft.font, title, screenWidth / 2, centerY,
                active.titleScale(), color);
        if (!active.subtitle().isBlank()) {
            Component subtitle = styled(active.subtitle());
            int subtitleY = centerY + Math.round(14 * active.titleScale());
            drawCenteredScaled(graphics, minecraft.font, subtitle, screenWidth / 2, subtitleY,
                    active.subtitleScale(), color);
        }
    }

    private static Component styled(String text) {
        // Intentionally use Minecraft's default font until a purpose-built,
        // pixel-readable Kings & Monsters typeface is available.
        return Component.literal(text);
    }

    private static void drawCenteredScaled(
            GuiGraphicsExtractor graphics, Font font, Component text, int centerX, int y, float scale, int color) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(centerX, y);
        graphics.pose().scale(scale, scale);
        graphics.text(font, text, -font.width(text) / 2, 0, color, true);
        graphics.pose().popMatrix();
    }

    private static float alpha(OgreOverlayPayload payload, float currentAge) {
        currentAge = Mth.clamp(currentAge, 0.0F, totalDuration(payload));
        if (currentAge < payload.fadeInTicks()) {
            return payload.fadeInTicks() == 0 ? 1.0F : currentAge / payload.fadeInTicks();
        }
        int fadeOutStart = payload.fadeInTicks() + payload.holdTicks();
        if (currentAge < fadeOutStart) {
            return 1.0F;
        }
        return payload.fadeOutTicks() == 0
                ? 0.0F
                : 1.0F - ((currentAge - fadeOutStart) / payload.fadeOutTicks());
    }

    private static int totalDuration(OgreOverlayPayload payload) {
        return payload.fadeInTicks() + payload.holdTicks() + payload.fadeOutTicks();
    }
}
