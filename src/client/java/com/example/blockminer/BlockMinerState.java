package com.example.blockminer;

public interface BlockMinerState {
	default void onStart(BlockMiner miner) {
	}

	BlockMinerState onTick(BlockMiner miner);

	default void onEnd(BlockMiner miner) {
	}
}
