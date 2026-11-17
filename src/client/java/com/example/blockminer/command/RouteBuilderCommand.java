package com.example.blockminer.command;

import com.example.blockminer.feature.impl.RouteBuilder;
import com.example.blockminer.handler.RouteHandler;
import com.example.blockminer.util.helper.route.WaypointType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

public final class RouteBuilderCommand {
	private RouteBuilderCommand() {
	}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
				dispatcher.register(rootCommand()));
	}

	private static LiteralArgumentBuilder<FabricClientCommandSource> rootCommand() {
		return LiteralArgumentBuilder.<FabricClientCommandSource>literal("rb")
				.executes(context -> {
					context.getSource().sendFeedback(Component.literal(
							"/rb list|reload|select <name>|add <walk|etherwarp|mine>|remove <index>"
									+ "|removeclosest|replace <index> <type>|delete <name>|enable|disable"));
					return 1;
				})
				.then(listCommand())
				.then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("reload")
						.executes(context -> {
							RouteHandler.getInstance().loadData();
							context.getSource().sendFeedback(Component.literal("Routes reloaded."));
							return 1;
						}))
				.then(selectCommand())
				.then(addCommand())
				.then(removeCommand())
				.then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("removeclosest")
						.executes(context -> {
							if (!requireBuilder(context.getSource())) {
								return 0;
							}
							if (!RouteBuilder.getInstance().removeClosest()) {
								context.getSource().sendError(Component.literal(
										"No waypoint could be removed from the selected route."));
								return 0;
							}
							context.getSource().sendFeedback(Component.literal("Removed the closest waypoint."));
							return 1;
						}))
				.then(replaceCommand())
				.then(deleteCommand())
				.then(toggleCommand("enable", true))
				.then(toggleCommand("disable", false))
				.then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("toggle")
						.executes(context -> {
							RouteBuilder.getInstance().toggle();
							return 1;
						}));
	}

	private static LiteralArgumentBuilder<FabricClientCommandSource> listCommand() {
		return LiteralArgumentBuilder.<FabricClientCommandSource>literal("list")
				.executes(context -> {
					String names = RouteHandler.getInstance().getRoutes().keySet().stream()
							.sorted().collect(Collectors.joining(", "));
					context.getSource().sendFeedback(Component.literal("Routes: " + names
							+ " (selected: " + RouteHandler.getInstance().getSelectedRouteName() + ")"));
					return 1;
				});
	}

	private static LiteralArgumentBuilder<FabricClientCommandSource> selectCommand() {
		return LiteralArgumentBuilder.<FabricClientCommandSource>literal("select")
				.then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument(
						"name", StringArgumentType.greedyString())
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
						}));
	}

	private static LiteralArgumentBuilder<FabricClientCommandSource> addCommand() {
		return LiteralArgumentBuilder.<FabricClientCommandSource>literal("add")
				.then(typeArgument("type", (context, type) -> {
					if (!requireBuilder(context.getSource())) {
						return 0;
					}
					if ("Default".equals(RouteHandler.getInstance().getSelectedRouteName())) {
						context.getSource().sendError(Component.literal("The Default route cannot be edited."));
						return 0;
					}
					RouteBuilder.getInstance().addToRoute(type);
					context.getSource().sendFeedback(Component.literal("Added " + type.name().toLowerCase(Locale.ROOT)
							+ " waypoint."));
					return 1;
				}));
	}

	private static LiteralArgumentBuilder<FabricClientCommandSource> removeCommand() {
		return LiteralArgumentBuilder.<FabricClientCommandSource>literal("remove")
				.then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument(
						"index", IntegerArgumentType.integer(1))
						.executes(context -> {
							if (!requireBuilder(context.getSource())) {
								return 0;
							}
							int index = IntegerArgumentType.getInteger(context, "index") - 1;
							return edit(context.getSource(), () -> RouteBuilder.getInstance().removeFromRoute(index));
						}));
	}

	private static LiteralArgumentBuilder<FabricClientCommandSource> replaceCommand() {
		return LiteralArgumentBuilder.<FabricClientCommandSource>literal("replace")
				.then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument(
						"index", IntegerArgumentType.integer(1))
						.then(typeArgument("type", (context, type) -> {
							if (!requireBuilder(context.getSource())) {
								return 0;
							}
							int index = IntegerArgumentType.getInteger(context, "index") - 1;
							return edit(context.getSource(),
									() -> RouteBuilder.getInstance().replaceNode(index, type));
						})));
	}

	private static LiteralArgumentBuilder<FabricClientCommandSource> deleteCommand() {
		return LiteralArgumentBuilder.<FabricClientCommandSource>literal("delete")
				.then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument(
						"name", StringArgumentType.greedyString())
						.executes(context -> {
							String name = StringArgumentType.getString(context, "name").trim();
							return edit(context.getSource(), () -> RouteHandler.getInstance().deleteRoute(name));
						}));
	}

	private static LiteralArgumentBuilder<FabricClientCommandSource> toggleCommand(String name, boolean enable) {
		return LiteralArgumentBuilder.<FabricClientCommandSource>literal(name)
				.executes(context -> {
					if (enable) {
						RouteBuilder.getInstance().start();
					} else {
						RouteBuilder.getInstance().stop();
					}
					return 1;
				});
	}

	private static RequiredArgumentBuilder<FabricClientCommandSource, String> typeArgument(
			String name,
			BiFunction<CommandContext<FabricClientCommandSource>, WaypointType, Integer> action
	) {
		return RequiredArgumentBuilder.<FabricClientCommandSource, String>argument(name, StringArgumentType.word())
				.executes(context -> {
					WaypointType type = parseType(StringArgumentType.getString(context, name));
					if (type == null) {
						context.getSource().sendError(Component.literal("Waypoint type must be walk, etherwarp, or mine."));
						return 0;
					}
					return action.apply(context, type);
				});
	}

	private static WaypointType parseType(String value) {
		return switch (value.toLowerCase(Locale.ROOT)) {
			case "walk" -> WaypointType.WALK;
			case "etherwarp" -> WaypointType.ETHERWARP;
			case "mine" -> WaypointType.MINE;
			default -> null;
		};
	}

	private static boolean requireBuilder(FabricClientCommandSource source) {
		if (RouteBuilder.getInstance().isRunning()) {
			return true;
		}
		source.sendError(Component.literal("Enable route recording first with /rb enable."));
		return false;
	}

	private static int edit(FabricClientCommandSource source, Runnable action) {
		try {
			action.run();
			source.sendFeedback(Component.literal("Route updated."));
			return 1;
		} catch (IndexOutOfBoundsException | IllegalArgumentException exception) {
			source.sendError(Component.literal("Could not update route: " + exception.getMessage()));
			return 0;
		}
	}
}
