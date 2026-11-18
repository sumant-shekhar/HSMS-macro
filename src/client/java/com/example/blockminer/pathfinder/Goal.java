package com.example.blockminer.pathfinder;

public final class Goal implements IGoal {
	private static final double SQRT_2 = Math.sqrt(2.0);
	private final int goalX;
	private final int goalY;
	private final int goalZ;
	private final ActionCosts costs;

	public Goal(int goalX, int goalY, int goalZ, ActionCosts costs) {
		this.goalX = goalX;
		this.goalY = goalY;
		this.goalZ = goalZ;
		this.costs = costs;
	}

	@Override
	public boolean isAtGoal(int x, int y, int z) {
		return goalX == x && goalZ == z && (goalY == y || goalY == y + 1);
	}

	@Override
	public double heuristic(int x, int y, int z) {
		int dx = Math.abs(goalX - x);
		int dz = Math.abs(goalZ - z);
		double straight = Math.abs(dx - dz);
		double vertical = Math.min(Math.abs(goalY - y), Math.abs(goalY - (y + 1)));
		double diagonal = Math.min(dx, dz);

		if (goalY > y + 1) {
			vertical *= 6.234399666206506;
		} else {
			vertical *= costs.N_BLOCK_FALL_COST[2] / 2.0;
		}

		return (straight + diagonal * SQRT_2) * costs.ONE_BLOCK_SPRINT_COST + vertical;
	}

	@Override
	public String toString() {
		return "x: " + goalX + ", y: " + goalY + ", z: " + goalZ;
	}
}
