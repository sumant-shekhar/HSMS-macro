package com.example.blockminer.feature.impl.AutoMobKiller.states;

import com.example.blockminer.RotationConfiguration;
import com.example.blockminer.RotationHandler;
import com.example.blockminer.Target;
import com.example.blockminer.feature.impl.AutoMobKiller.AutoMobKiller;
import com.example.blockminer.pathfinder.PathExecutor;
import com.example.blockminer.util.EntityUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;

public final class KillState implements AutoMobKillerState {
	private boolean rotationStarted;

	@Override
	public void onStart(AutoMobKiller mobKiller) {
		rotationStarted = false;
		PathExecutor.getInstance().stop();
	}

	@Override
	public AutoMobKillerState onTick(AutoMobKiller mobKiller) {
		Minecraft client = Minecraft.getInstance();
		LivingEntity target = mobKiller.getTargetMob();
		if (client.player == null || target == null || !target.isAlive()) {
			return new StartingState();
		}
		RotationHandler rotations = RotationHandler.getInstance();
		if (!rotationStarted) {
			rotations.easeTo(new RotationConfiguration(new Target(target), 400L, null));
			rotationStarted = true;
			return this;
		}
		if (client.hitResult instanceof EntityHitResult hit && hit.getEntity() == target) {
			if (client.gameMode != null) {
				client.gameMode.attack(client.player, target);
			}
			rotations.stop();
			return new StartingState();
		}
		if (!rotations.isEnabled()) {
			if (client.player.distanceToSqr(target) > 16.0D
					|| EntityUtil.getEntityLookingAt().isEmpty()) {
				return new PathfindingState();
			}
			rotations.easeTo(new RotationConfiguration(new Target(target), 250L, null));
		}
		return this;
	}

	@Override
	public void onEnd(AutoMobKiller mobKiller) {
	}
}
