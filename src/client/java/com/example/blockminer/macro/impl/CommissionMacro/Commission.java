package com.example.blockminer.macro.impl.CommissionMacro;

import com.example.blockminer.location.SubLocation;
import com.example.blockminer.util.CommissionUtil;
import com.example.blockminer.util.Strings;
import com.example.blockminer.util.helper.route.RouteWaypoint;
import com.example.blockminer.util.helper.route.WaypointType;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public enum Commission {
	MITHRIL_MINER(Strings.MITHRIL_MINER_COMMISSION, SubLocation.RAMPARTS_QUARRY),
	TITANIUM_MINER(Strings.TITANIUM_MINER_COMMISSION, SubLocation.RAMPARTS_QUARRY),
	UPPER_MITHRIL(Strings.UPPER_MITHRIL_COMMISSION, SubLocation.UPPER_MINES),
	UPPER_TITANIUM(Strings.UPPER_TITANIUM_COMMISSION, SubLocation.UPPER_MINES),
	ROYAL_MITHRIL(Strings.ROYAL_MITHRIL_COMMISSION, SubLocation.ROYAL_MINES),
	ROYAL_TITANIUM(Strings.ROYAL_TITANIUM_COMMISSION, SubLocation.ROYAL_MINES),
	LAVA_MITHRIL(Strings.LAVA_MITHRIL_COMMISSION, SubLocation.LAVA_SPRINGS),
	LAVA_TITANIUM(Strings.LAVA_TITANIUM_COMMISSION, SubLocation.LAVA_SPRINGS),
	CLIFFSIDE_MITHRIL(Strings.CLIFFSIDE_MITHRIL_COMMISSION, SubLocation.CLIFFSIDE_VEINS),
	CLIFFSIDE_TITANIUM(Strings.CLIFFSIDE_TITANIUM_COMMISSION, SubLocation.CLIFFSIDE_VEINS),
	RAMPARTS_MITHRIL(Strings.RAMPARTS_MITHRIL_COMMISSION, SubLocation.RAMPARTS_QUARRY),
	RAMPARTS_TITANIUM(Strings.RAMPARTS_TITANIUM_COMMISSION, SubLocation.RAMPARTS_QUARRY),
	GOBLIN_SLAYER(Strings.GOBLIN_SLAYER_COMMISSION, SubLocation.GOBLIN_BURROWS),
	GLACITE_WALKER_SLAYER(Strings.GLACITE_WALKER_SLAYER_COMMISSION, SubLocation.GREAT_ICE_WALL),
	MINES_SLAYER(Strings.MINES_SLAYER_COMMISSION, SubLocation.GOBLIN_BURROWS),
	COMMISSION_CLAIM(Strings.CLAIM_COMMISSION_NAME, SubLocation.THE_FORGE),
	REFUEL(Strings.REFUEL_DRILL_COMMISSION, SubLocation.FORGE_BASIN);

	private static final Map<SubLocation, List<RouteWaypoint>> WAYPOINTS = Map.of(
			SubLocation.FORGE_BASIN, waypoints(new int[][]{{-9, 144, -20}}),
			SubLocation.THE_FORGE, waypoints(new int[][]{
					{44, 134, 21}, {58, 197, -11}, {171, 149, 33}, {-75, 152, -11}, {-131, 173, -52}
			}),
			SubLocation.CLIFFSIDE_VEINS, waypoints(new int[][]{{93, 144, 51}}),
			SubLocation.ROYAL_MINES, waypoints(new int[][]{{115, 153, 83}}),
			SubLocation.GREAT_ICE_WALL, waypoints(new int[][]{{0, 127, 143}}),
			SubLocation.GOBLIN_BURROWS, waypoints(new int[][]{{-56, 134, 153}}),
			SubLocation.RAMPARTS_QUARRY, waypoints(new int[][]{{-41, 138, -13}, {-58, 146, -18}}),
			SubLocation.UPPER_MINES, waypoints(new int[][]{{-111, 166, -74}, {-145, 206, -30}}),
			SubLocation.LAVA_SPRINGS, waypoints(new int[][]{{53, 197, -24}})
	);

	private final String name;
	private final SubLocation location;

	Commission(String name, SubLocation location) {
		this.name = name;
		this.location = location;
	}

	public String getName() {
		return name;
	}

	public SubLocation getLocation() {
		return location;
	}

	public boolean isMining() {
		return switch (this) {
			case MITHRIL_MINER, TITANIUM_MINER, UPPER_MITHRIL, UPPER_TITANIUM,
					ROYAL_MITHRIL, ROYAL_TITANIUM, LAVA_MITHRIL, LAVA_TITANIUM,
					CLIFFSIDE_MITHRIL, CLIFFSIDE_TITANIUM, RAMPARTS_MITHRIL, RAMPARTS_TITANIUM -> true;
			default -> false;
		};
	}

	public boolean isTitanium() {
		return switch (this) {
			case TITANIUM_MINER, UPPER_TITANIUM, ROYAL_TITANIUM, LAVA_TITANIUM,
					CLIFFSIDE_TITANIUM, RAMPARTS_TITANIUM -> true;
			default -> false;
		};
	}

	public boolean isSlayer() {
		return this == GOBLIN_SLAYER || this == GLACITE_WALKER_SLAYER || this == MINES_SLAYER;
	}

	public static Commission getCommission(String name) {
		if (name == null) {
			return null;
		}
		return Arrays.stream(values())
				.filter(commission -> commission.name.equalsIgnoreCase(name.trim()))
				.findFirst()
				.orElse(null);
	}

	public RouteWaypoint getWaypoint() {
		List<RouteWaypoint> options;
		if (this == COMMISSION_CLAIM) {
			Vec3 closestEmissary = CommissionUtil.getClosestEmissaryPosition();
			options = WAYPOINTS.get(SubLocation.THE_FORGE);
			return options.stream().min(Comparator.comparingDouble(
					waypoint -> waypoint.toVec3().distanceToSqr(closestEmissary))).orElseThrow();
		}
		options = WAYPOINTS.get(location);
		if (options == null || options.isEmpty()) {
			throw new IllegalStateException("No route waypoint is configured for " + location);
		}
		return options.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(options.size()));
	}

	private static List<RouteWaypoint> waypoints(int[][] positions) {
		return Arrays.stream(positions)
				.map(position -> new RouteWaypoint(position[0], position[1], position[2], WaypointType.WALK))
				.toList();
	}
}
