package com.example.blockminer;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public final class BlockSelectionDebugCommand {
	private static final Logger LOGGER = LoggerFactory.getLogger("blockminer");

	private BlockSelectionDebugCommand() {
	}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("mmblocks")
					.executes(context -> scanBlocks(context.getSource(), List.of()))
					.then(com.mojang.brigadier.builder.RequiredArgumentBuilder
							.<FabricClientCommandSource, String>argument("block_ids", StringArgumentType.greedyString())
							.executes(context -> scanBlocks(
									context.getSource(),
									List.of(StringArgumentType.getString(context, "block_ids").split("\\s+"))
							))));

			dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("mmmine")
					.then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("start")
							.executes(context -> startMining(context.getSource(), List.of(), 1))
							.then(com.mojang.brigadier.builder.RequiredArgumentBuilder
									.<FabricClientCommandSource, String>argument("targets_and_priority", StringArgumentType.greedyString())
									.executes(context -> startMiningFromInput(
											context.getSource(),
											StringArgumentType.getString(context, "targets_and_priority")
									))))
					.then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("stop")
							.executes(context -> {
								BlockMiner.getInstance().stop();
								context.getSource().sendFeedback(Component.literal("Block selection miner stopped."));
								return 1;
							})));
		});
	}

	private static int startMiningFromInput(FabricClientCommandSource source, String input) {
		List<String> values = List.of(input.trim().split("\\s+"));
		int priority = 1;
		int blockIdCount = values.size();
		try {
			priority = Integer.parseInt(values.getLast());
			blockIdCount--;
			if (priority <= 0) {
				source.sendFeedback(Component.literal("Priority must be a positive integer."));
				return 0;
			}
		} catch (NumberFormatException ignored) {
			// The last token is a block ID rather than an optional priority.
		}
		return startMining(source, values.subList(0, blockIdCount), priority);
	}

	private static int startMining(FabricClientCommandSource source, List<String> requestedIds, int priority) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.level == null || client.gameMode == null) {
			source.sendFeedback(Component.literal("/mmmine requires an active world."));
			return 0;
		}
		try {
			Map<Block, Integer> priorities = BlockSelectionTargets.resolve(requestedIds, priority);
			BlockMiner.getInstance().start(priorities, Config.defaultMiningSpeed);
			source.sendFeedback(Component.literal(
					"Block selection miner started for " + priorities.size() + " block type(s), priority " + priority + "."
			));
			return 1;
		} catch (IllegalArgumentException exception) {
			source.sendFeedback(Component.literal(exception.getMessage()));
			return 0;
		}
	}

	private static int scanBlocks(FabricClientCommandSource source, List<String> requestedIds) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.level == null) {
			source.sendFeedback(Component.literal("/mmblocks requires an active world."));
			return 0;
		}

		Map<Block, Integer> priorities;
		try {
			priorities = BlockSelectionTargets.resolve(requestedIds, 1);
		} catch (IllegalArgumentException exception) {
			source.sendFeedback(Component.literal(exception.getMessage()));
			return 0;
		}

		List<BlockCandidate> candidates = BlockUtil.findMineableBlocksAroundPlayer(
				priorities,
				Config.defaultMiningSpeed
		);
		if (candidates.isEmpty()) {
			LOGGER.info("[blockminer] /mmblocks: no visible selected candidate blocks within reach");
			source.sendFeedback(Component.literal("No selected candidate blocks found within reach."));
			return 1;
		}

		LOGGER.info("[blockminer] /mmblocks top {} candidates:", Math.min(5, candidates.size()));
		for (int index = 0; index < Math.min(5, candidates.size()); index++) {
			BlockCandidate candidate = candidates.get(index);
			LOGGER.info("[blockminer] #{} {} at {} score={} hardness={} angle={} distanceSq={}",
					index + 1,
					BuiltInRegistries.BLOCK.getKey(candidate.state().getBlock()),
					candidate.position(),
					String.format(java.util.Locale.ROOT, "%.4f", candidate.score()),
					String.format(java.util.Locale.ROOT, "%.3f", candidate.hardness()),
					String.format(java.util.Locale.ROOT, "%.3f", candidate.angleChange()),
					String.format(java.util.Locale.ROOT, "%.3f", candidate.distanceSquared()));
		}
		source.sendFeedback(Component.literal(
				"Logged top " + Math.min(5, candidates.size()) + " block candidates to the client log."
		));
		return 1;
	}
}
