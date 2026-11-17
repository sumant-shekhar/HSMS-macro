package com.example.blockminer.macro.impl.CommissionMacro.states;

import com.example.blockminer.Config;
import com.example.blockminer.feature.impl.AutoGetStats.AutoGetStats;
import com.example.blockminer.feature.impl.AutoGetStats.tasks.impl.MiningSpeedRetrievalTask;
import com.example.blockminer.feature.impl.AutoGetStats.tasks.impl.PickaxeAbilityRetrievalTask;
import com.example.blockminer.macro.impl.CommissionMacro.CommissionMacro;

public final class GettingStatsState implements CommissionMacroState {
	private MiningSpeedRetrievalTask miningSpeedTask;
	private PickaxeAbilityRetrievalTask pickaxeAbilityTask;

	@Override
	public void onStart(CommissionMacro macro) {
		AutoGetStats.getInstance().stop();
		miningSpeedTask = new MiningSpeedRetrievalTask();
		pickaxeAbilityTask = new PickaxeAbilityRetrievalTask();
		AutoGetStats.getInstance().startTask(miningSpeedTask);
		AutoGetStats.getInstance().startTask(pickaxeAbilityTask);
	}

	@Override
	public CommissionMacroState onTick(CommissionMacro macro) {
		if (!AutoGetStats.getInstance().hasFinishedAllTasks()) {
			return this;
		}
		if (miningSpeedTask.getError() != null || pickaxeAbilityTask.getError() != null) {
			macro.disable("Could not retrieve mining stats: "
					+ (miningSpeedTask.getError() != null
					? miningSpeedTask.getError()
					: pickaxeAbilityTask.getError()));
			return this;
		}
		Integer speed = miningSpeedTask.getResult();
		if (speed == null || speed <= 0) {
			macro.disable("Mining speed retrieval returned no positive value.");
			return this;
		}
		macro.setMiningSpeed(speed);
		macro.setPickaxeAbility(Config.usePickaxeAbility
				? pickaxeAbilityTask.getResult()
				: AutoGetStats.PickaxeAbility.NONE);
		return new PathingState();
	}

	@Override
	public void onEnd(CommissionMacro macro) {
		AutoGetStats.getInstance().stop();
	}
}
