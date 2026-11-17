package com.example.blockminer.util;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public final class Logger {
	private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger("blockminer");
	private static final Map<String, String> LAST_MESSAGES = new HashMap<>();

	private Logger() {
	}

	public static void addMessage(String text) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.level == null) {
			LOGGER.info("{}", text);
		} else {
			client.player.sendSystemMessage(Component.literal(text));
		}
	}

	public static void sendMessage(String message) {
		addMessage("[Block Miner] " + message);
	}

	public static void sendWarning(String message) {
		addMessage("[Block Miner: warning] " + message);
	}

	public static void sendError(String message) {
		addMessage("[Block Miner: error] " + message);
	}

	public static void sendNote(String message) {
		sendMessage(message);
	}

	public static void sendLog(String message) {
		if (isDuplicate("debug", message)) {
			return;
		}
		LOGGER.info("{}", message);
	}

	private static boolean isDuplicate(String type, String message) {
		if (message.equals(LAST_MESSAGES.get(type))) {
			return true;
		}
		LAST_MESSAGES.put(type, message);
		return false;
	}
}
