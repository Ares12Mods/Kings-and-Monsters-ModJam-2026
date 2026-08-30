package com.kingsandmonsters.network;

import com.kingsandmonsters.KingsAndMonsters;
import com.kingsandmonsters.client.OgrebloodTotemActivation;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record OgrebloodTotemActivationPayload() implements CustomPacketPayload {
    public static final Type<OgrebloodTotemActivationPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(KingsAndMonsters.MODID, "ogreblood_totem_activation"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OgrebloodTotemActivationPayload> STREAM_CODEC =
            StreamCodec.of((buffer, payload) -> { }, buffer -> new OgrebloodTotemActivationPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OgrebloodTotemActivationPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (FMLEnvironment.getDist() == Dist.CLIENT) {
                OgrebloodTotemActivation.show();
            }
        });
    }
}
