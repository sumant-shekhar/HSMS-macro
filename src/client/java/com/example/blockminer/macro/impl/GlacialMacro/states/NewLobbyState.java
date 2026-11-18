package com.example.blockminer.macro.impl.GlacialMacro.states;

import com.example.blockminer.feature.impl.AutoWarp;
import com.example.blockminer.location.Location;
import com.example.blockminer.location.SubLocation;
import com.example.blockminer.macro.impl.GlacialMacro.GlacialMacro;

public final class NewLobbyState implements GlacialMacroState {
	private Phase phase;

	@Override
	public void onStart(GlacialMacro macro) {
		macro.getPreviousVeins().clear();
		phase = Phase.HUB;
		AutoWarp.getInstance().start(Location.HUB, null);
	}

	@Override
	public GlacialMacroState onTick(GlacialMacro macro) {
		AutoWarp warp = AutoWarp.getInstance();
		if (warp.isRunning()) {
			return this;
		}
		if (!warp.hasSucceeded()) {
			return new ErrorHandlingState("Lobby change failed: " + warp.getFailReason());
		}
		if (phase == Phase.HUB) {
			phase = Phase.BASE;
			warp.start(null, SubLocation.DWARVEN_BASE_CAMP);
			return this;
		}
		if (macro.getMiningSpeed() == 0) {
			macro.setMiningSpeed(com.example.blockminer.util.StatsUtil.getMiningSpeed());
		}
		return new PathfindingState();
	}

	@Override
	public void onEnd(GlacialMacro macro) {
		if (AutoWarp.getInstance().isRunning()) {
			AutoWarp.getInstance().stop();
		}
	}

	private enum Phase {
		HUB,
		BASE
	}
}
