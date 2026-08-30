package com.kingsandmonsters.entity;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.control.MoveControl;

final class OgreGruntMoveControl extends MoveControl {
    private final OgreGrunt grunt;

    OgreGruntMoveControl(OgreGrunt grunt) {
        super(grunt);
        this.grunt = grunt;
    }

    @Override
    protected float rotlerp(float currentAngle, float targetAngle, float maxSpeed) {
        float angle = Mth.wrapDegrees(targetAngle - currentAngle);
        float lerp = grunt.usesRunningTurnSmoothing() ? 0.35F : 0.12F;
        return currentAngle + angle * lerp;
    }
}
