package com.example.blockminer.feature.impl.AutoMobKiller.states;

import com.example.blockminer.feature.impl.AutoMobKiller.AutoMobKiller;
import com.example.blockminer.util.Logger;

public interface AutoMobKillerState {
	void onStart(AutoMobKiller mobKiller);

	AutoMobKillerState onTick(AutoMobKiller mobKiller);

	void onEnd(AutoMobKiller mobKiller);

	default void log(String message) {
		Logger.sendLog("[" + getClass().getSimpleName() + "] " + message);
	}

	default void logError(String message) {
		Logger.sendError("[" + getClass().getSimpleName() + "] " + message);
	}
}
