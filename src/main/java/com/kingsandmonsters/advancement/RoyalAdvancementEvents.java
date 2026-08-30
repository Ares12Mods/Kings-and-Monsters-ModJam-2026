package com.kingsandmonsters.advancement;

import com.kingsandmonsters.KingsAndMonsters;
import com.kingsandmonsters.entity.OgreBrute;
import com.kingsandmonsters.entity.OgreGruntCaptain;
import com.kingsandmonsters.entity.OgreLord;
import com.kingsandmonsters.entity.OgreMage;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

public final class RoyalAdvancementEvents {
    private static final String CRITERION = "defeated";

    private RoyalAdvancementEvents() {
    }

    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (event.getEntity() instanceof OgreLord) {
            award(player, "usurped");
        } else if (event.getEntity() instanceof OgreMage) {
            award(player, "mage_captain");
        } else if (event.getEntity() instanceof OgreBrute) {
            award(player, "brute_captain");
        } else if (event.getEntity() instanceof OgreGruntCaptain) {
            award(player, "grunt_captain");
        }
    }

    private static void award(ServerPlayer player, String path) {
        Identifier id = Identifier.fromNamespaceAndPath(
                KingsAndMonsters.MODID, "royal_defenses/" + path);
        AdvancementHolder advancement = player.level().getServer().getAdvancements().get(id);
        if (advancement != null) {
            player.getAdvancements().award(advancement, CRITERION);
        }
    }
}
