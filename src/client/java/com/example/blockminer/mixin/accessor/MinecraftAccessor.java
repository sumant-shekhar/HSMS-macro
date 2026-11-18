package com.example.blockminer.mixin.accessor;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Minecraft.class)
public interface MinecraftAccessor {
	@Accessor("rightClickDelay")
	int blockminer$getRightClickDelay();

	@Accessor("rightClickDelay")
	void blockminer$setRightClickDelay(int rightClickDelay);

	@Invoker("startUseItem")
	void blockminer$startUseItem();

	@Invoker("startAttack")
	boolean blockminer$startAttack();
}
