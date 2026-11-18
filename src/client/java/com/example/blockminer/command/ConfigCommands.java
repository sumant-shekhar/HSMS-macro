package com.example.blockminer.command;

import com.example.blockminer.BlockMinerClient;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

public final class ConfigCommands {
	private ConfigCommands() {
	}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
				dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("mmconfig")
						.executes(context -> {
							BlockMinerClient.requestConfigScreen();
							context.getSource().sendFeedback(Component.literal("Opening Block Miner configuration."));
							return 1;
						})));
	}
}
