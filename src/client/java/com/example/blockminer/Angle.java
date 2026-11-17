package com.example.blockminer;

public final class Angle {
	private float yaw;
	private float pitch;

	public Angle(float yaw, float pitch) {
		this.yaw = yaw;
		this.pitch = pitch;
	}

	public float getYaw() {
		return yaw;
	}

	public void setYaw(float yaw) {
		this.yaw = yaw;
	}

	public float getPitch() {
		return pitch;
	}

	public void setPitch(float pitch) {
		this.pitch = pitch;
	}

	public void setRotation(Angle rotation) {
		yaw = rotation.yaw;
		pitch = rotation.pitch;
	}

	public float getValue() {
		return Math.abs(yaw) + Math.abs(pitch);
	}

	public float lengthSqrt() {
		return (float) Math.sqrt(yaw * yaw + pitch * pitch);
	}

	@Override
	public String toString() {
		return "Rotation{yaw=" + yaw + ", pitch=" + pitch + "}";
	}
}
