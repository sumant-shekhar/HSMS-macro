package com.example.blockminer.command;

import com.example.blockminer.ConfigScreen;
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
							context.getSource().getClient().setScreenAndShow(new ConfigScreen());
							context.getSource().sendFeedback(Component.literal("Opened Block Miner configuration."));
							return 1;
						})));
	}
}
