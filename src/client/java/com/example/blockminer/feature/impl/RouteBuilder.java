package com.example.blockminer.feature.impl;

import com.example.blockminer.PlayerUtil;
import com.example.blockminer.feature.AbstractFeature;
import com.example.blockminer.handler.RouteHandler;
import com.example.blockminer.util.helper.route.Route;
import com.example.blockminer.util.helper.route.RouteWaypoint;
import com.example.blockminer.util.helper.route.WaypointType;
import net.minecraft.core.BlockPos;

import java.util.Optional;

public final class RouteBuilder extends AbstractFeature {
	private static final RouteBuilder INSTANCE = new RouteBuilder();

	private RouteBuilder() {
	}

	public static RouteBuilder getInstance() {
		return INSTANCE;
	}

	@Override
	public String getName() {
		return "RouteBuilder";
	}

	public void toggle() {
		if (enabled) {
			stop();
		} else {
			start();
		}
	}

	@Override
	public void start() {
		if (enabled) {
			return;
		}
		enabled = true;
		RouteHandler.getInstance().saveData();
		timer.schedule(1_000L);
		send("Route recording enabled.");
	}

	@Override
	public void stop() {
		RouteHandler.getInstance().saveData();
		enabled = false;
		timer.reset();
		send("Route recording disabled.");
	}

	public void addToRoute(WaypointType type) {
		BlockPos position = currentBlock();
		if (position == null) {
			return;
		}
		RouteHandler.getInstance().addToCurrentRoute(position, type);
		RouteHandler.getInstance().saveData();
	}

	public void removeFromRoute(int index) {
		RouteHandler.getInstance().removeFromCurrentRoute(index);
		RouteHandler.getInstance().saveData();
	}

	public boolean removeClosest() {
		RouteHandler handler = RouteHandler.getInstance();
		BlockPos position = currentBlock();
		if (position == null) {
			return false;
		}
		Route route = handler.getSelectedRoute();
		Optional<RouteWaypoint> closest = route.getClosest(position);
		if (closest.isEmpty()) {
			return false;
		}
		handler.removeFromCurrentRoute(route.indexOf(closest.get()));
		handler.saveData();
		return true;
	}

	public void replaceNode(int index, WaypointType type) {
		BlockPos position = currentBlock();
		if (position == null) {
			return;
		}
		RouteHandler.getInstance().replaceInCurrentRoute(index, new RouteWaypoint(position, type));
		RouteHandler.getInstance().saveData();
	}

	@Override
	protected void onTick(net.minecraft.client.Minecraft client) {
		if (timer.passed()) {
			RouteHandler.getInstance().saveData();
			timer.schedule(1_000L);
		}
	}

	private BlockPos currentBlock() {
		if (mc.player == null) {
			sendError("Route editing requires an active world.");
			return null;
		}
		return PlayerUtil.getBlockStandingOn(mc.player);
	}
}
