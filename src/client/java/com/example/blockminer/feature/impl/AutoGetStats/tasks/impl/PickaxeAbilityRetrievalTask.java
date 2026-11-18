package com.example.blockminer.feature.impl.AutoGetStats.tasks.impl;

import com.example.blockminer.Clock;
import com.example.blockminer.feature.impl.AutoGetStats.AutoGetStats;
import com.example.blockminer.feature.impl.AutoGetStats.tasks.AbstractInventoryTask;
import com.example.blockminer.feature.impl.AutoGetStats.tasks.MenuInventory;
import com.example.blockminer.feature.impl.AutoGetStats.tasks.TaskStatus;
import com.example.blockminer.util.Strings;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.Blocks;

public final class PickaxeAbilityRetrievalTask extends AbstractInventoryTask<AutoGetStats.PickaxeAbility> {
	private final Minecraft client = Minecraft.getInstance();
	private final Clock timer = new Clock();
	private AutoGetStats.PickaxeAbility pickaxeAbility = AutoGetStats.PickaxeAbility.NONE;

	@Override
	public void init() {
		taskStatus = TaskStatus.RUNNING;
		if (client.player == null) {
			fail("Cannot retrieve pickaxe ability without a local player.");
			return;
		}
		if (!MenuInventory.title(client).toLowerCase(java.util.Locale.ROOT).contains("heart of the mountain")) {
			closeScreen();
			client.player.connection.sendCommand("hotm");
		}
		timer.schedule(1000L);
	}

	@Override
	public void onTick() {
		if (taskStatus != TaskStatus.RUNNING || !timer.passed()) {
			return;
		}
		if (!MenuInventory.title(client).toLowerCase(java.util.Locale.ROOT).contains("heart of the mountain")) {
			fail("Could not open the Heart of the Mountain menu.");
			return;
		}
		var item = MenuInventory.findItem(client, Strings.PICKOBULUS_ITEM_NAME);
		if (item.isEmpty()) {
			var speedItem = MenuInventory.findItem(client, Strings.MINING_SPEED_ITEM_NAME);
			if (speedItem.isPresent()) {
				pickaxeAbility = speedItem.get().getItem() == Blocks.EMERALD_BLOCK.asItem()
						? AutoGetStats.PickaxeAbility.MINING_SPEED_BOOST
						: AutoGetStats.PickaxeAbility.NONE;
				taskStatus = TaskStatus.SUCCESS;
				return;
			}
			pickaxeAbility = AutoGetStats.PickaxeAbility.MINING_SPEED_BOOST;
			taskStatus = TaskStatus.SUCCESS;
			return;
		}
		pickaxeAbility = item.get().getItem() == Blocks.EMERALD_BLOCK.asItem()
				? AutoGetStats.PickaxeAbility.PICKOBULUS
				: AutoGetStats.PickaxeAbility.MINING_SPEED_BOOST;
		taskStatus = TaskStatus.SUCCESS;
	}

	@Override
	public void end() {
		closeScreen();
	}

	@Override
	public AutoGetStats.PickaxeAbility getResult() {
		return pickaxeAbility;
	}

	private void closeScreen() {
		if (client.gui.screen() != null) {
			client.gui.setScreen(null);
		}
	}
}
