package com.example.blockminer.macro.impl.CommissionMacro.states;

import com.example.blockminer.feature.impl.AutoWarp;
import com.example.blockminer.location.Location;
import com.example.blockminer.location.SubLocation;
import com.example.blockminer.macro.impl.CommissionMacro.CommissionMacro;

public final class NewLobbyState implements CommissionMacroState {
	private Phase phase;

	@Override
	public void onStart(CommissionMacro macro) {
		phase = Phase.HUB;
		AutoWarp.getInstance().start(Location.HUB, null);
	}

	@Override
	public CommissionMacroState onTick(CommissionMacro macro) {
		AutoWarp warp = AutoWarp.getInstance();
		if (warp.isRunning()) {
			return this;
		}
		if (!warp.hasSucceeded()) {
			macro.disable("Could not change lobby: " + warp.getFailReason());
			return this;
		}
		if (phase == Phase.HUB) {
			phase = Phase.FORGE;
			warp.start(null, SubLocation.THE_FORGE);
			return this;
		}
		macro.setCurrentCommission(null);
		return new StartingState();
	}

	@Override
	public void onEnd(CommissionMacro macro) {
		AutoWarp.getInstance().stop();
	}

	private enum Phase {
		HUB,
		FORGE
	}
}
