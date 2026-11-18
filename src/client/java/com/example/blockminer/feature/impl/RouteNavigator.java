package com.example.blockminer.feature.impl;

import com.example.blockminer.Angle;
import com.example.blockminer.AngleUtil;
import com.example.blockminer.BlockUtil;
import com.example.blockminer.KeyBindUtil;
import com.example.blockminer.PlayerUtil;
import com.example.blockminer.RotationConfiguration;
import com.example.blockminer.RotationHandler;
import com.example.blockminer.Target;
import com.example.blockminer.feature.AbstractFeature;
import com.example.blockminer.pathfinder.AStarPathFinder;
import com.example.blockminer.pathfinder.ActionCosts;
import com.example.blockminer.pathfinder.Goal;
import com.example.blockminer.pathfinder.PathExecutor;
import com.example.blockminer.pathfinder.PathResult;
import com.example.blockminer.pathfinder.PathfindingSnapshot;
import com.example.blockminer.pathfinder.WorldMoveCalculator;
import com.example.blockminer.util.FeatureInventory;
import com.example.blockminer.util.Logger;
import com.example.blockminer.util.helper.route.Route;
import com.example.blockminer.util.helper.route.RouteWaypoint;
import com.example.blockminer.util.helper.route.WaypointType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class RouteNavigator extends AbstractFeature {
	private static final RouteNavigator INSTANCE = new RouteNavigator();
	private static final ExecutorService PATH_SEARCH = Executors.newSingleThreadExecutor(task -> {
		Thread thread = new Thread(task, "blockminer-route-search");
		thread.setDaemon(true);
		return thread;
	});

	private Route routeToFollow;
	private int currentRouteIndex = -1;
	private int targetRouteIndex = -1;
	private State state = State.IDLE;
	private NavError navError = NavError.NONE;
	private RouteWaypoint currentWaypoint;
	private Vec3 rotationTarget;
	private Vec3 positionBeforeTeleport;
	private long searchGeneration;

	private RouteNavigator() {
	}

	public static RouteNavigator getInstance() {
		return INSTANCE;
	}

	@Override
	public String getName() {
		return "RouteNavigator";
	}

	public void start(Route route) {
		if (route == null || route.isEmpty()) {
			sendError("No route was selected or the selected route is empty.");
			return;
		}
		stop();
		routeToFollow = route;
		currentRouteIndex = -1;
		targetRouteIndex = route.size() - 1;
		navError = NavError.NONE;
		state = State.ADVANCE;
		enabled = true;
	}

	public void queueRoute(Route route) {
		start(route);
	}

	public void goTo(int index) {
		if (routeToFollow == null || routeToFollow.isEmpty()) {
			sendError("No route was selected or the selected route is empty.");
			return;
		}
		if (index < 0 || index >= routeToFollow.size()) {
			sendError("Route waypoint index is out of range.");
			return;
		}
		BlockPos playerBlock = mc.player == null ? null : PlayerUtil.getBlockStandingOn(mc.player);
		if (playerBlock == null) {
			sendError("Route navigation requires an active world.");
			return;
		}
		Route selectedRoute = routeToFollow;
		int nearestIndex = selectedRoute.getClosest(playerBlock)
				.map(selectedRoute::indexOf)
				.orElse(-1);
		stop();
		routeToFollow = selectedRoute;
		currentRouteIndex = nearestIndex - 1;
		targetRouteIndex = index;
		if (targetRouteIndex < nearestIndex - 1) {
			targetRouteIndex += selectedRoute.size();
		}
		navError = NavError.NONE;
		state = State.ADVANCE;
		enabled = true;
	}

	public void gotoNext() {
		if (routeToFollow == null || routeToFollow.isEmpty()) {
			sendError("No route was selected or the selected route is empty.");
			return;
		}
		goTo(Math.floorMod(currentRouteIndex + 1, routeToFollow.size()));
	}

	public void stop(NavError error) {
		navError = error;
		stop();
	}

	@Override
	public void pause() {
		if (!enabled) {
			return;
		}
		boolean retryCurrentWaypoint = currentWaypoint != null && state != State.ADVANCE;
		enabled = false;
		searchGeneration++;
		PathExecutor.getInstance().stop();
		KeyBindUtil.setKeyBindState(mc.options.keyShift, false);
		RotationHandler.getInstance().stop();
		timer.reset();
		if (retryCurrentWaypoint) {
			currentRouteIndex--;
		}
		currentWaypoint = null;
		rotationTarget = null;
		state = State.ADVANCE;
	}

	@Override
	public void stop() {
		enabled = false;
		searchGeneration++;
		state = State.IDLE;
		routeToFollow = null;
		currentWaypoint = null;
		rotationTarget = null;
		positionBeforeTeleport = null;
		timer.reset();
		PathExecutor.getInstance().stop();
		KeyBindUtil.setKeyBindState(mc.options.keyShift, false);
		RotationHandler.getInstance().stop();
	}

	@Override
	public void resetStatesAfterStop() {
		stop();
	}

	public boolean succeeded() {
		return !enabled && navError == NavError.NONE;
	}

	public NavError getNavError() {
		return navError;
	}

	public int getCurrentIndex(BlockPos playerBlock) {
		if (routeToFollow == null) {
			return -1;
		}
		return routeToFollow.getClosest(playerBlock)
				.map(routeToFollow::indexOf)
				.orElse(-1);
	}

	@Override
	protected void onTick(Minecraft client) {
		if (client.player == null || client.level == null) {
			stop(NavError.NONE);
			return;
		}
		if (client.gui.screen() != null) {
			PathExecutor.getInstance().stop();
			KeyBindUtil.stopMovement();
			return;
		}
		switch (state) {
			case ADVANCE -> advanceToNextWaypoint(client);
			case ROTATE -> rotateToWaypoint();
			case VERIFY_ROTATION -> verifyRotation();
			case READY_TO_TELEPORT -> useTravelItem(client);
			case VERIFY_TELEPORT -> verifyTeleport(client);
			case SEARCHING_WALK -> {
			}
			case FOLLOWING_WALK -> verifyWalk(client);
			case IDLE -> {
			}
		}
	}

	private void advanceToNextWaypoint(Minecraft client) {
		if (currentRouteIndex >= targetRouteIndex) {
			stop();
			return;
		}
		currentRouteIndex++;
		currentWaypoint = routeToFollow.get(currentRouteIndex);
		BlockPos target = currentWaypoint.toBlockPos();
		if (currentWaypoint.getTransportMethod() == WaypointType.WALK
				|| currentWaypoint.getTransportMethod() == WaypointType.MINE) {
			requestWalkingPath(client, target);
			return;
		}
		FeatureInventory.holdTeleportTool(client);
		if (client.level.getBlockState(target).getBlock() == Blocks.SNOW) {
			target = target.offset(0, -1, 0);
		}
		List<Vec3> visiblePoints = BlockUtil.bestPointsOnBestSide(client.level, client.player, target);
		rotationTarget = visiblePoints.isEmpty() ? Vec3.atCenterOf(target) : visiblePoints.getFirst();
		state = State.ROTATE;
	}

	private void rotateToWaypoint() {
		RotationHandler.getInstance().easeTo(new RotationConfiguration(
				new Target(rotationTarget),
				600L,
				RotationConfiguration.RotationType.CLIENT,
				null
		).followTarget(true));
		state = State.VERIFY_ROTATION;
		timer.schedule(2500L);
	}

	private void verifyRotation() {
		if (timer.passed()) {
			fail(NavError.TIME_FAIL, "Could not rotate to the route waypoint in time.");
			return;
		}
		if (RotationHandler.getInstance().isFollowingTarget()) {
			state = State.READY_TO_TELEPORT;
			timer.schedule(currentWaypoint.getTransportMethod() == WaypointType.ETHERWARP ? 250L : 0L);
			if (currentWaypoint.getTransportMethod() == WaypointType.ETHERWARP) {
				KeyBindUtil.setKeyBindState(mc.options.keyShift, true);
			}
			return;
		}
		if (mc.player != null && rotationTarget != null) {
			Angle change = AngleUtil.getNeededChange(
					AngleUtil.getPlayerAngle(),
					AngleUtil.getRotation(mc.player.getEyePosition(), rotationTarget)
			);
			if (change.lengthSqrt() <= 2.0F) {
				state = State.READY_TO_TELEPORT;
				timer.schedule(currentWaypoint.getTransportMethod() == WaypointType.ETHERWARP ? 250L : 0L);
				if (currentWaypoint.getTransportMethod() == WaypointType.ETHERWARP) {
					KeyBindUtil.setKeyBindState(mc.options.keyShift, true);
				}
			}
		}
	}

	private void useTravelItem(Minecraft client) {
		if (timer.isScheduled() && !timer.passed()) {
			return;
		}
		if (client.gameMode == null || client.player == null) {
			fail(NavError.TIME_FAIL, "Cannot use the travel item without an active game mode.");
			return;
		}
		FeatureInventory.holdTeleportTool(client);
		positionBeforeTeleport = client.player.position();
		client.gameMode.useItem(client.player, InteractionHand.MAIN_HAND);
		KeyBindUtil.setKeyBindState(client.options.keyShift, false);
		state = State.VERIFY_TELEPORT;
		timer.schedule(2500L);
	}

	private void verifyTeleport(Minecraft client) {
		Vec3 destination = currentWaypoint.toVec3().add(0.5D, 0.5D, 0.5D);
		double distanceSquared = client.player.position().distanceToSqr(destination);
		boolean moved = positionBeforeTeleport.distanceToSqr(client.player.position()) > 0.25D;
		if (moved && distanceSquared <= 36.0D) {
			RotationHandler.getInstance().stop();
			state = State.ADVANCE;
			timer.reset();
			return;
		}
		if (timer.passed()) {
			fail(NavError.TIME_FAIL, "No teleport was detected for the route waypoint.");
		}
	}

	private void requestWalkingPath(Minecraft client, BlockPos target) {
		ClientLevel level = client.level;
		BlockPos start = PlayerUtil.getBlockStandingOn(client.player);
		PathfindingSnapshot snapshot;
		try {
			snapshot = PathfindingSnapshot.capture(level, start, target);
		} catch (RuntimeException exception) {
			fail(NavError.PATHFIND_FAILED, "Could not capture route path area: " + exception.getMessage());
			return;
		}

		state = State.SEARCHING_WALK;
		long generation = ++searchGeneration;
		CompletableFuture.supplyAsync(() -> {
			ActionCosts costs = new ActionCosts();
			AStarPathFinder finder = new AStarPathFinder(
					start.getX(),
					start.getY(),
					start.getZ(),
					new Goal(target.getX(), target.getY(), target.getZ(), costs),
					new WorldMoveCalculator(snapshot, costs)
			);
			return finder.calculatePath();
		}, PATH_SEARCH).whenComplete((path, failure) -> client.execute(() ->
				finishPathSearch(client, level, target, generation, path, failure)));
	}

	private void finishPathSearch(
			Minecraft client,
			ClientLevel searchedLevel,
			BlockPos target,
			long generation,
			PathResult path,
			Throwable failure
	) {
		if (!enabled || generation != searchGeneration || state != State.SEARCHING_WALK) {
			return;
		}
		if (client.level != searchedLevel || client.player == null) {
			fail(NavError.PATHFIND_FAILED, "World changed while calculating the route path.");
			return;
		}
		if (failure != null) {
			Throwable cause = failure instanceof CompletionException && failure.getCause() != null
					? failure.getCause()
					: failure;
			Logger.sendError("Route path search failed: " + cause.getMessage());
			stop(NavError.PATHFIND_FAILED);
			return;
		}
		if (path == null) {
			fail(NavError.PATHFIND_FAILED, "No path found to route waypoint " + target + ".");
			return;
		}
		PathExecutor.getInstance().start(path);
		state = State.FOLLOWING_WALK;
		timer.schedule(60_000L);
	}

	private void verifyWalk(Minecraft client) {
		if (isAtWaypoint(client)) {
			PathExecutor.getInstance().stop();
			state = State.ADVANCE;
			timer.reset();
			return;
		}
		if (!PathExecutor.getInstance().isRunning()) {
			fail(NavError.PATHFIND_FAILED, "Path following ended before reaching the route waypoint.");
			return;
		}
		if (timer.passed()) {
			fail(NavError.PATHFIND_FAILED, "Timed out while following a route path.");
		}
	}

	private boolean isAtWaypoint(Minecraft client) {
		Vec3 feet = new Vec3(
				currentWaypoint.getX() + 0.5D,
				currentWaypoint.getY() + 1.0D,
				currentWaypoint.getZ() + 0.5D
		);
		return client.player.position().distanceToSqr(feet) <= 2.25D;
	}

	private void fail(NavError error, String message) {
		sendError(message);
		stop(error);
	}

	private enum State {
		IDLE,
		ADVANCE,
		ROTATE,
		VERIFY_ROTATION,
		READY_TO_TELEPORT,
		VERIFY_TELEPORT,
		SEARCHING_WALK,
		FOLLOWING_WALK
	}

	public enum NavError {
		NONE,
		TIME_FAIL,
		PATHFIND_FAILED
	}
}
