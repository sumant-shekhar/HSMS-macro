package com.example.blockminer.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

public final class FeatureInventory {
	private static final int PLAYER_MENU_SLOTS = 36;

	private FeatureInventory() {
	}

	public static int findHotbarItem(Minecraft client, String exactName) {
		return InventoryUtil.findHotbarItem(client, exactName);
	}

	public static int findInventoryItem(Minecraft client, String exactName) {
		if (client.player == null || exactName == null || exactName.isBlank()) {
			return -1;
		}
		for (int slot = 0; slot < 36; slot++) {
			if (matches(client.player.getInventory().getItem(slot), exactName)) {
				return slot;
			}
		}
		return -1;
	}

	public static boolean holdHotbarItem(Minecraft client, String exactName) {
		return InventoryUtil.holdHotbarItem(client, exactName);
	}

	public static boolean holdWeapon(Minecraft client) {
		return InventoryUtil.holdWeapon(client);
	}

	public static boolean holdMiningTool(Minecraft client) {
		return InventoryUtil.holdMiningTool(client);
	}

	public static boolean holdTeleportTool(Minecraft client) {
		return InventoryUtil.holdTeleportTool(client);
	}

	public static boolean isWeaponPresent(Minecraft client) {
		return InventoryUtil.isWeaponPresent(client);
	}

	public static boolean isMiningToolPresent(Minecraft client) {
		return InventoryUtil.isMiningToolPresent(client);
	}

	public static boolean isTeleportToolPresent(Minecraft client) {
		return InventoryUtil.isTeleportToolPresent(client);
	}

	public static String title(Minecraft client) {
		Screen screen = client.gui.screen();
		return screen == null ? "" : screen.getTitle().getString();
	}

	public static AbstractContainerMenu menu(Minecraft client) {
		Screen screen = client.gui.screen();
		return screen instanceof AbstractContainerScreen<?> containerScreen
				? containerScreen.getMenu()
				: null;
	}

	public static int findContainerItem(Minecraft client, String exactName) {
		AbstractContainerMenu menu = menu(client);
		if (menu == null || exactName == null || exactName.isBlank()) {
			return -1;
		}
		int containerSlots = Math.max(0, menu.slots.size() - PLAYER_MENU_SLOTS);
		for (int slot = 0; slot < containerSlots; slot++) {
			if (matches(menu.slots.get(slot).getItem(), exactName)) {
				return slot;
			}
		}
		return -1;
	}

	public static boolean clickContainerSlot(Minecraft client, int slot, ContainerInput input, int button) {
		AbstractContainerMenu menu = menu(client);
		if (client.player == null || client.gameMode == null || menu == null
				|| slot < 0 || slot >= menu.slots.size()) {
			return false;
		}
		return InventoryUtil.clickContainerSlot(slot, input, button);
	}

	private static boolean matches(ItemStack item, String exactName) {
		return !item.isEmpty() && item.getHoverName().getString().equalsIgnoreCase(exactName);
	}
}
