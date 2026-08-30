package com.kingsandmonsters.network;

import com.kingsandmonsters.KingsAndMonsters;
import com.kingsandmonsters.client.AngerHudState;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Sent server->client with the anger level of the nearest tribute camp to the player.
 * angerLevel of -1 means no camp is currently in range — the client should hide the HUD.
 */
public record AngerHudPayload(int angerLevel, int maxAngerLevel, boolean chiefAlive) implements CustomPacketPayload {
    public static final Type<AngerHudPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(KingsAndMonsters.MODID, "anger_hud"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AngerHudPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT,
            AngerHudPayload::angerLevel,
            ByteBufCodecs.INT,
            AngerHudPayload::maxAngerLevel,
            ByteBufCodecs.BOOL,
            AngerHudPayload::chiefAlive,
            AngerHudPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AngerHudPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (FMLEnvironment.getDist() == Dist.CLIENT) {
                AngerHudState.update(payload.angerLevel(), payload.maxAngerLevel(), payload.chiefAlive());
            }
        });
    }
}
