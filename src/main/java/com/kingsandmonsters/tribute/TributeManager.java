package com.kingsandmonsters.tribute;

import com.kingsandmonsters.Config;
import com.kingsandmonsters.api.KingsAndMonstersAPI;
import com.kingsandmonsters.api.event.ChiefDeathEvent;
import com.kingsandmonsters.api.event.PatrolSpawnEvent;
import com.kingsandmonsters.api.event.TributeChestLootedEvent;
import com.kingsandmonsters.api.patrol.PatrolTier;
import com.kingsandmonsters.api.tribute.ITributeData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

public final class TributeManager {
    @Nullable
    private static MinecraftServer server;

    private TributeManager() {}

    public static void attach(MinecraftServer activeServer) {
        server = activeServer;
        KingsAndMonstersAPI.setTributeDataProvider((dimension, campOrigin) -> {
            ServerLevel level = activeServer.getLevel(dimension);
            return level == null ? null : getData(level).get(campOrigin);
        });
    }

    public static void detach() {
        server = null;
        KingsAndMonstersAPI.setTributeDataProvider(null);
    }

    public static ITributeData getOrCreate(ServerLevel level, BlockPos campOrigin) {
        return getData(level).getOrCreate(campOrigin);
    }

    public static Optional<ITributeData> get(Level level, BlockPos campOrigin) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return Optional.empty();
        }

        return Optional.ofNullable(getData(serverLevel).get(campOrigin));
    }

    public static boolean recordTributeChestLooted(Player player, BlockPos chestPos, BlockPos campOrigin) {
        return recordTributeChestLooted(player, chestPos, campOrigin, Config.TRIBUTE_DEFAULT_ANGER_DELTA.get());
    }

    public static boolean recordTributeChestLooted(Player player, BlockPos chestPos, BlockPos campOrigin, int angerDelta) {
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }

        TributeChestLootedEvent event = new TributeChestLootedEvent(player, chestPos, level.dimension(), angerDelta);
        NeoForge.EVENT_BUS.post(event);

        if (event.isCanceled()) {
            return false;
        }

        getOrCreate(level, campOrigin).incrementAnger(event.getAngerDelta());
        return true;
    }

    public static void markChiefDead(LivingEntity chief, BlockPos campOrigin) {
        if (!(chief.level() instanceof ServerLevel level)) {
            return;
        }

        getOrCreate(level, campOrigin).setChiefAlive(false);
        NeoForge.EVENT_BUS.post(new ChiefDeathEvent(chief, campOrigin, level.dimension()));
    }

    public static Optional<PatrolTier> preparePatrol(ServerLevel level, BlockPos campOrigin) {
        ITributeData tributeData = getOrCreate(level, campOrigin);
        PatrolSpawnEvent event = new PatrolSpawnEvent(campOrigin, level.dimension(), tributeData.getCurrentPatrolTier());
        NeoForge.EVENT_BUS.post(event);

        if (event.isCanceled()) {
            return Optional.empty();
        }

        return Optional.of(event.getTier());
    }

    public static List<TributeSavedData.CampPatrolState> getPatrolStates(ServerLevel level) {
        return getData(level).getPatrolStates();
    }

    public static void registerOutpost(
            ServerLevel level,
            BlockPos campOrigin,
            OutpostCaptainType captainType,
            BoundingBox structureBounds) {
        getData(level).registerOutpost(campOrigin, captainType, structureBounds);
    }

    public static void registerFort(ServerLevel level, BlockPos fortOrigin) {
        getData(level).registerFort(fortOrigin);
    }

    public static void setOutpostFortMapTarget(ServerLevel level, BlockPos campOrigin, BlockPos fortTarget) {
        getData(level).setOutpostFortMapTarget(campOrigin, fortTarget);
    }

    public static List<TributeSavedData.DiscoverySite> getDiscoverySites(ServerLevel level) {
        return getData(level).getDiscoverySites();
    }

    public static boolean markStructureDiscovered(ServerLevel level, java.util.UUID playerId, BlockPos origin) {
        return getData(level).markDiscovered(playerId, origin);
    }

    public static List<TributeSavedData.OutpostPopulationState> getOutpostPopulationStates(ServerLevel level) {
        return getData(level).getOutpostPopulationStates();
    }

    public static void markPopulationInitialized(ServerLevel level, BlockPos campOrigin, long nextSpawnTime) {
        getData(level).markPopulationInitialized(campOrigin, nextSpawnTime);
    }

    public static void setNextResidentSpawnGameTime(ServerLevel level, BlockPos campOrigin, long gameTime) {
        getData(level).setNextResidentSpawnGameTime(campOrigin, gameTime);
    }

    public static void markOutpostCaptainKilled(ServerLevel level, BlockPos campOrigin, long respawnGameTime) {
        getData(level).markOutpostCaptainKilled(campOrigin, respawnGameTime);
    }

    public static void markOutpostCaptainAlive(ServerLevel level, BlockPos campOrigin) {
        getData(level).markOutpostCaptainAlive(campOrigin);
    }

    public static int getFactionAngerLevel(ServerLevel level) {
        return getData(level).getFactionAngerLevel();
    }

    public static void decayFactionAnger(ServerLevel level, int amount) {
        List<TributeSavedData.CampPatrolState> camps = getPatrolStates(level);
        if (camps.isEmpty() || amount <= 0) {
            return;
        }

        getData(level).incrementFactionAnger(-amount, camps.getFirst().campOrigin());
    }

    public static void setNextPatrolGameTime(ServerLevel level, BlockPos campOrigin, long gameTime) {
        getData(level).setNextPatrolGameTime(campOrigin, gameTime);
    }

    /** Arms (or refreshes) a player's single pending retaliation, eligible starting at gameTime. */
    public static void armPatrolRetaliation(ServerLevel level, UUID playerId, BlockPos campOrigin, long eligibleGameTime) {
        getData(level).armPatrolRetaliation(playerId, campOrigin, eligibleGameTime);
    }

    public static List<TributeSavedData.DuePatrolRetaliation> peekDuePatrolRetaliations(ServerLevel level, long gameTime) {
        return getData(level).peekDuePatrolRetaliations(gameTime);
    }

    public static void clearPatrolRetaliation(ServerLevel level, UUID playerId) {
        getData(level).clearPatrolRetaliation(playerId);
    }

    public static Optional<BlockPos> findNearestCamp(ServerLevel level, BlockPos pos, int maxRadius) {
        return getData(level).findNearestCamp(pos, maxRadius);
    }

    public static Optional<BlockPos> findNearestFort(ServerLevel level, BlockPos pos) {
        return getData(level).findNearestFort(pos);
    }

    /** Every Fort that has already, successfully generated. */
    public static List<BlockPos> getAllConfirmedForts(ServerLevel level) {
        return getData(level).getAllConfirmedForts();
    }

    public static Optional<BlockPos> findOutpostFortMapTarget(ServerLevel level, BlockPos pos) {
        return getData(level).findOutpostFortMapTarget(pos);
    }

    public static Optional<BlockPos> findOutpostCampOrigin(ServerLevel level, BlockPos pos) {
        return getData(level).findOutpostCampOrigin(pos);
    }

    public static void setOutpostMapChestPosIfAbsent(ServerLevel level, BlockPos campOrigin, BlockPos chestPos) {
        getData(level).setOutpostMapChestPosIfAbsent(campOrigin, chestPos);
    }

    public static Optional<BlockPos> getOutpostMapChestPos(ServerLevel level, BlockPos campOrigin) {
        return getData(level).getOutpostMapChestPos(campOrigin);
    }

    public static Optional<BlockPos> getOutpostFortMapTarget(ServerLevel level, BlockPos campOrigin) {
        return getData(level).getOutpostFortMapTarget(campOrigin);
    }

    public static boolean isMapRewardFulfilled(ServerLevel level, BlockPos campOrigin) {
        return getData(level).isMapRewardFulfilled(campOrigin);
    }

    public static boolean markMapRewardFulfilled(ServerLevel level, BlockPos campOrigin) {
        return getData(level).markMapRewardFulfilled(campOrigin);
    }

    public static boolean isMapRewardFulfilledNear(ServerLevel level, BlockPos pos) {
        return getData(level).isMapRewardFulfilledNear(pos);
    }

    public static boolean markMapRewardFulfilledNear(ServerLevel level, BlockPos pos) {
        return getData(level).markMapRewardFulfilledNear(pos);
    }

    public static Optional<ServerLevel> getLevel(net.minecraft.resources.ResourceKey<Level> dimension) {
        return server == null ? Optional.empty() : Optional.ofNullable(server.getLevel(dimension));
    }

    private static TributeSavedData getData(ServerLevel level) {
        net.minecraft.world.level.saveddata.SavedDataType<TributeSavedData> type =
                new net.minecraft.world.level.saveddata.SavedDataType<>(
                        net.minecraft.resources.Identifier.fromNamespaceAndPath(
                                com.kingsandmonsters.KingsAndMonsters.MODID, "tribute"),
                        ignored -> new TributeSavedData(level.dimension()),
                        ignored -> net.minecraft.nbt.CompoundTag.CODEC.xmap(
                                tag -> TributeSavedData.load(level.dimension(), tag, level.registryAccess()),
                                data -> data.save(new net.minecraft.nbt.CompoundTag(), level.registryAccess())),
                        null);
        return level.getDataStorage().computeIfAbsent(type);
    }
}
