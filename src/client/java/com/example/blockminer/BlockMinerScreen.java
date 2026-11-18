package com.example.blockminer;

import com.example.blockminer.MiningTargets.Mode;
import com.example.blockminer.feature.impl.RouteNavigator;
import com.example.blockminer.handler.RouteHandler;
import com.example.blockminer.location.LocationTracker;
import com.example.blockminer.macro.AbstractMacro;
import com.example.blockminer.macro.MacroManager;
import com.example.blockminer.util.Logger;
import com.example.blockminer.util.helper.route.Route;
import com.example.blockminer.util.helper.route.RouteWaypoint;
import com.example.blockminer.util.helper.route.WaypointType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public class BlockMinerScreen extends Screen {
	private static final int PANEL_WIDTH = 430;
	private static final int PANEL_HEIGHT = 280;
	private static final String[] MACRO_TYPES = {
			"Dwarven Commission", "Glacial Commission", "Mining Macro", "Route Miner"
	};
	private static final String[] CLAIM_METHODS = {
			"Emissary", "Abiphone", "Menu"
	};
	private static final String[] REFUEL_METHODS = {
			"Abiphone", "Greatforge"
	};

	public enum Tab {
		DASHBOARD("Dashboard", "Primary actions, macro controls, and status"),
		MINING("Mining", "Target ores, mining speed, and angle settings"),
		MACROS("Macros", "Commission, refuel, and mob killer automations"),
		ROUTES("Routes", "Waypoint management and pathing navigation"),
		SETTINGS("Settings", "HUD overlays, clicker, and configuration");

		final String label;
		final String description;

		Tab(String label, String description) {
			this.label = label;
			this.description = description;
		}
	}

	private Tab activeTab = Tab.DASHBOARD;
	private String statusMessage = "";
	private long statusMessageExpireTick = 0;

	public BlockMinerScreen() {
		this(Tab.DASHBOARD);
	}

	public BlockMinerScreen(Tab tab) {
		super(Component.literal("Mighty Miner Dashboard"));
		this.activeTab = tab != null ? tab : Tab.DASHBOARD;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private int panelLeft() {
		return Math.max(8, (width - PANEL_WIDTH) / 2);
	}

	private int panelTop() {
		return Math.max(8, (height - PANEL_HEIGHT) / 2);
	}

	@Override
	protected void init() {
		clearWidgets();
		int left = panelLeft();
		int top = panelTop();

		// Top Action Buttons (always visible on all tabs)
		buildTopActionButtons(left, top);

		// Navigation Tabs
		buildTabButtons(left, top);

		// Content based on active tab
		int contentY = top + 86;
		int leftCol = left + 14;
		int rightCol = left + 220;
		int colWidth = 196;

		switch (activeTab) {
			case DASHBOARD -> buildDashboardTab(leftCol, rightCol, contentY, colWidth);
			case MINING -> buildMiningTab(leftCol, rightCol, contentY, colWidth);
			case MACROS -> buildMacrosTab(leftCol, rightCol, contentY, colWidth);
			case ROUTES -> buildRoutesTab(leftCol, rightCol, contentY, colWidth);
			case SETTINGS -> buildSettingsTab(leftCol, rightCol, contentY, colWidth);
		}

		// Done / Close button
		addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
				.bounds(width / 2 - 45, top + PANEL_HEIGHT - 26, 90, 20)
				.build());
	}

	private void buildTopActionButtons(int left, int top) {
		int actionY = top + 34;
		int btnWidth = 96;
		int btnHeight = 22;
		int gap = 6;

		// 1. Start/Stop Mining
		MinerState miner = BlockMinerClient.getMiner();
		boolean minerActive = miner.isEnabled();
		String minerLabel = minerActive ? "■ STOP MINING" : "▶ START MINING";
		addRenderableWidget(Button.builder(Component.literal(minerLabel), button -> {
			Minecraft mc = Minecraft.getInstance();
			if (miner.isEnabled()) {
				miner.stop(mc);
				setStatus("Mining stopped.");
			} else {
				miner.start(mc);
				setStatus("Mining started (" + miner.getSelectedMode().displayName() + ").");
			}
			rebuildWidgets();
		}).bounds(left + 12, actionY, btnWidth, btnHeight).build());

		// 2. Start/Stop Macro
		MacroManager macroManager = MacroManager.getInstance();
		boolean macroActive = macroManager.isEnabled();
		String macroLabel = macroActive ? "■ STOP MACRO" : "▶ START MACRO";
		addRenderableWidget(Button.builder(Component.literal(macroLabel), button -> {
			try {
				macroManager.toggle();
				if (macroManager.isEnabled()) {
					AbstractMacro cur = macroManager.getCurrentMacro();
					setStatus("Macro started: " + (cur != null ? cur.getName() : "Active"));
				} else {
					setStatus("Macro stopped.");
				}
			} catch (Exception ex) {
				setStatus("Macro error: " + ex.getMessage());
			}
			rebuildWidgets();
		}).bounds(left + 12 + (btnWidth + gap), actionY, btnWidth, btnHeight).build());

		// 3. Start/Stop Route
		RouteNavigator nav = RouteNavigator.getInstance();
		boolean routeActive = nav.isRunning();
		String routeLabel = routeActive ? "■ STOP ROUTE" : "▶ START ROUTE";
		addRenderableWidget(Button.builder(Component.literal(routeLabel), button -> {
			if (nav.isRunning()) {
				nav.stop();
				setStatus("Route navigation stopped.");
			} else {
				Route route = RouteHandler.getInstance().getSelectedRoute();
				if (route != null && !route.getWaypoints().isEmpty()) {
					nav.start(route);
					setStatus("Route started: " + RouteHandler.getInstance().getSelectedRouteName());
				} else {
					setStatus("Route is empty; add waypoints first.");
				}
			}
			rebuildWidgets();
		}).bounds(left + 12 + (btnWidth + gap) * 2, actionY, btnWidth, btnHeight).build());

		// 4. Stop All
		addRenderableWidget(Button.builder(Component.literal("✖ STOP ALL"), button -> {
			BlockMinerClient.stopAll(Minecraft.getInstance());
			setStatus("All mining, macros, and navigation halted.");
			rebuildWidgets();
		}).bounds(left + 12 + (btnWidth + gap) * 3, actionY, btnWidth, btnHeight).build());
	}

	private void buildTabButtons(int left, int top) {
		int tabY = top + 60;
		Tab[] tabs = Tab.values();
		int tabGap = 4;
		int tabWidth = (PANEL_WIDTH - 24 - tabGap * (tabs.length - 1)) / tabs.length;

		for (int i = 0; i < tabs.length; i++) {
			Tab tab = tabs[i];
			int tabX = left + 12 + i * (tabWidth + tabGap);
			String label = (activeTab == tab ? "> " : "") + tab.label;
			addRenderableWidget(Button.builder(Component.literal(label), button -> {
				activeTab = tab;
				rebuildWidgets();
			}).bounds(tabX, tabY, tabWidth, 20).build());
		}
	}

	// ----------------------------------------------------
	// TAB 1: DASHBOARD
	// ----------------------------------------------------
	private void buildDashboardTab(int leftCol, int rightCol, int y, int width) {
		MinerState miner = BlockMinerClient.getMiner();

		// Left Column: Core Selection Controls
		addRenderableWidget(createCycleButton(leftCol, y, width, 20, "Macro", MACRO_TYPES,
				() -> Config.macroType, value -> {
					if (!MacroManager.getInstance().isEnabled()) {
						Config.macroType = value;
					} else {
						setStatus("Stop active macro before changing type.");
					}
				}));

		addRenderableWidget(Button.builder(Component.literal("Target Ore: " + miner.getSelectedMode().displayName()), button -> {
			Mode[] modes = Mode.values();
			int nextIndex = (miner.getSelectedMode().ordinal() + 1) % modes.length;
			miner.selectMode(Minecraft.getInstance(), modes[nextIndex]);
			button.setMessage(Component.literal("Target Ore: " + modes[nextIndex].displayName()));
		}).bounds(leftCol, y + 24, width, 20).build());

		addRenderableWidget(Button.builder(Component.literal("Route: " + RouteHandler.getInstance().getSelectedRouteName()), button -> {
			List<String> names = new ArrayList<>(RouteHandler.getInstance().getRoutes().keySet());
			if (!names.isEmpty()) {
				int cur = names.indexOf(RouteHandler.getInstance().getSelectedRouteName());
				int next = (cur + 1) % names.size();
				RouteHandler.getInstance().selectRoute(names.get(next));
				button.setMessage(Component.literal("Route: " + names.get(next)));
			}
		}).bounds(leftCol, y + 48, width, 20).build());

		addRenderableWidget(createToggleButton(leftCol, y + 72, width / 2 - 2, 20, "Refuel",
				() -> Config.drillRefuel, v -> Config.drillRefuel = v));

		addRenderableWidget(createToggleButton(leftCol + width / 2 + 2, y + 72, width / 2 - 2, 20, "MobKill",
				() -> !Config.slayerWeapon.isEmpty(), v -> Config.slayerWeapon = v ? "Slayer" : ""));

		addRenderableWidget(createToggleButton(leftCol, y + 96, width / 2 - 2, 20, "AutoClaim",
				() -> Config.commClaimMethod >= 0, v -> Config.commClaimMethod = v ? 0 : -1));

		addRenderableWidget(createToggleButton(leftCol + width / 2 + 2, y + 96, width / 2 - 2, 20, "AutoWarp",
				() -> Config.forgePathing, v -> Config.forgePathing = v));

		addRenderableWidget(Button.builder(Component.literal("Save Configuration"), button -> {
			saveConfigSafely();
			setStatus("Config saved successfully.");
		}).bounds(leftCol, y + 120, width, 20).build());

		// Right Column: Live Metrics Card is drawn via extractBackground
	}

	// ----------------------------------------------------
	// TAB 2: MINING
	// ----------------------------------------------------
	private void buildMiningTab(int leftCol, int rightCol, int y, int width) {
		// Left column: Target ores
		addRenderableWidget(createToggleButton(leftCol, y, width, 20, "Mine Gray Mithril",
				() -> Config.mineGrayMithril, v -> Config.mineGrayMithril = v));
		addRenderableWidget(createToggleButton(leftCol, y + 24, width, 20, "Mine Green Mithril",
				() -> Config.mineGreenMithril, v -> Config.mineGreenMithril = v));
		addRenderableWidget(createToggleButton(leftCol, y + 48, width, 20, "Mine Blue Mithril",
				() -> Config.mineBlueMithril, v -> Config.mineBlueMithril = v));
		addRenderableWidget(createToggleButton(leftCol, y + 72, width, 20, "Mine Titanium",
				() -> Config.mineTitanium, v -> Config.mineTitanium = v));
		addRenderableWidget(createToggleButton(leftCol, y + 96, width, 20, "Prioritise Titanium",
				() -> Config.prioritiseTitanium, v -> Config.prioritiseTitanium = v));
		addRenderableWidget(createToggleButton(leftCol, y + 120, width, 20, "Sneak While Mining",
				() -> Config.sneakWhileMining, v -> Config.sneakWhileMining = v));

		// Right column: Sliders & Mechanics
		addRenderableWidget(new SimpleSlider(rightCol, y, width, 20, "Mining Speed",
				100, 3000, Config.defaultMiningSpeed, v -> Config.defaultMiningSpeed = v.intValue()));

		addRenderableWidget(new SimpleSlider(rightCol, y + 24, width, 20, "Wait Timeout (ms)",
				500, 5000, Config.waitThresholdMs, v -> Config.waitThresholdMs = v.intValue()));

		addRenderableWidget(createToggleButton(rightCol, y + 48, width, 20, "Auto Pickaxe Ability",
				() -> Config.usePickaxeAbility, v -> Config.usePickaxeAbility = v));

		addRenderableWidget(createToggleButton(rightCol, y + 72, width, 20, "Random Rotations",
				() -> Config.randomizedRotations, v -> Config.randomizedRotations = v));

		addRenderableWidget(createToggleButton(rightCol, y + 96, width, 20, "Mine Gemstones",
				() -> Config.routeMineGemstone, v -> Config.routeMineGemstone = v));

		addRenderableWidget(createToggleButton(rightCol, y + 120, width, 20, "Mine Glacite",
				() -> Config.routeMineGlacite, v -> Config.routeMineGlacite = v));
	}

	// ----------------------------------------------------
	// TAB 3: MACROS
	// ----------------------------------------------------
	private void buildMacrosTab(int leftCol, int rightCol, int y, int width) {
		// Left: Commission & Refuel
		addRenderableWidget(createCycleButton(leftCol, y, width, 20, "Claim Method", CLAIM_METHODS,
				() -> Math.max(0, Config.commClaimMethod), v -> Config.commClaimMethod = v));

		addRenderableWidget(createToggleButton(leftCol, y + 24, width, 20, "Swap Tool On Claim",
				() -> Config.commSwapBeforeClaiming, v -> Config.commSwapBeforeClaiming = v));

		addRenderableWidget(createToggleButton(leftCol, y + 48, width, 20, "Auto Refuel Drill",
				() -> Config.drillRefuel, v -> Config.drillRefuel = v));

		addRenderableWidget(createCycleButton(leftCol, y + 72, width, 20, "Refuel Mode", REFUEL_METHODS,
				() -> Math.max(0, Config.refuelMachineFuel - 1), v -> Config.refuelMachineFuel = v + 1));

		addRenderableWidget(new SimpleSlider(leftCol, y + 96, width, 20, "Cold Limit",
				10, 100, Config.coldThreshold, v -> Config.coldThreshold = v.intValue()));

		addRenderableWidget(Button.builder(Component.literal("Reset Commission Stats"), button -> {
			Config.commHudResetStats = true;
			setStatus("Commission stats reset.");
		}).bounds(leftCol, y + 120, width, 20).build());

		// Right: Mob Killer & Glacial
		addRenderableWidget(createToggleButton(rightCol, y, width, 20, "Mob Killer Sprint",
				() -> Config.mobKillerSprint, v -> Config.mobKillerSprint = v));

		addRenderableWidget(createToggleButton(rightCol, y + 24, width, 20, "Mob Killer Smooth Look",
				() -> Config.mobKillerInterpolate, v -> Config.mobKillerInterpolate = v));

		addRenderableWidget(createToggleButton(rightCol, y + 48, width, 20, "Forge Pathing",
				() -> Config.forgePathing, v -> Config.forgePathing = v));

		addRenderableWidget(createToggleButton(rightCol, y + 72, width, 20, "Show Commission HUD",
				() -> Config.showDwarvenCommHUDOutside, v -> Config.showDwarvenCommHUDOutside = v));

		addRenderableWidget(createToggleButton(rightCol, y + 96, width, 20, "Show Glacial HUD",
				() -> Config.showGlacialHUDOutside, v -> Config.showGlacialHUDOutside = v));

		addRenderableWidget(Button.builder(Component.literal("Reset Glacial Stats"), button -> {
			Config.glacialHudResetStats = true;
			setStatus("Glacial stats reset.");
		}).bounds(rightCol, y + 120, width, 20).build());
	}

	// ----------------------------------------------------
	// TAB 4: ROUTES
	// ----------------------------------------------------
	private void buildRoutesTab(int leftCol, int rightCol, int y, int width) {
		RouteHandler rh = RouteHandler.getInstance();

		addRenderableWidget(Button.builder(Component.literal("Active Route: " + rh.getSelectedRouteName()), button -> {
			List<String> names = new ArrayList<>(rh.getRoutes().keySet());
			if (!names.isEmpty()) {
				int cur = names.indexOf(rh.getSelectedRouteName());
				int next = (cur + 1) % names.size();
				rh.selectRoute(names.get(next));
				button.setMessage(Component.literal("Active Route: " + names.get(next)));
			}
		}).bounds(leftCol, y, width, 20).build());

		addRenderableWidget(Button.builder(Component.literal("+ Add Walk Node (Here)"), button -> {
			Minecraft mc = Minecraft.getInstance();
			if (mc.player != null) {
				BlockPos pos = mc.player.blockPosition();
				ensureCustomRouteSelected();
				rh.addToCurrentRoute(pos, WaypointType.WALK);
				setStatus("Added walk node at " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
				rebuildWidgets();
			}
		}).bounds(leftCol, y + 24, width, 20).build());

		addRenderableWidget(Button.builder(Component.literal("+ Add Etherwarp Node (Here)"), button -> {
			Minecraft mc = Minecraft.getInstance();
			if (mc.player != null) {
				BlockPos pos = mc.player.blockPosition();
				ensureCustomRouteSelected();
				rh.addToCurrentRoute(pos, WaypointType.ETHERWARP);
				setStatus("Added etherwarp node at " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
				rebuildWidgets();
			}
		}).bounds(leftCol, y + 48, width, 20).build());

		addRenderableWidget(Button.builder(Component.literal("- Remove Last Node"), button -> {
			Route route = rh.getSelectedRoute();
			if (route != null && !route.getWaypoints().isEmpty()) {
				rh.removeFromCurrentRoute(route.getWaypoints().size() - 1);
				setStatus("Removed last waypoint.");
				rebuildWidgets();
			} else {
				setStatus("Route is empty.");
			}
		}).bounds(leftCol, y + 72, width, 20).build());

		addRenderableWidget(Button.builder(Component.literal("Clear Route"), button -> {
			Route route = rh.getSelectedRoute();
			if (route != null) {
				while (!route.getWaypoints().isEmpty()) {
					rh.removeFromCurrentRoute(0);
				}
				setStatus("Route cleared.");
				rebuildWidgets();
			}
		}).bounds(leftCol, y + 96, width, 20).build());

		addRenderableWidget(Button.builder(Component.literal("Save Routes to File"), button -> {
			rh.saveData();
			setStatus("Routes saved to disk.");
		}).bounds(leftCol, y + 120, width, 20).build());

		// Right column: Route info & Settings
		addRenderableWidget(new SimpleSlider(rightCol, y, width, 20, "Rotation Easing (ms)",
				200, 1500, Config.rotationTime, v -> Config.rotationTime = v.intValue()));

		addRenderableWidget(createToggleButton(rightCol, y + 24, width, 20, "Ungrab Mouse",
				() -> Config.ungrabMouse, v -> Config.ungrabMouse = v));

		addRenderableWidget(createToggleButton(rightCol, y + 48, width, 20, "Route Mine Topaz",
				() -> Config.routeMineTopaz, v -> Config.routeMineTopaz = v));

		addRenderableWidget(createToggleButton(rightCol, y + 72, width, 20, "Route Mine Tungsten",
				() -> Config.routeMineTungsten, v -> Config.routeMineTungsten = v));

		addRenderableWidget(createToggleButton(rightCol, y + 96, width, 20, "Route Mine Umber",
				() -> Config.routeMineUmber, v -> Config.routeMineUmber = v));
	}

	private void ensureCustomRouteSelected() {
		RouteHandler rh = RouteHandler.getInstance();
		if ("Default".equals(rh.getSelectedRouteName())) {
			rh.createRoute("Custom");
			rh.selectRoute("Custom");
		}
	}

	// ----------------------------------------------------
	// TAB 5: SETTINGS
	// ----------------------------------------------------
	private void buildSettingsTab(int leftCol, int rightCol, int y, int width) {
		addRenderableWidget(createToggleButton(leftCol, y, width, 20, "Auto-Clicker",
				BlockMinerClient::isAutoClickEnabled, BlockMinerClient::setAutoClickEnabled));

		addRenderableWidget(new SimpleSlider(leftCol, y + 24, width, 20, "Click Interval Ticks",
				20, 200, BlockMinerClient.getAutoClickIntervalTicks(), v -> BlockMinerClient.setAutoClickIntervalTicks(v.intValue())));

		addRenderableWidget(createToggleButton(leftCol, y + 48, width, 20, "Commission HUD",
				() -> Config.showDwarvenCommHUDOutside, v -> Config.showDwarvenCommHUDOutside = v));

		addRenderableWidget(createToggleButton(leftCol, y + 72, width, 20, "Glacial HUD",
				() -> Config.showGlacialHUDOutside, v -> Config.showGlacialHUDOutside = v));

		addRenderableWidget(Button.builder(Component.literal("Save All Settings"), button -> {
			saveConfigSafely();
			setStatus("Config saved.");
		}).bounds(leftCol, y + 96, width, 20).build());

		addRenderableWidget(Button.builder(Component.literal("Reload Config From File"), button -> {
			try {
				Config.load();
				setStatus("Config reloaded from disk.");
				rebuildWidgets();
			} catch (Exception ex) {
				setStatus("Failed to reload: " + ex.getMessage());
			}
		}).bounds(leftCol, y + 120, width, 20).build());

		// Right column: Keybind info
		// (Rendered in extractBackground with clean descriptive text)
	}

	// ----------------------------------------------------
	// RENDER BACKGROUND & OVERLAYS
	// ----------------------------------------------------
	@Override
	public void extractBackground(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(graphics, mouseX, mouseY, partialTick);
		int left = panelLeft();
		int top = panelTop();
		int right = left + PANEL_WIDTH;
		int bottom = top + PANEL_HEIGHT;

		// Main Card Background
		graphics.fill(left - 1, top - 1, right + 1, bottom + 1, 0xFF55D6B0);
		graphics.fill(left, top, right, bottom, 0xF211171B);
		graphics.fill(left, top, right, top + 30, 0xFF1B292B);
		graphics.fill(left + 12, top + 57, right - 12, top + 58, 0xFF334447);

		// Header Titles
		graphics.text(font, "MIGHTY MINER", left + 14, top + 8, 0xFF55D6B0, false);
		graphics.text(font, "Fabric 26.2 Client Dashboard", left + 14, top + 18, 0xFF88A0A0, false);

		// Status Badge (Top-Right of Header)
		renderStatusBadge(graphics, right - 14, top + 10);

		// Tab description line
		graphics.text(font, activeTab.description, left + 14, top + 61, 0xFF88A0A0, false);

		// Live notification / feedback status line
		if (!statusMessage.isEmpty()) {
			graphics.text(font, statusMessage, left + 14, bottom - 18, 0xFF55FF55, false);
		}

		// Tab Specific Extra Drawings
		int contentY = top + 86;
		int rightCol = left + 220;
		int colWidth = 196;

		if (activeTab == Tab.DASHBOARD) {
			renderDashboardStatusCard(graphics, rightCol, contentY, colWidth, 140);
		} else if (activeTab == Tab.ROUTES) {
			renderRouteStatsCard(graphics, rightCol, contentY + 98, colWidth, 42);
		} else if (activeTab == Tab.SETTINGS) {
			renderSettingsInfoCard(graphics, rightCol, contentY, colWidth, 140);
		}
	}

	private void renderStatusBadge(GuiGraphicsExtractor graphics, int right, int y) {
		MacroManager macroManager = MacroManager.getInstance();
		MinerState miner = BlockMinerClient.getMiner();
		RouteNavigator nav = RouteNavigator.getInstance();

		String badgeText;
		int badgeColor;

		if (macroManager.isEnabled()) {
			AbstractMacro cur = macroManager.getCurrentMacro();
			badgeText = "MACRO: " + (cur != null ? cur.getName() : "ACTIVE");
			badgeColor = 0xFF55FF55;
		} else if (miner.isEnabled()) {
			badgeText = "MINING: " + miner.getSelectedMode().displayName();
			badgeColor = 0xFF55FF55;
		} else if (nav.isRunning()) {
			badgeText = "ROUTE NAV: RUNNING";
			badgeColor = 0xFFFFAA00;
		} else {
			badgeText = "STATUS: IDLE";
			badgeColor = 0xFFAAAAAA;
		}

		int textWidth = font.width(badgeText);
		graphics.fill(right - textWidth - 8, y - 2, right, y + 10, 0x80000000);
		graphics.text(font, badgeText, right - textWidth - 4, y, badgeColor, false);
	}

	private void renderDashboardStatusCard(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
		graphics.fill(x, y, x + w, y + h, 0xFF182226);
		graphics.outline(x, y, w, h, 0xFF2A3A3E);

		graphics.text(font, "LIVE METRICS", x + 8, y + 8, 0xFF55D6B0, false);

		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		String area = LocationTracker.getGameState().getCleanedSubLocation();
		if (area == null || area.isEmpty()) {
			area = LocationTracker.getGameState().getAreaName();
		}
		if (area == null || area.isEmpty()) {
			area = LocationTracker.getGameState().getCurrentLocation().getName();
		}
		if (area == null || area.isEmpty()) {
			area = "Unknown Area";
		}

		graphics.text(font, "Area: " + area, x + 8, y + 24, 0xFFD5E0DC, false);

		String posText = player != null
				? String.format("Pos: %.0f, %.0f, %.0f", player.getX(), player.getY(), player.getZ())
				: "Pos: Offline";
		graphics.text(font, posText, x + 8, y + 38, 0xFFD5E0DC, false);

		String yawPitch = player != null
				? String.format("Look: Yaw %.0f° Pitch %.0f°", player.getYRot(), player.getXRot())
				: "Look: -";
		graphics.text(font, yawPitch, x + 8, y + 52, 0xFF9BB0AC, false);

		MinerState miner = BlockMinerClient.getMiner();
		String minerStatus = miner.isEnabled() ? miner.getStatus() : "Miner: Idle";
		if (minerStatus.length() > 28) {
			minerStatus = minerStatus.substring(0, 28) + "...";
		}
		graphics.text(font, minerStatus, x + 8, y + 68, 0xFF55FF55, false);

		Route route = RouteHandler.getInstance().getSelectedRoute();
		int wpCount = route != null ? route.getWaypoints().size() : 0;
		graphics.text(font, "Route Nodes: " + wpCount, x + 8, y + 84, 0xFFD5E0DC, false);

		String macroName = MACRO_TYPES[Math.clamp(Config.macroType, 0, MACRO_TYPES.length - 1)];
		graphics.text(font, "Selected: " + macroName, x + 8, y + 100, 0xFF9BB0AC, false);

		graphics.text(font, "GUI Key: Right Shift", x + 8, y + 118, 0xFF55D6B0, false);
	}

	private void renderRouteStatsCard(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
		graphics.fill(x, y, x + w, y + h, 0xFF182226);
		graphics.outline(x, y, w, h, 0xFF2A3A3E);
		Route route = RouteHandler.getInstance().getSelectedRoute();
		int count = route != null ? route.getWaypoints().size() : 0;
		graphics.text(font, "Current Route: " + RouteHandler.getInstance().getSelectedRouteName(), x + 6, y + 6, 0xFF55D6B0, false);
		graphics.text(font, "Waypoints recorded: " + count, x + 6, y + 20, 0xFFD5E0DC, false);
	}

	private void renderSettingsInfoCard(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
		graphics.fill(x, y, x + w, y + h, 0xFF182226);
		graphics.outline(x, y, w, h, 0xFF2A3A3E);

		graphics.text(font, "KEYBINDINGS", x + 8, y + 8, 0xFF55D6B0, false);
		graphics.text(font, "• Open GUI: Right Shift", x + 8, y + 24, 0xFFD5E0DC, false);
		graphics.text(font, "• Rebind in Options > Controls", x + 8, y + 38, 0xFF9BB0AC, false);
		graphics.text(font, "• Chat Command: /mm or /bm", x + 8, y + 54, 0xFFD5E0DC, false);
		graphics.text(font, "• Macro Key: ` (Grave)", x + 8, y + 70, 0xFF9BB0AC, false);
		graphics.text(font, "• Route Builder: [ (Bracket)", x + 8, y + 86, 0xFF9BB0AC, false);
		graphics.text(font, "All actions work without cmd!", x + 8, y + 110, 0xFF55FF55, false);
	}

	private void setStatus(String message) {
		this.statusMessage = message;
	}

	private void saveConfigSafely() {
		try {
			Config.save();
		} catch (Exception ex) {
			Logger.sendError("Failed to save config: " + ex.getMessage());
		}
	}

	@Override
	public void onClose() {
		saveConfigSafely();
		super.onClose();
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == GLFW.GLFW_KEY_ESCAPE || event.key() == GLFW.GLFW_KEY_RIGHT_SHIFT) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	// ----------------------------------------------------
	// WIDGET HELPERS
	// ----------------------------------------------------
	private static Button createToggleButton(int x, int y, int w, int h, String label,
											 BooleanSupplier getter, Consumer<Boolean> setter) {
		return Button.builder(Component.literal(label + ": " + (getter.getAsBoolean() ? "ON" : "OFF")), button -> {
			boolean next = !getter.getAsBoolean();
			setter.accept(next);
			button.setMessage(Component.literal(label + ": " + (next ? "ON" : "OFF")));
		}).bounds(x, y, w, h).build();
	}

	private static Button createCycleButton(int x, int y, int w, int h, String label, String[] options,
											IntSupplier getter, IntConsumer setter) {
		int current = Math.clamp(getter.getAsInt(), 0, options.length - 1);
		return Button.builder(Component.literal(label + ": " + options[current]), button -> {
			int next = (getter.getAsInt() + 1) % options.length;
			setter.accept(next);
			button.setMessage(Component.literal(label + ": " + options[next]));
		}).bounds(x, y, w, h).build();
	}

	private static final class SimpleSlider extends AbstractSliderButton {
		private final String prefix;
		private final double min;
		private final double max;
		private final Consumer<Double> onChange;

		SimpleSlider(int x, int y, int width, int height, String prefix,
					 double min, double max, double initialValue, Consumer<Double> onChange) {
			super(x, y, width, height, Component.empty(), (initialValue - min) / (max - min));
			this.prefix = prefix;
			this.min = min;
			this.max = max;
			this.onChange = onChange;
			updateMessage();
		}

		@Override
		protected void updateMessage() {
			double current = min + value * (max - min);
			setMessage(Component.literal(prefix + ": " + (int) Math.round(current)));
		}

		@Override
		protected void applyValue() {
			double current = min + value * (max - min);
			onChange.accept(current);
		}
	}
}
