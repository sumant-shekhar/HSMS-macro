package com.example.blockminer.macro.impl.CommissionMacro.states;

import com.example.blockminer.macro.impl.CommissionMacro.CommissionMacro;
import com.example.blockminer.util.StatsUtil;

public final class GettingStatsState implements CommissionMacroState {
	@Override
	public void onStart(CommissionMacro macro) {
		macro.setMiningSpeed(StatsUtil.getMiningSpeed());
	}

	@Override
	public CommissionMacroState onTick(CommissionMacro macro) {
		if (macro.getMiningSpeed() == 0) {
			macro.setMiningSpeed(StatsUtil.getMiningSpeed());
		}
		return new PathingState();
	}

	@Override
	public void onEnd(CommissionMacro macro) {
	}
}
