package com.example.blockminer;

import java.util.concurrent.ThreadLocalRandom;

public final class Config {
	public static double miningCoefficient = 200.0D;
	public static double angleCoefficient = 1.0D;
	public static double distanceCoefficient = 10.0D;
	public static int tickGlideOffset = 0;
	public static boolean sneakWhileMining = false;
	// TODO(verify): precision crit-particle aiming needs a particle hook; the bundled module has no spawn callback.
	public static boolean precisionMiner = false;
	public static boolean miscFullBlock = false;
	public static boolean randomizedRotations = true;
	public static int waitThresholdMs = 2_000;
	public static int defaultMiningSpeed = 600;
	public static long minimumRotationTimeMs = 600L;
	public static long maximumRotationTimeMs = 900L;

	private Config() {
	}

	public static long getRandomRotationTime() {
		if (minimumRotationTimeMs < 0L || maximumRotationTimeMs < minimumRotationTimeMs
				|| maximumRotationTimeMs == Long.MAX_VALUE) {
			throw new IllegalStateException("Rotation time range must be nonnegative and ordered");
		}
		return ThreadLocalRandom.current().nextLong(minimumRotationTimeMs, maximumRotationTimeMs + 1L);
	}
}
