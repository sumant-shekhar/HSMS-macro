package com.example.blockminer.feature.impl.AutoDrillRefuel.states;

import com.example.blockminer.feature.impl.AutoDrillRefuel.AutoDrillRefuel;
import com.example.blockminer.util.Logger;

public interface AutoDrillRefuelState {
	void onStart(AutoDrillRefuel refueler);

	AutoDrillRefuelState onTick(AutoDrillRefuel refueler);

	default void onEnd(AutoDrillRefuel refueler) {
	}

	default void log(String message) {
		Logger.sendLog("[" + getClass().getSimpleName() + "] " + message);
	}

	default void logError(String message) {
		Logger.sendError("[" + getClass().getSimpleName() + "] " + message);
	}
}
