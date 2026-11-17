package com.example.blockminer.macro.impl.CommissionMacro.states;

import com.example.blockminer.Config;
import com.example.blockminer.PlayerUtil;
import com.example.blockminer.feature.impl.RouteNavigator;
import com.example.blockminer.handler.GraphHandler;
import com.example.blockminer.macro.impl.CommissionMacro.Commission;
import com.example.blockminer.macro.impl.CommissionMacro.CommissionMacro;
import com.example.blockminer.util.helper.route.Route;
import com.example.blockminer.util.helper.route.RouteWaypoint;
import com.example.blockminer.Clock;
import net.minecraft.client.Minecraft;

import java.util.List;

public final class PathingState implements CommissionMacroState {
	private RouteWaypoint target;
	private boolean waitingForCommission;
	private List<RouteWaypoint> navigationPath = List.of();
	private int attempts;
	private final Clock waitClock = new Clock();

	@Override
	public void onStart(CommissionMacro macro) {
		Commission commission = macro.getCurrentCommission();
		if (commission == null) {
			waitingForCommission = true;
			waitClock.schedule(10_000L);
			return;
		}
		if (commission == Commission.COMMISSION_CLAIM && Config.commClaimMethod == 1) {
			return;
		}
		target = commission.getWaypoint();
		if (commission == Commission.COMMISSION_CLAIM || commission == Commission.REFUEL) {
			navigationPath = List.of(target);
		} else {
			navigationPath = GraphHandler.getInstance().findPathFrom(
					"Commission Macro", PlayerUtil.getBlockStandingOn(Minecraft.getInstance().player), target);
			if (navigationPath.isEmpty()) {
				navigationPath = List.of(target);
			}
		}
		startNavigation(navigationPath);
	}

	@Override
	public CommissionMacroState onTick(CommissionMacro macro) {
		if (waitingForCommission) {
			if (macro.getCurrentCommission() != null) {
				return new PathingState();
			}
			if (waitClock.passed()) {
				macro.disable("No supported commission appeared in the tab list within 10 seconds.");
			}
			return this;
		}
		Commission commission = macro.getCurrentCommission();
		if (commission == Commission.COMMISSION_CLAIM && Config.commClaimMethod == 1) {
			return new ClaimingCommissionState();
		}
		RouteNavigator navigator = RouteNavigator.getInstance();
		if (navigator.isRunning()) {
			return this;
		}
		if (!navigator.succeeded()) {
			if (++attempts >= 3) {
				return new NewLobbyState();
			}
			startNavigation(navigationPath);
			return this;
		}
		if (commission == null) {
			return new StartingState();
		}
		if (commission == Commission.COMMISSION_CLAIM) {
			return new ClaimingCommissionState();
		}
		if (commission == Commission.REFUEL) {
			return new RefuelState();
		}
		return commission.isMining() ? new MiningState() : new MobKillingState();
	}

	@Override
	public void onEnd(CommissionMacro macro) {
		RouteNavigator.getInstance().stop();
	}

	private void startNavigation(RouteWaypoint waypoint) {
		startNavigation(List.of(waypoint));
	}

	private void startNavigation(List<RouteWaypoint> waypoints) {
		RouteNavigator.getInstance().start(new Route(waypoints));
	}
}
