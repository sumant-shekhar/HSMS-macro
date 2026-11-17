package com.example.blockminer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public final class PlayerUtil {
	private PlayerUtil() {
	}

	public static BlockPos getBlockStandingOn(LocalPlayer player) {
		return BlockPos.containing(player.getX(), player.getY() - 0.01D, player.getZ());
	}

	public static Vec3 getPlayerEyePos() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			throw new IllegalStateException("A local player is required");
		}
		return player.getEyePosition();
	}
}
