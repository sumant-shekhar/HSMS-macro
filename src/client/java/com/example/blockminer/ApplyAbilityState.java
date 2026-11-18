package com.example.blockminer;

import net.minecraft.client.Minecraft;

public final class ApplyAbilityState implements BlockMinerState {
	@Override
	public BlockMinerState onTick(BlockMiner miner) {
		Minecraft client = Minecraft.getInstance();
		if (client.player != null) {
			PickaxeAbilityTracker.getInstance().executeAbility(client);
		}
		return new ChoosingBlockState();
	}
}
