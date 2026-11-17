package com.example.blockminer.util;

import com.example.blockminer.location.LocationTextParser;
import com.example.blockminer.location.LocationTracker;
import com.example.blockminer.BlockUtil;
import com.example.blockminer.PlayerUtil;
import com.example.blockminer.RaytracingUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class EntityUtil {
	private EntityUtil() {
	}

	public static boolean isNpc(Entity entity) {
		if (!(entity instanceof Player player)) {
			return false;
		}
		String name = player.getName().getString();
		return LocationTracker.getGameState().getTabList().stream()
				.map(LocationTextParser::stripFormatting)
				.noneMatch(row -> row.contains(name));
	}

	public static BlockPos getBlockStandingOn(Entity entity) {
		return BlockPos.containing(entity.getX(), entity.getY() - 0.01D, entity.getZ());
	}

	public static Optional<Entity> getEntityLookingAt() {
		Minecraft client = Minecraft.getInstance();
		return client.hitResult instanceof EntityHitResult hit
				? Optional.of(hit.getEntity())
				: Optional.empty();
	}

	public static boolean isStandDead(String name) {
		return getHealthFromStandName(name) == 0;
	}

	public static int getHealthFromStandName(String name) {
		String[] words = name.split(" ");
		if (words.length == 0) {
			return 0;
		}
		String health = words[words.length - 1].split("/", -1)[0].replace(",", "");
		try {
			return Integer.parseInt(health);
		} catch (NumberFormatException exception) {
			return 0;
		}
	}

	public static boolean isEntityHit() {
		return Minecraft.getInstance().hitResult != null
				&& Minecraft.getInstance().hitResult.getType() == HitResult.Type.ENTITY;
	}

	public static Entity getEntityCuttingOtherEntity(Entity entity, Class<?> entityType) {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null) {
			return null;
		}
		AABB bounds = entity.getBoundingBox().inflate(0.3D, 2.0D, 0.3D);
		return client.level.getEntities(entity, bounds, candidate ->
						candidate.isAlive()
								&& candidate != client.player
								&& !(candidate instanceof ArmorStand)
								&& !(candidate instanceof net.minecraft.world.entity.projectile.FishingHook)
								&& (entityType == null || entityType.isInstance(candidate)))
				.stream()
				.min(Comparator.comparingDouble(candidate -> candidate.distanceToSqr(entity)))
				.orElse(null);
	}

	public static List<LivingEntity> getEntities(Set<String> entityNames, Set<LivingEntity> entitiesToIgnore) {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null || client.level == null) {
			return List.of();
		}
		AABB searchArea = player.getBoundingBox().inflate(32.0D);
		List<LivingEntity> entities = new ArrayList<>();
		for (Entity entity : client.level.getEntities(player, searchArea, candidate -> candidate instanceof ArmorStand)) {
			if (!(entity instanceof ArmorStand stand) || stand.getCustomName() == null) {
				continue;
			}
			String name = stand.getCustomName().getString();
			if (name.contains(player.getName().getString())
					|| entityNames.stream().noneMatch(name::contains)) {
				continue;
			}
			Entity mob = getEntityCuttingOtherEntity(stand, null);
			if (mob instanceof LivingEntity living && living.isAlive()
					&& !entitiesToIgnore.contains(living)
					&& EntityUtil.getHealthFromStandName(name) != 1) {
				entities.add(living);
			}
		}
		Vec3 playerPosition = player.position();
		float playerYaw = player.getYRot();
		entities.sort(Comparator.comparingDouble(entity -> {
			double distanceCost = playerPosition.distanceTo(entity.position());
			float targetYaw = com.example.blockminer.AngleUtil.getRotation(entity.position()).getYaw();
			double rotationCost = Math.abs(com.example.blockminer.AngleUtil.getNeededYawChange(playerYaw, targetYaw));
			return distanceCost * 0.6D + rotationCost * 0.1D;
		}));
		return List.copyOf(entities);
	}

	public static BlockPos nearbyBlock(LivingEntity entity) {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null || client.level == null) {
			return getBlockStandingOn(entity);
		}
		BlockPos entityPosition = BlockPos.containing(entity.getX(), entity.getY(), entity.getZ());
		BlockPos playerPosition = PlayerUtil.getBlockStandingOn(player);
		BlockPos closest = null;
		double closestDistance = Double.POSITIVE_INFINITY;
		for (int x = -3; x <= 3; x++) {
			for (int y = -3; y <= 3; y++) {
				for (int z = -3; z <= 3; z++) {
					BlockPos candidate = entityPosition.offset(x, y, z);
					Vec3 point = Vec3.atCenterOf(candidate);
					if (BlockUtil.canStandAt(client.level, candidate)
							&& RaytracingUtil.canSeePoint(client.level, player, player.getEyePosition(), point)) {
						double distance = candidate.distToCenterSqr(Vec3.atCenterOf(playerPosition));
						if (distance < closestDistance) {
							closest = candidate;
							closestDistance = distance;
						}
					}
				}
			}
		}
		return closest == null ? getBlockStandingOn(entity) : closest;
	}
}
