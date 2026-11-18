package com.example.blockminer.macro.impl.GlacialMacro.states;

import com.example.blockminer.Config;
import com.example.blockminer.location.LocationTracker;
import com.example.blockminer.location.SubLocation;
import com.example.blockminer.macro.impl.GlacialMacro.GlacialMacro;
import com.example.blockminer.util.FeatureInventory;
import com.example.blockminer.util.StatsUtil;
import net.minecraft.client.Minecraft;

public final class StartingState implements GlacialMacroState {
	@Override
	public void onStart(GlacialMacro macro) {
	}

	@Override
	public GlacialMacroState onTick(GlacialMacro macro) {
		Minecraft client = Minecraft.getInstance();
		if (client.player != null) {
			if (!FeatureInventory.isMiningToolPresent(client)) {
				macro.disable("Mining tool must be in hotbar slot 2.");
				return this;
			}
			if (!FeatureInventory.isTeleportToolPresent(client)) {
				macro.disable("Teleport tool must be in hotbar slot 3.");
				return this;
			}
		}
		if (LocationTracker.getGameState().getCurrentSubLocation() != SubLocation.DWARVEN_BASE_CAMP) {
			return new TeleportingState(new StartingState());
		}
		var missing = macro.getNecessaryItems().stream()
				.filter(item -> !item.equals(com.example.blockminer.util.Strings.ASPECT_OF_THE_VOID_ITEM_NAME))
				.filter(item -> FeatureInventory.findHotbarItem(Minecraft.getInstance(), item) == -1)
				.toList();
		if (!missing.isEmpty()) {
			macro.disable("Put the required mining and travel items in the hotbar: "
					+ String.join(", ", missing));
			return this;
		}
		if (macro.getMiningSpeed() == 0) {
			macro.setMiningSpeed(StatsUtil.getMiningSpeed());
		}
		return new PathfindingState();
	}

	@Override
	public void onEnd(GlacialMacro macro) {
	}
}
