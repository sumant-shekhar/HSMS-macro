package com.example.blockminer.location;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

public final class LocationDebugCommand {
	private LocationDebugCommand() {
	}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
				dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("mmloc")
						.executes(context -> {
							printState(context.getSource(), LocationTracker.getGameState());
							return 1;
						}))
		);
	}

	private static void printState(FabricClientCommandSource source, GameStateHandler state) {
		source.sendFeedback(Component.literal("Location: " + state.getCurrentLocation()));
		source.sendFeedback(Component.literal("SubLocation: " + state.getCurrentSubLocation()));
		source.sendFeedback(Component.literal("Game state: " + state.getGameState()));
		source.sendFeedback(Component.literal("Area line: " + printable(state.getRawAreaLine())));
		source.sendFeedback(Component.literal("Parsed area: " + printable(state.getAreaName())));
		source.sendFeedback(Component.literal("Scoreboard title: " + state.getScoreboardTitle()));
		source.sendFeedback(Component.literal("Parsed scoreboard line: " + printable(state.getRawSubLocationLine())));
		source.sendFeedback(Component.literal("Parsed sub-location text: " + printable(state.getCleanedSubLocation())));
		source.sendFeedback(Component.literal("Sidebar lines:"));
		if (state.getScoreboardLines().isEmpty()) {
			source.sendFeedback(Component.literal("  <none>"));
		} else {
			for (String line : state.getScoreboardLines()) {
				source.sendFeedback(Component.literal("  " + line));
			}
		}
	}

	private static String printable(String value) {
		return value == null ? "<not found>" : value;
	}
}
