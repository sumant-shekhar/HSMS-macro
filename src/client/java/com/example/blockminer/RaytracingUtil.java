package com.example.blockminer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

public final class RaytracingUtil {
	private RaytracingUtil() {
	}

	public static boolean canSeePoint(Vec3 point) {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		return player != null && client.level != null && canSeePoint(client.level, player, player.getEyePosition(), point);
	}

	public static boolean canSeePoint(Vec3 from, Vec3 point) {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		return player != null && client.level != null && canSeePoint(client.level, player, from, point);
	}

	public static boolean canSeePoint(ClientLevel level, LocalPlayer player, Vec3 from, Vec3 point) {
		HitResult result = raytrace(level, player, from, point);
		if (result.getType() == HitResult.Type.MISS) {
			return true;
		}
		if (!(result instanceof BlockHitResult blockHit)) {
			return false;
		}
		return blockHit.getLocation().distanceToSqr(point) <= 0.01D;
	}

	public static HitResult raytraceTowards(Vec3 from, Vec3 direction, double distance) {
		if (!Double.isFinite(distance) || distance < 0.0D) {
			throw new IllegalArgumentException("Ray distance must be finite and nonnegative");
		}
		Vec3 normalized = direction.subtract(from).normalize();
		return raytrace(from, from.add(normalized.scale(distance)));
	}

	public static HitResult raytrace(Vec3 from, Vec3 to) {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null || client.level == null) {
			throw new IllegalStateException("Ray tracing requires an active client world and player");
		}
		return raytrace(client.level, player, from, to);
	}

	public static HitResult raytrace(ClientLevel level, LocalPlayer player, Vec3 from, Vec3 to) {
		AABB searchBounds = new AABB(from, to).inflate(1.0D);
		EntityHitResult entityHit = findEntityHit(level, player, from, to, searchBounds);
		if (entityHit != null) {
			return entityHit;
		}
		return level.clip(new ClipContext(
				from,
				to,
				ClipContext.Block.COLLIDER,
				ClipContext.Fluid.NONE,
				player
		));
	}

	private static EntityHitResult findEntityHit(
			ClientLevel level,
			LocalPlayer player,
			Vec3 from,
			Vec3 to,
			AABB bounds
	) {
		EntityHitResult closest = null;
		double closestDistance = Double.POSITIVE_INFINITY;
		for (Entity entity : level.getEntities(player, bounds, candidate -> candidate.isAlive() && candidate.isPickable())) {
			Optional<Vec3> intercept = entity.getBoundingBox().inflate(0.5D).clip(from, to);
			if (intercept.isPresent()) {
				double distance = from.distanceToSqr(intercept.get());
				if (distance < closestDistance) {
					closestDistance = distance;
					closest = new EntityHitResult(entity, intercept.get());
				}
			}
		}
		return closest;
	}
}
