package com.example.blockminer.commission;

import com.example.blockminer.location.LocationTextParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CommissionTabListParser {
	private static final Pattern SECTION_HEADER = Pattern.compile("^Commissions:\\s*$", Pattern.CASE_INSENSITIVE);
	private static final Pattern NEXT_WIDGET_HEADER = Pattern.compile("^(?:Area|Dungeon|Commissions):(?:\\s.*)?$", Pattern.CASE_INSENSITIVE);
	// TODO(verify): This legacy collector-row grammar is only a starting point; validate against live captures.
	private static final Pattern COLLECTOR_ROW = Pattern.compile(
			"(.+?) (Gemstone )?Collector: ?(\\d{1,3}(\\.\\d+)?%|DONE)",
			Pattern.CASE_INSENSITIVE
	);
	private static final Pattern GENERIC_ROW = Pattern.compile(
			"^(.+?):\\s*(\\d{1,3}(?:\\.\\d+)?%|DONE)$",
			Pattern.CASE_INSENSITIVE
	);

	private CommissionTabListParser() {
	}

	public static ParseResult parse(List<String> tabListRows) {
		int sectionStart = -1;
		for (int index = 0; index < tabListRows.size(); index++) {
			if (SECTION_HEADER.matcher(LocationTextParser.stripFormatting(tabListRows.get(index)).trim()).matches()) {
				sectionStart = index + 1;
				break;
			}
		}
		if (sectionStart == -1) {
			return new ParseResult(false, List.of(), List.of());
		}

		List<String> rawSectionLines = new ArrayList<>();
		List<Entry> entries = new ArrayList<>();
		for (int index = sectionStart; index < tabListRows.size(); index++) {
			String rawLine = tabListRows.get(index);
			String plainLine = LocationTextParser.stripFormatting(rawLine).trim();
			if (NEXT_WIDGET_HEADER.matcher(plainLine).matches()) {
				break;
			}
			rawSectionLines.add(rawLine);
			parseEntry(rawLine, plainLine).ifPresent(entries::add);
		}
		return new ParseResult(true, rawSectionLines, entries);
	}

	private static java.util.Optional<Entry> parseEntry(String rawLine, String plainLine) {
		if (plainLine.isEmpty()) {
			return java.util.Optional.empty();
		}

		Matcher collectorMatcher = COLLECTOR_ROW.matcher(plainLine);
		if (collectorMatcher.matches()) {
			String action = collectorMatcher.group(2) == null ? "Collector" : "Gemstone Collector";
			String name = collectorMatcher.group(1).trim() + " " + action;
			return parseProgress(name, collectorMatcher.group(3), rawLine);
		}

		Matcher genericMatcher = GENERIC_ROW.matcher(plainLine);
		if (!genericMatcher.matches()) {
			return java.util.Optional.empty();
		}
		return parseProgress(genericMatcher.group(1).trim(), genericMatcher.group(2), rawLine);
	}

	private static java.util.Optional<Entry> parseProgress(String name, String rawProgress, String rawLine) {
		if (name.isEmpty()) {
			return java.util.Optional.empty();
		}
		if ("DONE".equals(rawProgress.toUpperCase(Locale.ROOT))) {
			return java.util.Optional.of(new Entry(name, new Progress(ProgressType.DONE, null), rawLine));
		}

		double percent;
		try {
			percent = Double.parseDouble(rawProgress.substring(0, rawProgress.length() - 1));
		} catch (NumberFormatException exception) {
			return java.util.Optional.empty();
		}
		if (percent < 0.0 || percent > 100.0) {
			return java.util.Optional.empty();
		}
		return java.util.Optional.of(new Entry(name, new Progress(ProgressType.PERCENT, percent), rawLine));
	}

	public enum ProgressType {
		PERCENT,
		DONE
	}

	public record Progress(ProgressType type, Double percent) {
		public Progress {
			Objects.requireNonNull(type, "type");
			if ((type == ProgressType.PERCENT) != (percent != null)) {
				throw new IllegalArgumentException("Only percent progress carries a numeric value");
			}
		}
	}

	public record Entry(String name, Progress progress, String rawLine) {
	}

	public record ParseResult(boolean sectionFound, List<String> rawSectionLines, List<Entry> entries) {
		public ParseResult {
			rawSectionLines = List.copyOf(rawSectionLines);
			entries = List.copyOf(entries);
		}
	}
}
