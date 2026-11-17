package com.example.blockminer.command;

import com.example.blockminer.feature.impl.AutoGetStats.AutoGetStats;
import com.example.blockminer.feature.impl.AutoChestUnlocker;
import com.example.blockminer.feature.impl.AutoCommissionClaim;
import com.example.blockminer.feature.impl.AutoDrillRefuel.AutoDrillRefuel;
import com.example.blockminer.feature.impl.AutoMobKiller.AutoMobKiller;
import com.example.blockminer.feature.impl.AutoWarp;
import com.example.blockminer.feature.impl.LagDetector;
import com.example.blockminer.feature.impl.RouteNavigator;
import com.example.blockminer.handler.RouteHandler;
import com.example.blockminer.location.Location;
import com.example.blockminer.location.SubLocation;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

import java.util.stream.Collectors;

public final class FeatureCommands {
	private FeatureCommands() {
	}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("mmstats")
					.executes(context -> {
						AutoGetStats.getInstance().startDefaultTasks();
						context.getSource().sendFeedback(Component.literal("Queued mining-stat retrieval."));
						return 1;
					}));

			dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("mmwarp")
					.then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("stop")
							.executes(context -> {
								AutoWarp.getInstance().stop();
								context.getSource().sendFeedback(Component.literal("AutoWarp stopped."));
								return 1;
							}))
					.then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("location")
							.then(com.mojang.brigadier.builder.RequiredArgumentBuilder
									.<FabricClientCommandSource, String>argument("destination", StringArgumentType.word())
									.executes(context -> {
										String value = StringArgumentType.getString(context, "destination");
										Location location = AutoWarp.parseLocation(value);
										if (location == null) {
											context.getSource().sendError(Component.literal(
													"Unknown location. Use a Location enum name such as crystal_hollows."));
											return 0;
										}
										AutoWarp.getInstance().start(location, null);
										return 1;
									})))
					.then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("sub")
							.then(com.mojang.brigadier.builder.RequiredArgumentBuilder
									.<FabricClientCommandSource, String>argument("destination", StringArgumentType.word())
									.executes(context -> {
										String value = StringArgumentType.getString(context, "destination");
										SubLocation subLocation = AutoWarp.parseSubLocation(value);
										if (subLocation == null) {
											context.getSource().sendError(Component.literal(
													"Unknown sub-location. Use its enum name, for example crystal_nucleus."));
											return 0;
										}
										AutoWarp.getInstance().start(null, subLocation);
										return 1;
									}))));

			dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("mmroute")
					.then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("list")
							.executes(context -> {
								String routes = RouteHandler.getInstance().getRoutes().keySet().stream()
										.sorted()
										.collect(Collectors.joining(", "));
								context.getSource().sendFeedback(Component.literal("Routes: " + routes));
								return 1;
							}))
					.then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("select")
							.then(com.mojang.brigadier.builder.RequiredArgumentBuilder
									.<FabricClientCommandSource, String>argument("name", StringArgumentType.greedyString())
									.executes(context -> {
										String name = StringArgumentType.getString(context, "name").trim();
										if (name.isEmpty()) {
											context.getSource().sendError(Component.literal("Route name cannot be empty."));
											return 0;
										}
										RouteHandler.getInstance().selectRoute(name);
										RouteHandler.getInstance().saveData();
										context.getSource().sendFeedback(Component.literal("Selected route: " + name));
										return 1;
									})))
					.then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("start")
							.executes(context -> {
								RouteNavigator.getInstance().start(RouteHandler.getInstance().getSelectedRoute());
								return 1;
							}))
					.then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("stop")
							.executes(context -> {
								RouteNavigator.getInstance().stop();
								context.getSource().sendFeedback(Component.literal("Route navigation stopped."));
								return 1;
							}))
					.then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("goto")
							.then(com.mojang.brigadier.builder.RequiredArgumentBuilder
									.<FabricClientCommandSource, Integer>argument("index", IntegerArgumentType.integer(1))
									.executes(context -> {
										RouteNavigator.getInstance().goTo(
												IntegerArgumentType.getInteger(context, "index") - 1);
										return 1;
									})))
					.then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("graph")
							.executes(context -> {
								String names = com.example.blockminer.handler.GraphHandler.getInstance().graphs
										.keySet().stream().sorted().collect(Collectors.joining(", "));
								context.getSource().sendFeedback(Component.literal("Route graphs: " + names));
								return 1;
							})));

			dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("mmfeature")
					.then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("mobkill")
							.then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("start")
									.then(com.mojang.brigadier.builder.RequiredArgumentBuilder
											.<FabricClientCommandSource, String>argument(
													"weapon", StringArgumentType.word())
											.then(com.mojang.brigadier.builder.RequiredArgumentBuilder
													.<FabricClientCommandSource, String>argument(
															"mobs", StringArgumentType.greedyString())
													.executes(context -> {
														String names = StringArgumentType.getString(context, "mobs");
														AutoMobKiller.getInstance().start(
																java.util.Arrays.stream(names.split(","))
																		.map(String::trim)
																		.filter(name -> !name.isEmpty())
																		.toList(),
																StringArgumentType.getString(context, "weapon"));
														return 1;
													}))))
							.then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("stop")
									.executes(context -> {
										AutoMobKiller.getInstance().stop();
										context.getSource().sendFeedback(Component.literal("AutoMobKiller stopped."));
										return 1;
									})))
					.then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("commission")
							.then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("start")
									.executes(context -> {
										AutoCommissionClaim.getInstance().start();
										return 1;
									}))
							.then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("stop")
									.executes(context -> {
										AutoCommissionClaim.getInstance().stop();
										context.getSource().sendFeedback(Component.literal("Commission claiming stopped."));
										return 1;
									})))
					.then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("chest")
							.then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("start")
									.executes(context -> {
										AutoChestUnlocker.getInstance().start(true);
										return 1;
									}))
							.then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("stop")
									.executes(context -> {
										AutoChestUnlocker.getInstance().stop();
										context.getSource().sendFeedback(Component.literal("Chest scanning stopped."));
										return 1;
									})))
					.then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("drill")
							.then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("start")
									.then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("volta")
											.then(com.mojang.brigadier.builder.RequiredArgumentBuilder
													.<FabricClientCommandSource, String>argument(
															"drill", StringArgumentType.greedyString())
													.executes(context -> {
														AutoDrillRefuel.getInstance().start(
																StringArgumentType.getString(context, "drill"),
																AutoDrillRefuel.FuelType.VOLTA);
														return 1;
													})))
									.then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("oil_barrel")
											.then(com.mojang.brigadier.builder.RequiredArgumentBuilder
													.<FabricClientCommandSource, String>argument(
															"drill", StringArgumentType.greedyString())
													.executes(context -> {
														AutoDrillRefuel.getInstance().start(
																StringArgumentType.getString(context, "drill"),
																AutoDrillRefuel.FuelType.OIL_BARREL);
														return 1;
													}))))
							.then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("stop")
									.executes(context -> {
										AutoDrillRefuel.getInstance().stop();
										context.getSource().sendFeedback(Component.literal("AutoDrillRefuel stopped."));
										return 1;
									})))
					.then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("lag")
							.executes(context -> {
								LagDetector detector = LagDetector.getInstance();
								detector.start();
								context.getSource().sendFeedback(Component.literal(
										"Lag detector started; use /mmfeature lagstatus to inspect client world-time samples."));
								return 1;
							}))
					.then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("lagstatus")
							.executes(context -> {
								LagDetector detector = LagDetector.getInstance();
								context.getSource().sendFeedback(Component.literal(
										"Observed tick rate: " + detector.getTickRate()
												+ ", world-clock delay: " + detector.getTimeSinceLastTick() + "s"));
								return 1;
							})));
		});
	}
}
