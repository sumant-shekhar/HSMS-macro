package com.example.blockminer.location;

import java.util.HashMap;
import java.util.Map;

public enum Location {
	PRIVATE_ISLAND("Private Island"),
	HUB("Hub"),
	THE_PARK("The Park"),
	THE_FARMING_ISLANDS("The Farming Islands"),
	SPIDER_DEN("Spider's Den"),
	THE_END("The End"),
	CRIMSON_ISLE("Crimson Isle"),
	GOLD_MINE("Gold Mine"),
	DEEP_CAVERNS("Deep Caverns"),
	DWARVEN_MINES("Dwarven Mines"),
	CRYSTAL_HOLLOWS("Crystal Hollows"),
	JERRY_WORKSHOP("Jerry's Workshop"),
	DUNGEON_HUB("Dungeon Hub"),
	GARDEN("Garden"),
	DUNGEON("Dungeon"),
	LIMBO("UNKNOWN"),
	LOBBY("PROTOTYPE"),
	KNOWHERE("Knowhere");

	private static final Map<String, Location> BY_NAME = new HashMap<>();

	static {
		for (Location location : values()) {
			BY_NAME.put(location.name, location);
		}
	}

	private final String name;

	Location(String name) {
		this.name = name;
	}

	public String getName() {
		return name;
	}

	public static Location fromName(String name) {
		return BY_NAME.getOrDefault(name, KNOWHERE);
	}

	public boolean isSkyBlock() {
		return ordinal() < values().length - 3;
	}
}
