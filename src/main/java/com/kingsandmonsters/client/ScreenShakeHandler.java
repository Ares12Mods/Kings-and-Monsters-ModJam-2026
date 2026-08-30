package com.kingsandmonsters.client;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

public final class ScreenShakeHandler {
    private static final int DEFAULT_DURATION_TICKS = 12;
    private static final double MAX_DISTANCE = 32.0;

    private static int ticksRemaining;
    private static int durationTicks;
    private static float intensity;
    private static float frequencyMultiplier = 1.0F;

    private ScreenShakeHandler() {
    }

    public static void start(double x, double y, double z, float baseIntensity) {
        start(x, y, z, baseIntensity, DEFAULT_DURATION_TICKS, 1.0F);
    }

    public static void start(double x, double y, double z, float baseIntensity, int requestedDurationTicks,
                             float requestedFrequencyMultiplier) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        double distance = Math.sqrt(minecraft.player.distanceToSqr(x, y, z));
        float distanceScale = Mth.clamp(1.0F - (float) (distance / MAX_DISTANCE), 0.0F, 1.0F);
        float scaledIntensity = baseIntensity * distanceScale;
        if (scaledIntensity <= 0.02F) {
            return;
        }

        int clampedDuration = Math.max(1, requestedDurationTicks);
        ticksRemaining = Math.max(ticksRemaining, clampedDuration);
        durationTicks = Math.max(durationTicks, clampedDuration);
        intensity = Math.max(intensity, scaledIntensity);
        frequencyMultiplier = Math.max(frequencyMultiplier, requestedFrequencyMultiplier);
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        if (ticksRemaining > 0) {
            ticksRemaining--;
            if (ticksRemaining == 0) {
                intensity = 0.0F;
                durationTicks = 0;
                frequencyMultiplier = 1.0F;
            }
        }
    }

    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (ticksRemaining <= 0 || intensity <= 0.0F || durationTicks <= 0) {
            return;
        }

        float age = durationTicks - ticksRemaining + (float) event.getPartialTick();
        float fade = Mth.square(ticksRemaining / (float) durationTicks);
        float wave = Mth.sin(age * 2.8F * frequencyMultiplier);
        float counterWave = Mth.cos(age * 4.1F * frequencyMultiplier);
        float strength = intensity * fade;

        event.setPitch(event.getPitch() + wave * 1.15F * strength);
        event.setYaw(event.getYaw() + counterWave * 0.85F * strength);
        event.setRoll(event.getRoll() + Mth.sin(age * 5.2F * frequencyMultiplier) * 1.8F * strength);
    }
}
