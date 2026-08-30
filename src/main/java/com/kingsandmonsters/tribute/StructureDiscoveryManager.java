package com.kingsandmonsters.tribute;

import com.kingsandmonsters.network.OgreOverlayPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Cheap proximity checks against structure origins already recorded by world generation. */
public final class StructureDiscoveryManager {
    private static final int CHECK_INTERVAL_TICKS = 20;
    private static final int OUTPOST_DISCOVERY_RADIUS = 72;
    private static final int FORT_DISCOVERY_RADIUS = 112;
    private static int ticksUntilCheck;

    private StructureDiscoveryManager() {}

    public static void reset() {
        ticksUntilCheck = 0;
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        if (--ticksUntilCheck > 0) {
            return;
        }
        ticksUntilCheck = CHECK_INTERVAL_TICKS;
        checkPlayers(event.getServer());
    }

    private static void checkPlayers(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            var sites = TributeManager.getDiscoverySites(level);
            if (sites.isEmpty()) {
                continue;
            }
            for (ServerPlayer player : level.players()) {
                if (player.isSpectator()) {
                    continue;
                }
                for (TributeSavedData.DiscoverySite site : sites) {
                    int radius = site.kind() == TributeSavedData.StructureKind.FORT
                            ? FORT_DISCOVERY_RADIUS : OUTPOST_DISCOVERY_RADIUS;
                    double dx = player.getX() - site.origin().getX();
                    double dz = player.getZ() - site.origin().getZ();
                    if (dx * dx + dz * dz > (double) radius * radius) {
                        continue;
                    }
                    if (!TributeManager.markStructureDiscovered(level, player.getUUID(), site.origin())) {
                        continue;
                    }
                    boolean fort = site.kind() == TributeSavedData.StructureKind.FORT;
                    PacketDistributor.sendToPlayer(player, OgreOverlayPayload.location(
                            fort ? "OGRE FORT" : "OGRE OUTPOST",
                            fort ? "Seat of the Ogre King" : "Territory of the Ogre Tribe",
                            fort));
                    // Queue at most one new discovery per check so nearby sites cannot
                    // replace one another's overlay in the same client tick.
                    break;
                }
            }
        }
    }
}
