package com.example.blockminer.mixin;

import com.example.blockminer.macro.MacroManager;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MixinMouseHandler {
	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
	private void blockminer$cancelHotbarScrollWhileMacroRuns(
			long window,
			double horizontal,
			double vertical,
			CallbackInfo callback
	) {
		if (MacroManager.getInstance().isRunning()) {
			callback.cancel();
		}
	}
}
