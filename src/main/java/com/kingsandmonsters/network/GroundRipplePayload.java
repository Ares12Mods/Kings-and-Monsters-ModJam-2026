package com.kingsandmonsters.network;

import com.kingsandmonsters.KingsAndMonsters;
import com.kingsandmonsters.client.GroundRippleRenderer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Starts one render-only, terrain-conforming ground wave on nearby clients. */
public record GroundRipplePayload(double x, double y, double z, float radius, float propagationSpeed,
                                  int blockDurationTicks, float maxLift)
        implements CustomPacketPayload {
    public static final Type<GroundRipplePayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(KingsAndMonsters.MODID, "ground_ripple"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GroundRipplePayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeDouble(payload.x());
                buffer.writeDouble(payload.y());
                buffer.writeDouble(payload.z());
                buffer.writeFloat(payload.radius());
                buffer.writeFloat(payload.propagationSpeed());
                buffer.writeVarInt(payload.blockDurationTicks());
                buffer.writeFloat(payload.maxLift());
            },
            buffer -> new GroundRipplePayload(
                    buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readFloat(),
                    buffer.readFloat(), buffer.readVarInt(), buffer.readFloat()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(GroundRipplePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (FMLEnvironment.getDist() == Dist.CLIENT) {
                GroundRippleRenderer.start(payload);
            }
        });
    }
}
