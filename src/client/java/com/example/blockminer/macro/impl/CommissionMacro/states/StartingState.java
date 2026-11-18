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
		if (Config.miningTool.isBlank() || Config.slayerWeapon.isBlank()) {
			macro.disable("Set both the mining tool and slayer weapon before starting Commission Macro.");
			return this;
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
