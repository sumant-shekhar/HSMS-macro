package com.example.blockminer.macro.impl.RouteMiner.states;

import com.example.blockminer.macro.impl.RouteMiner.RouteMinerMacro;
import com.example.blockminer.util.StatsUtil;

public final class GettingStatsState implements RouteMinerState {
	@Override
	public void onStart(RouteMinerMacro macro) {
		macro.setMiningSpeed(StatsUtil.getMiningSpeed());
	}

	@Override
	public RouteMinerState onTick(RouteMinerMacro macro) {
		if (macro.getMiningSpeed() == 0) {
			macro.setMiningSpeed(StatsUtil.getMiningSpeed());
		}
		return new MovingState();
	}

	@Override
	public void onEnd(RouteMinerMacro macro) {
	}
}
