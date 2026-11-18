package com.example.blockminer;

import com.google.gson.JsonParseException;
import com.example.blockminer.command.FeatureCommands;
import com.example.blockminer.command.MacroCommands;
import com.example.blockminer.command.RouteBuilderCommand;
import com.example.blockminer.command.ConfigCommands;
import com.example.blockminer.feature.impl.AutoChestUnlocker;
import com.example.blockminer.feature.impl.AutoGetStats.AutoGetStats;
import com.example.blockminer.feature.impl.AutoMobKiller.AutoMobKiller;
import com.example.blockminer.feature.impl.RouteBuilder;
import com.example.blockminer.feature.impl.RouteNavigator;
import com.example.blockminer.feature.FeatureManager;
import com.example.blockminer.handler.RouteHandler;
import com.example.blockminer.hud.CommissionHudOverlay;
import com.example.blockminer.hud.DebugHudOverlay;
import com.example.blockminer.hud.GlacialCommissionHudOverlay;
import com.example.blockminer.location.LocationDebugCommand;
import com.example.blockminer.location.LocationDumpCommand;
import com.example.blockminer.location.LocationTracker;
import com.example.blockminer.macro.MacroManager;
import com.example.blockminer.pathfinder.PathfindingDebugCommands;
import com.example.blockminer.util.Strings;
import com.example.blockminer.util.RenderUtil;
import com.example.blockminer.util.helper.route.WaypointType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.input.KeyEvent;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.platform.InputConstants;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.util.Objects;

public final class BlockMinerClient implements ClientModInitializer {
	private static final String MOD_ID = "blockminer";
	private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	private static final MinerState MINER = new MinerState();
	private static int autoClickIntervalTicks = 100;
	public static final KeyMapping.Category BLOCKMINER_CATEGORY =
			KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));

	private static KeyMapping openConfigKey;
	private static KeyMapping toggleMacroKey;
	private static KeyMapping routeBuilderKey;
	private static KeyMapping routeBuilderAddWalkKey;
	private static KeyMapping routeBuilderAddEtherwarpKey;
	private static KeyMapping routeBuilderRemoveNodeKey;
	private static KeyMapping routeNavigationKey;
	private static KeyMapping getStatsKey;
	private static boolean openConfigPending;
	private static boolean openModePending;
	private static boolean autoClickEnabled;
	private static int autoClickTicks;

	@Override
	public void onInitializeClient() {
		LOGGER.info("[blockminer] initialized");
		try {
			Config.load();
		} catch (IOException | JsonParseException | IllegalArgumentException | IllegalStateException exception) {
			LOGGER.error("[blockminer] Could not load config; using in-memory defaults.", exception);
		}
		openConfigKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.blockminer.open_config",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_RIGHT_SHIFT,
				BLOCKMINER_CATEGORY
		));

		toggleMacroKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.blockminer.toggle_macro",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_GRAVE_ACCENT,
				BLOCKMINER_CATEGORY
		));

		routeBuilderKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.blockminer.toggle_route_builder",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_LEFT_BRACKET,
				BLOCKMINER_CATEGORY
		));

		routeBuilderAddWalkKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.blockminer.route_builder_add_walk",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_P,
				BLOCKMINER_CATEGORY
		));

		routeBuilderAddEtherwarpKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.blockminer.route_builder_add_etherwarp",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_I,
				BLOCKMINER_CATEGORY
		));

		routeBuilderRemoveNodeKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.blockminer.route_builder_remove_node",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_O,
				BLOCKMINER_CATEGORY
		));

		routeNavigationKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.blockminer.toggle_route_navigation",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_UNKNOWN,
				BLOCKMINER_CATEGORY
		));

		getStatsKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.blockminer.retrieve_mining_stats",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_UNKNOWN,
				BLOCKMINER_CATEGORY
		));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			// TODO(verify): END_CLIENT_TICK has no partial-tick value; confirm tick stepping is sufficient for camera easing.
			RotationHandler.getInstance().tick(client);
			KeyBindUtil.tick();
			com.example.blockminer.pathfinder.PathExecutor.getInstance().tick(client);
			MacroManager.getInstance().onTick(client);
			AutoChestUnlocker.getInstance().tick(client);
			MINER.tick(client);
			BlockMiner.getInstance().tick(client);
			tickAutoClick(client);
			while (openConfigKey.consumeClick()) {
				if (client.gui.screen() == null) {
					releaseAllKeys();
					client.setScreenAndShow(new BlockMinerScreen());
				} else if (client.gui.screen() instanceof BlockMinerScreen) {
					client.gui.screen().onClose();
				}
			}
			while (toggleMacroKey.consumeClick()) {
				if (client.player != null && client.level != null && client.gui.screen() == null) {
					MacroManager.getInstance().toggle();
				}
			}
			while (routeBuilderKey.consumeClick()) {
				if (client.player != null && client.level != null && client.gui.screen() == null) {
					RouteBuilder.getInstance().toggle();
				}
			}
			while (routeBuilderAddWalkKey.consumeClick()) {
				if (client.player != null && client.level != null && client.gui.screen() == null) {
					if (RouteBuilder.getInstance().isRunning()) {
						RouteBuilder.getInstance().addToRoute(WaypointType.WALK);
					}
				}
			}
			while (routeBuilderAddEtherwarpKey.consumeClick()) {
				if (client.player != null && client.level != null && client.gui.screen() == null) {
					if (RouteBuilder.getInstance().isRunning()) {
						RouteBuilder.getInstance().addToRoute(WaypointType.ETHERWARP);
					}
				}
			}
			while (routeBuilderRemoveNodeKey.consumeClick()) {
				if (client.player != null && client.level != null && client.gui.screen() == null) {
					if (RouteBuilder.getInstance().isRunning()) {
						RouteBuilder.getInstance().removeClosest();
					}
				}
			}
			while (routeNavigationKey.consumeClick()) {
				if (client.player != null && client.level != null && client.gui.screen() == null) {
					if (RouteNavigator.getInstance().isRunning()) {
						RouteNavigator.getInstance().stop();
					} else {
						RouteNavigator.getInstance().start(RouteHandler.getInstance().getSelectedRoute());
					}
				}
			}
			while (getStatsKey.consumeClick()) {
				if (client.player != null && client.level != null && client.gui.screen() == null) {
					AutoGetStats.getInstance().startDefaultTasks();
				}
			}
			if (openConfigPending) {
				openConfigPending = false;
				if (client.gui.screen() == null) {
					releaseAllKeys();
					client.setScreenAndShow(new BlockMinerScreen(openModePending ? BlockMinerScreen.Tab.MINING : BlockMinerScreen.Tab.DASHBOARD));
				}
				openModePending = false;
			}
		});
		RotationDebugCommands.register();
		BlockSelectionDebugCommand.register();
		LocationTracker.register();
		FeatureManager.getInstance().registerHooks();
		FeatureCommands.register();
		MacroCommands.register();
		ConfigCommands.register();
		RouteBuilderCommand.register();
		LocationDebugCommand.register();
		LocationDumpCommand.register();
		PathfindingDebugCommands.register();

		ClientSendMessageEvents.ALLOW_CHAT.register(message -> {
			String command = message.trim();
			if (isBlockMinerChatCommand(command)) {
				LOGGER.info("[blockminer] received chat command: {}", command);
			}
			if ("!ore".equalsIgnoreCase(command)
					|| "$mithril ore".equalsIgnoreCase(command)) {
				openModePending = true;
				openConfigPending = true;
				return false;
			}
			if ("!mithril settings".equalsIgnoreCase(command)
					|| "$mithril settings".equalsIgnoreCase(command)) {
				openConfigPending = true;
				return false;
			}
			if ("!click".equalsIgnoreCase(command)) {
				toggleAutoClick(Minecraft.getInstance());
				return false;
			}
			if (!"$mithril".equalsIgnoreCase(command)
					&& !"!mithril".equalsIgnoreCase(command)) {
				return true;
			}

			toggle(Minecraft.getInstance());
			return false;
		});

		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			MacroManager.getInstance().onChat(message.getString());
			AutoChestUnlocker.getInstance().onChat(message.getString());
			PickaxeAbilityTracker.getInstance().onChat(message.getString());
		});
		ClientReceiveMessageEvents.CHAT.register((message, playerChatMessage, sender, boundChatType, timeStamp) -> {
			MacroManager.getInstance().onChat(message.getString());
			AutoChestUnlocker.getInstance().onChat(message.getString());
			PickaxeAbilityTracker.getInstance().onChat(message.getString());
		});

		HudElementRegistry.attachElementBefore(
				VanillaHudElements.CHAT,
				Identifier.fromNamespaceAndPath(MOD_ID, "status"),
				BlockMinerClient::renderStatus
		);
		HudElementRegistry.attachElementBefore(
				VanillaHudElements.CHAT,
				Identifier.fromNamespaceAndPath(MOD_ID, "commission_hud"),
				(graphics, deltaTracker) -> CommissionHudOverlay.render(graphics)
		);
		HudElementRegistry.attachElementBefore(
				VanillaHudElements.CHAT,
				Identifier.fromNamespaceAndPath(MOD_ID, "glacial_commission_hud"),
				(graphics, deltaTracker) -> GlacialCommissionHudOverlay.render(graphics)
		);
		HudElementRegistry.attachElementBefore(
				VanillaHudElements.CHAT,
				Identifier.fromNamespaceAndPath(MOD_ID, "debug_hud"),
				(graphics, deltaTracker) -> DebugHudOverlay.render(graphics)
		);
		LevelRenderEvents.BEFORE_GIZMOS.register(context -> {
			MacroManager.getInstance().onWorldRender();
			RenderUtil.renderQueued();
		});
	}

	private static boolean isBlockMinerChatCommand(String command) {
		return "!ore".equalsIgnoreCase(command)
				|| "!mithril settings".equalsIgnoreCase(command)
				|| "$mithril settings".equalsIgnoreCase(command)
				|| "$mithril ore".equalsIgnoreCase(command)
				|| "!click".equalsIgnoreCase(command)
				|| "$mithril".equalsIgnoreCase(command)
				|| "!mithril".equalsIgnoreCase(command);
	}

	public static void requestConfigScreen() {
		openConfigPending = true;
	}

	public static MinerState getMiner() {
		return MINER;
	}

	public static KeyMapping getOpenConfigKey() {
		return openConfigKey;
	}

	public static KeyMapping getToggleMacroKey() {
		return toggleMacroKey;
	}

	public static KeyMapping getRouteBuilderKey() {
		return routeBuilderKey;
	}

	public static KeyMapping getRouteBuilderAddWalkKey() {
		return routeBuilderAddWalkKey;
	}

	public static KeyMapping getRouteBuilderAddEtherwarpKey() {
		return routeBuilderAddEtherwarpKey;
	}

	public static KeyMapping getRouteBuilderRemoveNodeKey() {
		return routeBuilderRemoveNodeKey;
	}

	public static KeyMapping getRouteNavigationKey() {
		return routeNavigationKey;
	}

	public static KeyMapping getGetStatsKey() {
		return getStatsKey;
	}

	public static void setLeftClick(boolean pressed) {
		Minecraft client = Minecraft.getInstance();
		if (client != null && client.options != null && client.options.keyAttack != null) {
			KeyBindUtil.setKeyBindState(client.options.keyAttack, pressed);
		}
	}

	public static void triggerLeftClick() {
		KeyBindUtil.leftClick();
	}

	public static void setRightClick(boolean pressed) {
		Minecraft client = Minecraft.getInstance();
		if (client != null && client.options != null && client.options.keyUse != null) {
			KeyBindUtil.setKeyBindState(client.options.keyUse, pressed);
		}
	}

	public static void triggerRightClick() {
		KeyBindUtil.rightClick();
	}

	public static void releaseAllKeys() {
		KeyBindUtil.releaseAll();
	}

	public static boolean handleRawKeyPress(KeyEvent event) {
		Minecraft client = Minecraft.getInstance();
		if (client == null) {
			return false;
		}

		if (client.gui.screen() instanceof BlockMinerScreen screen && screen.isRebindingKey()) {
			return false;
		}

		boolean matchesConfig = (openConfigKey != null && openConfigKey.matches(event));

		if (matchesConfig) {
			if (client.gui.screen() == null) {
				releaseAllKeys();
				client.setScreenAndShow(new BlockMinerScreen());
				return true;
			} else if (client.gui.screen() instanceof BlockMinerScreen) {
				client.gui.screen().onClose();
				return true;
			}
		}
		return false;
	}

	public static void stopAll(Minecraft client) {
		MINER.stop(client);
		AutoChestUnlocker.getInstance().stop();
		KeyBindUtil.releaseAll();
		releaseAllKeys();
		MacroManager.getInstance().disable();
		RouteNavigator.getInstance().stop();
		AutoMobKiller.getInstance().stop();
		KeyBindUtil.stopMovement();
	}

	public static boolean isAutoClickEnabled() {
		return autoClickEnabled;
	}

	public static void setAutoClickEnabled(boolean enabled) {
		autoClickEnabled = enabled;
	}

	public static int getAutoClickIntervalTicks() {
		return autoClickIntervalTicks;
	}

	public static void setAutoClickIntervalTicks(int ticks) {
		autoClickIntervalTicks = Math.max(1, ticks);
	}

	static void toggleAutoClick(Minecraft client) {
		autoClickEnabled = !autoClickEnabled;
		autoClickTicks = 0;
		if (client.player != null) {
			client.player.sendOverlayMessage(Component.literal(
					"Auto click: " + (autoClickEnabled ? "ON" : "OFF")
			));

		}
	}

	private static void tickAutoClick(Minecraft client) {
		if (!autoClickEnabled || client.player == null || client.gui.screen() != null) {
			return;
		}
		if (++autoClickTicks >= autoClickIntervalTicks) {
			autoClickTicks = 0;
			if (Config.autoClickerRightClick) {
				triggerRightClick();
			} else {
				triggerLeftClick();
			}
		}
	}

	private static void toggle(Minecraft client) {
		boolean enabled = MINER.toggle(client);
		if (client.player != null) {
			client.player.sendOverlayMessage(Component.literal("Block Miner: " + (enabled ? "ON" : "OFF")));
		}
	}

	private static void renderStatus(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft client = Minecraft.getInstance();
		MacroManager.getInstance().onOverlayRender(graphics, deltaTracker);
		if (!MINER.isEnabled() || client.gui.screen() != null) {
			return;
		}

		@NonNull String status = Objects.requireNonNull(MINER.getStatus());
		graphics.fill(6, 6, client.font.width(status) + 18, 24, 0xAA101820);
		graphics.text(client.font, status, 12, 11, 0xFF8BE28B, true);
	}
}
