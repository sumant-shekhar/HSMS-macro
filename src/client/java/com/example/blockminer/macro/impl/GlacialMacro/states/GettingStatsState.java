package com.example.blockminer.macro.impl.GlacialMacro.states;

import com.example.blockminer.macro.impl.GlacialMacro.GlacialMacro;
import com.example.blockminer.util.StatsUtil;

public final class GettingStatsState implements GlacialMacroState {
	@Override
	public void onStart(GlacialMacro macro) {
		macro.setMiningSpeed(StatsUtil.getMiningSpeed());
	}

	@Override
	public GlacialMacroState onTick(GlacialMacro macro) {
		if (macro.getMiningSpeed() == 0) {
			macro.setMiningSpeed(StatsUtil.getMiningSpeed());
		}
		return new PathfindingState();
	}

	@Override
	public void onEnd(GlacialMacro macro) {
	}
}
