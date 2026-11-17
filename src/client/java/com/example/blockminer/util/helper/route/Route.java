package com.example.blockminer.util.helper.route;

import com.google.gson.annotations.Expose;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class Route {
	@Expose
	private final List<RouteWaypoint> waypoints = new ArrayList<>();

	public Route() {
	}

	public Route(List<RouteWaypoint> waypoints) {
		this.waypoints.addAll(waypoints);
	}

	public void insert(RouteWaypoint waypoint) {
		insert(waypoints.size(), waypoint);
	}

	public void insert(int index, RouteWaypoint waypoint) {
		waypoints.add(index, waypoint);
	}

	public void remove(RouteWaypoint waypoint) {
		waypoints.remove(waypoint);
	}

	public void remove(int index) {
		waypoints.remove(index);
	}

	public RouteWaypoint get(int index) {
		if (waypoints.isEmpty()) {
			throw new IndexOutOfBoundsException("Cannot get a waypoint from an empty route");
		}
		return waypoints.get(Math.floorMod(index, waypoints.size()));
	}

	public Optional<RouteWaypoint> getClosest(BlockPos position) {
		Vec3 location = new Vec3(position.getX(), position.getY(), position.getZ());
		return waypoints.stream().min(Comparator.comparingDouble(waypoint ->
				waypoint.toVec3().distanceToSqr(location)));
	}

	public int indexOf(RouteWaypoint waypoint) {
		return waypoints.indexOf(waypoint);
	}

	public void replace(int index, RouteWaypoint waypoint) {
		waypoints.set(index, waypoint);
	}

	public boolean isEnd(int index) {
		return index + 1 == waypoints.size();
	}

	public boolean isEmpty() {
		return waypoints.isEmpty();
	}

	public int size() {
		return waypoints.size();
	}

	public List<RouteWaypoint> getWaypoints() {
		return List.copyOf(waypoints);
	}
}
