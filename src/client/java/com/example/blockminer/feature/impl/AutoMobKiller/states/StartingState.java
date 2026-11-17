package com.example.blockminer.feature.impl.AutoMobKiller.states;

import com.example.blockminer.feature.impl.AutoMobKiller.AutoMobKiller;

public final class StartingState implements AutoMobKillerState {
	@Override
	public void onStart(AutoMobKiller mobKiller) {
		log("Selecting a target.");
	}

	@Override
	public AutoMobKillerState onTick(AutoMobKiller mobKiller) {
		return new FindMobState();
	}

	@Override
	public void onEnd(AutoMobKiller mobKiller) {
	}
}
