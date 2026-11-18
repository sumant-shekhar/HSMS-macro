package com.example.blockminer;

public final class PickaxeAbilityTrackerSelfTest {
	private PickaxeAbilityTrackerSelfTest() {
	}

	public static void main(String[] args) {
		testInitialAvailability();
		testConfigDisable();
		testChatAvailable();
		testChatUsed();
		testChatCooldown();
		System.out.println("Pickaxe ability tracker self-tests passed (5 scenarios, 0 failures).");
	}

	private static void testInitialAvailability() {
		Config.usePickaxeAbility = true;
		PickaxeAbilityTracker tracker = PickaxeAbilityTracker.getInstance();
		tracker.reset();
		assertTrue(tracker.isAvailable(), "Tracker should initially be available after reset");
	}

	private static void testConfigDisable() {
		Config.usePickaxeAbility = false;
		PickaxeAbilityTracker tracker = PickaxeAbilityTracker.getInstance();
		tracker.reset();
		assertFalse(tracker.isAvailable(), "Tracker should not be available if Config.usePickaxeAbility is false");
		Config.usePickaxeAbility = true;
	}

	private static void testChatAvailable() {
		Config.usePickaxeAbility = true;
		PickaxeAbilityTracker tracker = PickaxeAbilityTracker.getInstance();
		tracker.setAvailable(false);
		assertFalse(tracker.isAvailable(), "Tracker should be unavailable after setAvailable(false)");

		tracker.onChat("§aMining Speed Boost is now available!");
		assertTrue(tracker.isAvailable(), "Mining Speed Boost message should make ability available");

		tracker.setAvailable(false);
		tracker.onChat("§aPickobulus is now available!");
		assertTrue(tracker.isAvailable(), "Pickobulus message should make ability available");
	}

	private static void testChatUsed() {
		Config.usePickaxeAbility = true;
		PickaxeAbilityTracker tracker = PickaxeAbilityTracker.getInstance();
		tracker.reset();
		assertTrue(tracker.isAvailable(), "Tracker should be available before use");

		tracker.onChat("§aYou used your §r§6Mining Speed Boost §r§aPickaxe Ability!");
		assertFalse(tracker.isAvailable(), "Tracker should be unavailable after using ability");
	}

	private static void testChatCooldown() {
		Config.usePickaxeAbility = true;
		PickaxeAbilityTracker tracker = PickaxeAbilityTracker.getInstance();
		tracker.reset();

		tracker.onChat("§cYour Pickaxe Ability is on cooldown for 45s.");
		assertFalse(tracker.isAvailable(), "Tracker should be unavailable after cooldown message");
		assertTrue(tracker.getCooldownRemainingSeconds() > 0, "Cooldown remaining should be positive");
	}

	private static void assertTrue(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private static void assertFalse(boolean condition, String message) {
		if (condition) {
			throw new AssertionError(message);
		}
	}
}
