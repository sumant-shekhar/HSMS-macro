package com.example.blockminer.feature.impl.AutoMobKiller.states;

import com.example.blockminer.Config;
import com.example.blockminer.BlockUtil;
import com.example.blockminer.feature.impl.AutoMobKiller.AutoMobKiller;
import com.example.blockminer.util.EntityUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;

public final class FindMobState implements AutoMobKillerState {
	private long noMobSince;

	@Override
	public void onStart(AutoMobKiller mobKiller) {
		noMobSince = System.nanoTime();
		log("Looking for a target mob.");
	}

	@Override
	public AutoMobKillerState onTick(AutoMobKiller mobKiller) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.level == null) {
			return null;
		}

		LivingEntity target = EntityUtil.getEntities(
						mobKiller.getMobsToKill(), mobKiller.getBlacklistedMobs())
				.stream()
				.filter(mob -> client.player.distanceToSqr(mob) < Config.mobKillerSearchDistanceSquared)
				.filter(mob -> BlockUtil.canStandAt(client.level, EntityUtil.getBlockStandingOn(mob)))
				.filter(mob -> !isNearAnotherPlayer(client, mob))
				.min(Comparator.comparingDouble(client.player::distanceToSqr))
				.orElse(null);
		if (target == null) {
			if (System.nanoTime() - noMobSince >= Config.mobKillerPathTimeoutMs * 1_000_000L) {
				logError("No eligible target mob appeared before the search timed out.");
				mobKiller.setError(AutoMobKiller.MKError.NO_ENTITIES);
				return null;
			}
			return this;
		}

		mobKiller.setTargetMob(target);
		mobKiller.setTargetMobOriginalPos(target.position());
		return new PathfindingState();
	}

	@Override
	public void onEnd(AutoMobKiller mobKiller) {
	}

	private static boolean isNearAnotherPlayer(Minecraft client, LivingEntity mob) {
		if (client.player == null || client.level == null) {
			return true;
		}
		AABB area = mob.getBoundingBox().inflate(3.0D);
		return client.level.getEntities(client.player, area, candidate -> candidate instanceof Player)
				.stream()
				.filter(Player.class::isInstance)
				.map(Player.class::cast)
				.filter(player -> player != client.player && !EntityUtil.isNpc(player))
				.anyMatch(player -> player.distanceToSqr(mob) < Config.mobKillerPlayerExclusionDistanceSquared);
	}
}
