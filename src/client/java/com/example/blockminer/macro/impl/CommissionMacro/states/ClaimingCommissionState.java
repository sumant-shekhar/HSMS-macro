package com.example.blockminer.macro.impl.CommissionMacro.states;

import com.example.blockminer.feature.impl.AutoCommissionClaim;
import com.example.blockminer.macro.impl.CommissionMacro.Commission;
import com.example.blockminer.macro.impl.CommissionMacro.CommissionMacro;

public final class ClaimingCommissionState implements CommissionMacroState {
	@Override
	public void onStart(CommissionMacro macro) {
		AutoCommissionClaim.getInstance().start();
	}

	@Override
	public CommissionMacroState onTick(CommissionMacro macro) {
		AutoCommissionClaim claimer = AutoCommissionClaim.getInstance();
		if (claimer.isRunning()) {
			return this;
		}
		if (claimer.succeeded()) {
			Commission next = claimer.getNextCommissions().stream()
					.map(Commission::getCommission)
					.filter(java.util.Objects::nonNull)
					.findFirst()
					.orElse(null);
			macro.setCurrentCommission(next);
			return new StartingState();
		}
		return switch (claimer.claimError()) {
			case INACCESSIBLE_NPC -> new WarpingState();
			case TIMEOUT -> new StartingState();
			case NONE, NO_ITEMS, NPC_NOT_UNLOCKED -> {
				macro.disable("Commission claiming failed: " + claimer.claimError());
				yield this;
			}
		};
	}

	@Override
	public void onEnd(CommissionMacro macro) {
		AutoCommissionClaim.getInstance().stop();
	}
}
