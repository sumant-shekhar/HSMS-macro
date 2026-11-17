package com.example.blockminer.macro.impl.RouteMiner.states;

import com.example.blockminer.macro.impl.RouteMiner.RouteMinerMacro;

public final class StartingState implements RouteMinerState {
	@Override
	public void onStart(RouteMinerMacro macro) {
	}

	@Override
	public RouteMinerState onTick(RouteMinerMacro macro) {
		return macro.getMiningSpeed() == 0 ? new GettingStatsState() : new MovingState();
	}

	@Override
	public void onEnd(RouteMinerMacro macro) {
	}
}
