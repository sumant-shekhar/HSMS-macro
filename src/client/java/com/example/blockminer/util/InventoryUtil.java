package com.example.blockminer.util;

import com.example.blockminer.mixin.accessor.KeyMappingAccessor;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class InventoryUtil {
	private static final int HOTBAR_SIZE = 9;
	private static final int PLAYER_INVENTORY_SIZE = 36;
	private static final Pattern FORMATTING_CODE = Pattern.compile("§[0-9a-fk-or]");

	private InventoryUtil() {
	}

	public static boolean holdItem(String itemName) {
		return holdHotbarItem(Minecraft.getInstance(), itemName);
	}

	public static boolean holdHotbarItem(Minecraft client, String exactName) {
		int slot = findHotbarItem(client, exactName);
		if (slot < 0 || client.player == null) {
			return false;
		}
		client.player.getInventory().setSelectedSlot(slot);
		return true;
	}

	public static int findHotbarItem(Minecraft client, String exactName) {
		if (client.player == null || exactName == null || exactName.isBlank()) {
			return -1;
		}
		Inventory inventory = client.player.getInventory();
		for (int slot = 0; slot < HOTBAR_SIZE; slot++) {
			if (matches(inventory.getItem(slot), exactName, true)) {
				return slot;
			}
		}
		return -1;
	}

	public static int getHotbarSlotOfItem(String itemName) {
		if (itemName == null || itemName.isBlank()) {
			return -1;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return -1;
		}
		Inventory inventory = client.player.getInventory();
		for (int slot = 0; slot < HOTBAR_SIZE; slot++) {
			if (matches(inventory.getItem(slot), itemName, false)) {
				return slot;
			}
		}
		return -1;
	}

	public static int getSlotIdOfItemInContainer(String itemName) {
		return getSlotIdOfItemInContainer(itemName, false);
	}

	public static int getSlotIdOfItemInContainer(String itemName, boolean exact) {
		Slot slot = getSlotOfItemInContainer(itemName, exact);
		AbstractContainerMenu menu = currentMenu(Minecraft.getInstance());
		return slot == null || menu == null ? -1 : menu.slots.indexOf(slot);
	}

	public static Slot getSlotOfItemInContainer(String itemName) {
		return getSlotOfItemInContainer(itemName, false);
	}

	public static Slot getSlotOfItemInContainer(String itemName, boolean exact) {
		AbstractContainerMenu menu = currentMenu(Minecraft.getInstance());
		if (menu == null || itemName == null || itemName.isBlank()) {
			return null;
		}
		for (Slot slot : menu.slots) {
			if (matches(slot.getItem(), itemName, exact)) {
				return slot;
			}
		}
		return null;
	}

	public static int getFirstEmptySlotId() {
		Slot slot = getFirstEmptySlot();
		AbstractContainerMenu menu = currentMenu(Minecraft.getInstance());
		return slot == null || menu == null ? -1 : menu.slots.indexOf(slot);
	}

	public static Slot getFirstEmptySlot() {
		AbstractContainerMenu menu = currentMenu(Minecraft.getInstance());
		if (menu == null) {
			return null;
		}
		for (Slot slot : menu.slots) {
			if (!slot.hasItem()) {
				return slot;
			}
		}
		return null;
	}

	public static List<String> getMissingItemsInInventory(Collection<String> requiredItems) {
		List<String> missing = new ArrayList<>(requiredItems);
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return missing;
		}
		Inventory inventory = client.player.getInventory();
		for (int slot = 0; slot < PLAYER_INVENTORY_SIZE; slot++) {
			String name = cleanName(inventory.getItem(slot));
			if (!name.isEmpty()) {
				missing.removeIf(name::contains);
			}
		}
		return missing;
	}

	public static boolean areItemsInInventory(Collection<String> itemNames) {
		return getMissingItemsInInventory(itemNames).isEmpty();
	}

	public static List<String> getMissingItemsInHotbar(Collection<String> requiredItems) {
		List<String> missing = new ArrayList<>(requiredItems);
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return missing;
		}
		Inventory inventory = client.player.getInventory();
		for (int slot = 0; slot < HOTBAR_SIZE; slot++) {
			String name = cleanName(inventory.getItem(slot));
			if (!name.isEmpty()) {
				missing.removeIf(name::contains);
			}
		}
		return missing;
	}

	public static boolean areItemsInHotbar(Collection<String> itemNames) {
		return getMissingItemsInHotbar(itemNames).isEmpty();
	}

	public static AvailableHotbarSlots getAvailableHotbarSlots(Collection<String> requiredItems) {
		List<String> missingItems = getMissingItemsInHotbar(requiredItems);
		List<Integer> availableSlots = new ArrayList<>();
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return new AvailableHotbarSlots(availableSlots, missingItems);
		}

		Inventory inventory = client.player.getInventory();
		for (int slot = 0; slot < HOTBAR_SIZE; slot++) {
			ItemStack stack = inventory.getItem(slot);
			String name = cleanName(stack);
			boolean holdsRequiredItem = requiredItems.stream()
					.anyMatch(required -> name.toLowerCase(Locale.ROOT).contains(required.toLowerCase(Locale.ROOT)));
			if (stack.isEmpty() || !holdsRequiredItem) {
				availableSlots.add(slot);
			}
		}
		return new AvailableHotbarSlots(availableSlots, missingItems);
	}

	public static String getInventoryName(AbstractContainerMenu menu) {
		Minecraft client = Minecraft.getInstance();
		Screen screen = client.gui.screen();
		if (screen instanceof AbstractContainerScreen<?> containerScreen
				&& containerScreen.getMenu() == menu) {
			return screen.getTitle().getString();
		}
		return "";
	}

	public static String getInventoryName() {
		return getInventoryName(currentMenu(Minecraft.getInstance()));
	}

	public static boolean clickContainerSlot(int slot, ContainerInput input, int button) {
		Minecraft client = Minecraft.getInstance();
		AbstractContainerMenu menu = currentMenu(client);
		if (client.player == null || client.gameMode == null || menu == null
				|| input == null || slot < 0 || slot >= menu.slots.size()) {
			return false;
		}
		client.gameMode.handleContainerInput(menu.containerId, slot, button, input, client.player);
		return true;
	}

	public static boolean swapSlots(int slot, int hotbarSlot) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.gameMode == null
				|| slot < 0 || slot >= client.player.inventoryMenu.slots.size()
				|| hotbarSlot < 0 || hotbarSlot >= HOTBAR_SIZE) {
			return false;
		}
		client.gameMode.handleContainerInput(
				client.player.inventoryMenu.containerId,
				slot,
				hotbarSlot,
				ContainerInput.SWAP,
				client.player
		);
		return true;
	}

	public static void openInventory() {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.gui.screen() != null) {
			return;
		}
		KeyMapping.click(((KeyMappingAccessor) client.options.keyInventory).blockminer$getKey());
	}

	public static void closeScreen() {
		Minecraft client = Minecraft.getInstance();
		if (client.player != null && client.gui.screen() != null) {
			client.setScreenAndShow(null);
		}
	}

	public static List<String> getItemLoreFromOpenContainer(String itemName) {
		AbstractContainerMenu menu = currentMenu(Minecraft.getInstance());
		if (menu == null) {
			return List.of();
		}
		for (Slot slot : menu.slots) {
			if (matches(slot.getItem(), itemName, false)) {
				return getItemLore(slot.getItem());
			}
		}
		return List.of();
	}

	public static List<String> getItemLoreFromInventory(String itemName) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || itemName == null || itemName.isBlank()) {
			return List.of();
		}
		Inventory inventory = client.player.getInventory();
		for (int slot = 0; slot < PLAYER_INVENTORY_SIZE; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (matches(stack, itemName, false)) {
				return getItemLore(stack);
			}
		}
		return List.of();
	}

	public static List<String> getItemLore(ItemStack itemStack) {
		if (itemStack == null || itemStack.isEmpty()) {
			return List.of();
		}
		ItemLore lore = itemStack.get(DataComponents.LORE);
		if (lore == null) {
			return List.of();
		}
		return lore.lines().stream()
				.map(Component::getString)
				.map(InventoryUtil::stripFormatting)
				.toList();
	}

	public static List<String> getLoreOfItemInContainer(int slot) {
		AbstractContainerMenu menu = currentMenu(Minecraft.getInstance());
		if (menu == null || slot < 0 || slot >= menu.slots.size()) {
			return List.of();
		}
		return getItemLore(menu.slots.get(slot).getItem());
	}

	public static int getAmountOfItemInInventory(String itemName) {
		if (itemName == null || itemName.isBlank()) {
			return 0;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return 0;
		}
		int count = 0;
		Inventory inventory = client.player.getInventory();
		for (int slot = 0; slot < PLAYER_INVENTORY_SIZE; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (!stack.isEmpty() && stripFormatting(stack.getHoverName().getString()).equals(itemName)) {
				count += stack.getCount();
			}
		}
		return count;
	}

	public static String getItemId(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return "";
		}
		// TODO(verify): Hypixel ExtraAttributes IDs need confirmation against live 26.2 item components.
		return stripFormatting(stack.getHoverName().getString());
	}

	public static boolean isInventoryLoaded() {
		AbstractContainerMenu menu = currentMenu(Minecraft.getInstance());
		int containerSlots = menu == null ? 0 : menu.slots.size() - PLAYER_INVENTORY_SIZE;
		return containerSlots > 0 && menu.slots.get(containerSlots - 1).hasItem();
	}

	public static boolean isInventoryEmpty() {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return true;
		}
		Inventory inventory = client.player.getInventory();
		for (int slot = 0; slot < PLAYER_INVENTORY_SIZE; slot++) {
			if (!inventory.getItem(slot).isEmpty()) {
				return false;
			}
		}
		return true;
	}

	public static String getFullName(String itemName) {
		Slot slot = getSlotOfItemInContainer(itemName);
		return slot == null ? "" : stripFormatting(slot.getItem().getHoverName().getString());
	}

	public static int getDrillFuelCapacity(String drillName) {
		return getDrillFuelValue(drillName, true);
	}

	public static int getDrillRemainingFuel(String drillName) {
		return getDrillFuelValue(drillName, false);
	}

	private static int getDrillFuelValue(String drillName, boolean capacity) {
		for (String lore : getItemLoreFromInventory(drillName)) {
			if (!lore.startsWith("Fuel: ")) {
				continue;
			}
			String[] values = lore.substring("Fuel: ".length()).split("/", 2);
			if (values.length != 2) {
				break;
			}
			try {
				return parseFuelValue(values[capacity ? 1 : 0]);
			} catch (NumberFormatException | ArithmeticException exception) {
				Logger.sendWarning("Could not parse drill fuel lore: " + lore);
				return -1;
			}
		}
		return -1;
	}

	private static int parseFuelValue(String value) {
		String normalized = value.strip().replace(",", "").toLowerCase(Locale.ROOT);
		BigDecimal multiplier = BigDecimal.ONE;
		if (normalized.endsWith("k")) {
			multiplier = BigDecimal.valueOf(1_000L);
			normalized = normalized.substring(0, normalized.length() - 1);
		}
		return new BigDecimal(normalized).multiply(multiplier).intValueExact();
	}

	private static AbstractContainerMenu currentMenu(Minecraft client) {
		Screen screen = client.gui.screen();
		return screen instanceof AbstractContainerScreen<?> containerScreen
				? containerScreen.getMenu()
				: null;
	}

	private static boolean matches(ItemStack stack, String itemName, boolean exact) {
		if (stack.isEmpty() || itemName == null || itemName.isBlank()) {
			return false;
		}
		String name = stripFormatting(stack.getHoverName().getString());
		return exact ? name.equalsIgnoreCase(itemName) : name.toLowerCase(Locale.ROOT)
				.contains(itemName.toLowerCase(Locale.ROOT));
	}

	private static String cleanName(ItemStack stack) {
		return stack.isEmpty() ? "" : stripFormatting(stack.getHoverName().getString());
	}

	private static String stripFormatting(String text) {
		return FORMATTING_CODE.matcher(text).replaceAll("");
	}

	public record AvailableHotbarSlots(List<Integer> availableSlots, List<String> missingItems) {
		public AvailableHotbarSlots {
			availableSlots = List.copyOf(availableSlots);
			missingItems = List.copyOf(missingItems);
		}
	}
}
