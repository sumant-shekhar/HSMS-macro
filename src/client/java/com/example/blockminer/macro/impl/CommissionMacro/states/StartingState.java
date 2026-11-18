package com.example.blockminer.macro.impl.CommissionMacro.states;

import com.example.blockminer.Config;
import com.example.blockminer.macro.impl.CommissionMacro.CommissionMacro;
import com.example.blockminer.util.FeatureInventory;
import com.example.blockminer.util.StatsUtil;
import net.minecraft.client.Minecraft;

public final class StartingState implements CommissionMacroState {
	@Override
	public void onStart(CommissionMacro macro) {
	}

	@Override
	public CommissionMacroState onTick(CommissionMacro macro) {
		Minecraft client = Minecraft.getInstance();
		if (client.player != null) {
			if (!FeatureInventory.isWeaponPresent(client)) {
				macro.disable("Weapon for killing must be in hotbar slot 1.");
				return this;
			}
			if (!FeatureInventory.isMiningToolPresent(client)) {
				macro.disable("Mining tool must be in hotbar slot 2.");
				return this;
			}
		}
		var missing = macro.getNecessaryItems().stream()
				.filter(item -> FeatureInventory.findHotbarItem(Minecraft.getInstance(), item) == -1)
				.toList();
		if (!missing.isEmpty()) {
			macro.disable("Put the required items in the hotbar: " + String.join(", ", missing));
			return this;
		}
		if (macro.getMiningSpeed() == 0) {
			macro.setMiningSpeed(StatsUtil.getMiningSpeed());
		}
		return new PathingState();
	}

	@Override
	public void onEnd(CommissionMacro macro) {
	}
}
