package com.example.blockminer.macro.impl.RouteMiner;

import com.example.blockminer.MineableBlock;
import com.example.blockminer.feature.impl.AutoGetStats.AutoGetStats;
import com.example.blockminer.macro.AbstractMacro;
import com.example.blockminer.macro.impl.MiningMacro;
import com.example.blockminer.macro.impl.RouteMiner.states.StartingState;
import com.example.blockminer.macro.impl.RouteMiner.states.RouteMinerState;
import com.example.blockminer.util.FeatureInventory;
import com.example.blockminer.util.Strings;
import com.example.blockminer.util.helper.route.Route;
import com.example.blockminer.util.helper.route.RouteWaypoint;
import com.example.blockminer.util.helper.route.WaypointType;
import com.example.blockminer.handler.RouteHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RouteMinerMacro extends AbstractMacro {
	private static final RouteMinerMacro INSTANCE = new RouteMinerMacro();
	private RouteMinerState currentState;
	private int routeIndex;
	private int targetRouteIndex;
	private int miningSpeed;
	private RouteWaypoint targetWaypoint;

	private RouteMinerMacro() {
	}

	public static RouteMinerMacro getInstance() {
		return INSTANCE;
	}

	@Override
	public String getName() {
		return "Route Miner";
	}

	@Override
	public List<String> getNecessaryItems() {
		List<String> items = new ArrayList<>();
		if (!com.example.blockminer.Config.miningTool.isBlank()) {
			items.add(com.example.blockminer.Config.miningTool);
		}
		Route route = RouteHandler.getInstance().getSelectedRoute();
		if (route != null && route.getWaypoints().stream().anyMatch(waypoint ->
				waypoint.getTransportMethod() == WaypointType.AOTV
						|| waypoint.getTransportMethod() == WaypointType.ETHERWARP)) {
			items.add(Strings.ASPECT_OF_THE_VOID_ITEM_NAME);
		}
		return List.copyOf(items);
	}

	@Override
	public void onEnable() {
		Route route = RouteHandler.getInstance().getSelectedRoute();
		if (route == null || route.isEmpty()) {
			disable("The selected route is empty. Add route waypoints before starting Route Miner.");
			return;
		}
		if (mc.player != null && !FeatureInventory.isMiningToolPresent(mc)) {
			disable("Mining tool must be in hotbar slot 2.");
			return;
		}
		boolean needsTeleport = route.getWaypoints().stream().anyMatch(waypoint ->
				waypoint.getTransportMethod() == WaypointType.AOTV
						|| waypoint.getTransportMethod() == WaypointType.ETHERWARP);
		if (needsTeleport && mc.player != null && !FeatureInventory.isTeleportToolPresent(mc)) {
			disable("Teleport tool must be in hotbar slot 3.");
			return;
		}
		miningSpeed = 0;
		targetWaypoint = null;
		routeIndex = route.getClosest(com.example.blockminer.PlayerUtil.getBlockStandingOn(mc.player))
				.map(route::indexOf)
				.orElse(0);
		transitionTo(new StartingState());
	}

	@Override
	public void onDisable() {
		if (currentState != null) {
			currentState.onEnd(this);
			currentState = null;
		}
		AutoGetStats.getInstance().stop();
	}

	@Override
	public void onTick(Minecraft client) {
		if (currentState == null || isTimerRunning()) {
			return;
		}
		RouteMinerState activeState = currentState;
		RouteMinerState nextState = activeState.onTick(this);
		if (isEnabled() && currentState == activeState && nextState != activeState && nextState != null) {
			transitionTo(nextState);
		}
	}

	public void transitionTo(RouteMinerState nextState) {
		if (nextState == currentState) {
			return;
		}
		String previous = currentState == null ? "None" : currentState.getClass().getSimpleName();
		if (currentState != null) {
			currentState.onEnd(this);
		}
		currentState = nextState;
		if (nextState != null) {
			reportStateTransition(previous, nextState.getClass().getSimpleName());
			nextState.onStart(this);
		}
	}

	public Route getRoute() {
		return RouteHandler.getInstance().getSelectedRoute();
	}

	public int getRouteIndex() {
		return routeIndex;
	}

	public void setRouteIndex(int routeIndex) {
		Route route = getRoute();
		if (route == null || route.isEmpty()) {
			throw new IllegalStateException("Cannot set an index for an empty route.");
		}
		this.routeIndex = Math.floorMod(routeIndex, route.size());
	}

	public int getTargetRouteIndex() {
		return targetRouteIndex;
	}

	public RouteWaypoint getTargetWaypoint() {
		return targetWaypoint;
	}

	public void setTargetWaypoint(int index) {
		Route route = getRoute();
		if (route == null || route.isEmpty()) {
			throw new IllegalStateException("Cannot select a waypoint from an empty route.");
		}
		targetRouteIndex = Math.floorMod(index, route.size());
		targetWaypoint = route.get(targetRouteIndex);
	}

	public void setMiningSpeed(int miningSpeed) {
		this.miningSpeed = miningSpeed;
	}

	public int getMiningSpeed() {
		return miningSpeed;
	}

	public Map<Block, Integer> getBlockPriorities() {
		Map<Block, Integer> priorities = new LinkedHashMap<>();
		for (MineableBlock type : MiningMacro.getConfiguredBlocks()) {
			type.blocks().forEach(block -> priorities.put(block, 1));
		}
		return Map.copyOf(priorities);
	}
}
