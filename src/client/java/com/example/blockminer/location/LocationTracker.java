package com.example.blockminer.location;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

public final class LocationTracker {
	private static final GameStateHandler GAME_STATE = new GameStateHandler();
	private static boolean hadWorld;

	private LocationTracker() {
	}

	public static void register() {
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			ServerData serverData = client.getCurrentServer();
			if (serverData != null && serverData.ip != null) {
				GAME_STATE.setServerIp(serverData.ip);
			}
			refresh(client);
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			hadWorld = false;
			GAME_STATE.reset();
		});
		ClientTickEvents.END_CLIENT_TICK.register(LocationTracker::refresh);
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> GAME_STATE.onMessage(message.getString()));
		ClientReceiveMessageEvents.CHAT.register(
				(message, playerChatMessage, sender, boundChatType, timeStamp) -> GAME_STATE.onMessage(message.getString())
		);
	}

	public static GameStateHandler getGameState() {
		return GAME_STATE;
	}

	private static void refresh(Minecraft client) {
		if (client.player == null || client.level == null || client.getConnection() == null) {
			if (hadWorld) {
				hadWorld = false;
				GAME_STATE.reset();
			}
			return;
		}

		hadWorld = true;
		GAME_STATE.update(
				LocationInputAdapter.readPlayerList(client),
				LocationInputAdapter.readTabFooter(client),
				LocationInputAdapter.readSidebarTitle(client),
				LocationInputAdapter.readSidebarLines(client),
				client.player.getInventory().isEmpty()
		);
	}
}
