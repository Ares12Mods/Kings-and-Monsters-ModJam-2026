package com.kingsandmonsters.world;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;

final class OgreMerchantSpawnData extends SavedData {
    static final String FILE_ID = "kingsandmonsters_ogre_merchant_spawns";
    private long nextAttemptGameTime;

    static OgreMerchantSpawnData load(CompoundTag tag, HolderLookup.Provider registries) {
        OgreMerchantSpawnData data = new OgreMerchantSpawnData();
        data.nextAttemptGameTime = tag.getLongOr("NextAttemptGameTime", 0L);
        return data;
    }

    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLong("NextAttemptGameTime", nextAttemptGameTime);
        return tag;
    }

    long nextAttemptGameTime() { return nextAttemptGameTime; }
    void schedule(long gameTime) { nextAttemptGameTime = gameTime; setDirty(); }
}
