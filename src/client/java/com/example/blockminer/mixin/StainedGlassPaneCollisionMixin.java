package com.example.blockminer.mixin;

import com.example.blockminer.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class StainedGlassPaneCollisionMixin {
	@Inject(
			method = "getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
			at = @At("RETURN"),
			cancellable = true
	)
	private void blockminer$useFullStainedGlassPaneShapeWithoutContext(
			BlockGetter level,
			BlockPos position,
			CallbackInfoReturnable<VoxelShape> callback
	) {
		BlockBehaviour.BlockStateBase state = (BlockBehaviour.BlockStateBase) (Object) this;
		if (Config.miscFullBlock && state.getBlock() instanceof StainedGlassPaneBlock) {
			callback.setReturnValue(Shapes.block());
		}
	}

	@Inject(
			method = "getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
			at = @At("RETURN"),
			cancellable = true
	)
	private void blockminer$useFullStainedGlassPaneShape(
			BlockGetter level,
			BlockPos position,
			CollisionContext context,
			CallbackInfoReturnable<VoxelShape> callback
	) {
		BlockBehaviour.BlockStateBase state = (BlockBehaviour.BlockStateBase) (Object) this;
		if (Config.miscFullBlock && state.getBlock() instanceof StainedGlassPaneBlock) {
			callback.setReturnValue(Shapes.block());
		}
	}
}
