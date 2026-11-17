package com.example.blockminer.macro.impl.RouteMiner.states;

import com.example.blockminer.macro.impl.RouteMiner.RouteMinerMacro;

public interface RouteMinerState {
	void onStart(RouteMinerMacro macro);

	RouteMinerState onTick(RouteMinerMacro macro);

	void onEnd(RouteMinerMacro macro);
}
