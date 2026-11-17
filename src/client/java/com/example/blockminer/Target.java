package com.example.blockminer;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class Target {
	private final Vec3 position;
	private final Entity entity;
	private final BlockPos blockPos;
	private final Angle angle;
	private float additionalY = (float) ((1.0 + Math.random()) * 0.75);

	public Target(Vec3 position) {
		this.position = position;
		this.entity = null;
		this.blockPos = null;
		this.angle = null;
	}

	public Target(Entity entity) {
		this.position = null;
		this.entity = entity;
		this.blockPos = null;
		this.angle = null;
	}

	public Target(BlockPos blockPos) {
		this.position = null;
		this.entity = null;
		this.blockPos = blockPos;
		this.angle = null;
	}

	public Target(Angle angle) {
		this.position = null;
		this.entity = null;
		this.blockPos = null;
		this.angle = angle;
	}

	public Entity getEntity() {
		return entity;
	}

	public BlockPos getBlockPos() {
		return blockPos;
	}

	public Angle getAngle() {
		return angle;
	}

	public float getAdditionalY() {
		return additionalY;
	}

	public void setAdditionalY(float additionalY) {
		this.additionalY = additionalY;
	}

	public Angle getTargetAngle() {
		if (blockPos != null) {
			return AngleUtil.getRotation(blockPos);
		}
		if (position != null) {
			return AngleUtil.getRotation(position);
		}
		if (entity != null) {
			return AngleUtil.getRotation(entity, additionalY);
		}
		if (angle == null) {
			throw new IllegalStateException("Rotation target has no position or angle");
		}
		return angle;
	}

	@Override
	public String toString() {
		return "Target{position=" + position + ", entity=" + entity + ", blockPos=" + blockPos + ", angle=" + angle + "}";
	}
}
