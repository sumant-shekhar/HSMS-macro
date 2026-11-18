package com.example.blockminer.macro.impl.GlacialMacro.states;

import com.example.blockminer.PlayerUtil;
import com.example.blockminer.feature.impl.RouteNavigator;
import com.example.blockminer.handler.GraphHandler;
import com.example.blockminer.macro.impl.GlacialMacro.GlacialMacro;
import com.example.blockminer.macro.impl.GlacialMacro.GlacialMacro.VeinTarget;
import com.example.blockminer.util.FeatureInventory;
import com.example.blockminer.util.Strings;
import com.example.blockminer.util.helper.route.Route;
import com.example.blockminer.util.helper.route.RouteWaypoint;
import com.example.blockminer.util.helper.route.WaypointType;
import net.minecraft.client.Minecraft;

import java.util.List;

public final class PathfindingState implements GlacialMacroState {
	private boolean navigating;
	private int pathingFailures;

	@Override
	public void onStart(GlacialMacro macro) {
		FeatureInventory.holdTeleportTool(Minecraft.getInstance());
		RouteNavigator.getInstance().stop();
		macro.updateMiningTasks();
		navigating = false;
	}

	@Override
	public GlacialMacroState onTick(GlacialMacro macro) {
		if (macro.getCold() >= macro.getColdThreshold()) {
			return new TeleportingState(new PathfindingState());
		}
		if (macro.hasCompletedCommission()) {
			return new ClaimingCommissionState();
		}
		if (navigating) {
			RouteNavigator navigator = RouteNavigator.getInstance();
			if (navigator.isRunning()) {
				return this;
			}
			navigating = false;
			if (navigator.succeeded()) {
				pathingFailures = 0;
				return new MiningState();
			}
			VeinTarget failedVein = macro.getCurrentVein();
			if (failedVein != null) {
				macro.getPreviousVeins().put(failedVein, System.currentTimeMillis());
			}
			if (++pathingFailures >= 5) {
				return new NewLobbyState();
			}
		}

		VeinTarget target = macro.findBestVein();
		if (target == null) {
			return new NewLobbyState();
		}
		macro.setCurrentVein(target);
		if (target.waypoint().isWithinRange(PlayerUtil.getBlockStandingOn(Minecraft.getInstance().player), 2)) {
			return new MiningState();
		}
		List<RouteWaypoint> path = GraphHandler.getInstance().findPathFrom(
				macro.getName(), PlayerUtil.getBlockStandingOn(Minecraft.getInstance().player), target.waypoint());
		if (path.isEmpty()) {
			pathingFailures++;
			macro.getPreviousVeins().put(target, System.currentTimeMillis());
			return pathingFailures >= 5 ? new NewLobbyState() : this;
		}
		boolean needsEtherwarp = path.stream().anyMatch(waypoint ->
				waypoint.getTransportMethod() == WaypointType.ETHERWARP
						|| waypoint.getTransportMethod() == WaypointType.AOTV);
		if (needsEtherwarp
				&& !FeatureInventory.holdTeleportTool(Minecraft.getInstance())) {
			macro.disable("Teleport tool is missing from hotbar slot 3.");
			return this;
		}
		RouteNavigator.getInstance().start(new Route(path));
		navigating = true;
		return this;
	}

	@Override
	public void onEnd(GlacialMacro macro) {
		RouteNavigator.getInstance().stop();
	}
}
