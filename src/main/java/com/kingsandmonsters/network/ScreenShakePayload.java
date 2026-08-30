package com.kingsandmonsters.network;

import com.kingsandmonsters.KingsAndMonsters;
import com.kingsandmonsters.client.ScreenShakeHandler;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ScreenShakePayload(double x, double y, double z, float intensity, int durationTicks,
                                 float frequencyMultiplier) implements CustomPacketPayload {
    public static final Type<ScreenShakePayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(KingsAndMonsters.MODID, "screen_shake"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ScreenShakePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE,
            ScreenShakePayload::x,
            ByteBufCodecs.DOUBLE,
            ScreenShakePayload::y,
            ByteBufCodecs.DOUBLE,
            ScreenShakePayload::z,
            ByteBufCodecs.FLOAT,
            ScreenShakePayload::intensity,
            ByteBufCodecs.VAR_INT,
            ScreenShakePayload::durationTicks,
            ByteBufCodecs.FLOAT,
            ScreenShakePayload::frequencyMultiplier,
            ScreenShakePayload::new);

    public ScreenShakePayload(double x, double y, double z, float intensity) {
        this(x, y, z, intensity, 12, 1.0F);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ScreenShakePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (FMLEnvironment.getDist() == Dist.CLIENT) {
                ScreenShakeHandler.start(payload.x(), payload.y(), payload.z(), payload.intensity(),
                        payload.durationTicks(), payload.frequencyMultiplier());
            }
        });
    }
}
