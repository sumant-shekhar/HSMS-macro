package com.example.blockminer.pathfinder;

import com.example.blockminer.PlayerUtil;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class PathfindingDebugCommands {
    private static final Logger LOGGER = LoggerFactory.getLogger("blockminer");
    private static final ExecutorService SEARCH_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "blockminer-path-search");
        thread.setDaemon(true);
        return thread;
    });

    private PathfindingDebugCommands() {
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            registerPathCommand(dispatcher, "mmpath", false);
            registerPathCommand(dispatcher, "mmwalk", true);
            dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("mmwalk")
                    .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("stop")
                            .executes(context -> {
                                PathExecutor.getInstance().stop();
                                context.getSource().sendFeedback(Component.literal("Path following stopped."));
                                return 1;
                            })));
        });
    }

    public static void tick(Minecraft client) {
        PathExecutor.getInstance().tick(client);
    }

    private static void registerPathCommand(
            com.mojang.brigadier.CommandDispatcher<FabricClientCommandSource> dispatcher,
            String name,
            boolean walk
    ) {
        RequiredArgumentBuilder<FabricClientCommandSource, Integer> z =
                RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("z", IntegerArgumentType.integer())
                        .executes(context -> requestPath(
                                context.getSource(),
                                IntegerArgumentType.getInteger(context, "x"),
                                IntegerArgumentType.getInteger(context, "y"),
                                IntegerArgumentType.getInteger(context, "z"),
                                walk
                        ));
        RequiredArgumentBuilder<FabricClientCommandSource, Integer> y =
                RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("y", IntegerArgumentType.integer())
                        .then(z);
        RequiredArgumentBuilder<FabricClientCommandSource, Integer> x =
                RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("x", IntegerArgumentType.integer())
                        .then(y);
        dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal(name)
                .executes(context -> {
                    context.getSource().sendFeedback(Component.literal(walk
                            ? "Usage: /mmwalk <x> <y> <z> | /mmwalk stop."
                            : "Usage: /mmpath <x> <y> <z>."));
                    return 0;
                })
                .then(x));
    }

    private static int requestPath(FabricClientCommandSource source, int x, int y, int z, boolean walk) {
        Minecraft client = Minecraft.getInstance();
        ClientLevel level = client.level;
        if (client.player == null || level == null) {
            source.sendError(Component.literal("/" + (walk ? "mmwalk" : "mmpath") + " requires an active world."));
            return 0;
        }

        BlockPos start = PlayerUtil.getBlockStandingOn(client.player);
        BlockPos goal = new BlockPos(x, y, z);
        long requestStarted = System.nanoTime();
        PathfindingSnapshot snapshot;
        try {
            snapshot = PathfindingSnapshot.capture(level, start, goal);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            source.sendError(Component.literal("Could not create path snapshot: " + exception.getMessage()));
            return 0;
        }

        source.sendFeedback(Component.literal(
                "Path search started from " + start.getX() + " " + start.getY() + " " + start.getZ()
                        + " to " + x + " " + y + " " + z + "."
        ));
        CompletableFuture.supplyAsync(() -> {
            ActionCosts costs = new ActionCosts();
            AStarPathFinder finder = new AStarPathFinder(
                    start.getX(),
                    start.getY(),
                    start.getZ(),
                    new Goal(x, y, z, costs),
                    new WorldMoveCalculator(snapshot, costs)
            );
            PathResult result = finder.calculatePath();
            long elapsedMillis = (System.nanoTime() - requestStarted) / 1_000_000L;
            return new SearchResult(result, elapsedMillis);
        }, SEARCH_EXECUTOR).whenComplete((search, failure) -> client.execute(() -> {
            if (failure != null) {
                Throwable cause = failure instanceof CompletionException && failure.getCause() != null
                        ? failure.getCause()
                        : failure;
                LOGGER.error("[blockminer] path search failed", cause);
                source.sendError(Component.literal("Path search failed: " + cause.getMessage()));
                return;
            }
            if (search.path() == null) {
                LOGGER.info("[blockminer] no path found to {} {} {}", x, y, z);
                source.sendFeedback(Component.literal(
                        "No path found to " + x + " " + y + " " + z
                                + " (snapshot + search: " + search.elapsedMillis() + " ms)."
                ));
                return;
            }

            int nodeCount = search.path().getNodes().size();
            LOGGER.info("[blockminer] path to {} {} {}: {} nodes in {} ms",
                    x, y, z, nodeCount, search.elapsedMillis());
            source.sendFeedback(Component.literal(
                    "Path found: " + nodeCount + " nodes in " + search.elapsedMillis() + " ms."
            ));
            if (walk) {
                if (client.level != level || client.player == null) {
                    source.sendError(Component.literal("Path found, but the world changed before following could start."));
                    return;
                }
                PathExecutor.getInstance().start(search.path());
                source.sendFeedback(Component.literal("Following path. Use /mmwalk stop to stop."));
            }
        }));
        return 1;
    }

    private record SearchResult(PathResult path, long elapsedMillis) {
    }
}
