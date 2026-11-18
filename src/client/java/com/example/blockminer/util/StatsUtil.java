package com.example.blockminer.util;

import com.example.blockminer.Config;
import com.example.blockminer.location.LocationTextParser;
import com.example.blockminer.location.LocationTracker;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class StatsUtil {
	private static final Pattern MINING_SPEED_PATTERN =
			Pattern.compile("(?i).*mining\\s+speed[:\\s]+[+]*([0-9,]+).*");

	private StatsUtil() {
	}

	public static int getMiningSpeed() {
		List<String> tabList = LocationTracker.getGameState().getTabList();
		if (tabList != null && !tabList.isEmpty()) {
			for (String row : tabList) {
				if (row == null) {
					continue;
				}
				String stripped = LocationTextParser.stripFormatting(row).trim();
				Matcher matcher = MINING_SPEED_PATTERN.matcher(stripped);
				if (matcher.matches()) {
					try {
						int speed = Integer.parseInt(matcher.group(1).replace(",", ""));
						if (speed > 0) {
							return speed;
						}
					} catch (NumberFormatException ignored) {
					}
				}
			}
		}

		return Config.defaultMiningSpeed > 0 ? Config.defaultMiningSpeed : 600;
	}
}
