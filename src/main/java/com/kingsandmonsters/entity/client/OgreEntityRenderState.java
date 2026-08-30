package com.kingsandmonsters.entity.client;

import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.renderer.base.GeoRenderState;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

import java.util.Map;

/** Render-state bridge required by Minecraft 26.1's extraction-based entity renderer. */
public final class OgreEntityRenderState extends LivingEntityRenderState implements GeoRenderState {
    public enum ClubRenderState {
        BACK,
        HAND
    }

    public boolean kingDyingAnimation;
    public ClubRenderState clubRenderState = ClubRenderState.BACK;
    public float proceduralHeadYaw;
    public float proceduralHeadPitch;
    public float proceduralHeadTrackingWeight;
    public float maximumDownwardHeadPitch = 12.0F;
    public boolean clampCombatHeadRotation;
    private final Map<DataTicket<?>, Object> geckolibData = new Reference2ObjectOpenHashMap<>();

    @Override
    public <D> void addGeckolibData(DataTicket<D> ticket, D data) {
        geckolibData.put(ticket, data);
    }

    @Override
    public Map<DataTicket<?>, Object> getDataMap() {
        return geckolibData;
    }
}
