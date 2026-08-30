package com.kingsandmonsters.world;

import com.kingsandmonsters.entity.OgreGrunt;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * Converts the minimal Forge/Forgematica trial-spawner NBT in Ogre structures
 * into complete, role-balanced encounters. The three royal trials are led by
 * the Mage, Brute, and Grunt Captains; Ogre Guards are reserved for protecting
 * the sleeping King.
 */
public final class OgreTrialSpawnerProcessor extends StructureProcessor {
    public static final MapCodec<OgreTrialSpawnerProcessor> CODEC =
            MapCodec.unit(OgreTrialSpawnerProcessor::new);
    private static final BlockPos ROYAL_MAGE_TRIAL_LOCAL_POS = new BlockPos(30, 3, 9);
    private static final BlockPos ROYAL_BRUTE_TRIAL_LOCAL_POS = new BlockPos(31, 4, 90);
    private static final BlockPos ROYAL_GRUNT_TRIAL_LOCAL_POS = new BlockPos(12, 3, 65);
    private static final BlockPos ROYAL_WEST_ARCHER_TRIAL_LOCAL_POS = new BlockPos(43, 11, 7);
    private static final BlockPos ROYAL_EAST_ARCHER_TRIAL_LOCAL_POS = new BlockPos(52, 11, 80);
    private static final Vec3i ROYAL_FORT_TEMPLATE_SIZE = new Vec3i(67, 27, 103);
    private static final Vec3i OUTPOST_TEMPLATE_SIZE = new Vec3i(34, 16, 26);
    public static final String ROYAL_MAGE_SQUAD_TAG = "KingsAndMonstersRoyalMageSquad";
    private static final Profile ROYAL_MAGE_PROFILE =
            new Profile(5, 1.0F, 1.0F, 0.0F, 0.0F, 80, 54_000, 14,
                    1.0F, 1.0F, "kingsandmonsters:spawners/ogre_mage", null);
    private static final Profile ROYAL_BRUTE_PROFILE =
            new Profile(5, 1.0F, 1.0F, 0.0F, 0.0F, 100, 54_000, 14,
                    1.0F, 1.0F, "kingsandmonsters:spawners/ogre_brute", null);
    private static final Profile ROYAL_GRUNT_CAPTAIN_PROFILE =
            new Profile(4, 1.0F, 1.0F, 0.0F, 0.0F, 120, 36_000, 14,
                    1.0F, 1.0F, "kingsandmonsters:spawners/ogre_guard", null);
    private static final Profile OUTPOST_REINFORCEMENT_PROFILE =
            new Profile(6, 1.0F, 1.0F, 0.0F, 0.0F, 40, 36_000, 10,
                    1.0F, 1.0F, "kingsandmonsters:spawners/ogre_grunt", null);

    private static final Map<String, Profile> PROFILES = Map.of(
            "kingsandmonsters:ogre_grunt",
            new Profile(5, 4.0F, 2.0F, 2.0F, 0.0F, 30, 36_000, 14,
                    5.0F, 2.0F, "kingsandmonsters:spawners/ogre_grunt", null),
            "kingsandmonsters:ogre_archer",
            new Profile(6, 3.0F, 2.0F, 1.0F, 0.0F, 50, 36_000, 24,
                    4.0F, 2.0F, "kingsandmonsters:spawners/ogre_archer", null),
            "kingsandmonsters:ogre_mage",
            new Profile(5, 1.0F, 1.0F, 0.0F, 0.0F, 80, 54_000, 14,
                    1.0F, 1.0F, "kingsandmonsters:spawners/ogre_mage", null),
            "kingsandmonsters:ogre_brute",
            new Profile(5, 1.0F, 1.0F, 0.0F, 0.0F, 100, 54_000, 14,
                    1.0F, 1.0F, "kingsandmonsters:spawners/ogre_brute", null),
            "kingsandmonsters:ogre_guard",
            new Profile(4, 2.0F, 2.0F, 0.0F, 0.0F, 120, 36_000, 14,
                    2.0F, 2.0F, "kingsandmonsters:spawners/ogre_guard", null),
            "kingsandmonsters:ogre_grunt_captain",
            ROYAL_GRUNT_CAPTAIN_PROFILE);

    @Nullable
    @Override
    public StructureTemplate.StructureBlockInfo process(
            LevelReader level,
            BlockPos offset,
            BlockPos pos,
            StructureTemplate.StructureBlockInfo rawInfo,
            StructureTemplate.StructureBlockInfo relativeInfo,
            StructurePlaceSettings settings,
            StructureTemplate template) {
        if (!relativeInfo.state().is(Blocks.TRIAL_SPAWNER) || relativeInfo.nbt() == null) {
            return relativeInfo;
        }

        CompoundTag nbt = relativeInfo.nbt().copy();
        boolean royalFortTemplate = template.getSize().equals(ROYAL_FORT_TEMPLATE_SIZE);
        boolean outpostTemplate = template.getSize().equals(OUTPOST_TEMPLATE_SIZE);
        String entityId = readEntityId(nbt);
        if (outpostTemplate) {
            entityId = "kingsandmonsters:ogre_grunt";
            CompoundTag entity = new CompoundTag();
            entity.putString("id", entityId);
            entity.putBoolean(OgreGrunt.OUTPOST_REINFORCEMENT_SQUAD_TAG, true);
            CompoundTag spawnData = new CompoundTag();
            spawnData.put("entity", entity);
            nbt.put("spawn_data", spawnData);
        }
        if (entityId.isEmpty() && royalFortTemplate) {
            entityId = royalFortEntityId(rawInfo.pos());
            if (!entityId.isEmpty()) {
                CompoundTag entity = new CompoundTag();
                entity.putString("id", entityId);
                CompoundTag spawnData = new CompoundTag();
                spawnData.put("entity", entity);
                nbt.put("spawn_data", spawnData);
            }
        }
        // Older fort templates used an Ogre Guard marker for this trial. Force that fixed royal
        // position to the dedicated Grunt Captain so both old and newly rebuilt structures use the
        // correct leader while ordinary Guards remain free to protect the sleeping King.
        if (royalFortTemplate && rawInfo.pos().equals(ROYAL_GRUNT_TRIAL_LOCAL_POS)) {
            entityId = "kingsandmonsters:ogre_grunt_captain";
            nbt.getCompoundOrEmpty("spawn_data").getCompoundOrEmpty("entity").putString("id", entityId);
        }
        Profile profile = PROFILES.get(entityId);
        if (profile == null) {
            return relativeInfo;
        }

        boolean royalMageTrial = royalFortTemplate && "kingsandmonsters:ogre_mage".equals(entityId)
                && rawInfo.pos().equals(ROYAL_MAGE_TRIAL_LOCAL_POS);
        boolean royalBruteTrial = royalFortTemplate && "kingsandmonsters:ogre_brute".equals(entityId)
                && rawInfo.pos().equals(ROYAL_BRUTE_TRIAL_LOCAL_POS);
        boolean royalGruntTrial = royalFortTemplate && "kingsandmonsters:ogre_grunt_captain".equals(entityId)
                && rawInfo.pos().equals(ROYAL_GRUNT_TRIAL_LOCAL_POS);
        if (outpostTemplate) {
            profile = OUTPOST_REINFORCEMENT_PROFILE;
        } else if (royalMageTrial) {
            profile = ROYAL_MAGE_PROFILE;
            nbt.getCompoundOrEmpty("spawn_data").getCompoundOrEmpty("entity")
                    .putBoolean(ROYAL_MAGE_SQUAD_TAG, true);
        } else if (royalBruteTrial) {
            profile = ROYAL_BRUTE_PROFILE;
            nbt.getCompoundOrEmpty("spawn_data").getCompoundOrEmpty("entity")
                    .putBoolean(OgreGrunt.ROYAL_PATROL_SQUAD_TAG, true);
        } else if (royalGruntTrial) {
            profile = ROYAL_GRUNT_CAPTAIN_PROFILE;
            nbt.getCompoundOrEmpty("spawn_data").getCompoundOrEmpty("entity")
                    .putBoolean(OgreGrunt.ROYAL_PATROL_SQUAD_TAG, true);
        }

        boolean royalPatrolTrial = royalBruteTrial || royalGruntTrial;
        nbt.put("normal_config", createConfig(profile, entityId, false, royalMageTrial, royalPatrolTrial,
                outpostTemplate));
        nbt.put("ominous_config", createConfig(profile, entityId, true, royalMageTrial, royalPatrolTrial,
                outpostTemplate));
        nbt.putInt("target_cooldown_length", profile.cooldownTicks());
        nbt.putInt("required_player_range", profile.requiredPlayerRange());
        return new StructureTemplate.StructureBlockInfo(
                relativeInfo.pos(), relativeInfo.state(), nbt);
    }

    private static String readEntityId(CompoundTag nbt) {
        if (!nbt.contains("spawn_data")) {
            return "";
        }
        return nbt.getCompoundOrEmpty("spawn_data").getCompoundOrEmpty("entity").getStringOr("id", "");
    }

    private static String royalFortEntityId(BlockPos localPos) {
        if (localPos.equals(ROYAL_MAGE_TRIAL_LOCAL_POS)) {
            return "kingsandmonsters:ogre_mage";
        }
        if (localPos.equals(ROYAL_BRUTE_TRIAL_LOCAL_POS)) {
            return "kingsandmonsters:ogre_brute";
        }
        if (localPos.equals(ROYAL_GRUNT_TRIAL_LOCAL_POS)) {
            return "kingsandmonsters:ogre_grunt_captain";
        }
        if (localPos.equals(ROYAL_WEST_ARCHER_TRIAL_LOCAL_POS)
                || localPos.equals(ROYAL_EAST_ARCHER_TRIAL_LOCAL_POS)) {
            return "kingsandmonsters:ogre_archer";
        }
        return "";
    }

    private static CompoundTag createConfig(
            Profile profile,
            String entityId,
            boolean ominous,
            boolean royalMageTrial,
            boolean royalPatrolTrial,
            boolean outpostReinforcement) {
        CompoundTag config = new CompoundTag();
        config.putInt("spawn_range", profile.spawnRange());
        config.putFloat("total_mobs", ominous ? profile.ominousTotalMobs() : profile.totalMobs());
        config.putFloat("simultaneous_mobs",
                ominous ? profile.ominousSimultaneousMobs() : profile.simultaneousMobs());
        config.putFloat("total_mobs_added_per_player", profile.totalMobsPerExtraPlayer());
        config.putFloat("simultaneous_mobs_added_per_player",
                profile.simultaneousMobsPerExtraPlayer());
        config.putInt("ticks_between_spawn", profile.ticksBetweenSpawn());
        String followupEntityId = profile.followupEntityId() == null
                ? entityId
                : profile.followupEntityId();
        config.put("spawn_potentials", weightedSpawnPotential(
                followupEntityId, royalMageTrial, royalPatrolTrial, outpostReinforcement));
        config.put("loot_tables_to_eject", weightedValue(profile.rewardLootTable()));
        return config;
    }

    private static ListTag weightedSpawnPotential(
            String entityId, boolean royalMageTrial, boolean royalPatrolTrial,
            boolean outpostReinforcement) {
        CompoundTag entity = new CompoundTag();
        entity.putString("id", entityId);
        if (royalMageTrial) {
            entity.putBoolean(ROYAL_MAGE_SQUAD_TAG, true);
        }
        if (royalPatrolTrial) {
            entity.putBoolean(OgreGrunt.ROYAL_PATROL_SQUAD_TAG, true);
        }
        if (outpostReinforcement) {
            entity.putBoolean(OgreGrunt.OUTPOST_REINFORCEMENT_SQUAD_TAG, true);
        }
        CompoundTag data = new CompoundTag();
        data.put("entity", entity);
        return weightedEntry(data);
    }

    private static ListTag weightedValue(String value) {
        CompoundTag entry = new CompoundTag();
        entry.put("data", StringTag.valueOf(value));
        entry.putInt("weight", 1);
        ListTag list = new ListTag();
        list.add(entry);
        return list;
    }

    private static ListTag weightedEntry(CompoundTag data) {
        CompoundTag entry = new CompoundTag();
        entry.put("data", data);
        entry.putInt("weight", 1);
        ListTag list = new ListTag();
        list.add(entry);
        return list;
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return ModStructures.OGRE_TRIAL_SPAWNER.get();
    }

    private record Profile(
            int spawnRange,
            float totalMobs,
            float simultaneousMobs,
            float totalMobsPerExtraPlayer,
            float simultaneousMobsPerExtraPlayer,
            int ticksBetweenSpawn,
            int cooldownTicks,
            int requiredPlayerRange,
            float ominousTotalMobs,
            float ominousSimultaneousMobs,
            String rewardLootTable,
            @Nullable String followupEntityId) {
    }
}
