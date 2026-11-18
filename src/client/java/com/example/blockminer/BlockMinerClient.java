package com.example.blockminer;

import com.google.gson.JsonParseException;
import com.example.blockminer.command.FeatureCommands;
import com.example.blockminer.command.MacroCommands;
import com.example.blockminer.command.RouteBuilderCommand;
import com.example.blockminer.command.ConfigCommands;
import com.example.blockminer.feature.impl.AutoChestUnlocker;
import com.example.blockminer.feature.impl.AutoGetStats.AutoGetStats;
import com.example.blockminer.feature.impl.AutoMobKiller.AutoMobKiller;
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
	private static final String PICKOBULUS_MESSAGE = Strings.PICKOBULUS_AVAILABLE_CHAT;
	private static final MinerState MINER = new MinerState();
	private static int autoClickIntervalTicks = 100;
	private static KeyMapping openConfigKey;
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
				KeyMapping.Category.MISC
		));

		routeNavigationKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.blockminer.toggle_route_navigation",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_UNKNOWN,
				KeyMapping.Category.MISC
		));

		getStatsKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.blockminer.retrieve_mining_stats",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_UNKNOWN,
				KeyMapping.Category.MISC
		));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			// TODO(verify): END_CLIENT_TICK has no partial-tick value; confirm tick stepping is sufficient for camera easing.
			RotationHandler.getInstance().tick(client);
			KeyBindUtil.tick();
			PathfindingDebugCommands.tick(client);
			MacroManager.getInstance().onTick(client);
			AutoChestUnlocker.getInstance().tick(client);
			MINER.tick(client);
			BlockMiner.getInstance().tick(client);
			tickAutoClick(client);
			if (openConfigKey.consumeClick()) {
				if (client.gui.screen() == null) {
					releaseAllKeys();
					client.setScreenAndShow(new BlockMinerScreen());
				} else if (client.gui.screen() instanceof BlockMinerScreen) {
					client.gui.screen().onClose();
				}
			}
			if (routeNavigationKey.consumeClick()) {
				if (client.player != null && client.level != null && client.gui.screen() == null) {
					if (RouteNavigator.getInstance().isRunning()) {
						RouteNavigator.getInstance().stop();
					} else {
						RouteNavigator.getInstance().start(RouteHandler.getInstance().getSelectedRoute());
					}
				}
			}
			if (getStatsKey.consumeClick()) {
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
			if (message.getString().contains(PICKOBULUS_MESSAGE)) {
				rightClickWhenEnabled();
			}
		});
		ClientReceiveMessageEvents.CHAT.register((message, playerChatMessage, sender, boundChatType, timeStamp) -> {
			MacroManager.getInstance().onChat(message.getString());
			AutoChestUnlocker.getInstance().onChat(message.getString());
			if (message.getString().contains(PICKOBULUS_MESSAGE)) {
				rightClickWhenEnabled();
			}
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

	public static void setLeftClick(boolean pressed) {
		Minecraft client = Minecraft.getInstance();
		if (client != null && client.options != null && client.options.keyAttack != null) {
			KeyMapping.set(client.options.keyAttack.getDefaultKey(), pressed);
			client.options.keyAttack.setDown(pressed);
		}
	}

	public static void triggerLeftClick() {
		Minecraft client = Minecraft.getInstance();
		if (client != null && client.options != null && client.options.keyAttack != null) {
			KeyMapping.click(client.options.keyAttack.getDefaultKey());
		}
	}

	public static void setRightClick(boolean pressed) {
		Minecraft client = Minecraft.getInstance();
		if (client != null && client.options != null && client.options.keyUse != null) {
			KeyMapping.set(client.options.keyUse.getDefaultKey(), pressed);
			client.options.keyUse.setDown(pressed);
		}
	}

	public static void triggerRightClick() {
		Minecraft client = Minecraft.getInstance();
		if (client != null && client.options != null && client.options.keyUse != null) {
			KeyMapping.click(client.options.keyUse.getDefaultKey());
		}
	}

	public static void releaseAllKeys() {
		setLeftClick(false);
		setRightClick(false);
		KeyMapping.releaseAll();
	}

	public static boolean handleRawKeyPress(KeyEvent event) {
		Minecraft client = Minecraft.getInstance();
		if (client == null) {
			return false;
		}

		boolean matchesConfig = false;
		if (openConfigKey != null && openConfigKey.matches(event)) {
			matchesConfig = true;
		} else if (event.key() == GLFW.GLFW_KEY_RIGHT_SHIFT
				|| (Config.openConfigKey != GLFW.GLFW_KEY_UNKNOWN && event.key() == Config.openConfigKey)) {
			matchesConfig = true;
		}

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

	private static void rightClickWhenEnabled() {
		Minecraft client = Minecraft.getInstance();
		client.execute(() -> {
			if (!MINER.isEnabled() || client.player == null) {
				return;
			}
			triggerRightClick();
		});
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
