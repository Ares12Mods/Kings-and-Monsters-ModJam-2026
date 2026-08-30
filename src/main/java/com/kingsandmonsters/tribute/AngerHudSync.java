package com.kingsandmonsters.tribute;

import com.kingsandmonsters.Config;
import com.kingsandmonsters.network.AngerHudPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class AngerHudSync {
    private static final int SYNC_INTERVAL_TICKS = 10;

    private static int ticksUntilNextSync;
    private static final Map<ServerPlayer, AngerHudPayload> lastSentByPlayer = new HashMap<>();

    private AngerHudSync() {}

    public static void reset() {
        ticksUntilNextSync = 0;
        lastSentByPlayer.clear();
    }

    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            lastSentByPlayer.remove(player);
        }
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        ticksUntilNextSync--;
        if (ticksUntilNextSync > 0) {
            return;
        }

        ticksUntilNextSync = SYNC_INTERVAL_TICKS;
        syncAll(event.getServer());
    }

    private static void syncAll(MinecraftServer server) {
        int radius = Config.ANGER_HUD_RADIUS.get();

        for (ServerLevel level : server.getAllLevels()) {
            for (ServerPlayer player : level.players()) {
                AngerHudPayload payload = computePayload(level, player, radius);
                AngerHudPayload lastSent = lastSentByPlayer.get(player);
                if (!payload.equals(lastSent)) {
                    PacketDistributor.sendToPlayer(player, payload);
                    lastSentByPlayer.put(player, payload);
                }
            }
        }
    }

    private static AngerHudPayload computePayload(ServerLevel level, ServerPlayer player, int radius) {
        Optional<BlockPos> nearestCamp = TributeManager.findNearestCamp(level, player.blockPosition(), radius);
        boolean nearbyChiefAlive = nearestCamp
                .flatMap(camp -> TributeManager.get(level, camp))
                .map(data -> data.isChiefAlive())
                .orElse(true);
        return new AngerHudPayload(
                TributeManager.getFactionAngerLevel(level),
                Config.TRIBUTE_MAX_ANGER.get(),
                nearbyChiefAlive);
    }
}
