package com.example.blockminer.macro.impl.GlacialMacro.states;

import com.example.blockminer.Config;
import com.example.blockminer.location.LocationTracker;
import com.example.blockminer.location.SubLocation;
import com.example.blockminer.macro.impl.GlacialMacro.GlacialMacro;
import com.example.blockminer.util.FeatureInventory;
import net.minecraft.client.Minecraft;

public final class StartingState implements GlacialMacroState {
	@Override
	public void onStart(GlacialMacro macro) {
	}

	@Override
	public GlacialMacroState onTick(GlacialMacro macro) {
		if (Config.miningTool.isBlank()) {
			macro.disable("Set a mining tool before starting Glacial Macro.");
			return this;
		}
		if (LocationTracker.getGameState().getCurrentSubLocation() != SubLocation.DWARVEN_BASE_CAMP) {
			return new TeleportingState(new StartingState());
		}
		var missing = macro.getNecessaryItems().stream()
				.filter(item -> FeatureInventory.findHotbarItem(Minecraft.getInstance(), item) == -1)
				.toList();
		if (!missing.isEmpty()) {
			macro.disable("Put the required mining and travel items in the hotbar: "
					+ String.join(", ", missing));
			return this;
		}
		return macro.getMiningSpeed() == 0 ? new GettingStatsState() : new PathfindingState();
	}

	@Override
	public void onEnd(GlacialMacro macro) {
	}
}
