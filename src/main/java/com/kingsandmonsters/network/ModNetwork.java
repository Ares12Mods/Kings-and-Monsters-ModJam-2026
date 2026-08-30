package com.kingsandmonsters.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetwork {
    private ModNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(ScreenShakePayload.TYPE, ScreenShakePayload.STREAM_CODEC, ScreenShakePayload::handle);
        registrar.playToClient(GroundRipplePayload.TYPE, GroundRipplePayload.STREAM_CODEC, GroundRipplePayload::handle);
        registrar.playToClient(AngerHudPayload.TYPE, AngerHudPayload.STREAM_CODEC, AngerHudPayload::handle);
        registrar.playToClient(OgreOverlayPayload.TYPE, OgreOverlayPayload.STREAM_CODEC, OgreOverlayPayload::handle);
        registrar.playToClient(OgrebloodTotemActivationPayload.TYPE,
                OgrebloodTotemActivationPayload.STREAM_CODEC, OgrebloodTotemActivationPayload::handle);
        registrar.playToClient(BogEyeTargetPayload.TYPE, BogEyeTargetPayload.STREAM_CODEC, BogEyeTargetPayload::handle);
        registrar.playToServer(OpenBackpackPayload.TYPE, OpenBackpackPayload.STREAM_CODEC, OpenBackpackPayload::handle);
    }
}
