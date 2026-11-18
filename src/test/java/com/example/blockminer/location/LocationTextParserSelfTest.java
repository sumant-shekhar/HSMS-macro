package com.example.blockminer.location;

import java.util.List;
import java.util.stream.IntStream;

public final class LocationTextParserSelfTest {
	private LocationTextParserSelfTest() {
	}

	public static void main(String[] args) {
		parsesSyntheticTabAndSidebarStrings();
		parsesAlternateSyntheticSubLocationMarker();
		parsesHypixelResourcePackSubLocationMarker();
		parsesDungeonHeaderAndUnicodeScoreboardName();
		handlesUnknownNamesAndFormatting();
		matchesSyntheticVanillaTabListOrder();
		preservesLocationOrdinalContract();
		parsesColdAndResetMessages();
		System.out.println("Location text parser self-test passed (synthetic fixtures; 8 scenarios).");
	}

	private static void parsesSyntheticTabAndSidebarStrings() {
		// Invented synthetic fixtures; these are not claimed to match live-server text.
		List<String> tabList = List.of("Players (3)", "§aArea: Dwarven Mines", "Synthetic Name");
		List<String> sidebar = List.of("§6SKYBLOCK", "§b⏣ §aGoblin Burrows", "www.hypixel.net");

		LocationTextParser.ParsedText parsed = LocationTextParser.parse(tabList, sidebar);
		assertEquals("§aArea: Dwarven Mines", parsed.rawAreaLine(), "raw area line");
		assertEquals("Dwarven Mines", parsed.areaName(), "area name");
		assertEquals(Location.DWARVEN_MINES, parsed.location(), "location");
		assertEquals("§b⏣ §aGoblin Burrows", parsed.rawSubLocationLine(), "raw sidebar line");
		assertEquals(SubLocation.GOBLIN_BURROWS, parsed.subLocation(), "sub-location");

		GameStateHandler state = new GameStateHandler();
		state.update(tabList, List.of(), "§6SKYBLOCK", sidebar, false);
		assertEquals("SkyBlock", state.getGameState(), "game state");
		assertEquals(SubLocation.GOBLIN_BURROWS, state.getCurrentSubLocation(), "detected sub-location");
	}

	private static void parsesAlternateSyntheticSubLocationMarker() {
		LocationTextParser.ParsedText parsed = LocationTextParser.parse(
				List.of("Area: Crystal Hollows"),
				List.of("ф §cGoblin Burrows")
		);
		assertEquals(SubLocation.GOBLIN_BURROWS, parsed.subLocation(), "alternate-marker sub-location");
		assertEquals("Goblin Burrows", parsed.cleanedSubLocation(), "alternate-marker cleaned text");
	}

	private static void parsesHypixelResourcePackSubLocationMarker() {
		LocationTextParser.ParsedText parsed = LocationTextParser.parse(
				List.of("Area: Dwarven Mines"),
				List.of("§7\uE067 §bLava Spri§s§bngs")
		);
		assertEquals(Location.DWARVEN_MINES, parsed.location(), "resource-pack area");
		assertEquals(SubLocation.LAVA_SPRINGS, parsed.subLocation(), "resource-pack sub-location");
	}

	private static void parsesDungeonHeaderAndUnicodeScoreboardName() {
		LocationTextParser.ParsedText parsed = LocationTextParser.parse(
				List.of("§bDungeon: The Catacombs (F1)"),
				List.of("§b⏣ §cDéjà Vu Alley")
		);
		assertEquals("The Catacombs (F1)", parsed.areaName(), "Dungeon header area name");
		assertEquals(Location.KNOWHERE, parsed.location(), "unknown dungeon location fallback");
		assertEquals("Déjà Vu Alley", parsed.cleanedSubLocation(), "Unicode scoreboard name");
		assertEquals(SubLocation.DEJA_VU_ALLEY, parsed.subLocation(), "Unicode sub-location");
	}

	private static void handlesUnknownNamesAndFormatting() {
		LocationTextParser.ParsedText parsed = LocationTextParser.parse(
				List.of("Area: Synthetic Unknown Area"),
				List.of("ф §cSynthetic Unknown Zone")
		);
		assertEquals(Location.KNOWHERE, parsed.location(), "unknown location fallback");
		assertEquals(null, parsed.subLocation(), "unknown sub-location result");
		assertEquals("Synthetic Unknown Zone", parsed.cleanedSubLocation(), "sanitized unknown sub-location");
	}

	private static void preservesLocationOrdinalContract() {
		Location[] locations = Location.values();
		assertEquals(Location.LIMBO, locations[locations.length - 3], "penultimate state boundary");
		assertEquals(Location.LOBBY, locations[locations.length - 2], "lobby state boundary");
		assertEquals(Location.KNOWHERE, locations[locations.length - 1], "unknown state boundary");
		assertTrue(Location.DWARVEN_MINES.isSkyBlock(), "SkyBlock location classification");
		assertTrue(!Location.LIMBO.isSkyBlock(), "Limbo classification");

		GameStateHandler state = new GameStateHandler();
		state.update(List.of("single tab row"), List.of(), "", List.of(), true);
		assertEquals(Location.LIMBO, state.getCurrentLocation(), "Limbo detection");
		state.update(List.of(), List.of(), "LOBBY", List.of("www.hypixel.net"), false);
		assertEquals(Location.LOBBY, state.getCurrentLocation(), "Lobby detection");
	}

	private static void matchesSyntheticVanillaTabListOrder() {
		List<VanillaTabListOrder.Key> input = List.of(
				new VanillaTabListOrder.Key(0, false, "team", "zulu"),
				new VanillaTabListOrder.Key(5, true, "aaa", "spectator"),
				new VanillaTabListOrder.Key(5, false, "team", "bravo"),
				new VanillaTabListOrder.Key(5, false, "team", "Alpha"),
				new VanillaTabListOrder.Key(5, false, "alpha", "Charlie")
		);
		List<VanillaTabListOrder.Key> ordered = input.stream().sorted(VanillaTabListOrder.ORDER).toList();
		assertEquals("Charlie", ordered.get(0).profileName(), "team-name tiebreak");
		assertEquals("Alpha", ordered.get(1).profileName(), "case-insensitive profile-name tiebreak first");
		assertEquals("bravo", ordered.get(2).profileName(), "case-insensitive profile-name tiebreak second");
		assertEquals("spectator", ordered.get(3).profileName(), "spectator after non-spectator");
		assertEquals("zulu", ordered.get(4).profileName(), "lower tab-list order last");
		List<VanillaTabListOrder.Key> aboveLimit = IntStream.range(0, 81)
				.mapToObj(index -> new VanillaTabListOrder.Key(81 - index, false, "team", "player" + index))
				.toList();
		assertEquals(VanillaTabListOrder.MAX_ENTRIES,
				VanillaTabListOrder.order(aboveLimit, key -> key).size(),
				"vanilla tab-list entry limit");
	}

	private static void parsesColdAndResetMessages() {
		assertEquals(87, LocationTextParser.parseCold(List.of("§bCold: -87")), "cold value");
		assertEquals(0, LocationTextParser.parseCold(List.of("Synthetic sidebar")), "missing cold fallback");
		assertTrue(LocationTextParser.isColdResetMessage(
				"The warmth of the campfire reduced your Cold"
		), "cold reset message");
	}

	private static void assertEquals(Object expected, Object actual, String label) {
		if (expected == null ? actual != null : !expected.equals(actual)) {
			throw new AssertionError(label + ": expected " + expected + ", got " + actual);
		}
	}

	private static void assertTrue(boolean condition, String label) {
		if (!condition) {
			throw new AssertionError(label);
		}
	}
}
