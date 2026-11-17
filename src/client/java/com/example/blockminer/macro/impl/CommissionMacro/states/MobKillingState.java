package com.example.blockminer.macro.impl.CommissionMacro.states;

import com.example.blockminer.Config;
import com.example.blockminer.feature.impl.AutoMobKiller.AutoMobKiller;
import com.example.blockminer.macro.impl.CommissionMacro.Commission;
import com.example.blockminer.macro.impl.CommissionMacro.CommissionMacro;
import com.example.blockminer.util.CommissionUtil;

public final class MobKillingState implements CommissionMacroState {
	private Commission activeCommission;

	@Override
	public void onStart(CommissionMacro macro) {
		if (macro.getCurrentCommission() == null) {
			macro.disable("Cannot start mob killing without a commission.");
			return;
		}
		activeCommission = macro.getCurrentCommission();
		AutoMobKiller.getInstance().start(
				CommissionUtil.getMobForCommission(activeCommission.getName()),
				Config.slayerWeapon);
	}

	@Override
	public CommissionMacroState onTick(CommissionMacro macro) {
		if (macro.getCurrentCommission() == Commission.COMMISSION_CLAIM
				|| macro.getCurrentCommission() != null && macro.getCurrentCommission() != activeCommission) {
			return new PathingState();
		}
		if (AutoMobKiller.getInstance().isRunning()) {
			return this;
		}
		if (macro.getCurrentCommission() == null) {
			return new StartingState();
		}
		if (AutoMobKiller.getInstance().getError() == AutoMobKiller.MKError.NO_ENTITIES) {
			return new StartingState();
		}
		AutoMobKiller.getInstance().start(
				CommissionUtil.getMobForCommission(macro.getCurrentCommission().getName()),
				Config.slayerWeapon);
		return this;
	}

	@Override
	public void onEnd(CommissionMacro macro) {
		AutoMobKiller.getInstance().stop();
	}
}
