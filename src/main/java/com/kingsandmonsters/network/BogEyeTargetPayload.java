package com.kingsandmonsters.network;

import com.kingsandmonsters.KingsAndMonsters;
import com.kingsandmonsters.client.BogEyeGuidance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Sent once to the using player only, when a Bog Eye Charm successfully locates a chest. */
public record BogEyeTargetPayload(BlockPos target) implements CustomPacketPayload {
    public static final Type<BogEyeTargetPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(KingsAndMonsters.MODID, "bog_eye_target"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BogEyeTargetPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, payload -> payload.target().getX(),
            ByteBufCodecs.VAR_INT, payload -> payload.target().getY(),
            ByteBufCodecs.VAR_INT, payload -> payload.target().getZ(),
            (x, y, z) -> new BogEyeTargetPayload(new BlockPos(x, y, z)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BogEyeTargetPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (FMLEnvironment.getDist() == Dist.CLIENT) {
                BogEyeGuidance.start(payload.target());
            }
        });
    }
}
