package com.example.blockminer;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Objects;

public record BlockCandidate(
		BlockPos position,
		BlockState state,
		double score,
		double hardness,
		double distanceSquared,
		double angleChange
) {
	public BlockCandidate {
		Objects.requireNonNull(position, "position");
		Objects.requireNonNull(state, "state");
		if (!Double.isFinite(score) || !Double.isFinite(hardness)
				|| !Double.isFinite(distanceSquared) || !Double.isFinite(angleChange)) {
			throw new IllegalArgumentException("Candidate metrics must be finite");
		}
	}
}
