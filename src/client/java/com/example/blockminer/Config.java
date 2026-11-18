package com.example.blockminer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public final class Config {
	private static final Logger LOGGER = LoggerFactory.getLogger("blockminer/config");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	public static double miningCoefficient = 200.0D;
	public static double angleCoefficient = 1.0D;
	public static double distanceCoefficient = 10.0D;
	public static int tickGlideOffset = 0;
	public static boolean sneakWhileMining = false;
	// TODO(verify): precision crit-particle aiming needs a supported particle observation hook.
	public static boolean precisionMiner = false;
	public static boolean miscFullBlock = false;
	public static boolean randomizedRotations = true;
	public static int waitThresholdMs = 2_000;
	public static int defaultMiningSpeed = 600;
	public static long minimumRotationTimeMs = 600L;
	public static long maximumRotationTimeMs = 900L;
	public static double mobKillerSearchDistanceSquared = 1_000.0D;
	public static double mobKillerPlayerExclusionDistanceSquared = 9.0D;
	public static double mobKillerAttackDistanceSquared = 8.0D;
	public static int mobKillerMaximumRepaths = 3;
	public static long mobKillerPathTimeoutMs = 10_000L;
	public static int macroType = 2;
	public static int openConfigKey = GLFW.GLFW_KEY_RIGHT_SHIFT;
	public static int toggleMacro = GLFW.GLFW_KEY_GRAVE_ACCENT;
	public static String miningTool = "";
	public static boolean usePickaxeAbility = true;
	public static int oreRespawnWaitThreshold = 5;
	public static boolean drillRefuel = false;
	public static int refuelMachineFuel = 1;
	public static boolean ungrabMouse = false;
	public static int commClaimMethod = 0;
	public static boolean prioritiseTitanium = false;
	public static String altMiningTool = "";
	public static boolean commSwapBeforeClaiming = false;
	public static String slayerWeapon = "";
	public static boolean mobKillerSprint = true;
	public static boolean mobKillerInterpolate = true;
	public static boolean forgePathing = true;
	public static boolean showDwarvenCommHUDOutside = true;
	public static boolean commHudResetStats = false;
	public static int coldThreshold = 100;
	public static boolean glacialHudResetStats = false;
	public static boolean showGlacialHUDOutside = true;
	public static int oreType = 0;
	public static boolean mineGrayMithril = true;
	public static boolean mineGreenMithril = true;
	public static boolean mineBlueMithril = true;
	public static boolean mineTitanium = true;
	public static String selectedRoute = "";
	public static boolean routeMineGemstone = false;
	public static boolean routeMineTopaz = false;
	public static boolean routeMineOre = false;
	public static boolean routeMineGlacite = false;
	public static boolean routeMineUmber = false;
	public static boolean routeMineTungsten = false;
	public static int routeBuilder = GLFW.GLFW_KEY_LEFT_BRACKET;
	public static int routeBuilderWalkAddKeybind = GLFW.GLFW_KEY_P;
	public static int routeBuilderEtherwarpAddKeybind = GLFW.GLFW_KEY_I;
	public static int routeBuilderRemoveKeybind = GLFW.GLFW_KEY_O;
	public static int routeBuilderNodeColor = 0x6400ffff;
	public static int routeBuilderTracerColor = 0x6400ffff;
	public static int routeBuilderUnidi = GLFW.GLFW_KEY_KP_7;
	public static int routeBuilderBidi = GLFW.GLFW_KEY_KP_8;
	public static int routeBuilderSelect = GLFW.GLFW_KEY_KP_4;
	public static int routeBuilderMove = GLFW.GLFW_KEY_KP_5;
	public static int routeBuilderDelete = GLFW.GLFW_KEY_KP_6;
	public static boolean greatExplorerMaxed = false;
	public static int gemstoneCenterPitch = 10;
	public static int gemstoneYawRange = 45;
	public static int gemstonePitchRange = 15;
	public static float gemstoneOvalSpeed = 2.0F;
	public static int rotationTime = 600;
	public static int rotationTimeRandomizer = 300;
	public static int sneakTime = 500;
	public static int sneakTimeRandomizer = 300;
	public static int delaysGuiDelay = 450;
	public static int delaysGuiDelayRandomizer = 250;
	public static int delayAutoAotvLookDelay = 250;
	public static int delayAutoAotvEtherwarpLookDelay = 500;
	public static int delayAutoAotvServerRotation = 500;
	public static boolean debugMode = false;
	public static int devMKillDist = 100;
	public static int devMKillRot = 5;
	public static String devMKillerMob = "Goblin";
	public static String devMKillerWeapon = "Aspect of the Dragon";
	public static int devMKillTimer = 1_000;
	public static int cost4 = 1;
	public static boolean fixrot = false;
	public static int rottime = 500;
	public static float rotmult = 2.0F;
	public static double lagThresholdSeconds = 1.3D;

	private Config() {
	}

	public static void load() throws IOException {
		Path path = configPath();
		if (!Files.exists(path)) {
			save();
			return;
		}

		JsonElement root = JsonParser.parseString(Files.readString(path));
		if (!root.isJsonObject()) {
			throw new IllegalArgumentException("Config root must be a JSON object: " + path);
		}
		applyValues(decodeValues(root.getAsJsonObject()));
	}

	public static void save() throws IOException {
		Path path = configPath();
		Files.createDirectories(path.getParent());
		Files.writeString(path, GSON.toJson(snapshotValues()));
	}

	public static boolean jsonRoundTripSelfTest() {
		JsonObject before = snapshotValues();
		JsonObject parsed = JsonParser.parseString(GSON.toJson(before)).getAsJsonObject();
		Map<String, Object> values = decodeValues(parsed);
		try {
			applyValues(values);
			return snapshotValues().equals(parsed);
		} finally {
			applyValues(decodeValues(before));
		}
	}

	public static long getRandomRotationTime() {
		if (minimumRotationTimeMs < 0L || maximumRotationTimeMs < minimumRotationTimeMs
				|| maximumRotationTimeMs == Long.MAX_VALUE) {
			throw new IllegalStateException("Rotation time range must be nonnegative and ordered");
		}
		return ThreadLocalRandom.current().nextLong(minimumRotationTimeMs, maximumRotationTimeMs + 1L);
	}

	public static void setRotationTime(int milliseconds) {
		if (milliseconds < 0) {
			throw new IllegalArgumentException("Rotation time must be nonnegative");
		}
		rotationTime = milliseconds;
		syncRotationRange();
	}

	public static void setRotationTimeRandomizer(int milliseconds) {
		if (milliseconds < 0) {
			throw new IllegalArgumentException("Rotation randomizer must be nonnegative");
		}
		rotationTimeRandomizer = milliseconds;
		syncRotationRange();
	}

	public static int getRandomSneakTime() {
		return randomizeDelay(sneakTime, sneakTimeRandomizer);
	}

	public static int getRandomGuiWaitDelay() {
		return randomizeDelay(delaysGuiDelay, delaysGuiDelayRandomizer);
	}

	private static int randomizeDelay(int base, int randomizer) {
		if (base < 0 || randomizer < 0 || randomizer == Integer.MAX_VALUE
				|| base > Integer.MAX_VALUE - randomizer) {
			throw new IllegalStateException("Delay values must be nonnegative");
		}
		return base + ThreadLocalRandom.current().nextInt(randomizer + 1);
	}

	private static Path configPath() {
		return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("blockminer.json");
	}

	private static JsonObject snapshotValues() {
		JsonObject json = new JsonObject();
		for (Field field : configFields()) {
			try {
				json.add(field.getName(), GSON.toJsonTree(field.get(null), field.getGenericType()));
			} catch (IllegalAccessException exception) {
				throw new IllegalStateException("Cannot read config field " + field.getName(), exception);
			}
		}
		return json;
	}

	private static Map<String, Object> decodeValues(JsonObject json) {
		Map<String, Field> fields = new LinkedHashMap<>();
		for (Field field : configFields()) {
			fields.put(field.getName(), field);
		}

		Map<String, Object> values = new LinkedHashMap<>();
		for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
			Field field = fields.get(entry.getKey());
			if (field == null) {
				LOGGER.warn("Ignoring unknown config property '{}'.", entry.getKey());
				continue;
			}
			if (entry.getValue().isJsonNull()) {
				throw new IllegalArgumentException("Config property cannot be null: " + entry.getKey());
			}
			Object value = GSON.fromJson(entry.getValue(), field.getGenericType());
			validateValue(field, value);
			values.put(field.getName(), value);
		}
		validateRanges(values);
		return values;
	}

	private static void validateValue(Field field, Object value) {
		if (value == null || !field.getType().isInstance(value)
				&& !(field.getType().isPrimitive() && isPrimitiveWrapper(field.getType(), value))) {
			throw new IllegalArgumentException("Invalid value for config property: " + field.getName());
		}
		if (value instanceof Number number) {
			if (!Double.isFinite(number.doubleValue())) {
				throw new IllegalArgumentException("Config numeric values must be finite: " + field.getName());
			}
			if (isKeybindField(field)) {
				if (number.intValue() < -1) {
					throw new IllegalArgumentException("Config keybind must be valid or -1: " + field.getName());
				}
			} else if (number.doubleValue() < 0.0D) {
				throw new IllegalArgumentException("Config numeric values must be nonnegative: " + field.getName());
			}
		}
		if (value instanceof String text && text.length() > 512) {
			throw new IllegalArgumentException("Config text is too long: " + field.getName());
		}
	}

	private static boolean isKeybindField(Field field) {
		String name = field.getName();
		return name.equals("toggleMacro") || (name.startsWith("routeBuilder") && field.getType() == int.class && !name.endsWith("Color"));
	}

	private static boolean isPrimitiveWrapper(Class<?> primitive, Object value) {
		return primitive == boolean.class && value instanceof Boolean
				|| primitive == int.class && value instanceof Integer
				|| primitive == long.class && value instanceof Long
				|| primitive == float.class && value instanceof Float
				|| primitive == double.class && value instanceof Double;
	}

	private static void validateRanges(Map<String, Object> values) {
		requireRange(values, "macroType", 0, 3);
		requireRange(values, "oreType", 0, 7);
		requireRange(values, "commClaimMethod", 0, 1);
		requireRange(values, "refuelMachineFuel", 0, 1);
		requireRange(values, "coldThreshold", 1, 100);
		long minRotation = ((Number) valueOrCurrent(values, "minimumRotationTimeMs")).longValue();
		long maxRotation = ((Number) valueOrCurrent(values, "maximumRotationTimeMs")).longValue();
		if (maxRotation < minRotation || maxRotation == Long.MAX_VALUE) {
			throw new IllegalArgumentException("Rotation time range must be ordered and finite");
		}
	}

	private static void requireRange(Map<String, Object> values, String name, int min, int max) {
		int value = ((Number) valueOrCurrent(values, name)).intValue();
		if (value < min || value > max) {
			throw new IllegalArgumentException(name + " must be between " + min + " and " + max);
		}
	}

	private static Object valueOrCurrent(Map<String, Object> values, String name) {
		if (values.containsKey(name)) {
			return values.get(name);
		}
		try {
			return Config.class.getField(name).get(null);
		} catch (NoSuchFieldException | IllegalAccessException exception) {
			throw new IllegalStateException("Missing config field " + name, exception);
		}
	}

	private static void applyValues(Map<String, Object> values) {
		Map<String, Field> fields = new LinkedHashMap<>();
		for (Field field : configFields()) {
			fields.put(field.getName(), field);
		}
		for (Map.Entry<String, Object> entry : values.entrySet()) {
			Field field = fields.get(entry.getKey());
			if (field == null) {
				throw new IllegalStateException("Unknown staged config property: " + entry.getKey());
			}
			try {
				field.set(null, entry.getValue());
			} catch (IllegalAccessException | IllegalArgumentException exception) {
				throw new IllegalStateException("Cannot apply config property " + entry.getKey(), exception);
			}
		}
		syncRotationRange();
	}

	private static void syncRotationRange() {
		minimumRotationTimeMs = rotationTime;
		maximumRotationTimeMs = (long) rotationTime + rotationTimeRandomizer;
	}

	private static ArrayList<Field> configFields() {
		ArrayList<Field> fields = new ArrayList<>();
		for (Field field : Config.class.getDeclaredFields()) {
			int modifiers = field.getModifiers();
			Class<?> type = field.getType();
			if (Modifier.isPublic(modifiers) && Modifier.isStatic(modifiers) && !Modifier.isFinal(modifiers)
					&& (type.isPrimitive() || type == String.class)) {
				fields.add(field);
			}
		}
		fields.sort(Comparator.comparing(Field::getName));
		return fields;
	}
}
