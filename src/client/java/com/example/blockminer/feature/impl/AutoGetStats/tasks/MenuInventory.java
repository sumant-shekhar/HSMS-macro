package com.example.blockminer.feature.impl.AutoGetStats.tasks;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class MenuInventory {
	private MenuInventory() {
	}

	public static String title(Minecraft client) {
		Screen screen = client.gui.screen();
		return screen == null ? "" : screen.getTitle().getString();
	}

	public static Optional<ItemStack> findItem(Minecraft client, String exactName) {
		AbstractContainerMenu menu = getMenu(client);
		if (menu == null) {
			return Optional.empty();
		}
		for (Slot slot : menu.slots) {
			ItemStack item = slot.getItem();
			if (!item.isEmpty() && item.getHoverName().getString().equalsIgnoreCase(exactName)) {
				return Optional.of(item);
			}
		}
		return Optional.empty();
	}

	public static List<String> getLore(ItemStack item) {
		ItemLore lore = item.get(DataComponents.LORE);
		if (lore == null) {
			return List.of();
		}
		List<String> lines = new ArrayList<>(lore.lines().size());
		for (Component line : lore.lines()) {
			lines.add(line.getString());
		}
		return List.copyOf(lines);
	}

	private static AbstractContainerMenu getMenu(Minecraft client) {
		Screen screen = client.gui.screen();
		return screen instanceof AbstractContainerScreen<?> containerScreen
				? containerScreen.getMenu()
				: null;
	}
}
