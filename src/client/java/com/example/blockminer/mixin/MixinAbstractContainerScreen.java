package com.example.blockminer.mixin;

import com.example.blockminer.macro.MacroManager;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public abstract class MixinAbstractContainerScreen {
	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void blockminer$cancelClicksWhileMacroRuns(
			MouseButtonEvent event,
			boolean doubleClick,
			CallbackInfoReturnable<Boolean> callback
	) {
		if (MacroManager.getInstance().isRunning()) {
			callback.setReturnValue(false);
		}
	}
}
