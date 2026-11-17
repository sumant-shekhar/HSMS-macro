package com.example.blockminer.command;

import com.example.blockminer.Config;
import com.example.blockminer.macro.MacroManager;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

public final class MacroCommands {
	private MacroCommands() {
	}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
				dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("mm")
						.then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("start")
								.executes(context -> {
									MacroManager.getInstance().enable();
									return 1;
								}))
						.then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("stop")
								.executes(context -> {
									MacroManager.getInstance().disable();
									return 1;
								}))
						.then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("pause")
								.executes(context -> {
									MacroManager.getInstance().pause();
									return 1;
								}))
						.then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("resume")
								.executes(context -> {
									MacroManager.getInstance().resume();
									return 1;
								}))
						.then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("type")
								.then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument(
										"type", StringArgumentType.word())
										.executes(context -> {
											String type = StringArgumentType.getString(context, "type");
											int macroType = switch (type.toLowerCase(java.util.Locale.ROOT)) {
												case "commission" -> 0;
												case "glacial" -> 1;
												case "mining" -> 2;
												case "route", "route_miner" -> 3;
												default -> -1;
											};
											if (macroType < 0) {
												context.getSource().sendError(Component.literal(
														"Macro type must be commission, glacial, mining, or route."));
												return 0;
											}
											try {
												MacroManager.getInstance().setMacroType(macroType);
											} catch (IllegalStateException exception) {
												context.getSource().sendError(Component.literal(exception.getMessage()));
												return 0;
											}
											context.getSource().sendFeedback(Component.literal(
													"Selected macro type: " + type + "."));
											return 1;
										})))
						.then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("tool")
								.then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument(
										"name", StringArgumentType.greedyString())
										.executes(context -> setMiningTool(
												context.getSource(), StringArgumentType.getString(context, "name")))))
						.then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("weapon")
								.then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument(
										"name", StringArgumentType.greedyString())
										.executes(context -> setSlayerWeapon(
												context.getSource(), StringArgumentType.getString(context, "name")))))
						.then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("ore")
								.then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument(
										"type", StringArgumentType.word())
										.executes(context -> setMiningOre(
												context.getSource(), StringArgumentType.getString(context, "type")))))
						.then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("refuel")
								.then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument(
										"enabled", StringArgumentType.word())
										.executes(context -> setRefuel(
												context.getSource(), StringArgumentType.getString(context, "enabled")))))
						.then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("fuel")
								.then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument(
										"type", StringArgumentType.word())
										.executes(context -> setFuel(
												context.getSource(), StringArgumentType.getString(context, "type")))))
						.then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("claim")
								.then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument(
										"method", StringArgumentType.word())
										.executes(context -> setClaimMethod(
												context.getSource(), StringArgumentType.getString(context, "method")))))));
	}

	private static int setMiningTool(FabricClientCommandSource source, String value) {
		if (!requireStopped(source)) {
			return 0;
		}
		String name = value.trim();
		if (name.isEmpty()) {
			source.sendError(Component.literal("Mining tool name cannot be empty."));
			return 0;
		}
		Config.miningTool = name;
		source.sendFeedback(Component.literal("Mining tool set to: " + name));
		return 1;
	}

	private static int setSlayerWeapon(FabricClientCommandSource source, String value) {
		if (!requireStopped(source)) {
			return 0;
		}
		String name = value.trim();
		if (name.isEmpty()) {
			source.sendError(Component.literal("Slayer weapon name cannot be empty."));
			return 0;
		}
		Config.slayerWeapon = name;
		source.sendFeedback(Component.literal("Slayer weapon set to: " + name));
		return 1;
	}

	private static int setMiningOre(FabricClientCommandSource source, String value) {
		if (!requireStopped(source)) {
			return 0;
		}
		int ore = switch (value.toLowerCase(java.util.Locale.ROOT)) {
			case "mithril" -> 0;
			case "diamond" -> 1;
			case "emerald" -> 2;
			case "redstone" -> 3;
			case "lapis" -> 4;
			case "gold" -> 5;
			case "iron" -> 6;
			case "coal" -> 7;
			default -> -1;
		};
		if (ore < 0) {
			source.sendError(Component.literal(
					"Ore must be mithril, diamond, emerald, redstone, lapis, gold, iron, or coal."));
			return 0;
		}
		Config.oreType = ore;
		source.sendFeedback(Component.literal("Mining target set to: " + value));
		return 1;
	}

	private static int setRefuel(FabricClientCommandSource source, String value) {
		if (!requireStopped(source)) {
			return 0;
		}
		if ("on".equalsIgnoreCase(value)) {
			Config.drillRefuel = true;
		} else if ("off".equalsIgnoreCase(value)) {
			Config.drillRefuel = false;
		} else {
			source.sendError(Component.literal("Use /mm refuel on|off."));
			return 0;
		}
		source.sendFeedback(Component.literal(
				"Automatic drill refueling " + (Config.drillRefuel ? "enabled." : "disabled.")));
		return 1;
	}

	private static int setFuel(FabricClientCommandSource source, String value) {
		if (!requireStopped(source)) {
			return 0;
		}
		if ("volta".equalsIgnoreCase(value)) {
			Config.refuelMachineFuel = 0;
		} else if ("oil_barrel".equalsIgnoreCase(value)) {
			Config.refuelMachineFuel = 1;
		} else {
			source.sendError(Component.literal("Fuel type must be volta or oil_barrel."));
			return 0;
		}
		source.sendFeedback(Component.literal("Drill fuel set to: " + value));
		return 1;
	}

	private static int setClaimMethod(FabricClientCommandSource source, String value) {
		if (!requireStopped(source)) {
			return 0;
		}
		if ("emissary".equalsIgnoreCase(value)) {
			Config.commClaimMethod = 0;
		} else if ("pigeon".equalsIgnoreCase(value)) {
			Config.commClaimMethod = 1;
		} else {
			source.sendError(Component.literal("Claim method must be emissary or pigeon."));
			return 0;
		}
		source.sendFeedback(Component.literal("Commission claim method set to: " + value));
		return 1;
	}

	private static boolean requireStopped(FabricClientCommandSource source) {
		if (!MacroManager.getInstance().isEnabled()) {
			return true;
		}
		source.sendError(Component.literal("Stop the active macro before changing its settings."));
		return false;
	}
}
