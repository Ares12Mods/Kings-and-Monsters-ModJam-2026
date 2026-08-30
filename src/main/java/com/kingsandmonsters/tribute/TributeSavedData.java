package com.kingsandmonsters.tribute;

import com.kingsandmonsters.Config;
import com.kingsandmonsters.api.event.AngerLevelChangedEvent;
import com.kingsandmonsters.api.tribute.ITributeData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.common.NeoForge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class TributeSavedData extends SavedData {
    public static final String FILE_ID = "kingsandmonsters_tribute";

    private final ResourceKey<Level> dimension;
    private final Map<Long, CampTributeData> camps = new HashMap<>();
    private final Map<UUID, Set<Long>> discoveredStructures = new HashMap<>();
    // One pending retaliation per player, keyed by UUID — a repeat provocation during the grace
    // period overwrites this rather than queuing a second one.
    private final Map<UUID, PendingRetaliation> pendingPatrolRetaliations = new HashMap<>();
    private int factionAngerLevel;

    public TributeSavedData(ResourceKey<Level> dimension) {
        this.dimension = dimension;
    }

    public static TributeSavedData load(ResourceKey<Level> dimension, CompoundTag tag, HolderLookup.Provider registries) {
        TributeSavedData data = new TributeSavedData(dimension);
        ListTag campsTag = tag.getListOrEmpty("camps");
        int legacyAngerTotal = 0;

        for (int i = 0; i < campsTag.size(); i++) {
            CompoundTag campTag = campsTag.getCompoundOrEmpty(i);
            long posKey = campTag.getLongOr("pos", 0L);
            CampTributeData camp = data.new CampTributeData(BlockPos.of(posKey));
            legacyAngerTotal += campTag.getIntOr("angerLevel", 0);
            camp.chiefAlive = !campTag.contains("chiefAlive") || campTag.getBooleanOr("chiefAlive", false);
            camp.nextPatrolGameTime = campTag.getLongOr("nextPatrolGameTime", 0L);
            camp.populationManaged = campTag.getBooleanOr("populationManaged", false);
            camp.captainType = OutpostCaptainType.fromSerializedName(campTag.getStringOr("captainType", ""));
            camp.populationInitialized = campTag.getBooleanOr("populationInitialized", false);
            camp.nextResidentSpawnGameTime = campTag.getLongOr("nextResidentSpawnGameTime", 0L);
            camp.captainRespawnGameTime = campTag.getLongOr("captainRespawnGameTime", 0L);
            camp.structureKind = StructureKind.fromSerializedName(campTag.getStringOr("structureKind", ""));
            if (campTag.contains("fortMapTarget")) {
                camp.fortMapTarget = BlockPos.of(campTag.getLongOr("fortMapTarget", 0L));
            }
            if (campTag.contains("mapChestPos")) {
                camp.mapChestPos = BlockPos.of(campTag.getLongOr("mapChestPos", 0L));
            }
            camp.mapRewardFulfilled = campTag.getBooleanOr("mapRewardFulfilled", false);
            if (campTag.contains("outpostMinX")) {
                camp.outpostMinX = campTag.getIntOr("outpostMinX", 0);
                camp.outpostMaxX = campTag.getIntOr("outpostMaxX", 0);
                camp.outpostMinZ = campTag.getIntOr("outpostMinZ", 0);
                camp.outpostMaxZ = campTag.getIntOr("outpostMaxZ", 0);
                camp.outpostGroundY = campTag.getIntOr("outpostGroundY", 0);
                camp.hasOutpostBounds = true;
            }
            if (camp.structureKind == StructureKind.UNKNOWN) {
                camp.structureKind = camp.populationManaged ? StructureKind.OUTPOST : StructureKind.FORT;
            }
            data.camps.put(posKey, camp);
        }

        ListTag discoveriesTag = tag.getListOrEmpty("structureDiscoveries");
        for (int i = 0; i < discoveriesTag.size(); i++) {
            CompoundTag playerTag = discoveriesTag.getCompoundOrEmpty(i);
            Optional<UUID> playerId = playerTag.read("player", UUIDUtil.CODEC);
            if (playerId.isEmpty()) {
                continue;
            }
            Set<Long> positions = new HashSet<>();
            for (long position : playerTag.getLongArray("positions").orElseGet(() -> new long[0])) {
                positions.add(position);
            }
            data.discoveredStructures.put(playerId.get(), positions);
        }

        // Older saves stored anger separately on every camp. Preserve the total
        // hostility those camps generated when migrating to faction-wide anger.
        data.factionAngerLevel = tag.contains("factionAngerLevel")
                ? data.clampAnger(tag.getIntOr("factionAngerLevel", 0))
                : data.clampAnger(legacyAngerTotal);

        ListTag retaliationsTag = tag.getListOrEmpty("patrolRetaliations");
        for (int i = 0; i < retaliationsTag.size(); i++) {
            CompoundTag retaliationTag = retaliationsTag.getCompoundOrEmpty(i);
            Optional<UUID> playerId = retaliationTag.read("player", UUIDUtil.CODEC);
            if (playerId.isEmpty()) {
                continue;
            }
            data.pendingPatrolRetaliations.put(playerId.get(), new PendingRetaliation(
                    BlockPos.of(retaliationTag.getLongOr("campOrigin", 0L)),
                    retaliationTag.getLongOr("eligibleGameTime", 0L)));
        }

        // Legacy reservedForts/pendingForts tags are intentionally ignored. They described
        // speculative targets that are not authoritative under the unconditional grid architecture.
        return data;
    }

    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag campsTag = new ListTag();

        for (CampTributeData camp : camps.values()) {
            CompoundTag campTag = new CompoundTag();
            campTag.putLong("pos", camp.campOrigin.asLong());
            campTag.putBoolean("chiefAlive", camp.chiefAlive);
            campTag.putLong("nextPatrolGameTime", camp.nextPatrolGameTime);
            campTag.putBoolean("populationManaged", camp.populationManaged);
            campTag.putString("captainType", camp.captainType.serializedName());
            campTag.putBoolean("populationInitialized", camp.populationInitialized);
            campTag.putLong("nextResidentSpawnGameTime", camp.nextResidentSpawnGameTime);
            campTag.putLong("captainRespawnGameTime", camp.captainRespawnGameTime);
            campTag.putString("structureKind", camp.structureKind.serializedName);
            if (camp.fortMapTarget != null) {
                campTag.putLong("fortMapTarget", camp.fortMapTarget.asLong());
            }
            if (camp.mapChestPos != null) {
                campTag.putLong("mapChestPos", camp.mapChestPos.asLong());
            }
            campTag.putBoolean("mapRewardFulfilled", camp.mapRewardFulfilled);
            if (camp.hasOutpostBounds) {
                campTag.putInt("outpostMinX", camp.outpostMinX);
                campTag.putInt("outpostMaxX", camp.outpostMaxX);
                campTag.putInt("outpostMinZ", camp.outpostMinZ);
                campTag.putInt("outpostMaxZ", camp.outpostMaxZ);
                campTag.putInt("outpostGroundY", camp.outpostGroundY);
            }
            campsTag.add(campTag);
        }

        ListTag discoveriesTag = new ListTag();
        discoveredStructures.forEach((playerId, positions) -> {
            CompoundTag playerTag = new CompoundTag();
            playerTag.store("player", UUIDUtil.CODEC, playerId);
            playerTag.putLongArray("positions", positions.stream().mapToLong(Long::longValue).toArray());
            discoveriesTag.add(playerTag);
        });

        ListTag retaliationsTag = new ListTag();
        pendingPatrolRetaliations.forEach((playerId, retaliation) -> {
            CompoundTag retaliationTag = new CompoundTag();
            retaliationTag.store("player", UUIDUtil.CODEC, playerId);
            retaliationTag.putLong("campOrigin", retaliation.campOrigin().asLong());
            retaliationTag.putLong("eligibleGameTime", retaliation.eligibleGameTime());
            retaliationsTag.add(retaliationTag);
        });

        tag.putInt("factionAngerLevel", factionAngerLevel);
        tag.put("camps", campsTag);
        tag.put("structureDiscoveries", discoveriesTag);
        tag.put("patrolRetaliations", retaliationsTag);
        return tag;
    }

    public ITributeData getOrCreate(BlockPos campOrigin) {
        long posKey = campOrigin.asLong();
        return camps.computeIfAbsent(posKey, ignored -> {
            setDirty();
            return new CampTributeData(campOrigin);
        });
    }

    public ITributeData get(BlockPos campOrigin) {
        return camps.get(campOrigin.asLong());
    }

    public List<CampPatrolState> getPatrolStates() {
        return camps.values().stream()
                .map(camp -> new CampPatrolState(
                        camp.campOrigin,
                        factionAngerLevel,
                        camp.chiefAlive,
                        camp.nextPatrolGameTime))
                .toList();
    }

    public Optional<BlockPos> findNearestCamp(BlockPos pos, int maxRadius) {
        long maxRadiusSq = (long) maxRadius * maxRadius;
        BlockPos nearest = null;
        long nearestDistSq = Long.MAX_VALUE;

        for (CampTributeData camp : camps.values()) {
            long dx = camp.campOrigin.getX() - pos.getX();
            long dz = camp.campOrigin.getZ() - pos.getZ();
            long distSq = dx * dx + dz * dz;
            if (distSq <= maxRadiusSq && distSq < nearestDistSq) {
                nearestDistSq = distSq;
                nearest = camp.campOrigin;
            }
        }
        return Optional.ofNullable(nearest);
    }

    public Optional<BlockPos> findNearestFort(BlockPos pos) {
        return getAllConfirmedForts().stream().min(nearestTo(pos));
    }

    /** Every Fort that has already, successfully generated. */
    public List<BlockPos> getAllConfirmedForts() {
        return camps.values().stream()
                .filter(camp -> camp.structureKind == StructureKind.FORT)
                .map(camp -> camp.campOrigin)
                .toList();
    }

    private static java.util.Comparator<BlockPos> nearestTo(BlockPos pos) {
        return java.util.Comparator.comparingLong(candidate -> {
            long dx = candidate.getX() - pos.getX();
            long dz = candidate.getZ() - pos.getZ();
            return dx * dx + dz * dz;
        });
    }

    public Optional<BlockPos> findOutpostFortMapTarget(BlockPos pos) {
        return findOwningOutpostCamp(pos)
                .filter(camp -> camp.fortMapTarget != null)
                .map(camp -> camp.fortMapTarget);
    }

    /** The Outpost (if any) whose structure bounds contain {@code pos} — nearest origin wins on overlap. */
    public Optional<BlockPos> findOutpostCampOrigin(BlockPos pos) {
        return findOwningOutpostCamp(pos).map(camp -> camp.campOrigin);
    }

    private Optional<CampTributeData> findOwningOutpostCamp(BlockPos pos) {
        return camps.values().stream()
                .filter(camp -> camp.structureKind == StructureKind.OUTPOST
                        && camp.hasOutpostBounds
                        && pos.getX() >= camp.outpostMinX && pos.getX() <= camp.outpostMaxX
                        && pos.getZ() >= camp.outpostMinZ && pos.getZ() <= camp.outpostMaxZ)
                .min(java.util.Comparator.comparingLong(camp -> {
                    long dx = camp.campOrigin.getX() - pos.getX();
                    long dz = camp.campOrigin.getZ() - pos.getZ();
                    return dx * dx + dz * dz;
                }));
    }

    /**
     * Records the single physical chest that owes this Outpost's guaranteed Fort Map, the first
     * time it's found. Idempotent: a later call for the same Outpost (afterPlace runs once per
     * chunk intersecting the structure) never overwrites an already-recorded chest.
     */
    public void setOutpostMapChestPosIfAbsent(BlockPos campOrigin, BlockPos chestPos) {
        CampTributeData camp = (CampTributeData) getOrCreate(campOrigin);
        if (camp.mapChestPos == null) {
            camp.mapChestPos = chestPos.immutable();
            setDirty();
        }
    }

    public Optional<BlockPos> getOutpostMapChestPos(BlockPos campOrigin) {
        CampTributeData camp = camps.get(campOrigin.asLong());
        return camp == null ? Optional.empty() : Optional.ofNullable(camp.mapChestPos);
    }

    public Optional<BlockPos> getOutpostFortMapTarget(BlockPos campOrigin) {
        CampTributeData camp = camps.get(campOrigin.asLong());
        return camp == null ? Optional.empty() : Optional.ofNullable(camp.fortMapTarget);
    }

    public boolean isMapRewardFulfilled(BlockPos campOrigin) {
        CampTributeData camp = camps.get(campOrigin.asLong());
        return camp != null && camp.mapRewardFulfilled;
    }

    /** @return true if this call is the one that first marked the reward fulfilled. */
    public boolean markMapRewardFulfilled(BlockPos campOrigin) {
        CampTributeData camp = (CampTributeData) getOrCreate(campOrigin);
        if (camp.mapRewardFulfilled) {
            return false;
        }
        camp.mapRewardFulfilled = true;
        setDirty();
        return true;
    }

    public boolean isMapRewardFulfilledNear(BlockPos pos) {
        return findOwningOutpostCamp(pos).map(camp -> camp.mapRewardFulfilled).orElse(false);
    }

    /** @return true if this call is the one that first marked the reward fulfilled. */
    public boolean markMapRewardFulfilledNear(BlockPos pos) {
        return findOwningOutpostCamp(pos).map(camp -> {
            if (camp.mapRewardFulfilled) {
                return false;
            }
            camp.mapRewardFulfilled = true;
            setDirty();
            return true;
        }).orElse(false);
    }

    public int getFactionAngerLevel() {
        return factionAngerLevel;
    }

    public void registerOutpost(
            BlockPos campOrigin, OutpostCaptainType captainType, BoundingBox structureBounds) {
        CampTributeData camp = (CampTributeData) getOrCreate(campOrigin);
        if (camp.populationManaged && camp.captainType == captainType
                && camp.structureKind == StructureKind.OUTPOST
                && camp.hasOutpostBounds
                && camp.outpostMinX == structureBounds.minX()
                && camp.outpostMaxX == structureBounds.maxX()
                && camp.outpostMinZ == structureBounds.minZ()
                && camp.outpostMaxZ == structureBounds.maxZ()
                && camp.outpostGroundY == structureBounds.minY()) {
            return;
        }
        camp.populationManaged = true;
        camp.captainType = captainType;
        camp.structureKind = StructureKind.OUTPOST;
        camp.outpostMinX = structureBounds.minX();
        camp.outpostMaxX = structureBounds.maxX();
        camp.outpostMinZ = structureBounds.minZ();
        camp.outpostMaxZ = structureBounds.maxZ();
        camp.outpostGroundY = structureBounds.minY();
        camp.hasOutpostBounds = true;
        setDirty();
    }

    public void registerFort(BlockPos fortOrigin) {
        CampTributeData camp = (CampTributeData) getOrCreate(fortOrigin);
        if (camp.structureKind != StructureKind.FORT) {
            camp.structureKind = StructureKind.FORT;
            setDirty();
        }
    }

    public void setOutpostFortMapTarget(BlockPos campOrigin, BlockPos fortTarget) {
        CampTributeData camp = (CampTributeData) getOrCreate(campOrigin);
        BlockPos immutableTarget = fortTarget.immutable();
        if (!immutableTarget.equals(camp.fortMapTarget)) {
            camp.fortMapTarget = immutableTarget;
            setDirty();
        }
    }

    public List<DiscoverySite> getDiscoverySites() {
        // Reservations are not real, physically generated structures yet — a player walking near
        // one must not trigger a discovery overlay for something that isn't there.
        return camps.values().stream()
                .filter(camp -> camp.structureKind == StructureKind.OUTPOST
                        || camp.structureKind == StructureKind.FORT)
                .map(camp -> new DiscoverySite(camp.campOrigin, camp.structureKind))
                .toList();
    }

    public boolean markDiscovered(UUID playerId, BlockPos structureOrigin) {
        boolean added = discoveredStructures
                .computeIfAbsent(playerId, ignored -> new HashSet<>())
                .add(structureOrigin.asLong());
        if (added) {
            setDirty();
        }
        return added;
    }

    public List<OutpostPopulationState> getOutpostPopulationStates() {
        return camps.values().stream()
                .filter(camp -> camp.populationManaged)
                .map(camp -> new OutpostPopulationState(
                        camp.campOrigin,
                        camp.captainType,
                        camp.populationInitialized,
                        camp.nextResidentSpawnGameTime,
                        camp.captainRespawnGameTime,
                        camp.hasOutpostBounds,
                        camp.outpostMinX,
                        camp.outpostMaxX,
                        camp.outpostMinZ,
                        camp.outpostMaxZ,
                        camp.outpostGroundY))
                .toList();
    }

    public void markPopulationInitialized(BlockPos campOrigin, long nextResidentSpawnGameTime) {
        CampTributeData camp = camps.get(campOrigin.asLong());
        if (camp == null) return;
        camp.populationInitialized = true;
        camp.nextResidentSpawnGameTime = nextResidentSpawnGameTime;
        camp.chiefAlive = true;
        setDirty();
    }

    public void setNextResidentSpawnGameTime(BlockPos campOrigin, long gameTime) {
        CampTributeData camp = camps.get(campOrigin.asLong());
        if (camp == null || camp.nextResidentSpawnGameTime == gameTime) return;
        camp.nextResidentSpawnGameTime = gameTime;
        setDirty();
    }

    public void markOutpostCaptainKilled(BlockPos campOrigin, long respawnGameTime) {
        CampTributeData camp = camps.get(campOrigin.asLong());
        if (camp == null || !camp.populationManaged) return;
        camp.chiefAlive = false;
        camp.captainRespawnGameTime = respawnGameTime;
        setDirty();
    }

    public void markOutpostCaptainAlive(BlockPos campOrigin) {
        CampTributeData camp = camps.get(campOrigin.asLong());
        if (camp == null || !camp.populationManaged) return;
        camp.chiefAlive = true;
        camp.captainRespawnGameTime = 0L;
        setDirty();
    }

    public void incrementFactionAnger(int delta, BlockPos sourceCamp) {
        setFactionAnger(factionAngerLevel + delta, sourceCamp);
    }

    private void setFactionAnger(int level, BlockPos sourceCamp) {
        int oldAngerLevel = factionAngerLevel;
        int newAngerLevel = clampAnger(level);

        if (oldAngerLevel == newAngerLevel) {
            return;
        }

        factionAngerLevel = newAngerLevel;
        setDirty();
        NeoForge.EVENT_BUS.post(new AngerLevelChangedEvent(
                sourceCamp, dimension, oldAngerLevel, newAngerLevel));
    }

    /** Arms (or refreshes) this player's single pending retaliation — overwrites any existing one. */
    public void armPatrolRetaliation(UUID playerId, BlockPos campOrigin, long eligibleGameTime) {
        pendingPatrolRetaliations.put(playerId, new PendingRetaliation(campOrigin.immutable(), eligibleGameTime));
        setDirty();
    }

    /**
     * Returns pending retaliations whose delay has elapsed, without removing them — a caller that
     * can't currently deliver one (player offline/elsewhere) should leave it pending rather than
     * losing it, so this only reports readiness. Pair with {@link #clearPatrolRetaliation}.
     */
    public List<DuePatrolRetaliation> peekDuePatrolRetaliations(long gameTime) {
        List<DuePatrolRetaliation> due = new ArrayList<>();
        pendingPatrolRetaliations.forEach((playerId, retaliation) -> {
            if (gameTime >= retaliation.eligibleGameTime()) {
                due.add(new DuePatrolRetaliation(playerId, retaliation.campOrigin()));
            }
        });
        return due;
    }

    public void clearPatrolRetaliation(UUID playerId) {
        if (pendingPatrolRetaliations.remove(playerId) != null) {
            setDirty();
        }
    }

    public void setNextPatrolGameTime(BlockPos campOrigin, long gameTime) {
        CampTributeData camp = camps.get(campOrigin.asLong());
        if (camp == null || camp.nextPatrolGameTime == gameTime) {
            return;
        }

        camp.nextPatrolGameTime = gameTime;
        setDirty();
    }

    private int maxAngerLevel() {
        return Config.TRIBUTE_MAX_ANGER.get();
    }

    private int clampAnger(int angerLevel) {
        return Math.clamp(angerLevel, 0, maxAngerLevel());
    }

    private final class CampTributeData implements ITributeData {
        private final BlockPos campOrigin;
        private boolean chiefAlive = true;
        private long nextPatrolGameTime;
        private boolean populationManaged;
        private OutpostCaptainType captainType = OutpostCaptainType.GRUNT;
        private boolean populationInitialized;
        private long nextResidentSpawnGameTime;
        private long captainRespawnGameTime;
        private StructureKind structureKind = StructureKind.UNKNOWN;
        private BlockPos fortMapTarget;
        // The single physical chest designated to receive this Outpost's guaranteed Fort Map,
        // captured once in OgreOutpostStructure.afterPlace (first TRAPPED_CHEST found for the
        // loot table that actually carries the map reward). Only used by FortMapDeliveryService
        // for the deferred-insertion path — see mapRewardFulfilled below.
        private BlockPos mapChestPos;
        // True once a Fort Map has actually been handed to the player for this Outpost, either
        // via the normal loot-table roll (CachedFortMapFunction) or a deferred manual insertion
        // (FortMapDeliveryService). Prevents a second trapped chest belonging to the same Outpost
        // (e.g. grunt_outpost.nbt has two) from independently producing a duplicate map, and
        // prevents the deferred delivery path from re-inserting once the reward already landed.
        private boolean mapRewardFulfilled;
        private boolean hasOutpostBounds;
        private int outpostMinX;
        private int outpostMaxX;
        private int outpostMinZ;
        private int outpostMaxZ;
        private int outpostGroundY;

        private CampTributeData(BlockPos campOrigin) {
            this.campOrigin = campOrigin.immutable();
        }

        @Override
        public int getAngerLevel() {
            return factionAngerLevel;
        }

        @Override
        public void setAngerLevel(int level) {
            setFactionAnger(level, campOrigin);
        }

        @Override
        public void incrementAnger(int delta) {
            incrementFactionAnger(delta, campOrigin);
        }

        @Override
        public int getMaxAngerLevel() {
            return maxAngerLevel();
        }

        @Override
        public boolean isChiefAlive() {
            return chiefAlive;
        }

        @Override
        public void setChiefAlive(boolean alive) {
            if (chiefAlive == alive) {
                return;
            }

            chiefAlive = alive;
            setDirty();
        }
    }

    public record CampPatrolState(BlockPos campOrigin, int angerLevel, boolean chiefAlive, long nextPatrolGameTime) {
    }

    private record PendingRetaliation(BlockPos campOrigin, long eligibleGameTime) {
    }

    public record DuePatrolRetaliation(UUID playerId, BlockPos campOrigin) {
    }

    public record OutpostPopulationState(
            BlockPos campOrigin,
            OutpostCaptainType captainType,
            boolean populationInitialized,
            long nextResidentSpawnGameTime,
            long captainRespawnGameTime,
            boolean hasOutpostBounds,
            int outpostMinX,
            int outpostMaxX,
            int outpostMinZ,
            int outpostMaxZ,
            int outpostGroundY) {
    }

    public record DiscoverySite(BlockPos origin, StructureKind kind) {}

    public enum StructureKind {
        UNKNOWN("unknown"),
        OUTPOST("outpost"),
        FORT("fort");

        private final String serializedName;

        StructureKind(String serializedName) {
            this.serializedName = serializedName;
        }

        static StructureKind fromSerializedName(String name) {
            for (StructureKind kind : values()) {
                if (kind.serializedName.equals(name)) {
                    return kind;
                }
            }
            return UNKNOWN;
        }
    }
}
