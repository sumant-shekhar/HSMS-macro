package com.example.blockminer.util.helper.route;

import com.google.gson.annotations.Expose;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;

public final class RouteWaypoint {
	@Expose
	private int x;
	@Expose
	private int y;
	@Expose
	private int z;
	@Expose
	private WaypointType transportMethod;

	public RouteWaypoint() {
	}

	public RouteWaypoint(int x, int y, int z, WaypointType transportMethod) {
		this.x = x;
		this.y = y;
		this.z = z;
		this.transportMethod = Objects.requireNonNull(transportMethod, "transportMethod");
	}

	public RouteWaypoint(BlockPos position, WaypointType transportMethod) {
		this(position.getX(), position.getY(), position.getZ(), transportMethod);
	}

	public int getX() {
		return x;
	}

	public int getY() {
		return y;
	}

	public int getZ() {
		return z;
	}

	public WaypointType getTransportMethod() {
		return transportMethod;
	}

	public void setX(int x) {
		this.x = x;
	}

	public void setY(int y) {
		this.y = y;
	}

	public void setZ(int z) {
		this.z = z;
	}

	public void setTransportMethod(WaypointType transportMethod) {
		this.transportMethod = Objects.requireNonNull(transportMethod, "transportMethod");
	}

	public boolean isWithinRange(BlockPos position, int range) {
		return Math.abs(position.getX() - x) <= range
				&& Math.abs(position.getY() - y) <= range
				&& Math.abs(position.getZ() - z) <= range;
	}

	public int distanceTo(BlockPos position) {
		return (int) Math.sqrt(distanceSquared(position));
	}

	public Vec3 toVec3() {
		return new Vec3(x, y, z);
	}

	public BlockPos toBlockPos() {
		return new BlockPos(x, y, z);
	}

	private double distanceSquared(BlockPos position) {
		double dx = position.getX() - x;
		double dy = position.getY() - y;
		double dz = position.getZ() - z;
		return dx * dx + dy * dy + dz * dz;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof RouteWaypoint waypoint
				&& x == waypoint.x && y == waypoint.y && z == waypoint.z;
	}

	@Override
	public int hashCode() {
		return Objects.hash(x, y, z);
	}

	@Override
	public String toString() {
		return x + "," + y + "," + z + "," + transportMethod.name();
	}
}
