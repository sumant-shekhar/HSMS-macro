package com.example.blockminer;

import com.example.blockminer.location.LocationDebugCommand;
import com.example.blockminer.location.LocationDumpCommand;
import com.example.blockminer.location.LocationTracker;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.platform.InputConstants;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.lwjgl.glfw.GLFW;

import java.util.Objects;

public final class BlockMinerClient implements ClientModInitializer {
	private static final String MOD_ID = "blockminer";
	private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	private static final String PICKOBULUS_MESSAGE = "Pickobulus is now available!";
	private static final MinerState MINER = new MinerState();
	private static final int AUTO_CLICK_INTERVAL_TICKS = 100;
	private static KeyMapping openSettingsKey;
	private static boolean openSettingsPending;
	private static boolean openModeTabPending;
	private static boolean autoClickEnabled;
	private static int autoClickTicks;

	@Override
	public void onInitializeClient() {
		LOGGER.info("[blockminer] initialized");
		openSettingsKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.blockminer.open_settings",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_K,
				KeyMapping.Category.MISC
		));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			// TODO(verify): END_CLIENT_TICK has no partial-tick value; confirm tick stepping is sufficient for camera easing.
			RotationHandler.getInstance().tick(client);
			KeyBindUtil.tick();
			MINER.tick(client);
			BlockMiner.getInstance().tick(client);
			tickAutoClick(client);
			if (openSettingsKey.consumeClick()) {
				openSettingsPending = false;
				openModeTabPending = false;
				client.setScreenAndShow(new MinerSettingsScreen(MINER));
			}
			if (openSettingsPending) {
				openSettingsPending = false;
				MinerSettingsScreen settingsScreen = new MinerSettingsScreen(MINER, openModeTabPending);
				client.setScreenAndShow(settingsScreen);
				LOGGER.info("[blockminer] settings screen active: {}", client.gui.screen() == settingsScreen);
				openModeTabPending = false;
			}
		});
		RotationDebugCommands.register();
		BlockSelectionDebugCommand.register();
		LocationTracker.register();
		LocationDebugCommand.register();
		LocationDumpCommand.register();

		ClientSendMessageEvents.ALLOW_CHAT.register(message -> {
			String command = message.trim();
			if (isBlockMinerChatCommand(command)) {
				LOGGER.info("[blockminer] received chat command: {}", command);
			}
			if ("!ore".equalsIgnoreCase(command)) {
				openModeTabPending = true;
				openSettingsPending = true;
				return false;
			}
			if ("!mithril settings".equalsIgnoreCase(command)
					|| "$mithril settings".equalsIgnoreCase(command)
					|| "$mithril ore".equalsIgnoreCase(command)) {
				openModeTabPending = false;
				openSettingsPending = true;
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
			if (message.getString().contains(PICKOBULUS_MESSAGE)) {
				rightClickWhenEnabled();
			}
		});
		ClientReceiveMessageEvents.CHAT.register((message, playerChatMessage, sender, boundChatType, timeStamp) -> {
			if (message.getString().contains(PICKOBULUS_MESSAGE)) {
				rightClickWhenEnabled();
			}
		});

		HudElementRegistry.attachElementBefore(
				VanillaHudElements.CHAT,
				Identifier.fromNamespaceAndPath(MOD_ID, "status"),
				BlockMinerClient::renderStatus
		);
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

	static boolean isAutoClickEnabled() {
		return autoClickEnabled;
	}

	static void toggleAutoClick(Minecraft client) {
		autoClickEnabled = !autoClickEnabled;
		autoClickTicks = 0;
		if (client.player != null) {
			client.player.sendOverlayMessage(Component.literal(
					"Auto click: " + (autoClickEnabled ? "ON (every 5s)" : "OFF")
			));
		}
	}

	private static void tickAutoClick(Minecraft client) {
		if (!autoClickEnabled || client.player == null || client.gameMode == null || client.gui.screen() != null) {
			return;
		}
		LocalPlayer player = Objects.requireNonNull(client.player);
		MultiPlayerGameMode gameMode = Objects.requireNonNull(client.gameMode);

		if (++autoClickTicks >= AUTO_CLICK_INTERVAL_TICKS) {
			autoClickTicks = 0;
			gameMode.useItem(player, InteractionHand.MAIN_HAND);
		}
	}

	private static void rightClickWhenEnabled() {
		Minecraft client = Minecraft.getInstance();
		client.execute(() -> {
			LocalPlayer player = client.player;
			MultiPlayerGameMode gameMode = client.gameMode;
			if (!MINER.isEnabled() || player == null || gameMode == null) {
				return;
			}

			if (client.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
				gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
			} else {
				gameMode.useItem(player, InteractionHand.MAIN_HAND);
			}
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
		if (!MINER.isEnabled() || client.gui.screen() != null) {
			return;
		}

		@NonNull String status = Objects.requireNonNull(MINER.getStatus());
		graphics.fill(6, 6, client.font.width(status) + 18, 24, 0xAA101820);
		graphics.text(client.font, status, 12, 11, 0xFF8BE28B, true);
	}
}
