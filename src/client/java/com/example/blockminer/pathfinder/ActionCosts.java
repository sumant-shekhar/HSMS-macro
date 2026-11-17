package com.example.blockminer.pathfinder;

public final class ActionCosts {
    public static final double INF_COST = 1e6;
    public final double[] N_BLOCK_FALL_COST = generateNBlocksFallCost();
    public final double ONE_UP_LADDER_COST = 1 / (0.12 * 9.8);
    public final double ONE_DOWN_LADDER_COST = 1 / 0.15;

    public final double JUMP_ONE_BLOCK_COST;
    public final double ONE_BLOCK_WALK_COST;
    public final double ONE_BLOCK_SPRINT_COST;
    public final double ONE_BLOCK_SNEAK_COST;
    public final double ONE_BLOCK_WALK_IN_WATER_COST;
    public final double ONE_BLOCK_WALK_OVER_SOUL_SAND_COST;
    public final double WALK_OFF_ONE_BLOCK_COST;
    public final double CENTER_AFTER_FALL_COST;
    public final double SPRINT_MULTIPLIER;

    public ActionCosts() {
        this(0.13, 0.1, 0.03, -1);
    }

    public ActionCosts(double sprintMovementFactor, double walkingMovementFactor,
                       double sneakingMovementFactor, int jumpBoostLevel) {
        ONE_BLOCK_WALK_COST = 1 / actionTime(getWalkingFriction(walkingMovementFactor));
        ONE_BLOCK_SPRINT_COST = 1 / actionTime(getWalkingFriction(sprintMovementFactor));
        ONE_BLOCK_SNEAK_COST = 1 / actionTime(getWalkingFriction(sneakingMovementFactor));
        ONE_BLOCK_WALK_IN_WATER_COST = 20 * actionTime(getWalkingInWaterFriction(walkingMovementFactor));
        ONE_BLOCK_WALK_OVER_SOUL_SAND_COST = ONE_BLOCK_WALK_COST * 2;
        WALK_OFF_ONE_BLOCK_COST = ONE_BLOCK_WALK_COST * 0.8;
        CENTER_AFTER_FALL_COST = ONE_BLOCK_WALK_COST * 0.2;
        SPRINT_MULTIPLIER = walkingMovementFactor / sprintMovementFactor;

        double velocity = 0.42 + (jumpBoostLevel + 1) * 0.1;
        double height = 0.0;
        double time = 1.0;
        for (int tick = 1; tick <= 20; tick++) {
            height += velocity;
            velocity = (velocity - 0.08) * 0.98;
            if (velocity < 0) {
                break;
            }
            time++;
        }
        JUMP_ONE_BLOCK_COST = time + fallDistanceToTicks(height - 1);
    }

    private static double getWalkingFriction(double landMovementFactor) {
        return landMovementFactor * (0.16277136 / (0.91 * 0.91 * 0.91));
    }

    private static double getWalkingInWaterFriction(double landMovementFactor) {
        return 0.02 + (landMovementFactor - 0.02) * (1.0 / 3.0);
    }

    private static double actionTime(double friction) {
        return friction * 10;
    }

    public double motionYAtTick(int tick) {
        double velocity = -0.0784000015258789;
        for (int i = 1; i <= tick; i++) {
            velocity = (velocity - 0.08) * 0.9800000190734863;
        }
        return velocity;
    }

    public double fallDistanceToTicks(double distance) {
        if (distance == 0.0) {
            return 0.0;
        }
        double remainingDistance = distance;
        int tickCount = 0;
        while (true) {
            double fallDistance = downwardMotionAtTick(tickCount);
            if (remainingDistance <= fallDistance) {
                return tickCount + remainingDistance / fallDistance;
            }
            remainingDistance -= fallDistance;
            tickCount++;
        }
    }

    private static double downwardMotionAtTick(int tick) {
        return (Math.pow(0.98, tick) - 1) * -3.92;
    }

    private static double[] generateNBlocksFallCost() {
        double[] timeCost = new double[257];
        double currentDistance = 0.0;
        int targetDistance = 1;
        int tickCount = 0;

        while (true) {
            double velocityAtTick = downwardMotionAtTick(tickCount);
            if (currentDistance + velocityAtTick >= targetDistance) {
                timeCost[targetDistance] = tickCount + (targetDistance - currentDistance) / velocityAtTick;
                targetDistance++;
                if (targetDistance > 256) {
                    break;
                }
                continue;
            }
            currentDistance += velocityAtTick;
            tickCount++;
        }
        return timeCost;
    }
}
