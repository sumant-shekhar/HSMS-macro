package com.example.blockminer.macro.impl.RouteMiner.states;

import com.example.blockminer.feature.impl.RouteNavigator;
import com.example.blockminer.macro.impl.RouteMiner.RouteMinerMacro;
import com.example.blockminer.util.FeatureInventory;
import com.example.blockminer.util.Strings;
import com.example.blockminer.util.helper.route.Route;
import com.example.blockminer.util.helper.route.RouteWaypoint;
import com.example.blockminer.util.helper.route.WaypointType;
import net.minecraft.client.Minecraft;

import java.util.List;

public final class MovingState implements RouteMinerState {
	@Override
	public void onStart(RouteMinerMacro macro) {
		macro.setTargetWaypoint(macro.getRouteIndex() + 1);
		RouteWaypoint target = macro.getTargetWaypoint();
		if ((target.getTransportMethod() == WaypointType.AOTV
				|| target.getTransportMethod() == WaypointType.ETHERWARP)
				&& !FeatureInventory.holdTeleportTool(Minecraft.getInstance())) {
			macro.disable("Teleport tool is missing from hotbar slot 3.");
			return;
		}
		RouteNavigator.getInstance().start(new Route(List.of(target)));
	}

	@Override
	public RouteMinerState onTick(RouteMinerMacro macro) {
		RouteNavigator navigator = RouteNavigator.getInstance();
		if (navigator.isRunning()) {
			return this;
		}
		if (!navigator.succeeded()) {
			macro.disable("Route navigation failed: " + navigator.getNavError());
			return this;
		}
		if (macro.getTargetWaypoint().getTransportMethod()
				== com.example.blockminer.util.helper.route.WaypointType.MINE) {
			return new MiningState();
		}
		macro.setRouteIndex(macro.getTargetRouteIndex());
		return new MovingState();
	}

	@Override
	public void onEnd(RouteMinerMacro macro) {
		RouteNavigator.getInstance().stop();
	}
}
