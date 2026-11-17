package com.example.blockminer.feature.impl;

import com.example.blockminer.feature.AbstractFeature;
import com.example.blockminer.location.GameStateHandler;
import com.example.blockminer.location.Location;
import com.example.blockminer.location.LocationTracker;
import com.example.blockminer.location.SubLocation;
import com.example.blockminer.util.Strings;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class AutoWarp extends AbstractFeature {
	private static final AutoWarp INSTANCE = new AutoWarp();
	private static final int MAX_ATTEMPTS = 10;
	private static final Map<Location, String> LOCATION_WARP_COMMANDS = locationCommands();
	private static final Map<SubLocation, String> SUBLOCATION_WARP_COMMANDS = subLocationCommands();

	private Error failReason = Error.NONE;
	private int attempts;
	private boolean notOnSkyBlockError;
	private Location targetLocation;
	private SubLocation targetSubLocation;

	private AutoWarp() {
	}

	public static AutoWarp getInstance() {
		return INSTANCE;
	}

	@Override
	public String getName() {
		return "AutoWarp";
	}

	public void start(Location location, SubLocation subLocation) {
		if (location == null && subLocation == null) {
			sendError("Choose a destination before starting AutoWarp.");
			return;
		}
		if ((location != null && !LOCATION_WARP_COMMANDS.containsKey(location))
				|| (subLocation != null && !SUBLOCATION_WARP_COMMANDS.containsKey(subLocation))) {
			sendError("No fast-travel destination is configured for that location.");
			return;
		}
		targetLocation = location;
		targetSubLocation = subLocation;
		failReason = Error.NONE;
		attempts = 0;
		notOnSkyBlockError = false;
		enabled = true;
		timer.reset();
		log("Enabled");
	}

	@Override
	public void stop() {
		stopState();
		log("Disabled");
	}

	public void stop(Error reason) {
		failReason = Objects.requireNonNull(reason);
		stopState();
	}

	public Error getFailReason() {
		return failReason;
	}

	public boolean hasSucceeded() {
		return !enabled && failReason == Error.NONE;
	}

	public boolean isDoneWarping() {
		GameStateHandler state = LocationTracker.getGameState();
		return (targetLocation == null || state.getCurrentLocation() == targetLocation)
				&& (targetSubLocation == null || state.getCurrentSubLocation() == targetSubLocation);
	}

	@Override
	protected void onTick(net.minecraft.client.Minecraft client) {
		if (client.player == null || client.level == null) {
			stop(Error.FAILED_TO_WARP);
			return;
		}
		if (attempts > MAX_ATTEMPTS) {
			stop(Error.FAILED_TO_WARP);
			sendError("Failed to reach the requested destination.");
			return;
		}
		if (isDoneWarping()) {
			stop();
			log("Done warping");
			return;
		}
		if (timer.isScheduled() && !timer.passed()) {
			return;
		}

		attempts++;
		timer.schedule(5000L);
		GameStateHandler state = LocationTracker.getGameState();
		if (notOnSkyBlockError) {
			notOnSkyBlockError = false;
			sendCommand(getWarpCommand(Location.LOBBY));
			return;
		}
		if (!state.isPlayerInSkyBlock()) {
			sendCommand(state.getCurrentLocation() == Location.LIMBO
					? getWarpCommand(Location.LOBBY)
					: "play sb");
			return;
		}
		if (targetLocation != null && targetSubLocation == null
				&& targetLocation != state.getCurrentLocation()) {
			sendCommand(getWarpCommand(targetLocation));
			return;
		}
		if (targetSubLocation != null && targetSubLocation != state.getCurrentSubLocation()) {
			sendCommand(getWarpCommand(targetSubLocation));
			return;
		}
		stop(Error.FAILED_TO_WARP);
	}

	@Override
	protected void onChat(String message) {
		if (Strings.WARP_WAIT_CHAT_MESSAGES.contains(message)) {
			timer.schedule(10_000L);
		}
		if (message.contains(Strings.WARP_NOT_SKYBLOCK_CHAT)) {
			notOnSkyBlockError = true;
		}
		if (message.contains(Strings.WARP_NO_SCROLL_CHAT)) {
			Location location = targetLocation;
			SubLocation subLocation = targetSubLocation;
			stop(Error.NO_SCROLL);
			sendError("The required fast-travel scroll is not unlocked for "
					+ (location == null ? "" : location.getName())
					+ (subLocation == null ? "" : " / " + subLocation.getName()) + ".");
		}
	}

	public static Location parseLocation(String value) {
		String normalized = normalize(value);
		for (Location location : Location.values()) {
			if (normalize(location.name()).equals(normalized)
					|| normalize(location.getName()).equals(normalized)) {
				return location;
			}
		}
		return null;
	}

	public static SubLocation parseSubLocation(String value) {
		String normalized = normalize(value);
		for (SubLocation location : SubLocation.values()) {
			if (normalize(location.name()).equals(normalized)
					|| normalize(location.getName()).equals(normalized)) {
				return location;
			}
		}
		return null;
	}

	private void sendCommand(String command) {
		if (mc.player == null || mc.player.connection == null) {
			stop(Error.FAILED_TO_WARP);
			sendError("Cannot send a warp command without a connected player.");
			return;
		}
		mc.player.connection.sendCommand(command.startsWith("/") ? command.substring(1) : command);
		log("Sent warp command: /" + command.replaceFirst("^/", ""));
	}

	private static String getWarpCommand(Location location) {
		String command = LOCATION_WARP_COMMANDS.get(location);
		return command.startsWith("/") ? command : "warp " + command;
	}

	private static String getWarpCommand(SubLocation location) {
		return "warp " + SUBLOCATION_WARP_COMMANDS.get(location);
	}

	private void stopState() {
		enabled = false;
		attempts = 0;
		targetLocation = null;
		targetSubLocation = null;
		notOnSkyBlockError = false;
		timer.reset();
	}

	private static String normalize(String value) {
		return value.trim().replace("'", "").replace(' ', '_').toLowerCase(Locale.ROOT);
	}

	private static Map<Location, String> locationCommands() {
		Map<Location, String> commands = new EnumMap<>(Location.class);
		commands.put(Location.PRIVATE_ISLAND, "island");
		commands.put(Location.HUB, "hub");
		commands.put(Location.THE_PARK, "park");
		commands.put(Location.THE_FARMING_ISLANDS, "barn");
		commands.put(Location.SPIDER_DEN, "spider");
		commands.put(Location.THE_END, "end");
		commands.put(Location.CRIMSON_ISLE, "isle");
		commands.put(Location.GOLD_MINE, "gold");
		commands.put(Location.DEEP_CAVERNS, "deep");
		commands.put(Location.DWARVEN_MINES, "mines");
		commands.put(Location.CRYSTAL_HOLLOWS, "ch");
		commands.put(Location.JERRY_WORKSHOP, "/savethejerrys");
		commands.put(Location.DUNGEON_HUB, "dhub");
		commands.put(Location.LOBBY, "/l");
		commands.put(Location.GARDEN, "garden");
		return Map.copyOf(commands);
	}

	private static Map<SubLocation, String> subLocationCommands() {
		Map<SubLocation, String> commands = new EnumMap<>(SubLocation.class);
		commands.put(SubLocation.MUSEUM, "museum");
		commands.put(SubLocation.RUINS, "castle");
		commands.put(SubLocation.MUSHROOM_DESERT, "desert");
		commands.put(SubLocation.TRAPPERS_DEN, "trapper");
		commands.put(SubLocation.HOWLING_CAVE, "howl");
		commands.put(SubLocation.JUNGLE_ISLAND, "jungle");
		commands.put(SubLocation.THE_FORGE, "forge");
		commands.put(SubLocation.CRYSTAL_NUCLEUS, "nucleus");
		commands.put(SubLocation.SPIDER_MOUND, "top");
		commands.put(SubLocation.ARACHNES_SANCTUARY, "arachne");
		commands.put(SubLocation.DRAGONS_NEST, "drag");
		commands.put(SubLocation.VOID_SEPULTURE, "void");
		commands.put(SubLocation.FORGOTTEN_SKULL, "skull");
		commands.put(SubLocation.SMOLDERING_TOMB, "tomb");
		commands.put(SubLocation.THE_WASTELAND, "wasteland");
		commands.put(SubLocation.DRAGONTAIL, "dragontail");
		commands.put(SubLocation.SCARLETON, "scarleton");
		commands.put(SubLocation.DWARVEN_BASE_CAMP, "base");
		commands.put(SubLocation.THE_LIFT, "mines");
		return Map.copyOf(commands);
	}

	public enum Error {
		NONE,
		FAILED_TO_WARP,
		NO_SCROLL
	}
}
