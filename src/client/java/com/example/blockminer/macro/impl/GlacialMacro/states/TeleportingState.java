package com.example.blockminer.macro.impl.GlacialMacro.states;

import com.example.blockminer.feature.impl.AutoWarp;
import com.example.blockminer.location.SubLocation;
import com.example.blockminer.macro.impl.GlacialMacro.GlacialMacro;

public final class TeleportingState implements GlacialMacroState {
	private final GlacialMacroState nextState;
	private int retries;

	public TeleportingState(GlacialMacroState nextState) {
		this.nextState = nextState;
	}

	@Override
	public void onStart(GlacialMacro macro) {
		startWarp(macro);
	}

	@Override
	public GlacialMacroState onTick(GlacialMacro macro) {
		AutoWarp warp = AutoWarp.getInstance();
		if (warp.isRunning()) {
			if (macro.hasTimerEnded()) {
				warp.stop(AutoWarp.Error.FAILED_TO_WARP);
			} else {
				return this;
			}
		}
		if (warp.hasSucceeded()) {
			return nextState;
		}
		if (++retries <= 3) {
			startWarp(macro);
			return this;
		}
		return new ErrorHandlingState("Failed to warp to Dwarven Base Camp after three attempts.");
	}

	private void startWarp(GlacialMacro macro) {
		AutoWarp.getInstance().start(null, SubLocation.DWARVEN_BASE_CAMP);
		macro.scheduleTimer(15_000L);
	}

	@Override
	public void onEnd(GlacialMacro macro) {
		if (AutoWarp.getInstance().isRunning()) {
			AutoWarp.getInstance().stop();
		}
	}
}
