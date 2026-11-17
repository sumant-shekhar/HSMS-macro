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
		if (!Strings.HOTM_MENU_TITLE.equals(MenuInventory.title(client))) {
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
		if (!Strings.HOTM_MENU_TITLE.equals(MenuInventory.title(client))) {
			fail("Could not open the Heart of the Mountain menu.");
			return;
		}
		var item = MenuInventory.findItem(client, Strings.PICKOBULUS_ITEM_NAME);
		if (item.isEmpty()) {
			fail("Pickobulus was not present in the Heart of the Mountain menu.");
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
