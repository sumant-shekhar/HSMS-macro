package com.example.blockminer.feature.impl.AutoDrillRefuel.states;

import com.example.blockminer.feature.impl.AutoDrillRefuel.AutoDrillRefuel;
import com.example.blockminer.util.FeatureInventory;
import com.example.blockminer.util.Strings;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.ContainerInput;

public final class GreatforgeState implements AutoDrillRefuelState {
	private int tickCounter;
	private long startedAt;

	@Override
	public void onStart(AutoDrillRefuel refueler) {
		tickCounter = 0;
		startedAt = System.nanoTime();
		log("Waiting for the Drill Anvil screen.");
	}

	@Override
	public AutoDrillRefuelState onTick(AutoDrillRefuel refueler) {
		Minecraft client = Minecraft.getInstance();
		if (!FeatureInventory.title(client).contains(Strings.DRILL_ANVIL_TITLE)) {
			if (System.nanoTime() - startedAt > 10_000_000_000L) {
				logError("Drill Anvil did not open within ten seconds.");
				refueler.fail(AutoDrillRefuel.AutoDrillRefuelError.INACCESSIBLE_ANVIL);
				return null;
			}
			return this;
		}
		tickCounter++;
		if (tickCounter == 10 && !moveItem(client, refueler.getDrillName(), refueler,
				AutoDrillRefuel.AutoDrillRefuelError.NO_DRILL)) {
			return null;
		}
		if (tickCounter == 20 && !moveItem(client, refueler.getFuelType().getName(), refueler,
				AutoDrillRefuel.AutoDrillRefuelError.NO_FUEL)) {
			return null;
		}
		if (tickCounter == 30) {
			int confirmSlot = FeatureInventory.findContainerItem(client, Strings.DRILL_ANVIL_CONFIRM_ITEM);
			if (confirmSlot == -1 || !FeatureInventory.clickContainerSlot(
					client, confirmSlot, ContainerInput.PICKUP, 0)) {
				logError("Could not find or click the Drill Anvil confirmation item.");
				refueler.fail(AutoDrillRefuel.AutoDrillRefuelError.NO_FUEL);
				return null;
			}
		}
		if (tickCounter == 40 && !moveItem(client, refueler.getDrillName(), refueler,
				AutoDrillRefuel.AutoDrillRefuelError.NO_DRILL)) {
			return null;
		}
		if (tickCounter >= 50) {
			log("Refuel sequence completed.");
			refueler.stop();
			return null;
		}
		return this;
	}

	@Override
	public void onEnd(AutoDrillRefuel refueler) {
		if (FeatureInventory.menu(Minecraft.getInstance()) != null) {
			Minecraft.getInstance().setScreenAndShow(null);
		}
	}

	private boolean moveItem(
			Minecraft client,
			String name,
			AutoDrillRefuel refueler,
			AutoDrillRefuel.AutoDrillRefuelError error
	) {
		int slot = FeatureInventory.findContainerItem(client, name);
		if (slot == -1 || !FeatureInventory.clickContainerSlot(client, slot, ContainerInput.QUICK_MOVE, 0)) {
			logError("Could not find " + name + " in the Drill Anvil screen.");
			refueler.fail(error);
			return false;
		}
		return true;
	}
}
