package com.kingsandmonsters.world;

import com.kingsandmonsters.KingsAndMonsters;
import com.kingsandmonsters.ModEntities;
import com.kingsandmonsters.entity.OgreMerchant;
import com.kingsandmonsters.network.OgreOverlayPayload;
import com.kingsandmonsters.tribute.TributeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Optional;

/** Lightweight, Wandering-Trader-style scheduler for the complete merchant encounter. */
public final class OgreMerchantSpawnManager {
    public static final int ATTEMPT_INTERVAL = 24_000;
    public static final int SUCCESS_COOLDOWN = 48_000;
    public static final int NATURAL_LIFETIME = 72_000;
    public static final float BASE_SUCCESS_CHANCE = 0.35F;
    public static final float OGRE_TERRITORY_SUCCESS_CHANCE = 0.50F;
    public static final int MIN_PLAYER_DISTANCE = 40;
    public static final int MAX_PLAYER_DISTANCE = 100;
    public static final int NEARBY_MERCHANT_RADIUS = 1_250;
    public static final int PREFERRED_STRUCTURE_MIN_DISTANCE = 500;
    public static final int PREFERRED_STRUCTURE_MAX_DISTANCE = 1_500;
    private static final int CANDIDATE_ATTEMPTS = 12;
    private static final int REQUIRED_LOADED_RADIUS = 5;
    private static final double ARRIVAL_MESSAGE_RADIUS = 128.0;
    private static final TagKey<Biome> SPAWN_BIOMES = TagKey.create(Registries.BIOME,
            Identifier.fromNamespaceAndPath(KingsAndMonsters.MODID, "ogre_merchant_spawns"));
    private static int tickDelay = 20;

    private OgreMerchantSpawnManager() {}

    public static void onServerTick(ServerTickEvent.Post event) {
        if (--tickDelay > 0) return;
        tickDelay = 20;
        ServerLevel level = event.getServer().overworld();
        OgreMerchantSpawnData data = getData(level);
        long now = level.getGameTime();
        if (data.nextAttemptGameTime() == 0) {
            data.schedule(now + ATTEMPT_INTERVAL);
            return;
        }
        if (now < data.nextAttemptGameTime()) return;
        boolean spawned = attemptSpawn(level);
        data.schedule(now + (spawned ? SUCCESS_COOLDOWN : ATTEMPT_INTERVAL));
    }

    private static boolean attemptSpawn(ServerLevel level) {
        List<ServerPlayer> players = level.getPlayers(player -> !player.isSpectator() && !player.isCreative());
        if (players.isEmpty()) return false;
        ServerPlayer player = players.get(level.getRandom().nextInt(players.size()));
        if (hasNearbyNaturalMerchant(level, player.blockPosition())) return false;

        float chance = BASE_SUCCESS_CHANCE;
        Optional<BlockPos> camp = TributeManager.findNearestCamp(level, player.blockPosition(), PREFERRED_STRUCTURE_MAX_DISTANCE);
        if (camp.isPresent() && horizontalDistance(player.blockPosition(), camp.get()) >= PREFERRED_STRUCTURE_MIN_DISTANCE) {
            chance = OGRE_TERRITORY_SUCCESS_CHANCE;
        }
        if (level.getRandom().nextFloat() >= chance) return false;

        Optional<BlockPos> position = findSpawnPosition(level, player.blockPosition());
        return position.isPresent() && spawnEncounter(level, position.get()) != null;
    }

    public static OgreMerchant spawnEncounter(ServerLevel level, BlockPos position) {
        OgreMerchant merchant = ModEntities.OGRE_MERCHANT.get().create(level, EntitySpawnReason.EVENT);
        if (merchant == null) return null;
        merchant.snapTo(position.getX() + .5, position.getY(), position.getZ() + .5,
                level.getRandom().nextFloat() * 360, 0);
        merchant.finalizeSpawn(level, level.getCurrentDifficultyAt(position), EntitySpawnReason.NATURAL, null);
        if (!level.addFreshEntity(merchant)) {
            return null;
        }

        double messageRadiusSq = ARRIVAL_MESSAGE_RADIUS * ARRIVAL_MESSAGE_RADIUS;
        for (ServerPlayer player : level.getPlayers(candidate -> !candidate.isSpectator()
                && candidate.distanceToSqr(merchant) <= messageRadiusSq)) {
            PacketDistributor.sendToPlayer(player, OgreOverlayPayload.major(
                    "You have been visited by the Ogre Merchant.",
                    ""));
        }
        return merchant;
    }

    private static Optional<BlockPos> findSpawnPosition(ServerLevel level, BlockPos playerPos) {
        RandomSource random = level.getRandom();
        for (int attempt = 0; attempt < CANDIDATE_ATTEMPTS; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            int distance = MIN_PLAYER_DISTANCE + random.nextInt(MAX_PLAYER_DISTANCE - MIN_PLAYER_DISTANCE + 1);
            int x = playerPos.getX() + (int)Math.round(Math.cos(angle) * distance);
            int z = playerPos.getZ() + (int)Math.round(Math.sin(angle) * distance);
            if (!level.hasChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z))) continue;
            BlockPos pos = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            if (isValidGroupPosition(level, pos)) return Optional.of(pos);
        }
        return Optional.empty();
    }

    private static boolean isValidGroupPosition(ServerLevel level, BlockPos center) {
        if (!hasLoadedArea(level, center) || !level.canSeeSky(center) || level.isCloseToVillage(center, 2)
                || !level.getBiome(center).is(SPAWN_BIOMES)) return false;
        BlockPos[] positions = { center, center.offset(2, 0, 1), center.offset(-2, 0, -1) };
        for (int i = 0; i < positions.length; i++) {
            BlockPos pos = positions[i];
            var type = i == 0 ? ModEntities.OGRE_MERCHANT.get() : ModEntities.OGRE_GRUNT.get();
            if (!level.canSeeSky(pos) || !level.getFluidState(pos).isEmpty()
                    || !level.getBlockState(pos.below()).is(BlockTags.VALID_SPAWN)
                    || !NaturalSpawner.isValidEmptySpawnBlock(level, pos, level.getBlockState(pos),
                    level.getFluidState(pos), type)
                    || !level.noCollision(type.getSpawnAABB(pos.getX() + .5, pos.getY(), pos.getZ() + .5))) return false;
        }
        return true;
    }

    private static boolean hasLoadedArea(ServerLevel level, BlockPos pos) {
        int minX = SectionPos.blockToSectionCoord(pos.getX() - REQUIRED_LOADED_RADIUS);
        int maxX = SectionPos.blockToSectionCoord(pos.getX() + REQUIRED_LOADED_RADIUS);
        int minZ = SectionPos.blockToSectionCoord(pos.getZ() - REQUIRED_LOADED_RADIUS);
        int maxZ = SectionPos.blockToSectionCoord(pos.getZ() + REQUIRED_LOADED_RADIUS);
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) if (!level.hasChunk(x, z)) return false;
        return true;
    }

    private static boolean hasNearbyNaturalMerchant(ServerLevel level, BlockPos center) {
        long radiusSq = (long)NEARBY_MERCHANT_RADIUS * NEARBY_MERCHANT_RADIUS;
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof OgreMerchant merchant && merchant.isNaturalEncounter()
                    && horizontalDistanceSq(center, merchant.blockPosition()) <= radiusSq) return true;
        }
        return false;
    }

    private static double horizontalDistance(BlockPos a, BlockPos b) { return Math.sqrt(horizontalDistanceSq(a, b)); }
    private static long horizontalDistanceSq(BlockPos a, BlockPos b) {
        long dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ(); return dx * dx + dz * dz;
    }

    private static OgreMerchantSpawnData getData(ServerLevel level) {
        net.minecraft.world.level.saveddata.SavedDataType<OgreMerchantSpawnData> type =
                new net.minecraft.world.level.saveddata.SavedDataType<>(
                        Identifier.fromNamespaceAndPath(KingsAndMonsters.MODID, "ogre_merchant_spawns"),
                        ignored -> new OgreMerchantSpawnData(),
                        ignored -> net.minecraft.nbt.CompoundTag.CODEC.xmap(
                                tag -> OgreMerchantSpawnData.load(tag, level.registryAccess()),
                                data -> data.save(new net.minecraft.nbt.CompoundTag(), level.registryAccess())),
                        null);
        return level.getDataStorage().computeIfAbsent(type);
    }

    public static void reset() { tickDelay = 20; }
}
