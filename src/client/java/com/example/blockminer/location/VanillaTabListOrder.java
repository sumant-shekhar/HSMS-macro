package com.example.blockminer.location;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public final class VanillaTabListOrder {
	public static final int MAX_ENTRIES = 80;
	public static final Comparator<Key> ORDER = Comparator
			.comparingInt(Key::tabListOrder).reversed()
			.thenComparing(Key::spectator)
			.thenComparing(Key::teamName)
			.thenComparing(Key::profileName, String.CASE_INSENSITIVE_ORDER);

	private VanillaTabListOrder() {
	}

	public static <T> List<T> order(Collection<T> entries, Function<T, Key> keyExtractor) {
		return entries.stream()
				.sorted(Comparator.comparing(keyExtractor, ORDER))
				.limit(MAX_ENTRIES)
				.toList();
	}

	public record Key(int tabListOrder, boolean spectator, String teamName, String profileName) {
		public Key {
			Objects.requireNonNull(teamName, "teamName");
			Objects.requireNonNull(profileName, "profileName");
		}
	}
}
