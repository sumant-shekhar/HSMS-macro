package com.example.blockminer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

import java.util.Arrays;
import java.util.Objects;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

public final class RotationHandler {
	private static final RotationHandler INSTANCE = new RotationHandler();
	private final Queue<RotationConfiguration> rotations = new ConcurrentLinkedQueue<>();
	private final Random random = new Random();
	private final Angle startRotation = new Angle(0.0F, 0.0F);
	private RotationConfiguration configuration;
	private Target target;
	private boolean enabled;
	private volatile boolean stopRequested;
	private boolean followingTarget;
	private long startTimeNanos;
	private long durationNanos;
	private float lastBezierYaw;
	private float lastBezierPitch;
	private int randomMultiplier1 = 1;
	private int randomMultiplier2 = 1;

	private RotationHandler() {
	}

	public static RotationHandler getInstance() {
		return INSTANCE;
	}

	public RotationHandler queueRotation(RotationConfiguration... configurations) {
		rotations.addAll(Arrays.asList(configurations));
		return this;
	}

	public void start() {
		if (!enabled && !rotations.isEmpty()) {
			easeTo(rotations.poll());
		}
	}

	public void easeTo(RotationConfiguration configuration) {
		Objects.requireNonNull(configuration, "configuration");
		if (configuration.rotationType() != RotationConfiguration.RotationType.CLIENT) {
			throw new UnsupportedOperationException("SERVER rotations are pending Phase B");
		}
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			throw new IllegalStateException("A local player is required to start a rotation");
		}
		this.configuration = configuration;
		this.target = configuration.target()
				.orElseThrow(() -> new IllegalArgumentException("Rotation configuration has no target"));
		startRotation.setRotation(configuration.from().orElseGet(AngleUtil::getPlayerAngle));

		Angle change = AngleUtil.getNeededChange(startRotation, target.getTargetAngle());
		long scaledDurationMs = scaleDurationMillis(
				Math.sqrt(change.getYaw() * change.getYaw() + change.getPitch() * change.getPitch()),
				configuration.durationMs()
		);
		startTimeNanos = System.nanoTime();
		durationNanos = TimeUnit.MILLISECONDS.toNanos(scaledDurationMs);
		randomMultiplier1 = random.nextBoolean() ? 1 : -1;
		randomMultiplier2 = random.nextBoolean() ? 1 : -1;
		lastBezierYaw = 0.0F;
		lastBezierPitch = 0.0F;
		stopRequested = false;
		enabled = true;
	}

	public void tick(Minecraft client) {
		if (stopRequested || client.gui.screen() != null) {
			reset();
			return;
		}
		if (!enabled || configuration == null) {
			return;
		}
		LocalPlayer player = client.player;
		if (player == null) {
			reset();
			return;
		}

		long elapsed = Math.max(0L, System.nanoTime() - startTimeNanos);
		float timeProgress = durationNanos <= 0L
				? 1.0F
				: Math.min(1.0F, (float) elapsed / durationNanos);
		Angle bezierAngle = getBezierAngle(configuration.easeFunction().invoke(timeProgress));
		player.setYRot(player.getYRot() + bezierAngle.getYaw() - lastBezierYaw);
		player.setXRot(Mth.clamp(
				player.getXRot() + bezierAngle.getPitch() - lastBezierPitch,
				-90.0F,
				90.0F
		));
		lastBezierYaw = bezierAngle.getYaw();
		lastBezierPitch = bezierAngle.getPitch();

		if (timeProgress >= 1.0F) {
			handleRotationEnd();
		}
	}

	public void stop() {
		rotations.clear();
		stopRequested = true;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public boolean isFollowingTarget() {
		return followingTarget;
	}

	public void stopFollowingTarget() {
		if (configuration != null) {
			configuration.followTarget(false);
		}
	}

	private Angle getBezierAngle(float progress) {
		Angle end = AngleUtil.getNeededChange(startRotation, target.getTargetAngle());
		Angle control1 = new Angle(
				end.getYaw() * 0.05F * randomMultiplier1,
				end.getPitch() * 0.1F * randomMultiplier1
		);
		Angle control2 = new Angle(
				end.getYaw() - end.getYaw() * 0.05F * randomMultiplier2,
				end.getPitch() - end.getPitch() * 0.1F * randomMultiplier2
		);
		return new Angle(
				(float) bezier(progress, control1.getYaw(), control2.getYaw(), end.getYaw()),
				(float) bezier(progress, control1.getPitch(), control2.getPitch(), end.getPitch())
		);
	}

	private static double bezier(float t, float control1, float control2, float end) {
		double inverse = 1.0 - t;
		return 3.0 * inverse * inverse * t * control1
				+ 3.0 * inverse * t * t * control2
				+ t * t * t * end;
	}

	private void handleRotationEnd() {
		if (configuration.followTarget()) {
			followingTarget = true;
			easeTo(configuration);
			return;
		}

		configuration.callback().ifPresent(Runnable::run);
		RotationConfiguration next = rotations.poll();
		if (next != null) {
			easeTo(next);
			return;
		}
		reset();
	}

	private void reset() {
		configuration = null;
		target = null;
		enabled = false;
		followingTarget = false;
		stopRequested = false;
		startTimeNanos = 0L;
		durationNanos = 0L;
		lastBezierYaw = 0.0F;
		lastBezierPitch = 0.0F;
	}

	static long scaleDurationMillis(double angularDistance, long durationMs) {
		if (durationMs <= 0L) {
			return 1L;
		}
		double multiplier;
		if (angularDistance < 25.0) {
			multiplier = 0.65;
		} else if (angularDistance < 45.0) {
			multiplier = 0.77;
		} else if (angularDistance < 80.0) {
			multiplier = 0.9;
		} else if (angularDistance > 100.0) {
			multiplier = 1.1;
		} else {
			multiplier = 1.0;
		}
		return Math.max(1L, (long) (durationMs * multiplier));
	}
}
