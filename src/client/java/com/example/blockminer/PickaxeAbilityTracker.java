package com.example.blockminer;

import com.example.blockminer.location.LocationTextParser;
import com.example.blockminer.util.FeatureInventory;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PickaxeAbilityTracker {
	private static final Logger LOGGER = LoggerFactory.getLogger("blockminer");
	private static final PickaxeAbilityTracker INSTANCE = new PickaxeAbilityTracker();

	private static final Pattern COOLDOWN_PATTERN = Pattern.compile("(?i)(?:on cooldown for|cooldown:?\\s*)([0-9]+)s?");
	private static final long DEFAULT_COOLDOWN_MS = 115_000L;

	private boolean available = true;
	private long cooldownExpireMs = 0L;
	private long lastExecutedMs = 0L;
	private int retryAttempts = 0;

	private PickaxeAbilityTracker() {
	}

	public static PickaxeAbilityTracker getInstance() {
		return INSTANCE;
	}

	public synchronized boolean isAvailable() {
		if (!Config.usePickaxeAbility) {
			return false;
		}
		if (available) {
			return true;
		}
		if (cooldownExpireMs > 0L && System.currentTimeMillis() >= cooldownExpireMs) {
			available = true;
			cooldownExpireMs = 0L;
			retryAttempts = 0;
			LOGGER.info("[blockminer] Pickaxe ability cooldown expired; ability is now AVAILABLE.");
			return true;
		}
		return false;
	}

	public synchronized void setAvailable(boolean available) {
		this.available = available;
		if (available) {
			this.cooldownExpireMs = 0L;
			this.retryAttempts = 0;
		}
	}

	public synchronized void reset() {
		available = true;
		cooldownExpireMs = 0L;
		retryAttempts = 0;
		lastExecutedMs = 0L;
	}

	public synchronized void onChat(String rawMessage) {
		if (rawMessage == null || rawMessage.isBlank()) {
			return;
		}
		String stripped = LocationTextParser.stripFormatting(rawMessage).trim();
		String lower = stripped.toLowerCase(Locale.ROOT);

		// "Mining Speed Boost is now available!" / "Pickobulus is now available!" / "is now available!"
		if (lower.contains("is now available!")) {
			available = true;
			cooldownExpireMs = 0L;
			retryAttempts = 0;
			LOGGER.info("[blockminer] Pickaxe ability became available: {}", stripped);
			return;
		}

		// "You used your Mining Speed Boost Pickaxe Ability!"
		if (lower.contains("you used your") && lower.contains("pickaxe ability")) {
			available = false;
			cooldownExpireMs = System.currentTimeMillis() + DEFAULT_COOLDOWN_MS;
			retryAttempts = 0;
			LOGGER.info("[blockminer] Pickaxe ability activated: {}", stripped);
			return;
		}

		// "Your Pickaxe Ability is on cooldown for 114s." / "This ability is on cooldown for 30s."
		if (lower.contains("pickaxe ability is on cooldown for")
				|| lower.contains("this ability is on cooldown for")
				|| (lower.contains("ability") && lower.contains("on cooldown"))) {
			available = false;
			retryAttempts = 0;
			Matcher matcher = COOLDOWN_PATTERN.matcher(lower);
			if (matcher.find()) {
				try {
					long seconds = Long.parseLong(matcher.group(1));
					cooldownExpireMs = System.currentTimeMillis() + (seconds * 1000L);
					LOGGER.info("[blockminer] Pickaxe ability on cooldown for {}s: {}", seconds, stripped);
					return;
				} catch (NumberFormatException ignored) {
				}
			}
			cooldownExpireMs = System.currentTimeMillis() + DEFAULT_COOLDOWN_MS;
			LOGGER.info("[blockminer] Pickaxe ability on cooldown: {}", stripped);
		}
	}

	public synchronized boolean executeAbility(Minecraft client) {
		if (!Config.usePickaxeAbility || client == null || client.player == null) {
			return false;
		}

		long now = System.currentTimeMillis();
		if (now - lastExecutedMs < 1000L) {
			// Debounce: don't spam right click within 1 second
			return false;
		}
		lastExecutedMs = now;
		retryAttempts++;

		// Ensure the mining tool is held
		FeatureInventory.holdMiningTool(client);

		// If Minecraft is currently destroying a block, abort destruction so startUseItem won't return early
		if (client.gameMode != null && client.gameMode.isDestroying()) {
			client.gameMode.stopDestroyBlock();
		}

		// Trigger right-click natively
		KeyBindUtil.rightClick();

		// Optimistically assume cooldown unless chat says otherwise or retry threshold exceeded
		if (retryAttempts >= 4) {
			LOGGER.warn("[blockminer] Pickaxe ability right-click reached max attempts ({}); cooling down for 120s.", retryAttempts);
			available = false;
			cooldownExpireMs = now + DEFAULT_COOLDOWN_MS;
			retryAttempts = 0;
		} else {
			available = false;
			cooldownExpireMs = now + DEFAULT_COOLDOWN_MS;
			LOGGER.info("[blockminer] Executed drill right-click ability (attempt {}).", retryAttempts);
		}

		return true;
	}

	public synchronized long getCooldownRemainingSeconds() {
		if (available) {
			return 0L;
		}
		long remaining = (cooldownExpireMs - System.currentTimeMillis()) / 1000L;
		return Math.max(0L, remaining);
	}
}
