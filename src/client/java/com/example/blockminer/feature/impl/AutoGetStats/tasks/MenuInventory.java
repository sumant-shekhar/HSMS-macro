package com.example.blockminer.feature.impl.AutoGetStats.tasks;

import com.example.blockminer.location.LocationTextParser;
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
import java.util.Locale;
import java.util.Optional;

public final class MenuInventory {
	private MenuInventory() {
	}

	public static String title(Minecraft client) {
		Screen screen = client.gui.screen();
		return screen == null ? "" : LocationTextParser.stripFormatting(screen.getTitle().getString()).trim();
	}

	public static Optional<ItemStack> findItem(Minecraft client, String name) {
		AbstractContainerMenu menu = getMenu(client);
		if (menu == null || name == null || name.isBlank()) {
			return Optional.empty();
		}
		for (Slot slot : menu.slots) {
			ItemStack item = slot.getItem();
			if (!item.isEmpty()) {
				String itemName = LocationTextParser.stripFormatting(item.getHoverName().getString()).trim();
				if (itemName.equalsIgnoreCase(name)) {
					return Optional.of(item);
				}
			}
		}
		for (Slot slot : menu.slots) {
			ItemStack item = slot.getItem();
			if (!item.isEmpty()) {
				String itemName = LocationTextParser.stripFormatting(item.getHoverName().getString()).trim();
				if (itemName.toLowerCase(Locale.ROOT).contains(name.toLowerCase(Locale.ROOT))) {
					return Optional.of(item);
				}
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
			lines.add(LocationTextParser.stripFormatting(line.getString()));
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
