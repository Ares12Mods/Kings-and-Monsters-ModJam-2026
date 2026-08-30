package com.kingsandmonsters;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server-authoritative configuration for Kings and Monsters.
 *
 * <p>The TOML layout intentionally uses player-facing section names while the
 * field names stay stable for callers inside the mod.</p>
 */
public final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.DoubleValue OGRE_GRUNT_MAX_HEALTH;
    public static final ModConfigSpec.DoubleValue OGRE_GRUNT_ATTACK_DAMAGE;
    public static final ModConfigSpec.DoubleValue OGRE_GRUNT_MOVEMENT_SPEED;
    public static final ModConfigSpec.DoubleValue OGRE_GRUNT_FOLLOW_RANGE;
    public static final ModConfigSpec.DoubleValue OGRE_GRUNT_ARMOR;
    public static final ModConfigSpec.DoubleValue OGRE_GRUNT_ATTACK_KNOCKBACK;
    public static final ModConfigSpec.DoubleValue OGRE_GRUNT_STEP_HEIGHT;
    public static final ModConfigSpec.DoubleValue OGRE_GRUNT_SAFE_FALL_DISTANCE;

    public static final ModConfigSpec.DoubleValue OGRE_GRUNT_CAPTAIN_MAX_HEALTH;
    public static final ModConfigSpec.DoubleValue OGRE_GRUNT_CAPTAIN_ARMOR;

    public static final ModConfigSpec.DoubleValue OGRE_ARCHER_MAX_HEALTH;
    public static final ModConfigSpec.DoubleValue OGRE_ARCHER_MELEE_DAMAGE;
    public static final ModConfigSpec.DoubleValue OGRE_ARCHER_ARROW_DAMAGE;
    public static final ModConfigSpec.DoubleValue OGRE_ARCHER_MOVEMENT_SPEED;
    public static final ModConfigSpec.DoubleValue OGRE_ARCHER_FOLLOW_RANGE;
    public static final ModConfigSpec.DoubleValue OGRE_ARCHER_ARMOR;

    public static final ModConfigSpec.DoubleValue OGRE_GUARD_MAX_HEALTH;
    public static final ModConfigSpec.DoubleValue OGRE_GUARD_MELEE_DAMAGE;
    public static final ModConfigSpec.DoubleValue OGRE_GUARD_SPEAR_DAMAGE;
    public static final ModConfigSpec.DoubleValue OGRE_GUARD_MOVEMENT_SPEED;
    public static final ModConfigSpec.DoubleValue OGRE_GUARD_FOLLOW_RANGE;
    public static final ModConfigSpec.DoubleValue OGRE_GUARD_ARMOR;

    public static final ModConfigSpec.DoubleValue OGRE_MAGE_MAX_HEALTH;
    public static final ModConfigSpec.DoubleValue OGRE_MAGE_ATTACK_DAMAGE;
    public static final ModConfigSpec.DoubleValue OGRE_MAGE_MOVEMENT_SPEED;
    public static final ModConfigSpec.DoubleValue OGRE_MAGE_FOLLOW_RANGE;
    public static final ModConfigSpec.DoubleValue OGRE_MAGE_ARMOR;

    public static final ModConfigSpec.DoubleValue OGRE_BRUTE_MAX_HEALTH;
    public static final ModConfigSpec.DoubleValue OGRE_BRUTE_ATTACK_DAMAGE;
    public static final ModConfigSpec.DoubleValue OGRE_BRUTE_MOVEMENT_SPEED;
    public static final ModConfigSpec.DoubleValue OGRE_BRUTE_FOLLOW_RANGE;
    public static final ModConfigSpec.DoubleValue OGRE_BRUTE_ARMOR;

    public static final ModConfigSpec.DoubleValue OGRE_LORD_MAX_HEALTH;
    public static final ModConfigSpec.DoubleValue OGRE_LORD_ATTACK_DAMAGE;
    public static final ModConfigSpec.DoubleValue OGRE_LORD_MOVEMENT_SPEED;
    public static final ModConfigSpec.DoubleValue OGRE_LORD_FOLLOW_RANGE;
    public static final ModConfigSpec.DoubleValue OGRE_LORD_ARMOR;
    public static final ModConfigSpec.DoubleValue OGRE_LORD_PULL_OUT_BONUS_DAMAGE;
    public static final ModConfigSpec.DoubleValue OGRE_LORD_CLUB_UPSWING_BONUS_DAMAGE;
    public static final ModConfigSpec.DoubleValue OGRE_LORD_CLUB_DOUBLE_OVERHEAD_BONUS_DAMAGE;
    public static final ModConfigSpec.DoubleValue OGRE_LORD_SINGLE_OVERHEAD_BONUS_DAMAGE;

    public static final ModConfigSpec.IntValue CRIPPLED_BITE_DURATION_TICKS;
    public static final ModConfigSpec.IntValue CRIPPLED_SPEAR_DURATION_TICKS;
    public static final ModConfigSpec.DoubleValue HUNTERS_SPEAR_CRIPPLE_CHANCE;
    public static final ModConfigSpec.DoubleValue HUNTERS_SPEAR_ARMOR_PIERCE;
    public static final ModConfigSpec.IntValue DAZED_HEAVY_ATTACK_DURATION_TICKS;
    public static final ModConfigSpec.DoubleValue HEAVY_THROW_DAMAGE_PER_LEVEL;
    public static final ModConfigSpec.DoubleValue HEAVY_THROW_KNOCKBACK_PER_LEVEL;
    public static final ModConfigSpec.DoubleValue HEAVY_THROW_SPEED_PENALTY_PER_LEVEL;
    public static final ModConfigSpec.DoubleValue BARBED_BLEED_CHANCE;
    public static final ModConfigSpec.IntValue BARBED_BLEED_DURATION_TICKS;
    public static final ModConfigSpec.DoubleValue BARBED_BLEED_DAMAGE;
    public static final ModConfigSpec.DoubleValue TYRANT_SEARCH_RADIUS;

    public static final ModConfigSpec.IntValue TRIBUTE_MAX_ANGER;
    public static final ModConfigSpec.IntValue TRIBUTE_DEFAULT_ANGER_DELTA;
    public static final ModConfigSpec.IntValue TRIBUTE_CHEST_ANGER_DELTA;
    public static final ModConfigSpec.IntValue TRIBUTE_CAMP_KILL_ANGER;
    public static final ModConfigSpec.IntValue TRIBUTE_CAMP_KILL_RADIUS;
    public static final ModConfigSpec.DoubleValue TRIBUTE_PATROL_CHANCE;
    public static final ModConfigSpec.IntValue ANGER_DECAY_INTERVAL_TICKS;
    public static final ModConfigSpec.IntValue ANGER_HUD_RADIUS;
    public static final ModConfigSpec.DoubleValue SWAMP_OUTPOST_FORT_MAP_CHANCE;
    public static final ModConfigSpec.BooleanValue CARTOGRAPHER_FORT_MAP_ENABLED;
    public static final ModConfigSpec.IntValue CARTOGRAPHER_FORT_MAP_PRICE;
    public static final ModConfigSpec.IntValue CARTOGRAPHER_FORT_MAP_LEVEL;

    public static final ModConfigSpec.BooleanValue PATROLS_ENABLED;
    public static final ModConfigSpec.IntValue PATROL_CHECK_INTERVAL_TICKS;
    public static final ModConfigSpec.IntValue PATROL_MIN_COOLDOWN_TICKS;
    public static final ModConfigSpec.IntValue PATROL_MAX_COOLDOWN_TICKS;
    public static final ModConfigSpec.IntValue PATROL_PLAYER_SEARCH_RADIUS;
    public static final ModConfigSpec.IntValue PATROL_MIN_SPAWN_DISTANCE;
    public static final ModConfigSpec.IntValue PATROL_MAX_SPAWN_DISTANCE;

    static final ModConfigSpec SPEC;

    static {
        BUILDER.comment("Configure regular ogre stats.").push("Mob Config");
        BUILDER.push("Ogre Grunt");
        OGRE_GRUNT_MAX_HEALTH        = BUILDER.comment("Maximum health.").defineInRange("maxHealth", 60.0, 1.0, 200.0);
        OGRE_GRUNT_ATTACK_DAMAGE     = BUILDER.comment("Base melee damage.").defineInRange("attackDamage", 10.5, 0.0, 100.0);
        OGRE_GRUNT_MOVEMENT_SPEED    = BUILDER.comment("Movement speed.").defineInRange("movementSpeed", 0.2772, 0.0, 1.0);
        OGRE_GRUNT_FOLLOW_RANGE      = BUILDER.comment("How far away it can notice targets.").defineInRange("followRange", 30.0, 1.0, 128.0);
        OGRE_GRUNT_ARMOR             = BUILDER.comment("Base armor points.").defineInRange("armor", 8.0, 0.0, 30.0);
        OGRE_GRUNT_ATTACK_KNOCKBACK  = BUILDER.comment("Base melee knockback strength.").defineInRange("attackKnockback", 0.9, 0.0, 10.0);
        OGRE_GRUNT_STEP_HEIGHT       = BUILDER.comment("How high it can step while walking.").defineInRange("stepHeight", 1.0, 0.0, 2.0);
        OGRE_GRUNT_SAFE_FALL_DISTANCE = BUILDER.comment("How far it can safely path downward.").defineInRange("safeFallDistance", 4.0, 0.0, 16.0);
        BUILDER.pop();

        BUILDER.push("Grunt Captain");
        OGRE_GRUNT_CAPTAIN_MAX_HEALTH = BUILDER.comment("Maximum health.").defineInRange("maxHealth", 100.0, 1.0, 300.0);
        OGRE_GRUNT_CAPTAIN_ARMOR = BUILDER.comment("Base armor points.").defineInRange("armor", 12.0, 0.0, 40.0);
        BUILDER.pop();

        BUILDER.push("Ogre Archer");
        OGRE_ARCHER_MAX_HEALTH    = BUILDER.comment("Maximum health.").defineInRange("maxHealth", 60.0, 1.0, 200.0);
        OGRE_ARCHER_MELEE_DAMAGE  = BUILDER.comment("Base melee (punch) damage.").defineInRange("meleeDamage", 5.0, 0.0, 100.0);
        OGRE_ARCHER_ARROW_DAMAGE  = BUILDER.comment("Base arrow damage before bow variant bonuses.").defineInRange("arrowDamage", 11.0, 0.0, 100.0);
        OGRE_ARCHER_MOVEMENT_SPEED = BUILDER.comment("Movement speed.").defineInRange("movementSpeed", 0.288, 0.0, 1.0);
        OGRE_ARCHER_FOLLOW_RANGE  = BUILDER.comment("How far away it can notice targets.").defineInRange("followRange", 34.0, 1.0, 128.0);
        OGRE_ARCHER_ARMOR         = BUILDER.comment("Base armor points.").defineInRange("armor", 9.0, 0.0, 30.0);
        BUILDER.pop();

        BUILDER.push("Ogre Guard");
        OGRE_GUARD_MAX_HEALTH     = BUILDER.comment("Maximum health.").defineInRange("maxHealth", 70.0, 1.0, 250.0);
        OGRE_GUARD_MELEE_DAMAGE   = BUILDER.comment("Base punch damage.").defineInRange("meleeDamage", 7.0, 0.0, 100.0);
        OGRE_GUARD_SPEAR_DAMAGE   = BUILDER.comment("Base thrown spear damage.").defineInRange("spearDamage", 10.0, 0.0, 100.0);
        OGRE_GUARD_MOVEMENT_SPEED = BUILDER.comment("Movement speed.").defineInRange("movementSpeed", 0.275, 0.0, 1.0);
        OGRE_GUARD_FOLLOW_RANGE   = BUILDER.comment("How far away it can notice targets.").defineInRange("followRange", 34.0, 1.0, 128.0);
        OGRE_GUARD_ARMOR          = BUILDER.comment("Base armor points.").defineInRange("armor", 11.0, 0.0, 40.0);
        BUILDER.pop();

        BUILDER.push("Ogre Mage");
        OGRE_MAGE_MAX_HEALTH     = BUILDER.comment("Maximum health.").defineInRange("maxHealth", 100.0, 1.0, 200.0);
        OGRE_MAGE_ATTACK_DAMAGE  = BUILDER.comment("Base melee damage.").defineInRange("attackDamage", 6.0, 0.0, 100.0);
        OGRE_MAGE_MOVEMENT_SPEED = BUILDER.comment("Movement speed.").defineInRange("movementSpeed", 0.189, 0.0, 1.0);
        OGRE_MAGE_FOLLOW_RANGE   = BUILDER.comment("How far away it can notice targets.").defineInRange("followRange", 30.0, 1.0, 128.0);
        OGRE_MAGE_ARMOR          = BUILDER.comment("Base armor points.").defineInRange("armor", 10.0, 0.0, 30.0);
        BUILDER.pop();

        BUILDER.push("Ogre Brute");
        OGRE_BRUTE_MAX_HEALTH     = BUILDER.comment("Maximum health.").defineInRange("maxHealth", 120.0, 1.0, 500.0);
        OGRE_BRUTE_ATTACK_DAMAGE  = BUILDER.comment("Base melee damage.").defineInRange("attackDamage", 11.0, 0.0, 100.0);
        OGRE_BRUTE_MOVEMENT_SPEED = BUILDER.comment("Movement speed.").defineInRange("movementSpeed", 0.325, 0.0, 1.0);
        OGRE_BRUTE_FOLLOW_RANGE   = BUILDER.comment("How far away it can notice targets.").defineInRange("followRange", 30.0, 1.0, 128.0);
        OGRE_BRUTE_ARMOR          = BUILDER.comment("Base armor points.").defineInRange("armor", 18.0, 0.0, 40.0);
        BUILDER.pop(2);

        BUILDER.comment("Configure boss stats and attack damage overrides.").push("Boss Config");
        BUILDER.push("Ogre Lord");
        OGRE_LORD_MAX_HEALTH     = BUILDER.comment("Maximum health.").defineInRange("maxHealth", 300.0, 1.0, 1000.0);
        OGRE_LORD_ATTACK_DAMAGE  = BUILDER.comment("Base melee damage.").defineInRange("attackDamage", 18.0, 0.0, 100.0);
        OGRE_LORD_MOVEMENT_SPEED = BUILDER.comment("Movement speed.").defineInRange("movementSpeed", 0.333, 0.0, 1.0);
        OGRE_LORD_FOLLOW_RANGE   = BUILDER.comment("How far away it can notice targets.").defineInRange("followRange", 34.0, 1.0, 128.0);
        OGRE_LORD_ARMOR          = BUILDER.comment("Base armor points.").defineInRange("armor", 20.0, 0.0, 50.0);
        OGRE_LORD_PULL_OUT_BONUS_DAMAGE = BUILDER.comment("Flat bonus damage added to the phase-transition club swing.").defineInRange("pullOutBonusDamage", 10.0, 0.0, 100.0);
        OGRE_LORD_CLUB_UPSWING_BONUS_DAMAGE = BUILDER.comment("Flat bonus damage added to the phase-two club upswing.").defineInRange("clubUpswingBonusDamage", 10.0, 0.0, 100.0);
        OGRE_LORD_CLUB_DOUBLE_OVERHEAD_BONUS_DAMAGE = BUILDER.comment("Flat bonus damage added to Club Double Overhead.").defineInRange("clubDoubleOverheadBonusDamage", 10.0, 0.0, 100.0);
        OGRE_LORD_SINGLE_OVERHEAD_BONUS_DAMAGE = BUILDER.comment("Flat bonus damage added to Single Overhead Slam.").defineInRange("singleOverheadBonusDamage", 10.0, 0.0, 100.0);
        BUILDER.pop(2);

        BUILDER.comment("Configure weapons, enchantments, and combat effects.").push("Combat");
        CRIPPLED_BITE_DURATION_TICKS = BUILDER.comment("Crippled duration from an Ogre Grunt bite.").defineInRange("crippledBiteDurationTicks", 80, 1, 1200);
        CRIPPLED_SPEAR_DURATION_TICKS = BUILDER.comment("Crippled duration from Hunter's Spear thrown hits.").defineInRange("crippledSpearDurationTicks", 100, 1, 1200);
        HUNTERS_SPEAR_CRIPPLE_CHANCE = BUILDER.comment("Base chance (0.0-1.0) for any Hunter's Spear thrown hit to apply Crippled. Barbed guarantees the effect.").defineInRange("huntersSpearCrippleChance", 0.25, 0.0, 1.0);
        HUNTERS_SPEAR_ARMOR_PIERCE = BUILDER.comment("Fraction (0.0-1.0) of the target's armor damage reduction that Hunter's Spear hits (melee or thrown) bypass.").defineInRange("huntersSpearArmorPierce", 0.20, 0.0, 1.0);
        DAZED_HEAVY_ATTACK_DURATION_TICKS = BUILDER.comment("Dazed duration from Brute and Ogre King heavy attacks.").defineInRange("dazedHeavyAttackDurationTicks", 100, 1, 1200);
        HEAVY_THROW_DAMAGE_PER_LEVEL = BUILDER.comment("Bonus thrown damage per Heavy Throw level.").defineInRange("heavyThrowDamagePerLevel", 2.0, 0.0, 50.0);
        HEAVY_THROW_KNOCKBACK_PER_LEVEL = BUILDER.comment("Bonus knockback per Heavy Throw level.").defineInRange("heavyThrowKnockbackPerLevel", 0.35, 0.0, 5.0);
        HEAVY_THROW_SPEED_PENALTY_PER_LEVEL = BUILDER.comment("Projectile speed reduction per Heavy Throw level.").defineInRange("heavyThrowSpeedPenaltyPerLevel", 0.08, 0.0, 0.4);
        BARBED_BLEED_CHANCE = BUILDER.comment("Chance (0.0-1.0) for Barbed melee or thrown hits to apply Bleeding.").defineInRange("barbedBleedChance", 0.50, 0.0, 1.0);
        BARBED_BLEED_DURATION_TICKS = BUILDER.comment("Bleeding duration applied by Barbed.").defineInRange("barbedBleedDurationTicks", 100, 1, 1200);
        BARBED_BLEED_DAMAGE = BUILDER.comment("Damage dealt by each Bleeding tick per Barbed level.").defineInRange("barbedBleedDamage", 1.25, 0.0, 100.0);
        TYRANT_SEARCH_RADIUS = BUILDER.comment("Radius used to count nearby hostile mobs for Tyrant.").defineInRange("tyrantSearchRadius", 8.0, 1.0, 32.0);
        BUILDER.pop();

        BUILDER.comment("Configure the ogre faction anger and tribute systems.").push("Tribute");
        TRIBUTE_MAX_ANGER           = BUILDER.comment("Maximum anger the ogre faction can store.").defineInRange("maxAnger", 30, 1, 100);
        TRIBUTE_DEFAULT_ANGER_DELTA = BUILDER.comment("Anger added when a tribute chest is looted (legacy fallback).").defineInRange("defaultAngerDelta", 1, -100, 100);
        TRIBUTE_CHEST_ANGER_DELTA   = BUILDER.comment("Anger added when a player loots a tribute chest.").defineInRange("tributeChestAngerDelta", 10, 0, 100);
        TRIBUTE_CAMP_KILL_ANGER     = BUILDER.comment("Anger added when a player kills a camp ogre near the outpost.").defineInRange("campKillAnger", 1, 0, 100);
        TRIBUTE_CAMP_KILL_RADIUS    = BUILDER.comment("Radius in blocks around a camp origin that counts as a camp kill.").defineInRange("campKillRadius", 64, 16, 256);
        TRIBUTE_PATROL_CHANCE       = BUILDER.comment("Chance (0.0-1.0) that looting a tribute chest immediately spawns a patrol.").defineInRange("tributePatrolChance", 0.20, 0.0, 1.0);
        ANGER_DECAY_INTERVAL_TICKS  = BUILDER.comment("How many ticks between each passive ogre faction anger decay of -1 (48000 = 2 in-game days).").defineInRange("angerDecayIntervalTicks", 48000, 1200, 20 * 60 * 60 * 24);
        ANGER_HUD_RADIUS            = BUILDER.comment("Radius in blocks within which a player near any camp sees the faction anger HUD.").defineInRange("angerHudRadius", 80, 16, 512);
        BUILDER.pop();

        BUILDER.comment("Configure structure discovery and map availability.").push("Worldgen");
        BUILDER.push("Fort Map Discovery");
        SWAMP_OUTPOST_FORT_MAP_CHANCE = BUILDER
                .comment("Chance (0.0-1.0) that a swamp outpost's tribute chest contains an Ogre King's Fort map.")
                .defineInRange("swampOutpostMapChance", 0.30, 0.0, 1.0);
        CARTOGRAPHER_FORT_MAP_ENABLED = BUILDER
                .comment("Whether cartographers can sell an Ogre King's Fort map.")
                .define("cartographerTradeEnabled", true);
        CARTOGRAPHER_FORT_MAP_PRICE = BUILDER
                .comment("Emerald cost of the cartographer fort map trade. The trade also requires one compass.")
                .defineInRange("cartographerTradePrice", 48, 1, 64);
        CARTOGRAPHER_FORT_MAP_LEVEL = BUILDER
                .comment("Villager level for the fort map trade (5 is Master).")
                .defineInRange("cartographerTradeLevel", 5, 1, 5);
        BUILDER.pop(2);

        BUILDER.comment("Configure patrol spawning caused by ogre faction anger.").push("Patrols");
        PATROLS_ENABLED             = BUILDER.comment("Whether angered camps can send patrols after nearby players.").define("enabled", true);
        PATROL_CHECK_INTERVAL_TICKS = BUILDER.comment("How often loaded camps check whether to spawn a patrol (ticks).").defineInRange("checkIntervalTicks", 200, 20, 20 * 60 * 10);
        PATROL_MIN_COOLDOWN_TICKS   = BUILDER.comment("Minimum cooldown between patrols from the same camp (ticks).").defineInRange("minCooldownTicks", 20 * 60 * 5, 20 * 10, 20 * 60 * 60);
        PATROL_MAX_COOLDOWN_TICKS   = BUILDER.comment("Maximum cooldown between patrols from the same camp (ticks).").defineInRange("maxCooldownTicks", 20 * 60 * 10, 20 * 10, 20 * 60 * 60);
        PATROL_PLAYER_SEARCH_RADIUS = BUILDER.comment("Maximum distance from a camp where players can be targeted for patrols.").defineInRange("playerSearchRadius", 384, 32, 1024);
        PATROL_MIN_SPAWN_DISTANCE   = BUILDER.comment("Minimum distance from the target player where patrols spawn.").defineInRange("minSpawnDistance", 28, 8, 128);
        PATROL_MAX_SPAWN_DISTANCE   = BUILDER.comment("Maximum distance from the target player where patrols spawn.").defineInRange("maxSpawnDistance", 48, 8, 192);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    public static boolean isLoaded() {
        return SPEC.isLoaded();
    }
}
