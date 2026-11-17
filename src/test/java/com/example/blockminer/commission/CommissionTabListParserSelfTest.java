package com.example.blockminer.commission;

import java.util.List;

public final class CommissionTabListParserSelfTest {
	private CommissionTabListParserSelfTest() {
	}

	public static void main(String[] args) {
		parsesSyntheticCommissionRowsAndPreservesRawLines();
		ignoresSyntheticUnstructuredDoneAndInvalidPercentages();
		handlesSyntheticMissingSectionAndNextWidget();
		System.out.println("Commission tab-list parser self-test passed (synthetic fixtures; 3 scenarios).");
	}

	private static void parsesSyntheticCommissionRowsAndPreservesRawLines() {
		CommissionTabListParser.ParseResult parsed = CommissionTabListParser.parse(List.of(
				"Players (3)",
				"§6Commissions:",
				"§bAmber Gemstone Collector: §a37.5%",
				"Mithril Miner: 100%",
				"Goblin Slayer: DONE",
				"Area: Dwarven Mines"
		));
		assertTrue(parsed.sectionFound(), "synthetic section found");
		assertEquals(3, parsed.entries().size(), "synthetic parsed entry count");
		assertEquals("Amber Gemstone Collector", parsed.entries().get(0).name(), "collector entry name");
		assertEquals(CommissionTabListParser.ProgressType.PERCENT, parsed.entries().get(0).progress().type(), "collector progress type");
		assertEquals(37.5, parsed.entries().get(0).progress().percent(), "collector progress percent");
		assertEquals("§bAmber Gemstone Collector: §a37.5%", parsed.entries().get(0).rawLine(), "raw colored row preserved");
		assertEquals(100.0, parsed.entries().get(1).progress().percent(), "100 percent accepted");
		assertEquals(CommissionTabListParser.ProgressType.DONE, parsed.entries().get(2).progress().type(), "DONE progress type");
		assertTrue(parsed.entries().get(2).progress().percent() == null, "DONE has no numeric percentage");
		assertEquals("Goblin Slayer: DONE", parsed.entries().get(2).rawLine(), "raw DONE row preserved");
		assertEquals(List.of(
				"§bAmber Gemstone Collector: §a37.5%",
				"Mithril Miner: 100%",
				"Goblin Slayer: DONE"
		), parsed.rawSectionLines(), "raw section lines retained up to next widget");
	}

	private static void ignoresSyntheticUnstructuredDoneAndInvalidPercentages() {
		CommissionTabListParser.ParseResult parsed = CommissionTabListParser.parse(List.of(
				"Commissions:",
				"DONE",
				"Progress complete: random DONE text",
				"Unknown Commission: 101%",
				": DONE",
				""
		));
		assertTrue(parsed.sectionFound(), "synthetic section found");
		assertEquals(0, parsed.entries().size(), "unstructured DONE and out-of-range values ignored");
	}

	private static void handlesSyntheticMissingSectionAndNextWidget() {
		CommissionTabListParser.ParseResult absent = CommissionTabListParser.parse(List.of("Mithril Miner: 20%"));
		assertTrue(!absent.sectionFound(), "missing synthetic section");
		assertTrue(absent.entries().isEmpty(), "no synthetic entries outside section");

		CommissionTabListParser.ParseResult bounded = CommissionTabListParser.parse(List.of(
				"Commissions:",
				"Jade Collector: 25%",
				"Dungeon: The Catacombs",
				"Later Item: DONE"
		));
		assertEquals(1, bounded.entries().size(), "entries stop at next synthetic widget");
		assertEquals(1, bounded.rawSectionLines().size(), "raw section ends at next widget");
	}

	private static void assertEquals(Object expected, Object actual, String label) {
		if (!expected.equals(actual)) {
			throw new AssertionError(label + ": expected " + expected + ", got " + actual);
		}
	}

	private static void assertTrue(boolean condition, String label) {
		if (!condition) {
			throw new AssertionError(label);
		}
	}
}
