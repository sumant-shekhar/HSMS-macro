package com.example.blockminer;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class RotationDebugCommands {
	private RotationDebugCommands() {
	}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			RequiredArgumentBuilder<FabricClientCommandSource, Long> rotationDuration =
					RequiredArgumentBuilder.<FabricClientCommandSource, Long>argument(
							"ms", LongArgumentType.longArg(1L, 60000L))
							.executes(context -> {
								Minecraft client = Minecraft.getInstance();
								if (client.player == null) {
									context.getSource().sendFeedback(Component.literal("mmrot requires an active world."));
									return 0;
								}
								Angle target = new Angle(
										FloatArgumentType.getFloat(context, "yaw"),
										FloatArgumentType.getFloat(context, "pitch")
								);
								long durationMs = LongArgumentType.getLong(context, "ms");
								RotationHandler.getInstance().queueRotation(
										new RotationConfiguration(target, durationMs, null)
								);
								RotationHandler.getInstance().start();
								context.getSource().sendFeedback(Component.literal(
										"Rotation queued to yaw " + target.getYaw() + ", pitch " + target.getPitch()
								));
								return 1;
							});
			RequiredArgumentBuilder<FabricClientCommandSource, Float> pitch =
					RequiredArgumentBuilder.<FabricClientCommandSource, Float>argument(
							"pitch", FloatArgumentType.floatArg(-90.0F, 90.0F))
							.then(rotationDuration);
			RequiredArgumentBuilder<FabricClientCommandSource, Float> yaw =
					RequiredArgumentBuilder.<FabricClientCommandSource, Float>argument(
							"yaw", FloatArgumentType.floatArg(-360000.0F, 360000.0F))
							.then(pitch);
			dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("mmrot").then(yaw));

			RequiredArgumentBuilder<FabricClientCommandSource, Long> keyDuration =
					RequiredArgumentBuilder.<FabricClientCommandSource, Long>argument(
							"ms", LongArgumentType.longArg(1L, 60000L))
							.executes(context -> {
								String name = StringArgumentType.getString(context, "key");
								var key = KeyBindUtil.getDebugKey(name);
								if (key == null) {
									context.getSource().sendFeedback(Component.literal(
											"Unknown key. Use w, a, s, d, or jump."
									));
									return 0;
								}
								long durationMs = LongArgumentType.getLong(context, "ms");
								KeyBindUtil.pressFor(key, durationMs);
								context.getSource().sendFeedback(Component.literal(
										"Pressed " + name.toLowerCase(java.util.Locale.ROOT) + " for " + durationMs + " ms."
								));
								return 1;
							});
			RequiredArgumentBuilder<FabricClientCommandSource, String> key =
					RequiredArgumentBuilder.<FabricClientCommandSource, String>argument(
							"key", StringArgumentType.word())
							.then(keyDuration);
			dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("mmkey").then(key));
		});
	}
}
