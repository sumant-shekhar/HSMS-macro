package com.example.blockminer;

public final class ApplyAbilityState implements BlockMinerState {
	@Override
	public BlockMinerState onTick(BlockMiner miner) {
		return new ChoosingBlockState();
	}
}
