package com.example.blockminer.mixin;

import com.example.blockminer.BlockMinerClient;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class MixinKeyboardHandler {
	@Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
	private void blockminer$onKeyPress(long window, int action, KeyEvent event, CallbackInfo ci) {
		if (action == 1) { // GLFW_PRESS
			if (BlockMinerClient.handleRawKeyPress(event)) {
				ci.cancel();
			}
		}
	}
}
