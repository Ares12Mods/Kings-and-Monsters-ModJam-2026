package com.kingsandmonsters.entity.client;

import com.kingsandmonsters.entity.OgreLord;

public class OgreLordModel extends OgreModel<OgreLord> {
    public OgreLordModel() {
        super("ogre_lord");
    }
    // Club-hand/club-back bone visibility is applied in OgreLordRenderer#adjustModelBonesForRender,
    // where GeckoLib 5 guarantees a valid BoneSnapshot exists — see that method for why.
}
