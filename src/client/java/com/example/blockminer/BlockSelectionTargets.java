package com.example.blockminer;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class BlockSelectionTargets {
	private static final List<String> DEFAULT_BLOCK_IDS = List.of(
			"coal_block",
			"iron_block",
			"gold_block",
			"redstone_block",
			"lapis_block",
			"diamond_block",
			"emerald_block"
	);

	private BlockSelectionTargets() {
	}

	static Map<Block, Integer> resolve(List<String> requestedIds, int priority) {
		if (priority <= 0) {
			throw new IllegalArgumentException("Priority must be a positive integer.");
		}

		List<String> ids = requestedIds.isEmpty() ? DEFAULT_BLOCK_IDS : requestedIds;
		Map<Block, Integer> blocks = new LinkedHashMap<>();
		for (String requestedId : ids) {
			String path;
			if (requestedId.startsWith("minecraft:")) {
				path = requestedId.substring("minecraft:".length());
			} else if (requestedId.indexOf(':') >= 0) {
				throw new IllegalArgumentException("Only vanilla minecraft block IDs are accepted: " + requestedId);
			} else {
				path = requestedId;
			}

			Identifier identifier;
			try {
				identifier = Identifier.fromNamespaceAndPath("minecraft", path);
			} catch (IllegalArgumentException exception) {
				throw new IllegalArgumentException("Invalid block ID: " + requestedId, exception);
			}

			Block block = BuiltInRegistries.BLOCK.getValue(identifier);
			if (block == Blocks.AIR && !identifier.equals(Identifier.fromNamespaceAndPath("minecraft", "air"))) {
				throw new IllegalArgumentException("Unknown vanilla block ID: minecraft:" + path);
			}
			blocks.put(block, priority);
		}
		return Map.copyOf(blocks);
	}
}
