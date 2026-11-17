package com.example.blockminer;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

final class RotationHelper {
	private static float maxStepPerTick = 4.0F;

	private RotationHelper() {
	}

	static float getMaxStepPerTick() {
		return maxStepPerTick;
	}

	static void setMaxStepPerTick(float speed) {
		maxStepPerTick = Mth.clamp(speed, 1.0F, 30.0F);
	}

	static boolean rotateToward(LocalPlayer player, Vec3 point) {
		Vec3 delta = point.subtract(player.getEyePosition());
		double horizontalDistance = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
		float wantedYaw = (float) (Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0);
		float wantedPitch = (float) -Math.toDegrees(Math.atan2(delta.y, horizontalDistance));

		float yawDifference = Mth.wrapDegrees(wantedYaw - player.getYRot());
		float pitchDifference = wantedPitch - player.getXRot();
		boolean aligned = Math.abs(yawDifference) < 3.0F && Math.abs(pitchDifference) < 3.0F;

		player.setYRot(player.getYRot() + Mth.clamp(yawDifference, -maxStepPerTick, maxStepPerTick));
		player.setXRot(Mth.clamp(
				player.getXRot() + Mth.clamp(pitchDifference, -maxStepPerTick, maxStepPerTick),
				-90.0F,
				90.0F
		));

		return aligned;
	}
}
