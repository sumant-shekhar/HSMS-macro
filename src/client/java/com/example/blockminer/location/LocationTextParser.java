package com.example.blockminer.location;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LocationTextParser {
	private static final Pattern AREA_PATTERN = Pattern.compile("^(?:Area|Dungeon):\\s*(.+)$");
	private static final Pattern COLD_PATTERN = Pattern.compile("Cold: -?(\\d{1,3})");

	private LocationTextParser() {
	}

	public static ParsedText parse(List<String> tabList, List<String> sidebarLines) {
		String rawAreaLine = null;
		String areaName = null;
		for (String line : tabList) {
			Matcher matcher = AREA_PATTERN.matcher(stripFormatting(line).trim());
			if (matcher.matches()) {
				rawAreaLine = line;
				areaName = matcher.group(1).trim();
				break;
			}
		}

		String rawSubLocationLine = null;
		String cleanedSubLocation = null;
		SubLocation subLocation = null;
		for (String line : sidebarLines) {
			if (!line.contains("⏣") && !line.contains("ф")) {
				continue;
			}
			rawSubLocationLine = line;
			cleanedSubLocation = sanitizeScoreboardLine(line).trim();
			subLocation = SubLocation.fromName(cleanedSubLocation);
			break;
		}

		return new ParsedText(
				rawAreaLine,
				areaName,
				areaName == null ? Location.KNOWHERE : Location.fromName(areaName),
				rawSubLocationLine,
				cleanedSubLocation,
				subLocation
		);
	}

	public static String sanitizeScoreboardLine(String line) {
		return stripFormatting(line).replace("⏣", "").replace("ф", "");
	}

	public static String stripFormatting(String text) {
		StringBuilder cleaned = new StringBuilder(text.length());
		for (int index = 0; index < text.length(); index++) {
			char character = text.charAt(index);
			if (character == '\u00a7') {
				index++;
			} else {
				cleaned.append(character);
			}
		}
		return cleaned.toString();
	}

	public static int parseCold(List<String> sidebarLines) {
		for (String line : sidebarLines) {
			Matcher matcher = COLD_PATTERN.matcher(stripFormatting(line));
			if (matcher.find()) {
				return Integer.parseInt(matcher.group(1));
			}
		}
		return 0;
	}

	public static boolean isColdResetMessage(String message) {
		String plainText = stripFormatting(message);
		return plainText.contains("The warmth of the campfire reduced your")
				&& plainText.contains("Cold");
	}

	public record ParsedText(
			String rawAreaLine,
			String areaName,
			Location location,
			String rawSubLocationLine,
			String cleanedSubLocation,
			SubLocation subLocation
	) {
	}
}
