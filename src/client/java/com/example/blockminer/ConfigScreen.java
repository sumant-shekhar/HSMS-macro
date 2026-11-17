package com.example.blockminer;

import com.example.blockminer.util.Logger;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public final class ConfigScreen extends Screen {
	private static final int PANEL_WIDTH = 430;
	private static final int PANEL_HEIGHT = 370;
	private static final String[] MACRO_TYPES = {
			"Dwarven Commission", "Glacial Commission", "Mining Macro", "Route Miner"
	};
	private static final String[] ORE_TYPES = {
			"Mithril", "Diamond", "Emerald", "Redstone", "Lapis", "Gold", "Iron", "Coal"
	};
	private final List<TextSetting> textSettings = new ArrayList<>();
	private Tab activeTab = Tab.GENERAL;
	private int contentY;

	public ConfigScreen() {
		super(Component.literal("Block Miner Configuration"));
	}

	@Override
	protected void init() {
		textSettings.clear();
		int left = panelLeft();
		int top = panelTop();
		int tabGap = 5;
		int tabWidth = (panelWidth() - 24 - tabGap * (Tab.values().length - 1)) / Tab.values().length;
		for (int index = 0; index < Tab.values().length; index++) {
			Tab tab = Tab.values()[index];
			int x = left + 12 + index * (tabWidth + tabGap);
			addRenderableWidget(Button.builder(Component.literal(
							(activeTab == tab ? "> " : "") + tab.label), button -> showTab(tab))
					.bounds(x, top + 42, tabWidth, 22)
					.build());
		}

		contentY = top + 76;
		switch (activeTab) {
			case GENERAL -> addGeneralOptions(left);
			case COMMISSION -> addCommissionOptions(left);
			case MINING -> addMiningOptions(left);
			case TIMING -> addTimingOptions(left);
			case ROUTE_DEBUG -> addRouteDebugOptions(left);
		}
		addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
				.bounds(width / 2 - 42, top + PANEL_HEIGHT - 32, 84, 20)
				.build());
	}

	@Override
	public void extractBackground(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY,
			float partialTick) {
		super.extractBackground(graphics, mouseX, mouseY, partialTick);
		int left = panelLeft();
		int top = panelTop();
		int right = left + panelWidth();
		int bottom = top + PANEL_HEIGHT;
		graphics.fill(left - 1, top - 1, right + 1, bottom + 1, 0xff4dd6b0);
		graphics.fill(left, top, right, bottom, 0xf011171b);
		graphics.fill(left, top, right, top + 34, 0xff1b292b);
		graphics.text(font, "BLOCK MINER CONFIG", left + 14, top + 10, 0xfff1f6f4, false);
		graphics.text(font, "Settings are saved to config/blockminer.json on close",
				left + 14, top + 22, 0xff9bb0ac, false);
		graphics.fill(left + 12, top + 69, right - 12, top + 70, 0xff334447);
		graphics.text(font, activeTab.description, left + 14, top + 72, 0xff9bb0ac, false);
		for (TextSetting setting : textSettings) {
			graphics.text(font, setting.label, setting.box.getX(), setting.box.getY() - 10,
					0xffd5e0dc, false);
		}
	}

	@Override
	public void onClose() {
		applyTextSettings();
		try {
			Config.save();
		} catch (IOException exception) {
			Logger.sendError("Could not save config/blockminer.json: " + exception.getMessage());
		}
		super.onClose();
	}

	private void addGeneralOptions(int left) {
		addCycle(left, "Macro type", MACRO_TYPES, () -> Config.macroType,
				value -> Config.macroType = value);
		addCycle(left, "Mining ore", ORE_TYPES, () -> Config.oreType,
				value -> Config.oreType = value);
		addText(left, "Mining tool", Config.miningTool, value -> Config.miningTool = value);
		addToggle(left, "Use pickaxe ability", () -> Config.usePickaxeAbility,
				value -> Config.usePickaxeAbility = value);
		addToggle(left, "Sneak while mining", () -> Config.sneakWhileMining,
				value -> Config.sneakWhileMining = value);
		addToggle(left, "Randomized rotations", () -> Config.randomizedRotations,
				value -> Config.randomizedRotations = value);
		addToggle(left, "Precision Miner particle aiming", () -> Config.precisionMiner,
				value -> Config.precisionMiner = value);
		addToggle(left, "Full block hitbox", () -> Config.miscFullBlock,
				value -> Config.miscFullBlock = value);
		addSlider(left, "Empty-target wait (ms)", 500, 10_000, Config.waitThresholdMs,
				value -> Config.waitThresholdMs = value);
	}

	private void addCommissionOptions(int left) {
		addCycle(left, "Commission claim method", new String[]{"NPC", "Royal Pigeon"},
				() -> Config.commClaimMethod, value -> Config.commClaimMethod = value);
		addText(left, "Alternate mining tool", Config.altMiningTool, value -> Config.altMiningTool = value);
		addText(left, "Mob-killer weapon", Config.slayerWeapon, value -> Config.slayerWeapon = value);
		addToggle(left, "Swap tool before claiming", () -> Config.commSwapBeforeClaiming,
				value -> Config.commSwapBeforeClaiming = value);
		addToggle(left, "Automatic drill refuel", () -> Config.drillRefuel,
				value -> Config.drillRefuel = value);
		addCycle(left, "Machine fuel", new String[]{"Volta", "Oil Barrel"},
				() -> Config.refuelMachineFuel, value -> Config.refuelMachineFuel = value);
		addSlider(left, "Glacial cold threshold", 1, 100, Config.coldThreshold,
				value -> Config.coldThreshold = value);
		addToggle(left, "Show Dwarven HUD outside mines", () -> Config.showDwarvenCommHUDOutside,
				value -> Config.showDwarvenCommHUDOutside = value);
	}

	private void addMiningOptions(int left) {
		addToggle(left, "Mine Gray Mithril", () -> Config.mineGrayMithril,
				value -> Config.mineGrayMithril = value);
		addToggle(left, "Mine Green Mithril", () -> Config.mineGreenMithril,
				value -> Config.mineGreenMithril = value);
		addToggle(left, "Mine Blue Mithril", () -> Config.mineBlueMithril,
				value -> Config.mineBlueMithril = value);
		addToggle(left, "Mine Titanium", () -> Config.mineTitanium,
				value -> Config.mineTitanium = value);
		addSlider(left, "Mining coefficient", 0, 1_000, (int) Config.miningCoefficient,
				value -> Config.miningCoefficient = value);
		addSlider(left, "Angle coefficient", 0, 10, (int) Config.angleCoefficient,
				value -> Config.angleCoefficient = value);
		addSlider(left, "Distance coefficient", 0, 20, (int) Config.distanceCoefficient,
				value -> Config.distanceCoefficient = value);
		addSlider(left, "Tick glide offset", 0, 10, Config.tickGlideOffset,
				value -> Config.tickGlideOffset = value);
		addSlider(left, "Default mining speed", 1, 2_000, Config.defaultMiningSpeed,
				value -> Config.defaultMiningSpeed = value);
		addToggle(left, "Prioritize Titanium", () -> Config.prioritiseTitanium,
				value -> Config.prioritiseTitanium = value);
	}

	private void addTimingOptions(int left) {
		addSlider(left, "Rotation time (ms)", 50, 1_000, Config.rotationTime,
				Config::setRotationTime);
		addSlider(left, "Rotation randomizer (ms)", 0, 1_000, Config.rotationTimeRandomizer,
				Config::setRotationTimeRandomizer);
		addSlider(left, "Sneak time (ms)", 0, 2_000, Config.sneakTime,
				value -> Config.sneakTime = value);
		addSlider(left, "Sneak randomizer (ms)", 0, 2_000, Config.sneakTimeRandomizer,
				value -> Config.sneakTimeRandomizer = value);
		addSlider(left, "GUI delay (ms)", 50, 2_000, Config.delaysGuiDelay,
				value -> Config.delaysGuiDelay = value);
		addSlider(left, "GUI delay randomizer (ms)", 0, 1_000, Config.delaysGuiDelayRandomizer,
				value -> Config.delaysGuiDelayRandomizer = value);
		addSlider(left, "AOTV look delay (ms)", 50, 1_000, Config.delayAutoAotvLookDelay,
				value -> Config.delayAutoAotvLookDelay = value);
		addSlider(left, "Etherwarp look delay (ms)", 0, 2_000, Config.delayAutoAotvEtherwarpLookDelay,
				value -> Config.delayAutoAotvEtherwarpLookDelay = value);
		addSlider(left, "Server rotation delay (ms)", 0, 2_000, Config.delayAutoAotvServerRotation,
				value -> Config.delayAutoAotvServerRotation = value);
	}

	private void addRouteDebugOptions(int left) {
		addText(left, "Selected route", Config.selectedRoute, value -> Config.selectedRoute = value);
		addToggle(left, "Route: mine gemstones", () -> Config.routeMineGemstone,
				value -> Config.routeMineGemstone = value);
		addToggle(left, "Route: mine Topaz", () -> Config.routeMineTopaz,
				value -> Config.routeMineTopaz = value);
		addToggle(left, "Route: mine ore", () -> Config.routeMineOre,
				value -> Config.routeMineOre = value);
		addToggle(left, "Route: mine Glacite", () -> Config.routeMineGlacite,
				value -> Config.routeMineGlacite = value);
		addToggle(left, "Route: mine Umber", () -> Config.routeMineUmber,
				value -> Config.routeMineUmber = value);
		addToggle(left, "Route: mine Tungsten", () -> Config.routeMineTungsten,
				value -> Config.routeMineTungsten = value);
		addToggle(left, "Debug HUD and diagnostics", () -> Config.debugMode,
				value -> Config.debugMode = value);
		addToggle(left, "Show Glacial HUD outside mines", () -> Config.showGlacialHUDOutside,
				value -> Config.showGlacialHUDOutside = value);
	}

	private void addCycle(int left, String label, String[] choices, IntSupplier getValue, IntConsumer setValue) {
		int rowY = nextRow(26);
		Button button = Button.builder(cycleMessage(label, choices, getValue.getAsInt()), ignored -> {
			int selected = Math.floorMod(getValue.getAsInt() + 1, choices.length);
			setValue.accept(selected);
			ignored.setMessage(cycleMessage(label, choices, selected));
		}).bounds(left + 14, rowY, panelWidth() - 28, 22).build();
		addRenderableWidget(button);
	}

	private void addToggle(int left, String label, BooleanSupplier getValue, Consumer<Boolean> setValue) {
		int rowY = nextRow(26);
		Button button = Button.builder(toggleMessage(label, getValue.getAsBoolean()), ignored -> {
			boolean selected = !getValue.getAsBoolean();
			setValue.accept(selected);
			ignored.setMessage(toggleMessage(label, selected));
		}).bounds(left + 14, rowY, panelWidth() - 28, 22).build();
		addRenderableWidget(button);
	}

	private void addSlider(int left, String label, int min, int max, int current, IntConsumer setValue) {
		int rowY = nextRow(26);
		double normalized = (double) (Math.clamp(current, min, max) - min) / (max - min);
		addRenderableWidget(new AbstractSliderButton(
				left + 14, rowY, panelWidth() - 28, 22, Component.empty(), normalized
		) {
			private int selectedValue() {
				return min + (int) Math.round(value * (max - min));
			}

			@Override
			protected void updateMessage() {
				setMessage(Component.literal(label + ": " + selectedValue()));
			}

			@Override
			protected void applyValue() {
				setValue.accept(selectedValue());
			}
		});
	}

	private void addText(int left, String label, String current, Consumer<String> apply) {
		int rowY = nextRow(39);
		EditBox box = new EditBox(font, left + 14, rowY + 12, panelWidth() - 28, 20,
				Component.literal(label));
		box.setMaxLength(512);
		box.setValue(current);
		textSettings.add(new TextSetting(label, box, apply));
		addRenderableWidget(box);
	}

	private void applyTextSettings() {
		for (TextSetting setting : textSettings) {
			setting.apply.accept(setting.box.getValue().strip());
		}
	}

	private int nextRow(int height) {
		int rowY = contentY;
		contentY += height;
		return rowY;
	}

	private void showTab(Tab tab) {
		if (activeTab == tab) {
			return;
		}
		applyTextSettings();
		activeTab = tab;
		clearWidgets();
		init();
	}

	private int panelWidth() {
		return Math.min(PANEL_WIDTH, Math.max(260, width - 16));
	}

	private int panelLeft() {
		return (width - panelWidth()) / 2;
	}

	private int panelTop() {
		return Math.max(8, (height - PANEL_HEIGHT) / 2);
	}

	private static Component cycleMessage(String label, String[] options, int index) {
		int selected = Math.floorMod(index, options.length);
		return Component.literal(label + ": " + options[selected] + "  ›");
	}

	private static Component toggleMessage(String label, boolean enabled) {
		return Component.literal(label + ": " + (enabled ? "ON" : "OFF"));
	}

	private enum Tab {
		GENERAL("General", "Macro type, mining behavior, and target wait"),
		COMMISSION("Commission", "Claiming, refueling, cold threshold, and HUD"),
		MINING("Mining", "Ore selection and block-priority tuning"),
		TIMING("Timing", "Movement, GUI, and ability delays"),
		ROUTE_DEBUG("Route / Debug", "Route targets, diagnostics, and legacy settings");

		private final String label;
		private final String description;

		Tab(String label, String description) {
			this.label = label;
			this.description = description;
		}
	}

	private record TextSetting(String label, EditBox box, Consumer<String> apply) {
	}
}
