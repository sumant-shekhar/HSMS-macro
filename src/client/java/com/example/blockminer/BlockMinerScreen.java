package com.example.blockminer;

import com.example.blockminer.MiningTargets.Mode;
import com.example.blockminer.feature.impl.AutoChestUnlocker;
import com.example.blockminer.feature.impl.RouteNavigator;
import com.example.blockminer.handler.RouteHandler;
import com.example.blockminer.location.LocationTracker;
import com.example.blockminer.macro.AbstractMacro;
import com.example.blockminer.macro.MacroManager;
import com.example.blockminer.util.helper.route.Route;
import com.example.blockminer.util.helper.route.RouteWaypoint;
import com.example.blockminer.util.helper.route.WaypointType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public class BlockMinerScreen extends Screen {
	private static final int PANEL_WIDTH = 540;
	private static final int PANEL_HEIGHT = 320;
	private static final long MOD_START_TIME = System.currentTimeMillis();

	private static final String[] MACRO_TYPES = {
			"Dwarven Commission", "Glacial Commission", "Mining Macro", "Route Miner"
	};
	private static final String[] CLAIM_METHODS = {
			"Emissary", "Royal Pigeon / Abiphone"
	};
	private static final String[] REFUEL_FUELS = {
			"Volta", "Oil Barrel"
	};
	private static final String[] CLICKER_BUTTONS = {
			"Right Click", "Left Click"
	};

	public enum Tab {
		DASHBOARD("Dashboard", "Master switches, quick launch & live telemetry"),
		MINING("Mining", "Target blocks, mining mechanics & camera dynamics"),
		CHESTS("Chests & Perks", "Auto powder chest unlocker & precision mining"),
		MACROS("Macros & Combat", "Commission automations, drill refuel & auto mob killer"),
		ROUTES("Routes", "Waypoint management, pathing & gemstone filters"),
		SETTINGS("HUD & Settings", "Overlay displays, auto-clicker & keybind directory");

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
		super(Component.literal("Mighty Miner Control Center"));
		this.activeTab = tab != null ? tab : Tab.DASHBOARD;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private int panelWidth() {
		return Math.max(380, Math.min(PANEL_WIDTH, width - 16));
	}

	private int panelHeight() {
		return Math.max(240, Math.min(PANEL_HEIGHT, height - 16));
	}

	private int panelLeft() {
		return (width - panelWidth()) / 2;
	}

	private int panelTop() {
		return Math.max(4, (height - panelHeight()) / 2);
	}

	public static String getUptimeFormatted() {
		long sec = (System.currentTimeMillis() - MOD_START_TIME) / 1000L;
		long hours = sec / 3600L;
		long minutes = (sec % 3600L) / 60L;
		long seconds = sec % 60L;
		return String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds);
	}

	public void setStatus(String message) {
		this.statusMessage = message;
		this.statusMessageExpireTick = System.currentTimeMillis() + 4000L;
	}

	private void saveConfigSafely() {
		try {
			Config.cameraSpeed = RotationHelper.getMaxStepPerTick();
			Config.viewConeAngle = MiningTargets.getMaxViewAngle();
			Config.save();
			setStatus("Config saved successfully.");
		} catch (Exception ex) {
			setStatus("Error saving config: " + ex.getMessage());
		}
	}

	private void resetAllToDefaults() {
		Config.cameraSpeed = 4.0F;
		Config.viewConeAngle = 90.0D;
		Config.defaultMiningSpeed = 600;
		Config.waitThresholdMs = 2000;
		Config.autoChestUnlocker = true;
		Config.chestSearchRadius = 8.0D;
		Config.chestRotationMs = 250;
		Config.chestAutoWalk = true;
		Config.chestTimeoutSeconds = 12;
		Config.precisionMining = false;
		Config.precisionAimBias = 0.5D;
		Config.randomizedRotations = true;
		Config.sneakWhileMining = false;
		Config.prioritiseTitanium = false;
		Config.mineGrayMithril = true;
		Config.mineGreenMithril = true;
		Config.mineBlueMithril = true;
		Config.mineTitanium = true;
		Config.routeMineGemstone = false;
		Config.drillRefuel = false;
		Config.refuelMachineFuel = 1;
		Config.commClaimMethod = 0;
		Config.commSwapBeforeClaiming = false;
		Config.coldThreshold = 100;
		Config.mobKillerSprint = true;
		Config.mobKillerInterpolate = true;
		Config.forgePathing = true;
		Config.rotationTime = 600;
		Config.showDwarvenCommHUDOutside = true;
		Config.showGlacialHUDOutside = true;
		Config.showChestTrackerHUD = true;
		Config.debugMode = false;
		Config.autoClickerRightClick = true;
		RotationHelper.setMaxStepPerTick(Config.cameraSpeed);
		MiningTargets.setMaxViewAngle(Config.viewConeAngle);
		saveConfigSafely();
		setStatus("Settings reset to defaults.");
		rebuildWidgets();
	}

	@Override
	protected void init() {
		clearWidgets();
		int left = panelLeft();
		int top = panelTop();
		int pWidth = panelWidth();
		int pHeight = panelHeight();

		// Top Quick Controls (always visible)
		buildTopActionButtons(left, top, pWidth);

		// 6 Navigation Tabs
		buildTabButtons(left, top, pWidth);

		// Content layout
		int contentY = top + 58;
		int contentH = pHeight - 58 - 26;
		int colGap = 8;
		int colW = (pWidth - 20 - colGap) / 2;
		int leftCol = left + 10;
		int rightCol = leftCol + colW + colGap;

		switch (activeTab) {
			case DASHBOARD -> buildDashboardTab(leftCol, rightCol, contentY, colW, contentH);
			case MINING -> buildMiningTab(leftCol, rightCol, contentY, colW, contentH);
			case CHESTS -> buildChestsTab(leftCol, rightCol, contentY, colW, contentH);
			case MACROS -> buildMacrosTab(leftCol, rightCol, contentY, colW, contentH);
			case ROUTES -> buildRoutesTab(leftCol, rightCol, contentY, colW, contentH);
			case SETTINGS -> buildSettingsTab(leftCol, rightCol, contentY, colW, contentH);
		}

		// Done Button in footer
		addRenderableWidget(new ModernButton(
				left + pWidth - 76,
				top + pHeight - 22,
				66,
				17,
				Component.literal("Done"),
				"Close control center [Esc]",
				btn -> onClose(),
				false,
				false
		));
	}

	// ----------------------------------------------------
	// TOP CONTROLS & STATUS
	// ----------------------------------------------------
	private void buildTopActionButtons(int left, int top, int pWidth) {
		int actionY = top + 7;
		int btnH = 20;
		int btnW = 66;
		int gap = 4;
		int rightEdge = left + pWidth - 10;

		// STOP ALL button (crimson)
		addRenderableWidget(new ModernButton(
				rightEdge - 72,
				actionY,
				72,
				btnH,
				Component.literal("✖ STOP ALL"),
				"Instantly halt all routines and release all keybinds",
				button -> {
					BlockMinerClient.stopAll(Minecraft.getInstance());
					setStatus("Stopped all mining, macros, and navigation.");
					rebuildWidgets();
				},
				true,
				false
		));

		// Route Toggle button
		RouteNavigator nav = RouteNavigator.getInstance();
		boolean routeActive = nav.isRunning();
		addRenderableWidget(new ModernButton(
				rightEdge - 72 - gap - btnW,
				actionY,
				btnW,
				btnH,
				Component.literal(routeActive ? "■ Route" : "▶ Route"),
				"Toggle route path navigation",
				button -> {
					if (nav.isRunning()) {
						nav.stop();
						setStatus("Route navigation halted.");
					} else {
						Route route = RouteHandler.getInstance().getSelectedRoute();
						if (route != null && !route.getWaypoints().isEmpty()) {
							nav.start(route);
							setStatus("Route started: " + RouteHandler.getInstance().getSelectedRouteName());
						} else {
							setStatus("Route is empty; record waypoints first.");
						}
					}
					rebuildWidgets();
				},
				false,
				routeActive
		));

		// Macro Toggle button
		MacroManager macroManager = MacroManager.getInstance();
		boolean macroActive = macroManager.isEnabled();
		addRenderableWidget(new ModernButton(
				rightEdge - 72 - (gap + btnW) * 2,
				actionY,
				btnW,
				btnH,
				Component.literal(macroActive ? "■ Macro" : "▶ Macro"),
				"Toggle active macro routine",
				button -> {
					try {
						macroManager.toggle();
						if (macroManager.isEnabled()) {
							AbstractMacro cur = macroManager.getCurrentMacro();
							setStatus("Macro started: " + (cur != null ? cur.getName() : "Active"));
						} else {
							setStatus("Macro halted.");
						}
					} catch (Exception ex) {
						setStatus("Macro error: " + ex.getMessage());
					}
					rebuildWidgets();
				},
				false,
				macroActive
		));

		// Mining Toggle button
		MinerState miner = BlockMinerClient.getMiner();
		boolean minerActive = miner.isEnabled();
		addRenderableWidget(new ModernButton(
				rightEdge - 72 - (gap + btnW) * 3,
				actionY,
				btnW,
				btnH,
				Component.literal(minerActive ? "■ Mining" : "▶ Mining"),
				"Toggle automated block miner",
				button -> {
					Minecraft mc = Minecraft.getInstance();
					if (miner.isEnabled()) {
						miner.stop(mc);
						setStatus("Block miner stopped.");
					} else {
						miner.start(mc);
						setStatus("Mining started (" + miner.getSelectedMode().displayName() + ").");
					}
					rebuildWidgets();
				},
				false,
				minerActive
		));
	}

	private void buildTabButtons(int left, int top, int pWidth) {
		int tabY = top + 34;
		Tab[] tabs = Tab.values();
		int tabGap = 3;
		int tabWidth = (pWidth - 20 - tabGap * (tabs.length - 1)) / tabs.length;

		for (int i = 0; i < tabs.length; i++) {
			Tab tab = tabs[i];
			int tabX = left + 10 + i * (tabWidth + tabGap);
			addRenderableWidget(new ModernTabButton(
					tabX,
					tabY,
					tabWidth,
					19,
					tab,
					() -> activeTab == tab,
					selected -> {
						activeTab = selected;
						rebuildWidgets();
					}
			));
		}
	}

	// ----------------------------------------------------
	// TAB 1: DASHBOARD
	// ----------------------------------------------------
	private void buildDashboardTab(int leftCol, int rightCol, int y, int colW, int colH) {
		MinerState miner = BlockMinerClient.getMiner();
		int itemX = leftCol + 6;
		int itemW = colW - 12;
		int rowY = y + 22;
		int rowGap = 24;

		// Macro mode selector
		addRenderableWidget(new ModernCycleButton(
				itemX, rowY, itemW, 20, "Macro Mode", "Select active macro type",
				MACRO_TYPES,
				() -> Math.clamp(Config.macroType, 0, MACRO_TYPES.length - 1),
				value -> {
					if (!MacroManager.getInstance().isEnabled()) {
						Config.macroType = value;
						setStatus("Macro mode: " + MACRO_TYPES[value]);
					} else {
						setStatus("Stop macro before changing type.");
					}
				}
		));
		rowY += rowGap;

		// Target ore selector
		Mode[] modes = Mode.values();
		String[] modeNames = new String[modes.length];
		for (int i = 0; i < modes.length; i++) {
			modeNames[i] = modes[i].displayName();
		}
		addRenderableWidget(new ModernCycleButton(
				itemX, rowY, itemW, 20, "Target Ore", "Select primary mining target block",
				modeNames,
				() -> miner.getSelectedMode().ordinal(),
				idx -> {
					miner.selectMode(Minecraft.getInstance(), modes[idx]);
					setStatus("Target ore: " + modes[idx].displayName());
				}
		));
		rowY += rowGap;

		// Active route selector
		List<String> routeNames = new ArrayList<>(RouteHandler.getInstance().getRoutes().keySet());
		String[] routesArr = routeNames.toArray(String[]::new);
		addRenderableWidget(new ModernCycleButton(
				itemX, rowY, itemW, 20, "Active Route", "Select active waypoint navigation route",
				routesArr,
				() -> Math.max(0, routeNames.indexOf(RouteHandler.getInstance().getSelectedRouteName())),
				idx -> {
					if (!routeNames.isEmpty()) {
						RouteHandler.getInstance().selectRoute(routeNames.get(idx));
						setStatus("Selected route: " + routeNames.get(idx));
					}
				}
		));
		rowY += rowGap;

		// Master quick switches
		addRenderableWidget(new ModernPillToggle(
				itemX, rowY, itemW, 20, "Powder Chests", "Auto-solve powder chests using lockpick particle aiming",
				() -> Config.autoChestUnlocker, v -> Config.autoChestUnlocker = v
		));
		rowY += rowGap;

		addRenderableWidget(new ModernPillToggle(
				itemX, rowY, itemW, 20, "Precision Mining", "Bias crosshair toward crit/villager particles for speed",
				() -> Config.precisionMining, v -> Config.precisionMining = v
		));
		rowY += rowGap;

		addRenderableWidget(new ModernPillToggle(
				itemX, rowY, itemW, 20, "Auto Refuel Drill", "Automatically refuels empty drills via Abiphone / Greatforge",
				() -> Config.drillRefuel, v -> Config.drillRefuel = v
		));
		rowY += rowGap;

		addRenderableWidget(new ModernPillToggle(
				itemX, rowY, itemW, 20, "Auto Mob Killer", "Attack nearby hostile mobs with real Left-Click hits",
				() -> !Config.slayerWeapon.isEmpty(), v -> Config.slayerWeapon = v ? "Slayer" : ""
		));
		rowY += rowGap;

		// Save Configuration button
		addRenderableWidget(new ModernButton(
				itemX, rowY, itemW, 20, Component.literal("Save Configuration"),
				"Persist all current settings to config/blockminer.json",
				btn -> saveConfigSafely(),
				false,
				false
		));
	}

	// ----------------------------------------------------
	// TAB 2: MINING
	// ----------------------------------------------------
	private void buildMiningTab(int leftCol, int rightCol, int y, int colW, int colH) {
		// Left: Target Ore Filters
		int leftX = leftCol + 6;
		int leftW = colW - 12;
		int rowY = y + 22;
		int rowGap = 24;

		addRenderableWidget(new ModernPillToggle(leftX, rowY, leftW, 20, "Mine Gray Mithril",
				"Scan and mine Gray Mithril (wool/terracotta)", () -> Config.mineGrayMithril, v -> Config.mineGrayMithril = v));
		rowY += rowGap;

		addRenderableWidget(new ModernPillToggle(leftX, rowY, leftW, 20, "Mine Green Mithril",
				"Scan and mine Green Mithril (prismarine bricks)", () -> Config.mineGreenMithril, v -> Config.mineGreenMithril = v));
		rowY += rowGap;

		addRenderableWidget(new ModernPillToggle(leftX, rowY, leftW, 20, "Mine Blue Mithril",
				"Scan and mine Blue Mithril (prismarine/light blue wool)", () -> Config.mineBlueMithril, v -> Config.mineBlueMithril = v));
		rowY += rowGap;

		addRenderableWidget(new ModernPillToggle(leftX, rowY, leftW, 20, "Mine Titanium",
				"Scan and mine Titanium (polished diorite)", () -> Config.mineTitanium, v -> Config.mineTitanium = v));
		rowY += rowGap;

		addRenderableWidget(new ModernPillToggle(leftX, rowY, leftW, 20, "Prioritize Titanium",
				"Score Titanium highest so it is broken before Mithril", () -> Config.prioritiseTitanium, v -> Config.prioritiseTitanium = v));
		rowY += rowGap;

		addRenderableWidget(new ModernPillToggle(leftX, rowY, leftW, 20, "Mine Gemstones",
				"Scan and mine gemstone veins", () -> Config.routeMineGemstone, v -> Config.routeMineGemstone = v));
		rowY += rowGap;

		addRenderableWidget(new ModernPillToggle(leftX, rowY, leftW, 20, "Sneak While Mining",
				"Hold sneak key during mining to prevent walking off ledge", () -> Config.sneakWhileMining, v -> Config.sneakWhileMining = v));
		rowY += rowGap;

		addRenderableWidget(new ModernPillToggle(leftX, rowY, leftW, 20, "Auto Drill Ability",
				"Execute native Right-Click to trigger pickaxe abilities", () -> Config.usePickaxeAbility, v -> Config.usePickaxeAbility = v));

		// Right: Mechanics & Camera
		int rightX = rightCol + 6;
		int rightW = colW - 12;
		rowY = y + 22;

		addRenderableWidget(new ModernPillToggle(rightX, rowY, rightW, 20, "Humanized Aim Curve",
				"Randomize Bezier curve control points for humanized rotations", () -> Config.randomizedRotations, v -> Config.randomizedRotations = v));
		rowY += rowGap;

		addRenderableWidget(new ModernSlider(rightX, rowY, rightW, 20, "Mining Speed",
				"Mining speed delay calculation factor", 100, 3000, Config.defaultMiningSpeed, "", true,
				v -> Config.defaultMiningSpeed = v.intValue()));
		rowY += rowGap;

		addRenderableWidget(new ModernSlider(rightX, rowY, rightW, 20, "Break Timeout",
				"Maximum time spent breaking a single block before moving on", 500, 5000, Config.waitThresholdMs, "ms", true,
				v -> Config.waitThresholdMs = v.intValue()));
		rowY += rowGap;

		// Camera Speed & View Cone copied from old GUI
		addRenderableWidget(new ModernSlider(rightX, rowY, rightW, 20, "Camera Speed",
				"Camera rotation speed step per client tick (1.0 - 30.0)", 1.0, 30.0, RotationHelper.getMaxStepPerTick(), "", false,
				v -> {
					RotationHelper.setMaxStepPerTick(v.floatValue());
					Config.cameraSpeed = v.floatValue();
				}));
		rowY += rowGap;

		addRenderableWidget(new ModernSlider(rightX, rowY, rightW, 20, "View Cone",
				"Maximum angular field of view to target blocks (30° - 180°)", 30.0, 180.0, MiningTargets.getMaxViewAngle(), "°", true,
				v -> {
					MiningTargets.setMaxViewAngle(v);
					Config.viewConeAngle = v;
				}));
		rowY += rowGap;

		addRenderableWidget(new ModernButton(rightX, rowY, rightW, 20, Component.literal("Reset Camera & Cone"),
				"Restore default camera speed (4.0) and view cone (90°)",
				btn -> {
					RotationHelper.setMaxStepPerTick(4.0F);
					Config.cameraSpeed = 4.0F;
					MiningTargets.setMaxViewAngle(90.0D);
					Config.viewConeAngle = 90.0D;
					setStatus("Camera speed and view cone reset.");
					rebuildWidgets();
				}, false, false));
	}

	// ----------------------------------------------------
	// TAB 3: CHESTS & PERKS
	// ----------------------------------------------------
	private void buildChestsTab(int leftCol, int rightCol, int y, int colW, int colH) {
		// Left: Powder Chest Unlocker
		int leftX = leftCol + 6;
		int leftW = colW - 12;
		int rowY = y + 22;
		int rowGap = 24;

		addRenderableWidget(new ModernPillToggle(leftX, rowY, leftW, 20, "Chest Unlocker",
				"Detects, opens and solves powder chests", () -> Config.autoChestUnlocker, v -> Config.autoChestUnlocker = v));
		rowY += rowGap;

		addRenderableWidget(new ModernPillToggle(leftX, rowY, leftW, 20, "Auto-Walk to Chest",
				"Automatically walks toward chests farther than 4.2 blocks", () -> Config.chestAutoWalk, v -> Config.chestAutoWalk = v));
		rowY += rowGap;

		addRenderableWidget(new ModernSlider(leftX, rowY, leftW, 20, "Search Radius",
				"Radius in blocks to scan for spawned treasure chests", 3.0, 16.0, Config.chestSearchRadius, "blk", false,
				v -> Config.chestSearchRadius = v));
		rowY += rowGap;

		addRenderableWidget(new ModernSlider(leftX, rowY, leftW, 20, "Lockpick Aim Speed",
				"Easing speed to snap crosshair onto crit lockpick particles", 100, 1000, Config.chestRotationMs, "ms", true,
				v -> Config.chestRotationMs = v.intValue()));
		rowY += rowGap;

		addRenderableWidget(new ModernSlider(leftX, rowY, leftW, 20, "Chest Timeout",
				"Timeout in seconds before abandoning a chest", 5, 30, Config.chestTimeoutSeconds, "s", true,
				v -> Config.chestTimeoutSeconds = v.intValue()));
		rowY += rowGap;

		addRenderableWidget(new ModernButton(leftX, rowY, leftW, 20, Component.literal("Reset Chest Counter"),
				"Reset session count of unlocked chests to 0",
				btn -> {
					AutoChestUnlocker.getInstance().resetChestsUnlocked();
					setStatus("Chest counter reset.");
					rebuildWidgets();
				}, false, false));

		// Right: Precision Mining
		int rightX = rightCol + 6;
		int rightW = colW - 12;
		rowY = y + 22;

		addRenderableWidget(new ModernPillToggle(rightX, rowY, rightW, 20, "Precision Mining",
				"Bias aim toward crit particles spawning on mined block", () -> Config.precisionMining, v -> Config.precisionMining = v));
		rowY += rowGap;

		addRenderableWidget(new ModernSlider(rightX, rowY, rightW, 20, "Particle Aim Bias",
				"Strength of rotation bias toward ore crit particles", 0.1, 1.0, Config.precisionAimBias, "", false,
				v -> Config.precisionAimBias = v));
	}

	// ----------------------------------------------------
	// TAB 4: MACROS & COMBAT
	// ----------------------------------------------------
	private void buildMacrosTab(int leftCol, int rightCol, int y, int colW, int colH) {
		// Left: Commission & Refuel
		int leftX = leftCol + 6;
		int leftW = colW - 12;
		int rowY = y + 22;
		int rowGap = 24;

		addRenderableWidget(new ModernCycleButton(leftX, rowY, leftW, 20, "Claim Method",
				"Method used to claim completed commissions", CLAIM_METHODS,
				() -> Math.clamp(Config.commClaimMethod, 0, 1),
				v -> Config.commClaimMethod = v));
		rowY += rowGap;

		addRenderableWidget(new ModernPillToggle(leftX, rowY, leftW, 20, "Tool Swap on Claim",
				"Swap to designated tool before claiming rewards", () -> Config.commSwapBeforeClaiming, v -> Config.commSwapBeforeClaiming = v));
		rowY += rowGap;

		addRenderableWidget(new ModernSlider(leftX, rowY, leftW, 20, "Glacial Cold Limit",
				"Cold percentage threshold before warping to safety", 10, 100, Config.coldThreshold, "%", true,
				v -> Config.coldThreshold = v.intValue()));
		rowY += rowGap;

		addRenderableWidget(new ModernPillToggle(leftX, rowY, leftW, 20, "Auto Drill Refuel",
				"Automatically refuels drill when fuel runs out", () -> Config.drillRefuel, v -> Config.drillRefuel = v));
		rowY += rowGap;

		addRenderableWidget(new ModernCycleButton(leftX, rowY, leftW, 20, "Refuel Fuel Type",
				"Fuel type purchased when refueling drill", REFUEL_FUELS,
				() -> Math.clamp(Config.refuelMachineFuel, 0, 1),
				v -> Config.refuelMachineFuel = v));
		rowY += rowGap;

		addRenderableWidget(new ModernButton(leftX, rowY, leftW, 20, Component.literal("Reset Commission Stats"),
				"Reset session commission and glacial tracking stats",
				btn -> {
					Config.commHudResetStats = true;
					Config.glacialHudResetStats = true;
					setStatus("Commission & glacial stats reset.");
				}, false, false));

		// Right: Mob Killer & Combat
		int rightX = rightCol + 6;
		int rightW = colW - 12;
		rowY = y + 22;

		addRenderableWidget(new ModernPillToggle(rightX, rowY, rightW, 20, "Auto Mob Killer",
				"Engage and attack designated hostile mobs with real Left-Clicks",
				() -> !Config.slayerWeapon.isEmpty(), v -> Config.slayerWeapon = v ? "Slayer" : ""));
		rowY += rowGap;

		addRenderableWidget(new ModernPillToggle(rightX, rowY, rightW, 20, "Sprint When Pathing",
				"Sprint toward hostile mob targets", () -> Config.mobKillerSprint, v -> Config.mobKillerSprint = v));
		rowY += rowGap;

		addRenderableWidget(new ModernPillToggle(rightX, rowY, rightW, 20, "Smooth Camera Look",
				"Interpolate camera toward mobs rather than instant snap", () -> Config.mobKillerInterpolate, v -> Config.mobKillerInterpolate = v));
		rowY += rowGap;

		addRenderableWidget(new ModernPillToggle(rightX, rowY, rightW, 20, "Forge Pathing",
				"Use Forge auto-warp and route pathing", () -> Config.forgePathing, v -> Config.forgePathing = v));
		rowY += rowGap;

		addRenderableWidget(new ModernSlider(rightX, rowY, rightW, 20, "Max Repaths",
				"Maximum re-pathing attempts before selecting a new target", 1, 10, Config.mobKillerMaximumRepaths, "", true,
				v -> Config.mobKillerMaximumRepaths = v.intValue()));
	}

	// ----------------------------------------------------
	// TAB 5: ROUTES
	// ----------------------------------------------------
	private void buildRoutesTab(int leftCol, int rightCol, int y, int colW, int colH) {
		RouteHandler rh = RouteHandler.getInstance();
		int leftX = leftCol + 6;
		int leftW = colW - 12;
		int rowY = y + 22;
		int rowGap = 24;

		List<String> names = new ArrayList<>(rh.getRoutes().keySet());
		String[] namesArr = names.toArray(String[]::new);
		addRenderableWidget(new ModernCycleButton(leftX, rowY, leftW, 20, "Active Route",
				"Select route file to record or navigate", namesArr,
				() -> Math.max(0, names.indexOf(rh.getSelectedRouteName())),
				idx -> {
					if (!names.isEmpty()) {
						rh.selectRoute(names.get(idx));
						setStatus("Selected route: " + names.get(idx));
						rebuildWidgets();
					}
				}));
		rowY += rowGap;

		addRenderableWidget(new ModernButton(leftX, rowY, leftW, 20, Component.literal("+ Add Walk Node (Here)"),
				"Add walk waypoint at player's current block position",
				btn -> {
					Minecraft mc = Minecraft.getInstance();
					if (mc.player != null) {
						ensureCustomRouteSelected();
						BlockPos pos = mc.player.blockPosition();
						rh.addToCurrentRoute(pos, WaypointType.WALK);
						setStatus("Added walk node at " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
						rebuildWidgets();
					}
				}, false, false));
		rowY += rowGap;

		addRenderableWidget(new ModernButton(leftX, rowY, leftW, 20, Component.literal("+ Add Etherwarp Node (Here)"),
				"Add etherwarp teleport waypoint at player's position",
				btn -> {
					Minecraft mc = Minecraft.getInstance();
					if (mc.player != null) {
						ensureCustomRouteSelected();
						BlockPos pos = mc.player.blockPosition();
						rh.addToCurrentRoute(pos, WaypointType.ETHERWARP);
						setStatus("Added etherwarp node at " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
						rebuildWidgets();
					}
				}, false, false));
		rowY += rowGap;

		addRenderableWidget(new ModernButton(leftX, rowY, leftW, 20, Component.literal("- Remove Last Node"),
				"Remove the most recently added waypoint from route",
				btn -> {
					Route route = rh.getSelectedRoute();
					if (route != null && !route.getWaypoints().isEmpty()) {
						rh.removeFromCurrentRoute(route.getWaypoints().size() - 1);
						setStatus("Removed last waypoint.");
						rebuildWidgets();
					} else {
						setStatus("Route is empty.");
					}
				}, false, false));
		rowY += rowGap;

		addRenderableWidget(new ModernButton(leftX, rowY, leftW, 20, Component.literal("Clear Route"),
				"Clear all waypoints from active route",
				btn -> {
					Route route = rh.getSelectedRoute();
					if (route != null) {
						while (!route.getWaypoints().isEmpty()) {
							rh.removeFromCurrentRoute(0);
						}
						setStatus("Route cleared.");
						rebuildWidgets();
					}
				}, true, false));
		rowY += rowGap;

		addRenderableWidget(new ModernButton(leftX, rowY, leftW, 20, Component.literal("Save Routes to File"),
				"Persist all recorded routes to disk",
				btn -> {
					rh.saveData();
					setStatus("Routes saved to disk.");
				}, false, false));

		// Right: Travel Dynamics
		int rightX = rightCol + 6;
		int rightW = colW - 12;
		rowY = y + 22;

		addRenderableWidget(new ModernSlider(rightX, rowY, rightW, 20, "Path Camera Easing",
				"Camera easing time in milliseconds during route navigation", 200, 1500, Config.rotationTime, "ms", true,
				v -> Config.rotationTime = v.intValue()));
		rowY += rowGap;

		addRenderableWidget(new ModernPillToggle(rightX, rowY, rightW, 20, "Ungrab Mouse",
				"Ungrab mouse during routing for background operation", () -> Config.ungrabMouse, v -> Config.ungrabMouse = v));
		rowY += rowGap;

		addRenderableWidget(new ModernPillToggle(rightX, rowY, rightW, 20, "Route Mine Topaz",
				"Mine Topaz gemstones along route path", () -> Config.routeMineTopaz, v -> Config.routeMineTopaz = v));
		rowY += rowGap;

		addRenderableWidget(new ModernPillToggle(rightX, rowY, rightW, 20, "Route Mine Tungsten",
				"Mine Tungsten along route path", () -> Config.routeMineTungsten, v -> Config.routeMineTungsten = v));
		rowY += rowGap;

		addRenderableWidget(new ModernPillToggle(rightX, rowY, rightW, 20, "Route Mine Umber",
				"Mine Umber along route path", () -> Config.routeMineUmber, v -> Config.routeMineUmber = v));
	}

	private void ensureCustomRouteSelected() {
		RouteHandler rh = RouteHandler.getInstance();
		if ("Default".equals(rh.getSelectedRouteName())) {
			rh.createRoute("Custom");
			rh.selectRoute("Custom");
		}
	}

	// ----------------------------------------------------
	// TAB 6: HUD & SETTINGS
	// ----------------------------------------------------
	private void buildSettingsTab(int leftCol, int rightCol, int y, int colW, int colH) {
		// Left: HUD & Clicker
		int leftX = leftCol + 6;
		int leftW = colW - 12;
		int rowY = y + 22;
		int rowGap = 24;

		addRenderableWidget(new ModernPillToggle(leftX, rowY, leftW, 20, "Commission HUD",
				"Display commission objective tracking HUD on screen", () -> Config.showDwarvenCommHUDOutside, v -> Config.showDwarvenCommHUDOutside = v));
		rowY += rowGap;

		addRenderableWidget(new ModernPillToggle(leftX, rowY, leftW, 20, "Glacial HUD",
				"Display glacial cold and shaft stats HUD on screen", () -> Config.showGlacialHUDOutside, v -> Config.showGlacialHUDOutside = v));
		rowY += rowGap;

		addRenderableWidget(new ModernPillToggle(leftX, rowY, leftW, 20, "Chest Tracker HUD",
				"Display powder chest session counter HUD overlay", () -> Config.showChestTrackerHUD, v -> Config.showChestTrackerHUD = v));
		rowY += rowGap;

		addRenderableWidget(new ModernPillToggle(leftX, rowY, leftW, 20, "Debug HUD",
				"Display position, facing and pathfinding diagnostics", () -> Config.debugMode, v -> Config.debugMode = v));
		rowY += rowGap;

		addRenderableWidget(new ModernPillToggle(leftX, rowY, leftW, 20, "Auto-Clicker",
				"Automated native mouse clicking without opening inventory", BlockMinerClient::isAutoClickEnabled, BlockMinerClient::setAutoClickEnabled));
		rowY += rowGap;

		addRenderableWidget(new ModernCycleButton(leftX, rowY, leftW, 20, "Click Button",
				"Mouse button triggered by auto-clicker", CLICKER_BUTTONS,
				() -> Config.autoClickerRightClick ? 0 : 1,
				v -> Config.autoClickerRightClick = (v == 0)));
		rowY += rowGap;

		addRenderableWidget(new ModernSlider(leftX, rowY, leftW, 20, "Click Interval",
				"Ticks between automated clicks (20 ticks = 1 sec)", 1, 40, BlockMinerClient.getAutoClickIntervalTicks(), "ticks", true,
				v -> BlockMinerClient.setAutoClickIntervalTicks(v.intValue())));

		// Right: Config Actions
		int rightX = rightCol + 6;
		int rightW = colW - 12;
		rowY = y + 22 + rowGap * 4;

		addRenderableWidget(new ModernButton(rightX, rowY, rightW, 20, Component.literal("Save Config to Disk"),
				"Write current settings to config/blockminer.json",
				btn -> saveConfigSafely(), false, false));
		rowY += rowGap;

		addRenderableWidget(new ModernButton(rightX, rowY, rightW, 20, Component.literal("Reload Config"),
				"Reload settings from config/blockminer.json",
				btn -> {
					try {
						Config.load();
						setStatus("Config reloaded from disk.");
						rebuildWidgets();
					} catch (Exception ex) {
						setStatus("Error reloading config: " + ex.getMessage());
					}
				}, false, false));
		rowY += rowGap;

		addRenderableWidget(new ModernButton(rightX, rowY, rightW, 20, Component.literal("Reset to Defaults"),
				"Restore all settings to original factory defaults",
				btn -> resetAllToDefaults(), true, false));
	}

	// ----------------------------------------------------
	// BACKGROUND & CARDS RENDERING
	// ----------------------------------------------------
	@Override
	public void extractBackground(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(graphics, mouseX, mouseY, partialTick);

		// Subtle full-screen dark translucent backdrop so live game is visible behind menu
		graphics.fill(0, 0, width, height, 0x880A0D14);

		int left = panelLeft();
		int top = panelTop();
		int pWidth = panelWidth();
		int pHeight = panelHeight();
		int right = left + pWidth;
		int bottom = top + pHeight;

		// Main Panel Background (Deep obsidian #10131A)
		graphics.fill(left, top, right, bottom, 0xF410131A);
		// Main Panel 1px Border (Slate #2A3244)
		graphics.outline(left, top, pWidth, pHeight, 0xFF2A3244);

		// Header Background (#181D27)
		graphics.fill(left, top, right, top + 32, 0xFF181D27);
		graphics.horizontalLine(left, right, top + 32, 0xFF2A3244);

		Font font = Minecraft.getInstance().font;

		// Mod Title & Version Badge
		graphics.text(font, "MIGHTY MINER", left + 12, top + 10, 0xFF00E5A8, false);
		int badgeX = left + 12 + font.width("MIGHTY MINER") + 6;
		graphics.fill(badgeX, top + 9, badgeX + 26, top + 21, 0xFF1E2638);
		graphics.outline(badgeX, top + 9, 26, 12, 0xFF2A3244);
		graphics.text(font, "v2.0", badgeX + 4, top + 11, 0xFF94A3B8, false);

		// Center Live Status Pill
		renderStatusPill(graphics, left + pWidth / 2 - 30, top + 8);

		// Card Backgrounds
		int contentY = top + 58;
		int contentH = pHeight - 58 - 26;
		int colGap = 8;
		int colW = (pWidth - 20 - colGap) / 2;
		int leftCol = left + 10;
		int rightCol = leftCol + colW + colGap;

		// Left Card Background (#181D27)
		graphics.fill(leftCol, contentY, leftCol + colW, contentY + contentH, 0xFF181D27);
		graphics.outline(leftCol, contentY, colW, contentH, 0xFF2A3244);

		// Right Card Background (#181D27)
		graphics.fill(rightCol, contentY, rightCol + colW, contentY + contentH, 0xFF181D27);
		graphics.outline(rightCol, contentY, colW, contentH, 0xFF2A3244);

		// Card Headers & Divider
		drawCardHeader(graphics, font, leftCol, contentY, colW, getLeftCardTitle());
		drawCardHeader(graphics, font, rightCol, contentY, colW, getRightCardTitle());

		// Tab-Specific Graphics
		if (activeTab == Tab.DASHBOARD) {
			renderDashboardTelemetry(graphics, rightCol, contentY, colW, contentH);
		} else if (activeTab == Tab.CHESTS) {
			renderChestLockpickCard(graphics, rightCol, contentY + 70, colW, contentH - 74);
		} else if (activeTab == Tab.MACROS) {
			renderCombatInfoCard(graphics, rightCol, contentY + 144, colW, contentH - 148);
		} else if (activeTab == Tab.ROUTES) {
			renderRouteStats(graphics, rightCol, contentY + 144, colW, contentH - 148);
		} else if (activeTab == Tab.SETTINGS) {
			renderKeybindInfo(graphics, rightCol, contentY, colW, 114);
		}

		// Footer Status Feedback
		if (!statusMessage.isEmpty() && System.currentTimeMillis() < statusMessageExpireTick) {
			graphics.text(font, "● " + statusMessage, left + 12, bottom - 17, 0xFF00E5A8, false);
		} else {
			graphics.text(font, activeTab.description, left + 12, bottom - 17, 0xFF64748B, false);
		}
	}

	private String getLeftCardTitle() {
		return switch (activeTab) {
			case DASHBOARD -> "QUICK CONFIGURATION";
			case MINING -> "TARGET ORE SELECTION";
			case CHESTS -> "POWDER CHEST UNLOCKER";
			case MACROS -> "COMMISSION AUTOMATION";
			case ROUTES -> "ROUTE RECORDER & WAYPOINTS";
			case SETTINGS -> "HUD OVERLAYS & CLICKER";
		};
	}

	private String getRightCardTitle() {
		return switch (activeTab) {
			case DASHBOARD -> "LIVE BOT TELEMETRY";
			case MINING -> "CAMERA & MINING DYNAMICS";
			case CHESTS -> "PRECISION MINING (PARTICLES)";
			case MACROS -> "AUTO MOB KILLER & COMBAT";
			case ROUTES -> "ROUTE DYNAMICS & GEMSTONES";
			case SETTINGS -> "KEYBINDS & PROFILE";
		};
	}

	private void drawCardHeader(GuiGraphicsExtractor graphics, Font font, int x, int y, int w, String title) {
		graphics.text(font, title, x + 8, y + 6, 0xFF00E5A8, false);
		graphics.horizontalLine(x + 6, x + w - 6, y + 17, 0xFF2A3244);
	}

	private void renderStatusPill(GuiGraphicsExtractor graphics, int centerX, int y) {
		MacroManager macroManager = MacroManager.getInstance();
		MinerState miner = BlockMinerClient.getMiner();
		RouteNavigator nav = RouteNavigator.getInstance();
		AutoChestUnlocker chest = AutoChestUnlocker.getInstance();

		String statusText;
		int statusColor;

		if (chest.getState() != AutoChestUnlocker.State.IDLE) {
			statusText = "CHEST: " + chest.getState().name();
			statusColor = 0xFFF59E0B;
		} else if (macroManager.isEnabled()) {
			AbstractMacro cur = macroManager.getCurrentMacro();
			statusText = "MACRO: " + (cur != null ? cur.getName().toUpperCase(Locale.ROOT) : "ACTIVE");
			statusColor = 0xFF38BDF8;
		} else if (miner.isEnabled()) {
			statusText = "MINING: " + miner.getSelectedMode().displayName().toUpperCase(Locale.ROOT);
			statusColor = 0xFF00E5A8;
		} else if (nav.isRunning()) {
			statusText = "ROUTING: " + RouteHandler.getInstance().getSelectedRouteName().toUpperCase(Locale.ROOT);
			statusColor = 0xFFA855F7;
		} else {
			statusText = "IDLE";
			statusColor = 0xFF94A3B8;
		}

		Font font = Minecraft.getInstance().font;
		int textW = font.width(statusText);
		int pillW = textW + 20;
		int pillH = 16;
		int pillX = centerX - pillW / 2;
		int pillY = y;

		graphics.fill(pillX, pillY, pillX + pillW, pillY + pillH, 0xFF10131A);
		graphics.outline(pillX, pillY, pillW, pillH, statusColor);

		// Glowing dot
		int dotX = pillX + 6;
		int dotY = pillY + 5;
		graphics.fill(dotX, dotY, dotX + 5, dotY + 5, statusColor);

		graphics.text(font, statusText, pillX + 15, pillY + 4, statusColor, false);
	}

	private void renderDashboardTelemetry(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		Font font = mc.font;

		int lineY = y + 24;
		int lineGap = 16;

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
		drawMetricRow(graphics, font, x + 8, lineY, w - 16, "Current Area", area, 0xFFE2E8F0);
		lineY += lineGap;

		String posStr = player != null
				? String.format(Locale.ROOT, "%.0f, %.0f, %.0f", player.getX(), player.getY(), player.getZ())
				: "Offline";
		drawMetricRow(graphics, font, x + 8, lineY, w - 16, "Coordinates", posStr, 0xFF38BDF8);
		lineY += lineGap;

		String lookStr = player != null
				? String.format(Locale.ROOT, "Yaw %.0f° / Pitch %.0f°", player.getYRot(), player.getXRot())
				: "-";
		drawMetricRow(graphics, font, x + 8, lineY, w - 16, "Facing / Aim", lookStr, 0xFF94A3B8);
		lineY += lineGap;

		MinerState miner = BlockMinerClient.getMiner();
		String minerStatus = miner.isEnabled() ? miner.getStatus() : "Idle";
		if (minerStatus.length() > 24) {
			minerStatus = minerStatus.substring(0, 24) + "..";
		}
		drawMetricRow(graphics, font, x + 8, lineY, w - 16, "Active Target", minerStatus, miner.isEnabled() ? 0xFF00E5A8 : 0xFF64748B);
		lineY += lineGap;

		int solved = AutoChestUnlocker.getInstance().getChestsUnlocked();
		drawMetricRow(graphics, font, x + 8, lineY, w - 16, "Chests Unlocked", String.valueOf(solved), 0xFFF59E0B);
		lineY += lineGap;

		Route route = RouteHandler.getInstance().getSelectedRoute();
		int wpCount = route != null ? route.getWaypoints().size() : 0;
		drawMetricRow(graphics, font, x + 8, lineY, w - 16, "Route Nodes", String.valueOf(wpCount), 0xFFE2E8F0);
		lineY += lineGap;

		drawMetricRow(graphics, font, x + 8, lineY, w - 16, "Session Uptime", getUptimeFormatted(), 0xFF10B981);
		lineY += lineGap;

		drawMetricRow(graphics, font, x + 8, lineY, w - 16, "Control Center", "Right Shift", 0xFF00E5A8);
	}

	private void renderChestLockpickCard(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
		Font font = Minecraft.getInstance().font;
		graphics.text(font, "LOCKPICK TELEMETRY", x + 8, y + 6, 0xFFF59E0B, false);
		graphics.horizontalLine(x + 6, x + w - 6, y + 17, 0xFF2A3244);

		AutoChestUnlocker unlocker = AutoChestUnlocker.getInstance();
		int lineY = y + 22;
		int lineGap = 15;

		drawMetricRow(graphics, font, x + 8, lineY, w - 16, "State", unlocker.getState().name(), 0xFFF59E0B);
		lineY += lineGap;

		BlockPos cur = unlocker.getCurrentChest();
		String curPos = cur != null ? cur.getX() + ", " + cur.getY() + ", " + cur.getZ() : "None";
		drawMetricRow(graphics, font, x + 8, lineY, w - 16, "Active Chest", curPos, 0xFF38BDF8);
		lineY += lineGap;

		drawMetricRow(graphics, font, x + 8, lineY, w - 16, "Queue Size", String.valueOf(unlocker.getQueuedChests().size()), 0xFFE2E8F0);
		lineY += lineGap;

		drawMetricRow(graphics, font, x + 8, lineY, w - 16, "Session Solved", String.valueOf(unlocker.getChestsUnlocked()), 0xFF00E5A8);
		lineY += lineGap + 6;

		// Explanation tip box
		graphics.fill(x + 6, lineY, x + w - 6, lineY + 44, 0xFF10131A);
		graphics.outline(x + 6, lineY, w - 12, 44, 0xFF2A3244);
		graphics.text(font, "Lockpicking Guide:", x + 10, lineY + 4, 0xFF00E5A8, false);
		graphics.text(font, "Rotates directly onto crit particles", x + 10, lineY + 16, 0xFF94A3B8, false);
		graphics.text(font, "spawning on the front face of chest.", x + 10, lineY + 28, 0xFF94A3B8, false);
	}

	private void renderCombatInfoCard(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
		Font font = Minecraft.getInstance().font;
		graphics.fill(x + 6, y, x + w - 6, y + h, 0xFF10131A);
		graphics.outline(x + 6, y, w - 12, h, 0xFF2A3244);
		graphics.text(font, "COMBAT MECHANICS", x + 10, y + 6, 0xFF00E5A8, false);
		graphics.horizontalLine(x + 8, x + w - 8, y + 17, 0xFF2A3244);
		graphics.text(font, "• Uses native Left-Click attack inputs", x + 10, y + 24, 0xFFCBD5E1, false);
		graphics.text(font, "• Attacks only when raycast confirms target", x + 10, y + 36, 0xFF94A3B8, false);
		graphics.text(font, "• Avoids other players within exclusion zone", x + 10, y + 48, 0xFF94A3B8, false);
	}

	private void renderRouteStats(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
		Font font = Minecraft.getInstance().font;
		graphics.fill(x + 6, y, x + w - 6, y + h, 0xFF10131A);
		graphics.outline(x + 6, y, w - 12, h, 0xFF2A3244);
		graphics.text(font, "ROUTE OVERVIEW", x + 10, y + 6, 0xFF38BDF8, false);
		graphics.horizontalLine(x + 8, x + w - 8, y + 17, 0xFF2A3244);

		RouteHandler rh = RouteHandler.getInstance();
		Route route = rh.getSelectedRoute();
		int lineY = y + 22;
		int lineGap = 14;

		drawMetricRow(graphics, font, x + 10, lineY, w - 20, "Route Name", rh.getSelectedRouteName(), 0xFF00E5A8);
		lineY += lineGap;

		int total = route != null ? route.getWaypoints().size() : 0;
		drawMetricRow(graphics, font, x + 10, lineY, w - 20, "Total Nodes", String.valueOf(total), 0xFFE2E8F0);
		lineY += lineGap;

		int walkNodes = 0;
		int etherNodes = 0;
		if (route != null) {
			for (RouteWaypoint wp : route.getWaypoints()) {
				if (wp.getTransportMethod() == WaypointType.WALK) walkNodes++;
				else if (wp.getTransportMethod() == WaypointType.ETHERWARP) etherNodes++;
			}
		}
		drawMetricRow(graphics, font, x + 10, lineY, w - 20, "Walk Nodes", String.valueOf(walkNodes), 0xFF38BDF8);
	}

	private void renderKeybindInfo(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
		Font font = Minecraft.getInstance().font;
		int lineY = y + 24;
		int lineGap = 15;

		drawMetricRow(graphics, font, x + 8, lineY, w - 16, "Control Center", "Right Shift", 0xFF00E5A8);
		lineY += lineGap;
		drawMetricRow(graphics, font, x + 8, lineY, w - 16, "Toggle Macro", "Grave (~)", 0xFF38BDF8);
		lineY += lineGap;
		drawMetricRow(graphics, font, x + 8, lineY, w - 16, "Route Builder", "Left Bracket ([)", 0xFFE2E8F0);
		lineY += lineGap;
		drawMetricRow(graphics, font, x + 8, lineY, w - 16, "Add Walk Node", "P", 0xFFE2E8F0);
		lineY += lineGap;
		drawMetricRow(graphics, font, x + 8, lineY, w - 16, "Add Etherwarp", "I", 0xFFE2E8F0);
		lineY += lineGap;
		drawMetricRow(graphics, font, x + 8, lineY, w - 16, "Remove Last Node", "O", 0xFFEF4444);
	}

	private void drawMetricRow(GuiGraphicsExtractor graphics, Font font, int x, int y, int w, String label, String value, int valColor) {
		graphics.text(font, label + ":", x, y, 0xFF94A3B8, false);
		int valW = font.width(value);
		graphics.text(font, value, x + w - valW, y, valColor, false);
	}

	@Override
	public void onClose() {
		saveConfigSafely();
		super.onClose();
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == GLFW.GLFW_KEY_ESCAPE || event.key() == GLFW.GLFW_KEY_RIGHT_SHIFT
				|| (BlockMinerClient.getOpenConfigKey() != null && BlockMinerClient.getOpenConfigKey().matches(event))) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	// ----------------------------------------------------
	// REUSABLE MODERN CUSTOM CONTROLS
	// ----------------------------------------------------

	public static class ModernPillToggle extends AbstractWidget {
		private final BooleanSupplier getter;
		private final Consumer<Boolean> setter;

		public ModernPillToggle(int x, int y, int width, int height, String label, String description,
				BooleanSupplier getter, Consumer<Boolean> setter) {
			super(x, y, width, height, Component.literal(label));
			this.getter = getter;
			this.setter = setter;
			if (description != null && !description.isEmpty()) {
				setTooltip(Tooltip.create(Component.literal(description)));
			}
		}

		@Override
		protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
			boolean hovered = isHovered();
			if (hovered) {
				graphics.fill(getX(), getY(), getRight(), getBottom(), 0x1438BDF8);
			}
			Font font = Minecraft.getInstance().font;
			graphics.text(font, getMessage(), getX() + 4, getY() + (getHeight() - 8) / 2, hovered ? 0xFFFFFFFF : 0xFFCBD5E1, false);

			int switchW = 28;
			int switchH = 12;
			int switchX = getRight() - switchW - 4;
			int switchY = getY() + (getHeight() - switchH) / 2;
			boolean active = getter.getAsBoolean();

			if (active) {
				graphics.fill(switchX, switchY, switchX + switchW, switchY + switchH, 0xFF059669);
				graphics.outline(switchX, switchY, switchW, switchH, 0xFF10B981);
				int knobSize = 8;
				int knobX = switchX + switchW - knobSize - 2;
				int knobY = switchY + 2;
				graphics.fill(knobX, knobY, knobX + knobSize, knobY + knobSize, 0xFFFFFFFF);
			} else {
				graphics.fill(switchX, switchY, switchX + switchW, switchY + switchH, 0xFF1E2638);
				graphics.outline(switchX, switchY, switchW, switchH, 0xFF2A3244);
				int knobSize = 8;
				int knobX = switchX + 2;
				int knobY = switchY + 2;
				graphics.fill(knobX, knobY, knobX + knobSize, knobY + knobSize, 0xFF64748B);
			}
		}

		@Override
		public void onClick(@NonNull MouseButtonEvent event, boolean doubleClick) {
			playButtonClickSound(Minecraft.getInstance().getSoundManager());
			setter.accept(!getter.getAsBoolean());
		}

		@Override
		protected void updateWidgetNarration(@NonNull NarrationElementOutput output) {
			defaultButtonNarrationText(output);
		}
	}

	public static class ModernSlider extends AbstractSliderButton {
		private final String label;
		private final double min;
		private final double max;
		private final String unit;
		private final boolean isInteger;
		private final Consumer<Double> onApply;

		public ModernSlider(int x, int y, int width, int height, String label, String tooltip,
				double min, double max, double initialValue, String unit, boolean isInteger,
				Consumer<Double> onApply) {
			super(x, y, width, height, Component.literal(label), (initialValue - min) / (max - min));
			this.label = label;
			this.min = min;
			this.max = max;
			this.unit = unit;
			this.isInteger = isInteger;
			this.onApply = onApply;
			if (tooltip != null && !tooltip.isEmpty()) {
				setTooltip(Tooltip.create(Component.literal(tooltip)));
			}
			updateMessage();
		}

		private double getCurrentValue() {
			return min + value * (max - min);
		}

		private String formatValueText() {
			double val = getCurrentValue();
			if (isInteger) {
				return Math.round(val) + (unit.isEmpty() ? "" : " " + unit);
			} else {
				return String.format(Locale.ROOT, "%.1f", val) + (unit.isEmpty() ? "" : " " + unit);
			}
		}

		@Override
		protected void updateMessage() {
			setMessage(Component.literal(label + ": " + formatValueText()));
		}

		@Override
		protected void applyValue() {
			double actual = getCurrentValue();
			onApply.accept(actual);
		}

		@Override
		public void extractWidgetRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
			int x = getX();
			int y = getY();
			int w = getWidth();
			Font font = Minecraft.getInstance().font;

			// Top row: Label and value badge
			graphics.text(font, label, x + 4, y + 2, isHovered ? 0xFFFFFFFF : 0xFF94A3B8, false);
			String badge = formatValueText();
			int badgeW = font.width(badge);
			graphics.fill(x + w - badgeW - 8, y + 1, x + w - 2, y + 11, 0xFF1E2638);
			graphics.text(font, badge, x + w - badgeW - 5, y + 2, 0xFF38BDF8, false);

			// Track line
			int trackY = y + 13;
			int trackH = 4;
			graphics.fill(x + 4, trackY, x + w - 4, trackY + trackH, 0xFF1E2638);
			graphics.outline(x + 4, trackY, w - 8, trackH, 0xFF2A3244);

			int fillW = (int) Math.round((w - 8) * value);
			if (fillW > 0) {
				graphics.fill(x + 4, trackY, x + 4 + fillW, trackY + trackH, 0xFF00E5A8);
			}

			// Thumb
			int thumbW = 6;
			int thumbH = 8;
			int thumbX = (int) Math.round(x + 4 + (w - 8 - thumbW) * value);
			int thumbY = y + 11;
			graphics.fill(thumbX, thumbY, thumbX + thumbW, thumbY + thumbH, isHovered ? 0xFFFFFFFF : 0xFFE2E8F0);
			graphics.outline(thumbX, thumbY, thumbW, thumbH, 0xFF00E5A8);
		}
	}

	public static class ModernCycleButton extends AbstractWidget {
		private final String label;
		private final String[] options;
		private final IntSupplier getter;
		private final IntConsumer setter;

		public ModernCycleButton(int x, int y, int width, int height, String label, String tooltip,
				String[] options, IntSupplier getter, IntConsumer setter) {
			super(x, y, width, height, Component.literal(label));
			this.label = label;
			this.options = options;
			this.getter = getter;
			this.setter = setter;
			if (tooltip != null && !tooltip.isEmpty()) {
				setTooltip(Tooltip.create(Component.literal(tooltip)));
			}
		}

		@Override
		protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
			boolean hovered = isHovered();
			if (hovered) {
				graphics.fill(getX(), getY(), getRight(), getBottom(), 0x1438BDF8);
			}
			Font font = Minecraft.getInstance().font;
			graphics.text(font, label, getX() + 4, getY() + (getHeight() - 8) / 2, hovered ? 0xFFFFFFFF : 0xFFCBD5E1, false);

			int cur = Math.clamp(getter.getAsInt(), 0, Math.max(0, options.length - 1));
			String valText = options.length > 0 ? options[cur] : "-";
			int badgeW = Math.min(108, font.width(valText) + 14);
			int badgeX = getRight() - badgeW - 4;
			int badgeY = getY() + (getHeight() - 14) / 2;

			graphics.fill(badgeX, badgeY, badgeX + badgeW, badgeY + 14, hovered ? 0xFF2A354C : 0xFF1E2638);
			graphics.outline(badgeX, badgeY, badgeW, 14, hovered ? 0xFF38BDF8 : 0xFF2A3244);

			int textW = font.width(valText);
			int textX = badgeX + (badgeW - textW) / 2;
			graphics.text(font, valText, textX, badgeY + 3, hovered ? 0xFFFFFFFF : 0xFF38BDF8, false);
		}

		@Override
		public void onClick(@NonNull MouseButtonEvent event, boolean doubleClick) {
			playButtonClickSound(Minecraft.getInstance().getSoundManager());
			if (options.length > 0) {
				int next = (getter.getAsInt() + 1) % options.length;
				setter.accept(next);
			}
		}

		@Override
		protected void updateWidgetNarration(@NonNull NarrationElementOutput output) {
			defaultButtonNarrationText(output);
		}
	}

	public static class ModernButton extends AbstractWidget {
		private final Consumer<ModernButton> onPress;
		private final boolean danger;
		private final boolean activeAccent;

		public ModernButton(int x, int y, int width, int height, Component label, String tooltip,
				Consumer<ModernButton> onPress, boolean danger, boolean activeAccent) {
			super(x, y, width, height, label);
			this.onPress = onPress;
			this.danger = danger;
			this.activeAccent = activeAccent;
			if (tooltip != null && !tooltip.isEmpty()) {
				setTooltip(Tooltip.create(Component.literal(tooltip)));
			}
		}

		@Override
		protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
			boolean hovered = isHovered();
			int bg;
			int border;
			int textColor;

			if (danger) {
				bg = hovered ? 0xFF991B1B : 0xFF451A1A;
				border = hovered ? 0xFFF87171 : 0xFFEF4444;
				textColor = 0xFFFFFFFF;
			} else if (activeAccent) {
				bg = hovered ? 0xFF0D9488 : 0xFF065F46;
				border = hovered ? 0xFF34D399 : 0xFF10B981;
				textColor = 0xFFFFFFFF;
			} else {
				bg = hovered ? 0xFF2A354C : 0xFF181D27;
				border = hovered ? 0xFF00E5A8 : 0xFF2A3244;
				textColor = hovered ? 0xFFFFFFFF : 0xFFE2E8F0;
			}

			graphics.fill(getX(), getY(), getRight(), getBottom(), bg);
			graphics.outline(getX(), getY(), getWidth(), getHeight(), border);

			Font font = Minecraft.getInstance().font;
			graphics.centeredText(font, getMessage(), getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2, textColor);
		}

		@Override
		public void onClick(@NonNull MouseButtonEvent event, boolean doubleClick) {
			playButtonClickSound(Minecraft.getInstance().getSoundManager());
			onPress.accept(this);
		}

		@Override
		protected void updateWidgetNarration(@NonNull NarrationElementOutput output) {
			defaultButtonNarrationText(output);
		}
	}

	public static class ModernTabButton extends AbstractWidget {
		private final Tab tab;
		private final BooleanSupplier isCurrent;
		private final Consumer<Tab> onSelect;

		public ModernTabButton(int x, int y, int width, int height, Tab tab, BooleanSupplier isCurrent, Consumer<Tab> onSelect) {
			super(x, y, width, height, Component.literal(tab.label));
			this.tab = tab;
			this.isCurrent = isCurrent;
			this.onSelect = onSelect;
			setTooltip(Tooltip.create(Component.literal(tab.description)));
		}

		@Override
		protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
			boolean active = isCurrent.getAsBoolean();
			boolean hovered = isHovered();

			if (active) {
				graphics.fill(getX(), getY(), getRight(), getBottom(), 0x2400E5A8);
				graphics.fill(getX(), getBottom() - 2, getRight(), getBottom(), 0xFF00E5A8);
			} else if (hovered) {
				graphics.fill(getX(), getY(), getRight(), getBottom(), 0x1438BDF8);
				graphics.fill(getX(), getBottom() - 1, getRight(), getBottom(), 0xFF64748B);
			} else {
				graphics.fill(getX(), getBottom() - 1, getRight(), getBottom(), 0xFF2A3244);
			}

			Font font = Minecraft.getInstance().font;
			int textColor = active ? 0xFF00E5A8 : (hovered ? 0xFFFFFFFF : 0xFF94A3B8);
			graphics.centeredText(font, getMessage(), getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2, textColor);
		}

		@Override
		public void onClick(@NonNull MouseButtonEvent event, boolean doubleClick) {
			playButtonClickSound(Minecraft.getInstance().getSoundManager());
			onSelect.accept(tab);
		}

		@Override
		protected void updateWidgetNarration(@NonNull NarrationElementOutput output) {
			defaultButtonNarrationText(output);
		}
	}
}
