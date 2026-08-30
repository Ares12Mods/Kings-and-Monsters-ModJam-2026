package com.kingsandmonsters.client;

import com.kingsandmonsters.api.patrol.PatrolTier;

public final class AngerHudState {
    private static volatile int angerLevel;
    private static volatile int maxAngerLevel = 1;
    private static volatile boolean chiefAlive = true;
    private static volatile boolean nearOgreSite;
    private static boolean enabled;
    private static float opacity;

    private AngerHudState() {}

    public static void update(int newAngerLevel, int newMaxAngerLevel, boolean newChiefAlive) {
        nearOgreSite = newAngerLevel >= 0;
        if (nearOgreSite) {
            angerLevel = newAngerLevel;
            maxAngerLevel = Math.max(1, newMaxAngerLevel);
            chiefAlive = newChiefAlive;
        }
    }

    public static boolean isVisible() {
        return (nearOgreSite && enabled) || opacity > 0.01F;
    }

    public static float advanceFade() {
        float speed = nearOgreSite && enabled ? 0.08F : -0.04F;
        opacity = Math.clamp(opacity + speed, 0.0F, 1.0F);
        return opacity;
    }

    public static void setEnabled(boolean newEnabled) {
        enabled = newEnabled;
    }

    public static int getAngerLevel() {
        return angerLevel;
    }

    public static int getMaxAngerLevel() {
        return maxAngerLevel;
    }

    public static boolean isChiefAlive() {
        return chiefAlive;
    }

    public static String getAngerTierTranslationKey() {
        if (!chiefAlive) {
            return "hud.kingsandmonsters.ogre_anger_level_appeased";
        }
        return switch (PatrolTier.fromAnger(angerLevel, maxAngerLevel)) {
            case SMALL -> "hud.kingsandmonsters.ogre_anger_level_low";
            case MEDIUM -> "hud.kingsandmonsters.ogre_anger_level_moderate";
            case LARGE -> "hud.kingsandmonsters.ogre_anger_level_high";
        };
    }
}
