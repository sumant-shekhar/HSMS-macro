package com.example.blockminer.macro.impl.CommissionMacro.states;

import com.example.blockminer.Config;
import com.example.blockminer.feature.impl.AutoDrillRefuel.AutoDrillRefuel;
import com.example.blockminer.macro.impl.CommissionMacro.CommissionMacro;

public final class RefuelState implements CommissionMacroState {
	@Override
	public void onStart(CommissionMacro macro) {
		AutoDrillRefuel.FuelType[] fuels = AutoDrillRefuel.FuelType.values();
		if (Config.refuelMachineFuel < 0 || Config.refuelMachineFuel >= fuels.length) {
			macro.disable("Select a valid drill-refuel fuel type.");
			return;
		}
		AutoDrillRefuel.getInstance().start(Config.miningTool, fuels[Config.refuelMachineFuel]);
	}

	@Override
	public CommissionMacroState onTick(CommissionMacro macro) {
		AutoDrillRefuel refuel = AutoDrillRefuel.getInstance();
		if (refuel.isRunning()) {
			return this;
		}
		if (refuel.getError() == AutoDrillRefuel.AutoDrillRefuelError.NONE) {
			return new StartingState();
		}
		macro.disable("Drill refueling failed: " + refuel.getError());
		return this;
	}

	@Override
	public void onEnd(CommissionMacro macro) {
		AutoDrillRefuel.getInstance().stop();
	}
}
