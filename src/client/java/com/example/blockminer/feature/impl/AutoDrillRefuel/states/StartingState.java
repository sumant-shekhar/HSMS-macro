package com.example.blockminer.feature.impl.AutoDrillRefuel.states;

import com.example.blockminer.feature.impl.AutoDrillRefuel.AutoDrillRefuel;
import com.example.blockminer.util.FeatureInventory;
import net.minecraft.client.Minecraft;

public final class StartingState implements AutoDrillRefuelState {
	@Override
	public void onStart(AutoDrillRefuel refueler) {
		log("Checking drill and fuel inventory.");
	}

	@Override
	public AutoDrillRefuelState onTick(AutoDrillRefuel refueler) {
		Minecraft client = Minecraft.getInstance();
		if (FeatureInventory.findInventoryItem(client, refueler.getDrillName()) == -1) {
			logError("No matching drill was found in inventory.");
			refueler.fail(AutoDrillRefuel.AutoDrillRefuelError.NO_DRILL);
			return null;
		}
		if (FeatureInventory.findInventoryItem(client, refueler.getFuelType().getName()) == -1) {
			logError("No matching fuel item was found in inventory.");
			refueler.fail(AutoDrillRefuel.AutoDrillRefuelError.NO_FUEL);
			return null;
		}
		return new AbiphoneState();
	}
}
