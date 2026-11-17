package com.example.blockminer.feature.impl.AutoGetStats;

import com.example.blockminer.feature.AbstractFeature;
import com.example.blockminer.feature.impl.AutoGetStats.tasks.AbstractInventoryTask;
import com.example.blockminer.feature.impl.AutoGetStats.tasks.TaskStatus;
import com.example.blockminer.feature.impl.AutoGetStats.tasks.impl.MiningBoostRetrievalTask;
import com.example.blockminer.feature.impl.AutoGetStats.tasks.impl.MiningSpeedRetrievalTask;
import com.example.blockminer.feature.impl.AutoGetStats.tasks.impl.PickaxeAbilityRetrievalTask;
import java.util.ArrayDeque;
import java.util.Queue;

public final class AutoGetStats extends AbstractFeature {
	private static final AutoGetStats INSTANCE = new AutoGetStats();
	private final Queue<AbstractInventoryTask<?>> taskQueue = new ArrayDeque<>();
	private AbstractInventoryTask<?> currentTask;
	private MiningSpeedRetrievalTask miningSpeedTask;
	private MiningBoostRetrievalTask miningBoostTask;
	private PickaxeAbilityRetrievalTask pickaxeAbilityTask;

	private AutoGetStats() {
	}

	public static AutoGetStats getInstance() {
		return INSTANCE;
	}

	@Override
	public String getName() {
		return "AutoGetStats";
	}

	public void startDefaultTasks() {
		stop();
		miningSpeedTask = new MiningSpeedRetrievalTask();
		miningBoostTask = new MiningBoostRetrievalTask();
		pickaxeAbilityTask = new PickaxeAbilityRetrievalTask();
		startTask(miningSpeedTask);
		startTask(miningBoostTask);
		startTask(pickaxeAbilityTask);
	}

	public void startTask(AbstractInventoryTask<?> task) {
		if (task == null) {
			throw new IllegalArgumentException("Inventory task cannot be null");
		}
		taskQueue.add(task);
		enabled = true;
		if (currentTask == null && !timer.isScheduled()) {
			startNextTask();
		}
	}

	public boolean hasFinishedAllTasks() {
		return currentTask == null && taskQueue.isEmpty();
	}

	public Integer getMiningSpeed() {
		return miningSpeedTask == null ? null : miningSpeedTask.getResult();
	}

	public Integer getMiningBoost() {
		return miningBoostTask == null ? null : miningBoostTask.getResult();
	}

	public PickaxeAbility getPickaxeAbility() {
		return pickaxeAbilityTask == null ? PickaxeAbility.NONE : pickaxeAbilityTask.getResult();
	}

	@Override
	public void stop() {
		if (currentTask != null) {
			currentTask.end();
			currentTask = null;
		}
		taskQueue.clear();
		timer.reset();
		enabled = false;
	}

	@Override
	protected void onTick(net.minecraft.client.Minecraft client) {
		if (currentTask == null) {
			if (timer.isScheduled() && !timer.passed()) {
				return;
			}
			if (taskQueue.isEmpty()) {
				enabled = false;
				return;
			}
			startNextTask();
			return;
		}

		currentTask.onTick();
		TaskStatus status = currentTask.getTaskStatus();
		if (status.isFailure() || status.isSuccessful()) {
			if (status.isFailure()) {
				com.example.blockminer.util.Logger.sendError(
						"AutoGetStats task failed: " + currentTask.getError());
			} else {
				com.example.blockminer.util.Logger.sendLog(
						"AutoGetStats retrieved: " + currentTask.getResult());
			}
			currentTask.end();
			currentTask = null;
			timer.schedule(1000L);
		}
	}

	private void startNextTask() {
		currentTask = taskQueue.poll();
		if (currentTask != null) {
			currentTask.init();
		}
	}

	public enum PickaxeAbility {
		NONE,
		PICKOBULUS,
		MINING_SPEED_BOOST
	}
}
