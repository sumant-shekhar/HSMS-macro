package com.example.blockminer.macro.impl.GlacialMacro.states;

import com.example.blockminer.feature.impl.AutoCommissionClaim;
import com.example.blockminer.macro.impl.GlacialMacro.GlacialMacro;

public final class ClaimingCommissionState implements GlacialMacroState {
	private int retries;

	@Override
	public void onStart(GlacialMacro macro) {
		startClaim();
	}

	@Override
	public GlacialMacroState onTick(GlacialMacro macro) {
		AutoCommissionClaim claimer = AutoCommissionClaim.getInstance();
		if (claimer.isRunning()) {
			return this;
		}
		if (claimer.succeeded()) {
			macro.incrementCommissionCounter();
			macro.updateMiningTasks();
			return new PathfindingState();
		}
		if (++retries <= 3) {
			startClaim();
			return this;
		}
		return new ErrorHandlingState("Failed to claim a Glacial commission after three attempts: "
				+ claimer.claimError());
	}

	private void startClaim() {
		AutoCommissionClaim.getInstance().start();
	}

	@Override
	public void onEnd(GlacialMacro macro) {
		if (AutoCommissionClaim.getInstance().isRunning()) {
			AutoCommissionClaim.getInstance().stop();
		}
	}
}
