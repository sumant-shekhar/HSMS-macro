package com.example.blockminer.feature.impl.AutoGetStats.tasks.impl;

import com.example.blockminer.Clock;
import com.example.blockminer.feature.impl.AutoGetStats.tasks.AbstractInventoryTask;
import com.example.blockminer.feature.impl.AutoGetStats.tasks.MenuInventory;
import com.example.blockminer.feature.impl.AutoGetStats.tasks.TaskStatus;
import com.example.blockminer.util.Strings;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MiningSpeedRetrievalTask extends AbstractInventoryTask<Integer> {
	private static final Logger LOGGER = LoggerFactory.getLogger("blockminer");
	private static final Pattern MINING_SPEED_PATTERN = Pattern.compile(
			Strings.MINING_SPEED_LORE_PATTERN,
			Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
	);
	private static final Pattern FORMATTING_CODE = Pattern.compile("§.");
	private final Minecraft client = Minecraft.getInstance();
	private final Clock timer = new Clock();
	private Integer miningSpeed;

	@Override
	public void init() {
		taskStatus = TaskStatus.RUNNING;
		if (client.player == null) {
			fail("Cannot retrieve mining speed without a local player.");
			return;
		}
		if (!Strings.SKYBLOCK_MENU_TITLE.equals(MenuInventory.title(client))) {
			closeScreen();
			client.player.connection.sendCommand("sbmenu");
		}
		timer.schedule(1000L);
	}

	@Override
	public void onTick() {
		if (taskStatus != TaskStatus.RUNNING || !timer.passed()) {
			return;
		}
		if (!Strings.SKYBLOCK_MENU_TITLE.equals(MenuInventory.title(client))) {
			fail("Could not open the SkyBlock Menu.");
			return;
		}
		Optional<ItemStack> profileItem = MenuInventory.findItem(client, Strings.PROFILE_ITEM_NAME);
		if (profileItem.isEmpty()) {
			fail("Your SkyBlock Profile item was not present in the SkyBlock Menu.");
			return;
		}
		List<String> lore = MenuInventory.getLore(profileItem.get()).stream()
				.map(MiningSpeedRetrievalTask::stripFormatting)
				.toList();
		for (String line : lore) {
			Matcher matcher = MINING_SPEED_PATTERN.matcher(line);
			if (matcher.find()) {
				try {
					miningSpeed = (int) Double.parseDouble(matcher.group(1).replace(",", ""));
					taskStatus = TaskStatus.SUCCESS;
				} catch (NumberFormatException exception) {
					fail("Could not parse mining speed from menu text: " + line);
				}
				return;
			}
		}
		LOGGER.warn("[blockminer] Mining Speed was not found in the SkyBlock profile item. Stripped lore lines: {}",
				lore);
		fail("Mining speed was not present in the SkyBlock Menu profile item.");
	}

	@Override
	public void end() {
		closeScreen();
	}

	@Override
	public Integer getResult() {
		return miningSpeed;
	}

	private void closeScreen() {
		if (client.gui.screen() != null) {
			client.gui.setScreen(null);
		}
	}

	private static String stripFormatting(String text) {
		return FORMATTING_CODE.matcher(text).replaceAll("");
	}
}
