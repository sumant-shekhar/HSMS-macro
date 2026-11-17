package com.example.blockminer.feature.impl.AutoGetStats.tasks.impl;

import com.example.blockminer.Clock;
import com.example.blockminer.feature.impl.AutoGetStats.tasks.AbstractInventoryTask;
import com.example.blockminer.feature.impl.AutoGetStats.tasks.MenuInventory;
import com.example.blockminer.feature.impl.AutoGetStats.tasks.TaskStatus;
import com.example.blockminer.util.Strings;
import net.minecraft.client.Minecraft;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.List;

public final class MiningBoostRetrievalTask extends AbstractInventoryTask<Integer> {
	private static final Pattern BOOST_PATTERN = Pattern.compile(Strings.MINING_BOOST_LORE_PATTERN);
	private final Minecraft client = Minecraft.getInstance();
	private final Clock timer = new Clock();
	private Integer speedBoost;

	@Override
	public void init() {
		taskStatus = TaskStatus.RUNNING;
		if (client.player == null) {
			fail("Cannot retrieve mining boost without a local player.");
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
		List<String> lines = MenuInventory.findItem(client, Strings.MINING_SPEED_ITEM_NAME)
				.map(MenuInventory::getLore)
				.orElse(List.of());
		if (lines.isEmpty()) {
			fail("Mining Speed Boost item was not present in the menu.");
			return;
		}
		String lore = String.join(" ", lines);
		Matcher matcher = BOOST_PATTERN.matcher(lore);
		if (matcher.find()) {
			try {
				speedBoost = Integer.parseInt(matcher.group(1));
				taskStatus = TaskStatus.SUCCESS;
			} catch (NumberFormatException exception) {
				fail("Could not parse mining boost from menu text: " + lore);
			}
			return;
		}
		fail("Could not find a mining boost percentage in the Heart of the Mountain menu.");
	}

	@Override
	public void end() {
		closeScreen();
	}

	@Override
	public Integer getResult() {
		return speedBoost;
	}

	private void closeScreen() {
		if (client.gui.screen() != null) {
			client.gui.setScreen(null);
		}
	}
}
