package com.example.blockminer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class BreakingState implements BlockMinerState {
	private static final int FAILSAFE_TICKS = 40;
	private static final long LOOK_AWAY_THRESHOLD_MS = 500L;

	private final Clock lookAwayTimer = new Clock();
	private BlockPos targetPosition;
	private Vec3 targetPoint;
	private net.minecraft.world.level.block.Block targetBlock;
	private long miningTime;
	private int breakAttemptTicks;
	private boolean destroying;

	@Override
	public void onStart(BlockMiner miner) {
		Minecraft client = Minecraft.getInstance();
		ClientLevel level = client.level;
		LocalPlayer player = client.player;
		targetPosition = miner.getTargetBlockPos();
		targetBlock = miner.getTargetBlock();
		if (level == null || player == null || targetPosition == null || targetBlock == null) {
			miner.fail(BlockMiner.Error.NO_TARGET_BLOCKS);
			return;
		}

		BlockState state = level.getBlockState(targetPosition);
		miningTime = BlockUtil.getMiningTime(state, level, targetPosition, miner.getMiningSpeed());
		List<Vec3> points = BlockUtil.bestPointsOnBestSide(level, player, targetPosition);
		if (points.isEmpty()) {
			miner.fail(BlockMiner.Error.NO_POINTS_FOUND);
			return;
		}

		int selected = 0;
		if (Config.randomizedRotations && points.size() > 1 && ThreadLocalRandom.current().nextBoolean()) {
			selected = ThreadLocalRandom.current().nextInt(points.size() / 2, points.size());
		}
		targetPoint = points.get(selected);
		RotationHandler.getInstance().stop();
		RotationHandler.getInstance().easeTo(new RotationConfiguration(
				new Target(targetPoint),
				Config.getRandomRotationTime(),
				null
		));
	}

	@Override
	public BlockMinerState onTick(BlockMiner miner) {
		Minecraft client = Minecraft.getInstance();
		ClientLevel level = client.level;
		LocalPlayer player = client.player;
		if (level == null || player == null || targetPosition == null || targetBlock == null || targetPoint == null) {
			return new StartingState();
		}

		BlockState state = level.getBlockState(targetPosition);
		if (state.isAir() || state.getBlock() != targetBlock) {
			return new StartingState();
		}
		if (player.getEyePosition().distanceTo(targetPoint) > 4.0D) {
			return new StartingState();
		}

		client.options.keyShift.setDown(Config.sneakWhileMining);
		if (RotationHandler.getInstance().isEnabled()) {
			stopDestroying(client.gameMode);
		} else {
			if (client.gameMode != null
					&& client.hitResult instanceof BlockHitResult hit
					&& hit.getType() == HitResult.Type.BLOCK
					&& targetPosition.equals(hit.getBlockPos())) {
				if (!destroying) {
					destroying = client.gameMode.startDestroyBlock(targetPosition, hit.getDirection());
				} else if (!client.gameMode.continueDestroyBlock(targetPosition, hit.getDirection())) {
					stopDestroying(client.gameMode);
				}
			} else {
				stopDestroying(client.gameMode);
			}

			if (!(client.hitResult instanceof BlockHitResult hit)
					|| hit.getType() != HitResult.Type.BLOCK
					|| !targetPosition.equals(hit.getBlockPos())) {
				if (!lookAwayTimer.isScheduled()) {
					lookAwayTimer.schedule(LOOK_AWAY_THRESHOLD_MS);
				} else if (lookAwayTimer.passed()) {
					return new StartingState();
				}
			} else {
				lookAwayTimer.reset();
			}
		}

		if (++breakAttemptTicks > miningTime + FAILSAFE_TICKS) {
			miner.fail(BlockMiner.Error.STUCK);
			return null;
		}
		return this;
	}

	@Override
	public void onEnd(BlockMiner miner) {
		Minecraft client = Minecraft.getInstance();
		client.options.keyShift.setDown(false);
		stopDestroying(client.gameMode);
		RotationHandler.getInstance().stop();
	}

	private void stopDestroying(MultiPlayerGameMode gameMode) {
		if (destroying && gameMode != null) {
			gameMode.stopDestroyBlock();
		}
		destroying = false;
	}
}
