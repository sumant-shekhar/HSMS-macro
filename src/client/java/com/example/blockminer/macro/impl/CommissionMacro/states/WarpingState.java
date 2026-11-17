package com.example.blockminer.macro.impl.CommissionMacro.states;

import com.example.blockminer.feature.impl.AutoWarp;
import com.example.blockminer.location.SubLocation;
import com.example.blockminer.macro.impl.CommissionMacro.CommissionMacro;

public final class WarpingState implements CommissionMacroState {
	private int attempts;

	@Override
	public void onStart(CommissionMacro macro) {
		AutoWarp.getInstance().start(null, SubLocation.THE_FORGE);
	}

	@Override
	public CommissionMacroState onTick(CommissionMacro macro) {
		AutoWarp warp = AutoWarp.getInstance();
		if (warp.isRunning()) {
			return this;
		}
		if (warp.hasSucceeded()) {
			return new StartingState();
		}
		if (warp.getFailReason() == AutoWarp.Error.FAILED_TO_WARP && ++attempts < 3) {
			warp.start(null, SubLocation.THE_FORGE);
			return this;
		}
		macro.disable("Could not warp to the Forge: " + warp.getFailReason());
		return this;
	}

	@Override
	public void onEnd(CommissionMacro macro) {
		AutoWarp.getInstance().stop();
	}
}
