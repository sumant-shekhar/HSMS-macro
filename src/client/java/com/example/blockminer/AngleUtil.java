package com.example.blockminer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class AngleUtil {
	private AngleUtil() {
	}

	public static Angle getPlayerAngle() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return new Angle(0.0F, 0.0F);
		}
		return new Angle(get360RotationYaw(player.getYRot()), player.getXRot());
	}

	public static float get360RotationYaw(float yaw) {
		return (yaw % 360.0F + 360.0F) % 360.0F;
	}

	public static float normalizeAngle(float angle) {
		float normalized = angle % 360.0F;
		if (normalized < -180.0F) {
			normalized += 360.0F;
		}
		if (normalized > 180.0F) {
			normalized -= 360.0F;
		}
		return normalized;
	}

	public static float getNeededYawChange(float start, float end) {
		return normalizeAngle(end - start);
	}

	public static Angle getNeededChange(Angle start, Angle end) {
		return new Angle(
				normalizeAngle(normalizeAngle(end.getYaw()) - normalizeAngle(start.getYaw())),
				end.getPitch() - start.getPitch()
		);
	}

	public static Angle getRotation(Vec3 target) {
		LocalPlayer player = requirePlayer();
		return getRotation(player.getEyePosition(), target);
	}

	public static Angle getRotation(BlockPos target) {
		return getRotation(Vec3.atCenterOf(target));
	}

	public static Angle getRotation(Entity target, double additionalY) {
		return getRotation(target.position().add(0.0, additionalY, 0.0));
	}

	public static Angle getRotation(Vec3 from, Vec3 to) {
		double xDiff = to.x - from.x;
		double yDiff = to.y - from.y;
		double zDiff = to.z - from.z;
		double horizontalDistance = Math.sqrt(xDiff * xDiff + zDiff * zDiff);

		float yaw = (float) Math.toDegrees(Math.atan2(zDiff, xDiff)) - 90.0F;
		float pitch = (float) -Math.toDegrees(Math.atan2(yDiff, horizontalDistance));
		return new Angle(yaw, pitch);
	}

	private static LocalPlayer requirePlayer() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			throw new IllegalStateException("A local player is required to calculate a target rotation");
		}
		return player;
	}
}
