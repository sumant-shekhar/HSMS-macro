package com.example.blockminer.feature.impl;

import com.example.blockminer.Config;
import com.example.blockminer.feature.AbstractFeature;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Deque;

public final class LagDetector extends AbstractFeature {
	private static final LagDetector INSTANCE = new LagDetector();
	private static final int HISTORY_SIZE = 20;
	private static final int SAMPLE_INTERVAL_TICKS = 20;
	private final Deque<Float> tickRateHistory = new ArrayDeque<>();
	private long lastWorldTime = Long.MIN_VALUE;
	private long lastWorldTimeObservedAt;
	private long timeJoined;
	private long lastReceivedPacketTime = -1L;
	private long recentlyLaggedUntil;
	private Vec3 lastPacketPosition;
	private int ticksUntilSample;

	private LagDetector() {
	}

	public static LagDetector getInstance() {
		return INSTANCE;
	}

	@Override
	public String getName() {
		return "LagDetector";
	}

	@Override
	public void start() {
		tickRateHistory.clear();
		lastWorldTime = Long.MIN_VALUE;
		lastReceivedPacketTime = -1L;
		recentlyLaggedUntil = 0L;
		timeJoined = System.currentTimeMillis();
		enabled = true;
	}

	public long getLastReceivedPacketTime() {
		return lastReceivedPacketTime;
	}

	public Vec3 getLastPacketPosition() {
		return lastPacketPosition != null || mc.player == null
				? lastPacketPosition
				: mc.player.position();
	}

	public boolean isLagging() {
		return getTimeSinceLastTick() > Config.lagThresholdSeconds;
	}

	public boolean wasJustLagging() {
		return System.currentTimeMillis() < recentlyLaggedUntil;
	}

	public long getLaggingTime() {
		return lastReceivedPacketTime < 0L ? -1L : System.currentTimeMillis() - lastReceivedPacketTime;
	}

	public float getTickRate() {
		if (mc.player == null || mc.level == null) {
			return 0.0F;
		}
		if (System.currentTimeMillis() - timeJoined < 5_000L) {
			return 20.0F;
		}
		return (float) tickRateHistory.stream().mapToDouble(Float::doubleValue).average().orElse(0.0D);
	}

	public float getTimeSinceLastTick() {
		return lastReceivedPacketTime < 0L
				? 0.0F
				: (System.currentTimeMillis() - lastReceivedPacketTime) / 1_000.0F;
	}

	@Override
	protected void onTick(Minecraft client) {
		if (client.player == null || client.level == null) {
			lastWorldTime = Long.MIN_VALUE;
			lastReceivedPacketTime = -1L;
			tickRateHistory.clear();
			return;
		}
		long now = System.currentTimeMillis();
		long worldTime = client.level.getLevelData().getGameTime();
		if (lastWorldTime == Long.MIN_VALUE) {
			lastWorldTime = worldTime;
			lastWorldTimeObservedAt = now;
			timeJoined = now;
			lastReceivedPacketTime = now;
			lastPacketPosition = client.player.position();
			return;
		}
		if (--ticksUntilSample > 0) {
			return;
		}
		ticksUntilSample = SAMPLE_INTERVAL_TICKS;
		if (worldTime != lastWorldTime) {
			long elapsed = Math.max(1L, now - lastWorldTimeObservedAt);
			float rate = clamp((worldTime - lastWorldTime) * 1_000.0F / elapsed);
			tickRateHistory.addLast(rate);
			while (tickRateHistory.size() > HISTORY_SIZE) {
				tickRateHistory.removeFirst();
			}
			lastWorldTime = worldTime;
			lastWorldTimeObservedAt = now;
			lastReceivedPacketTime = now;
			lastPacketPosition = client.player.position();
		} else if ((now - lastReceivedPacketTime) / 1_000.0D > Config.lagThresholdSeconds) {
			recentlyLaggedUntil = now + 900L;
		}
	}

	private static float clamp(float value) {
		return Math.max(0.0F, Math.min(20.0F, value));
	}
}
