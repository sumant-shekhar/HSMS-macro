package com.example.blockminer.feature.impl.AutoMobKiller.states;

import com.example.blockminer.Config;
import com.example.blockminer.feature.impl.AutoMobKiller.AutoMobKiller;
import com.example.blockminer.pathfinder.PathExecutor;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;

public final class PathfindingState implements AutoMobKillerState {
	private long startedAt;
	private long lastPathRequestAt;
	private int pathAttempts;

	@Override
	public void onStart(AutoMobKiller mobKiller) {
		startedAt = System.nanoTime();
		pathAttempts = 0;
		lastPathRequestAt = startedAt;
		requestPath(mobKiller);
	}

	@Override
	public AutoMobKillerState onTick(AutoMobKiller mobKiller) {
		LivingEntity target = mobKiller.getTargetMob();
		Minecraft client = Minecraft.getInstance();
		if (target == null || !target.isAlive()) {
			return new StartingState();
		}
		if (client.player != null
				&& client.player.distanceToSqr(target) <= Config.mobKillerAttackDistanceSquared) {
			return new KillState();
		}
		if (System.nanoTime() - startedAt >= Config.mobKillerPathTimeoutMs * 1_000_000L) {
			log("Pathfinding timed out; blacklisting the target.");
			mobKiller.getBlacklistedMobs().add(target);
			return new StartingState();
		}

		if (mobKiller.getTargetMobOriginalPos() != null
				&& target.position().distanceToSqr(mobKiller.getTargetMobOriginalPos()) > 9.0D) {
			if (++pathAttempts > Config.mobKillerMaximumRepaths) {
				mobKiller.getBlacklistedMobs().add(target);
				return new StartingState();
			}
			mobKiller.setTargetMobOriginalPos(target.position());
			PathExecutor.getInstance().stop();
			requestPath(mobKiller);
			return this;
		}

		if (!PathExecutor.getInstance().isRunning() && !mobKiller.isPathSearchPending()
				&& client.player != null && client.player.distanceToSqr(target) > 16.0D
				&& System.nanoTime() - lastPathRequestAt > 1_000_000_000L) {
			if (++pathAttempts > Config.mobKillerMaximumRepaths) {
				mobKiller.getBlacklistedMobs().add(target);
				return new StartingState();
			}
			PathExecutor.getInstance().stop();
			requestPath(mobKiller);
		}
		return this;
	}

	@Override
	public void onEnd(AutoMobKiller mobKiller) {
		PathExecutor.getInstance().stop();
	}

	private void requestPath(AutoMobKiller mobKiller) {
		lastPathRequestAt = System.nanoTime();
		if (!mobKiller.requestPathToTarget()) {
			log("Unable to start a path search; choosing another target.");
		}
	}
}
