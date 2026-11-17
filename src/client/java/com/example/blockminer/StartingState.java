package com.example.blockminer;

public final class StartingState implements BlockMinerState {
	@Override
	public BlockMinerState onTick(BlockMiner miner) {
		if (miner.shouldApplyAbility()) {
			return new ApplyAbilityState();
		}
		return new ChoosingBlockState();
	}
}
