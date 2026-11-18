package com.example.blockminer;

import com.example.blockminer.mixin.accessor.KeyMappingAccessor;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class KeyBindUtil {
	private static final Minecraft CLIENT = Minecraft.getInstance();
	private static final KeyMapping[] ALL_KEYS = {
			CLIENT.options.keyAttack,
			CLIENT.options.keyUse,
			CLIENT.options.keyDown,
			CLIENT.options.keyUp,
			CLIENT.options.keyLeft,
			CLIENT.options.keyRight,
			CLIENT.options.keyJump,
			CLIENT.options.keyShift,
			CLIENT.options.keySprint
	};
	private static final Map<KeyMapping, Long> TIMED_RELEASES = new IdentityHashMap<>();

	private KeyBindUtil() {
	}

	public static void setKeyBindState(KeyMapping key, boolean pressed) {
		if (key == null) {
			return;
		}
		InputConstants.Key boundKey = (key instanceof KeyMappingAccessor accessor)
				? accessor.blockminer$getKey()
				: null;
		if (boundKey == null) {
			boundKey = key.getDefaultKey();
		}

		if (pressed && CLIENT.gui.screen() != null) {
			KeyMapping.set(boundKey, false);
			key.setDown(false);
			if (key instanceof KeyMappingAccessor accessor) {
				accessor.blockminer$setClickCount(0);
			}
			TIMED_RELEASES.remove(key);
			return;
		}

		if (pressed) {
			if (!key.isDown()) {
				KeyMapping.click(boundKey);
				if (key instanceof KeyMappingAccessor accessor && accessor.blockminer$getClickCount() == 0) {
					accessor.blockminer$setClickCount(1);
				}
			}
			KeyMapping.set(boundKey, true);
			key.setDown(true);
		} else {
			KeyMapping.set(boundKey, false);
			key.setDown(false);
			if (key instanceof KeyMappingAccessor accessor) {
				accessor.blockminer$setClickCount(0);
			}
			TIMED_RELEASES.remove(key);
		}
	}

	public static void pressFor(KeyMapping key, long durationMs) {
		if (durationMs <= 0L) {
			throw new IllegalArgumentException("Key press duration must be positive");
		}
		setKeyBindState(key, true);
		TIMED_RELEASES.put(key, System.nanoTime() + durationMs * 1_000_000L);
	}

	public static void rightClick() {
		if (CLIENT.options != null && CLIENT.options.keyUse != null) {
			pressFor(CLIENT.options.keyUse, 100L);
		}
	}

	public static void leftClick() {
		if (CLIENT.options != null && CLIENT.options.keyAttack != null) {
			pressFor(CLIENT.options.keyAttack, 80L);
		}
	}

	public static void releaseAll() {
		stopMovement(false);
		for (KeyMapping key : ALL_KEYS) {
			setKeyBindState(key, false);
		}
		TIMED_RELEASES.clear();
		KeyMapping.releaseAll();
	}

	public static void tick() {
		if (CLIENT.gui.screen() != null) {
			if (!areAllKeybindsReleased() || !TIMED_RELEASES.isEmpty()) {
				stopMovement();
			}
			TIMED_RELEASES.clear();
			return;
		}
		long now = System.nanoTime();
		TIMED_RELEASES.entrySet().removeIf(entry -> {
			if (now >= entry.getValue()) {
				setKeyBindState(entry.getKey(), false);
				return true;
			}
			return false;
		});
	}

	public static void stopMovement() {
		stopMovement(false);
	}

	public static void stopMovement(boolean ignoreAttack) {
		setKeyBindState(CLIENT.options.keyUp, false);
		setKeyBindState(CLIENT.options.keyDown, false);
		setKeyBindState(CLIENT.options.keyRight, false);
		setKeyBindState(CLIENT.options.keyLeft, false);
		if (!ignoreAttack) {
			setKeyBindState(CLIENT.options.keyAttack, false);
			setKeyBindState(CLIENT.options.keyUse, false);
		}
		setKeyBindState(CLIENT.options.keyShift, false);
		setKeyBindState(CLIENT.options.keyJump, false);
		setKeyBindState(CLIENT.options.keySprint, false);
	}

	public static void holdThese(boolean withAttack, KeyMapping... keys) {
		releaseAllExcept(keys);
		for (KeyMapping key : keys) {
			setKeyBindState(key, true);
		}
		if (withAttack) {
			setKeyBindState(CLIENT.options.keyAttack, true);
		}
	}

	public static void holdThese(KeyMapping... keys) {
		holdThese(false, keys);
	}

	public static void releaseAllExcept(KeyMapping... keys) {
		for (KeyMapping key : ALL_KEYS) {
			if (!contains(keys, key)) {
				setKeyBindState(key, false);
			}
		}
	}

	public static boolean contains(KeyMapping[] keys, KeyMapping key) {
		for (KeyMapping candidate : keys) {
			if (candidate == key) {
				return true;
			}
		}
		return false;
	}

	public static boolean areAllKeybindsReleased() {
		for (KeyMapping key : ALL_KEYS) {
			if (key.isDown()) {
				return false;
			}
		}
		return true;
	}

	public static KeyMapping[] getHeldKeybinds() {
		List<KeyMapping> held = new ArrayList<>();
		for (KeyMapping key : ALL_KEYS) {
			if (key.isDown()) {
				held.add(key);
			}
		}
		return held.toArray(KeyMapping[]::new);
	}

	public static List<KeyMapping> getNeededKeyPresses(Vec3 origin, Vec3 destination) {
		double deltaX = origin.x - destination.x;
		double deltaZ = origin.z - destination.z;
		float requiredAngle = (float) Math.toDegrees(Math.atan2(deltaX, -deltaZ));
		return getNeededKeyPresses(requiredAngle);
	}

	public static List<KeyMapping> getNeededKeyPresses(float neededYaw) {
		List<KeyMapping> keys = new ArrayList<>();
		if (CLIENT.player == null) {
			return keys;
		}
		float relativeYaw = AngleUtil.normalizeAngle(neededYaw - CLIENT.player.getYRot()) * -1.0F;
		addIfInRange(keys, CLIENT.options.keyUp, 0.0F, relativeYaw);
		addIfInRange(keys, CLIENT.options.keyLeft, 90.0F, relativeYaw);
		addIfInRange(keys, CLIENT.options.keyDown, 180.0F, relativeYaw);
		addIfInRange(keys, CLIENT.options.keyRight, -90.0F, relativeYaw);
		return keys;
	}

	public static List<KeyMapping> getOppositeKeys(List<KeyMapping> keys) {
		List<KeyMapping> opposites = new ArrayList<>();
		for (KeyMapping key : keys) {
			if (key == CLIENT.options.keyUp) {
				opposites.add(CLIENT.options.keyDown);
			} else if (key == CLIENT.options.keyDown) {
				opposites.add(CLIENT.options.keyUp);
			} else if (key == CLIENT.options.keyLeft) {
				opposites.add(CLIENT.options.keyRight);
			} else if (key == CLIENT.options.keyRight) {
				opposites.add(CLIENT.options.keyLeft);
			}
		}
		return opposites;
	}

	public static List<KeyMapping> getKeyPressesToDecelerate(Vec3 origin, Vec3 destination) {
		return getOppositeKeys(getNeededKeyPresses(origin, destination));
	}

	public static KeyMapping getDebugKey(String name) {
		return switch (name.toLowerCase(java.util.Locale.ROOT)) {
			case "w" -> CLIENT.options.keyUp;
			case "a" -> CLIENT.options.keyLeft;
			case "s" -> CLIENT.options.keyDown;
			case "d" -> CLIENT.options.keyRight;
			case "jump" -> CLIENT.options.keyJump;
			default -> null;
		};
	}

	private static void addIfInRange(List<KeyMapping> keys, KeyMapping key, float direction, float relativeYaw) {
		if (Math.abs(direction - relativeYaw) < 67.5F
				|| Math.abs(direction - (relativeYaw + 360.0F)) < 67.5F) {
			keys.add(key);
		}
	}
}
