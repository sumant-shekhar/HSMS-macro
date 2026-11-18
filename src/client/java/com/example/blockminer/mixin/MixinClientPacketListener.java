package com.example.blockminer.mixin;

import com.example.blockminer.PickaxeAbilityTracker;
import com.example.blockminer.feature.impl.AutoChestUnlocker;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class MixinClientPacketListener {
	@Inject(method = "handleBlockUpdate", at = @At("RETURN"))
	private void blockminer$onBlockUpdate(ClientboundBlockUpdatePacket packet, CallbackInfo ci) {
		AutoChestUnlocker.getInstance().onBlockUpdate(packet.getPos(), packet.getBlockState());
	}

	@Inject(method = "handleChunkBlocksUpdate", at = @At("RETURN"))
	private void blockminer$onChunkBlocksUpdate(ClientboundSectionBlocksUpdatePacket packet, CallbackInfo ci) {
		packet.runUpdates((pos, state) -> AutoChestUnlocker.getInstance().onBlockUpdate(pos, state));
	}

	@Inject(method = "handleParticleEvent", at = @At("HEAD"))
	private void blockminer$onParticleEvent(ClientboundLevelParticlesPacket packet, CallbackInfo ci) {
		AutoChestUnlocker.getInstance().onParticle(
				packet.getParticle().getType(),
				packet.getX(),
				packet.getY(),
				packet.getZ()
		);
	}

	@Inject(method = "handleSystemChat", at = @At("HEAD"))
	private void blockminer$onSystemChat(ClientboundSystemChatPacket packet, CallbackInfo ci) {
		String text = packet.content().getString();
		AutoChestUnlocker.getInstance().onChat(text);
		PickaxeAbilityTracker.getInstance().onChat(text);
	}
}
