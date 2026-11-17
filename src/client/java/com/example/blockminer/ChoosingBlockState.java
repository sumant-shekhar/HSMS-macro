package com.example.blockminer;

import java.util.List;

public final class ChoosingBlockState implements BlockMinerState {
	private final Clock waitTimer = new Clock();

	@Override
	public void onStart(BlockMiner miner) {
		waitTimer.reset();
	}

	@Override
	public BlockMinerState onTick(BlockMiner miner) {
		List<BlockCandidate> candidates = BlockUtil.findMineableBlocksFromAccessiblePositions(
				miner.getPriorities(),
				miner.getTargetBlockPos(),
				miner.getMiningSpeed()
		);
		if (candidates.isEmpty()) {
			if (!waitTimer.isScheduled()) {
				waitTimer.schedule(miner.getWaitThresholdMs());
			} else if (waitTimer.passed()) {
				miner.fail(BlockMiner.Error.NOT_ENOUGH_BLOCKS);
				return null;
			}
			return this;
		}

		BlockCandidate candidate = candidates.getFirst();
		miner.setTarget(candidate.position(), candidate.state().getBlock());
		return new BreakingState();
	}
}
