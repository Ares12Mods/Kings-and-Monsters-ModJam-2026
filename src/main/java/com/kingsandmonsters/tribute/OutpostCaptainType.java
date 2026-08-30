package com.kingsandmonsters.tribute;

import com.kingsandmonsters.ModEntities;
import com.kingsandmonsters.entity.OgreGrunt;
import net.minecraft.world.entity.EntityType;

import java.util.Locale;

/** The single captain role permanently assigned to an Ogre outpost. */
public enum OutpostCaptainType {
    BRUTE,
    MAGE,
    GRUNT;

    public EntityType<? extends OgreGrunt> entityType() {
        return switch (this) {
            case BRUTE -> ModEntities.OGRE_BRUTE.get();
            case MAGE -> ModEntities.OGRE_MAGE.get();
            case GRUNT -> ModEntities.OGRE_GRUNT_CAPTAIN.get();
        };
    }

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static OutpostCaptainType fromSerializedName(String name) {
        for (OutpostCaptainType type : values()) {
            if (type.serializedName().equals(name)) {
                return type;
            }
        }
        return GRUNT;
    }
}
