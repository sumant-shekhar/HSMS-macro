package com.example.blockminer;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

public final class RotationConfiguration {
	private Optional<Angle> from = Optional.empty();
	private Optional<Target> target;
	private Optional<Runnable> callback = Optional.empty();
	private long durationMs;
	private boolean easeBackToClientSide;
	private boolean followTarget;
	private RotationType rotationType = RotationType.CLIENT;
	private Ease easeFunction = Ease.values()[ThreadLocalRandom.current().nextInt(Ease.values().length)];

	public RotationConfiguration(Angle from, Angle to, long durationMs, RotationType rotationType, Runnable callback) {
		this(from, new Target(Objects.requireNonNull(to)), durationMs, rotationType, callback);
	}

	public RotationConfiguration(Angle from, Target target, long durationMs, RotationType rotationType, Runnable callback) {
		this.from = Optional.ofNullable(from);
		this.target = Optional.ofNullable(target);
		this.durationMs = durationMs;
		this.rotationType = Objects.requireNonNull(rotationType);
		this.callback = Optional.ofNullable(callback);
	}

	public RotationConfiguration(Angle to, long durationMs, Runnable callback) {
		this(null, new Target(Objects.requireNonNull(to)), durationMs, RotationType.CLIENT, callback);
	}

	public RotationConfiguration(Angle to, long durationMs, RotationType rotationType, Runnable callback) {
		this(null, new Target(Objects.requireNonNull(to)), durationMs, rotationType, callback);
	}

	public RotationConfiguration(Target target, long durationMs, Runnable callback) {
		this(null, target, durationMs, RotationType.CLIENT, callback);
	}

	public RotationConfiguration(Target target, long durationMs, RotationType rotationType, Runnable callback) {
		this(null, target, durationMs, rotationType, callback);
	}

	public Optional<Angle> from() {
		return from;
	}

	public Optional<Target> target() {
		return target;
	}

	public Optional<Runnable> callback() {
		return callback;
	}

	public long durationMs() {
		return durationMs;
	}

	public RotationConfiguration durationMs(long durationMs) {
		this.durationMs = durationMs;
		return this;
	}

	public boolean easeBackToClientSide() {
		return easeBackToClientSide;
	}

	public RotationConfiguration easeBackToClientSide(boolean easeBackToClientSide) {
		this.easeBackToClientSide = easeBackToClientSide;
		return this;
	}

	public boolean followTarget() {
		return followTarget;
	}

	public RotationConfiguration followTarget(boolean followTarget) {
		this.followTarget = followTarget;
		return this;
	}

	public RotationType rotationType() {
		return rotationType;
	}

	public RotationConfiguration rotationType(RotationType rotationType) {
		this.rotationType = Objects.requireNonNull(rotationType);
		return this;
	}

	public RotationConfiguration setTarget(Target target) {
		this.target = Optional.of(Objects.requireNonNull(target));
		return this;
	}

	public RotationConfiguration easeFunction(Ease ease) {
		this.easeFunction = Objects.requireNonNull(ease);
		return this;
	}

	public Ease easeFunction() {
		return easeFunction;
	}

	public enum RotationType {
		SERVER,
		CLIENT
	}

	public enum Ease {
		EASE_OUT_SINE(x -> (float) Math.sin((x * Math.PI) / 2.0)),
		EASE_IN_OUT_SINE(x -> (float) (-(Math.cos(x * Math.PI) - 1.0) / 2.0)),
		EASE_OUT_QUAD(x -> 1.0F - (1.0F - x) * (1.0F - x)),
		EASE_OUT_CUBIC(x -> 1.0F - (1.0F - x) * (1.0F - x) * (1.0F - x)),
		EASE_OUT_CIRC(x -> (float) Math.sqrt(1.0 - (x - 1.0F) * (x - 1.0F)));

		private final Function<Float, Float> easingFunction;

		Ease(Function<Float, Float> easingFunction) {
			this.easingFunction = easingFunction;
		}

		public float invoke(float x) {
			float clamped = Math.max(0.0F, Math.min(1.0F, x));
			return easingFunction.apply(clamped);
		}
	}
}
