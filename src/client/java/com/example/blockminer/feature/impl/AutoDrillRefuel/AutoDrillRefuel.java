package com.example.blockminer.feature.impl.AutoDrillRefuel;

import com.example.blockminer.feature.AbstractFeature;
import com.example.blockminer.feature.impl.AutoDrillRefuel.states.AutoDrillRefuelState;
import com.example.blockminer.feature.impl.AutoDrillRefuel.states.StartingState;
import com.example.blockminer.util.Strings;
import net.minecraft.client.Minecraft;

public final class AutoDrillRefuel extends AbstractFeature {
	private static final AutoDrillRefuel INSTANCE = new AutoDrillRefuel();
	private FuelType fuelType;
	private String drillName;
	private AutoDrillRefuelState currentState;
	private AutoDrillRefuelError error = AutoDrillRefuelError.NONE;

	private AutoDrillRefuel() {
	}

	public static AutoDrillRefuel getInstance() {
		return INSTANCE;
	}

	@Override
	public String getName() {
		return "AutoDrillRefuel";
	}

	public void start(String drillName, FuelType fuelType) {
		if (drillName == null || drillName.isBlank() || fuelType == null) {
			sendError("Provide a drill name and fuel type.");
			return;
		}
		stop();
		this.drillName = drillName;
		this.fuelType = fuelType;
		error = AutoDrillRefuelError.NONE;
		enabled = true;
		setState(new StartingState());
	}

	@Override
	public void stop() {
		if (currentState != null) {
			currentState.onEnd(this);
		}
		currentState = null;
		enabled = false;
		timer.reset();
	}

	@Override
	public void resetStatesAfterStop() {
		currentState = null;
		timer.reset();
	}

	public void fail(AutoDrillRefuelError failure) {
		error = failure;
		stop();
	}

	public void setError(AutoDrillRefuelError error) {
		this.error = error;
	}

	public AutoDrillRefuelError getError() {
		return error;
	}

	public FuelType getFuelType() {
		return fuelType;
	}

	public String getDrillName() {
		return drillName;
	}

	public void transitionTo(AutoDrillRefuelState nextState) {
		if (currentState == nextState) {
			return;
		}
		if (currentState != null) {
			currentState.onEnd(this);
		}
		setState(nextState);
	}

	@Override
	protected void onTick(Minecraft client) {
		if (client.player == null || client.level == null || client.gameMode == null) {
			fail(AutoDrillRefuelError.NO_DRILL);
			return;
		}
		if (currentState != null) {
			AutoDrillRefuelState activeState = currentState;
			AutoDrillRefuelState nextState = activeState.onTick(this);
			if (enabled && currentState == activeState) {
				transitionTo(nextState);
			}
		}
	}

	private void setState(AutoDrillRefuelState nextState) {
		currentState = nextState;
		if (currentState != null) {
			currentState.onStart(this);
		}
	}

	public enum FuelType {
		VOLTA(Strings.VOLTA_FUEL_ITEM_NAME),
		OIL_BARREL(Strings.OIL_BARREL_FUEL_ITEM_NAME);

		private final String name;

		FuelType(String name) {
			this.name = name;
		}

		public String getName() {
			return name;
		}
	}

	public enum AutoDrillRefuelError {
		NONE,
		NO_DRILL,
		NO_FUEL,
		NO_ABIPHONE,
		NO_GREATFORGE_CONTACT,
		INACCESSIBLE_ANVIL
	}
}
