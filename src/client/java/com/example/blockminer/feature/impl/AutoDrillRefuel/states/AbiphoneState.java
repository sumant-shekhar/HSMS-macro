package com.example.blockminer.feature.impl.AutoDrillRefuel.states;

import com.example.blockminer.feature.impl.AutoDrillRefuel.AutoDrillRefuel;
import com.example.blockminer.util.FeatureInventory;
import com.example.blockminer.util.Strings;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;

public final class AbiphoneState implements AutoDrillRefuelState {
	@Override
	public void onStart(AutoDrillRefuel refueler) {
		Minecraft client = Minecraft.getInstance();
		if (!FeatureInventory.holdHotbarItem(client, Strings.ABIPHONE_ITEM_NAME)
				|| client.player == null || client.gameMode == null) {
			logError("Abiphone is not in the hotbar.");
			refueler.fail(AutoDrillRefuel.AutoDrillRefuelError.NO_ABIPHONE);
			return;
		}
		client.gameMode.useItem(client.player, InteractionHand.MAIN_HAND);
		log("Opened the Abiphone.");
	}

	@Override
	public AutoDrillRefuelState onTick(AutoDrillRefuel refueler) {
		Minecraft client = Minecraft.getInstance();
		if (!FeatureInventory.title(client).contains(Strings.ABIPHONE_MENU_TITLE)) {
			return this;
		}
		int slot = FeatureInventory.findContainerItem(client, Strings.GREATFORGE_CONTACT_NAME);
		if (slot == -1) {
			logError("Greatforge contact was not found.");
			refueler.fail(AutoDrillRefuel.AutoDrillRefuelError.NO_GREATFORGE_CONTACT);
			return null;
		}
		if (!FeatureInventory.clickContainerSlot(client, slot,
				net.minecraft.world.inventory.ContainerInput.PICKUP, 0)) {
			refueler.fail(AutoDrillRefuel.AutoDrillRefuelError.NO_GREATFORGE_CONTACT);
			return null;
		}
		return new GreatforgeState();
	}
}
