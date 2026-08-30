package com.kingsandmonsters.entity;

import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.phys.Vec3;

final class OgreGruntNavigation extends GroundPathNavigation {
    private static final double NODE_LOOK_AHEAD_DISTANCE_SQR = 1.0;
    private static final double BRUTE_NODE_LOOK_AHEAD_DISTANCE_SQR = 1.25 * 1.25;
    private static final double KING_NODE_LOOK_AHEAD_DISTANCE_SQR = 1.75 * 1.75;

    OgreGruntNavigation(OgreGrunt grunt, Level level) {
        super(grunt, level);
    }

    @Override
    protected void followThePath() {
        if (path != null && path.getNextNodeIndex() + 1 < path.getNodeCount()) {
            Vec3 currentNode = path.getNextEntityPos(mob);
            Node nextNode = path.getNode(path.getNextNodeIndex() + 1);
            double dx = mob.getX() - currentNode.x;
            double dz = mob.getZ() - currentNode.z;
            double handoffDistanceSqr = mob instanceof OgreLord
                    ? KING_NODE_LOOK_AHEAD_DISTANCE_SQR
                    : mob instanceof OgreBrute ? BRUTE_NODE_LOOK_AHEAD_DISTANCE_SQR : NODE_LOOK_AHEAD_DISTANCE_SQR;
            if (dx * dx + dz * dz <= handoffDistanceSqr
                    && Math.abs(nextNode.y - currentNode.y) <= 1.0
                    && canCutCorner(nextNode.type)) {
                path.advance();
            }
        }
        super.followThePath();
    }
}
