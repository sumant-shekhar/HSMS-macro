package com.example.blockminer.macro;

import com.example.blockminer.Clock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.protocol.Packet;

import java.util.List;

public abstract class AbstractMacro {
	protected final Minecraft mc = Minecraft.getInstance();
	protected final Clock timer = new Clock();
	protected final Clock uptime = new Clock();
	private boolean enabled;
	private boolean paused;
	private long enabledAtNanos;
	private long accumulatedUptimeNanos;

	public abstract String getName();

	public List<String> getNecessaryItems() {
		return List.of();
	}

	public final void enable() {
		if (enabled) {
			return;
		}
		enabled = true;
		paused = false;
		enabledAtNanos = System.nanoTime();
		accumulatedUptimeNanos = 0L;
		onEnable();
	}

	public final void disable() {
		if (!enabled) {
			return;
		}
		enabled = false;
		paused = false;
		accumulatedUptimeNanos += System.nanoTime() - enabledAtNanos;
		onDisable();
		timer.reset();
	}

	public final void disable(String reason) {
		error(reason);
		disable();
	}

	public final void pause() {
		if (!enabled || paused) {
			return;
		}
		paused = true;
		accumulatedUptimeNanos += System.nanoTime() - enabledAtNanos;
		onPause();
	}

	public final void resume() {
		if (!enabled || !paused) {
			return;
		}
		paused = false;
		enabledAtNanos = System.nanoTime();
		onResume();
	}

	public final void toggle() {
		if (enabled) {
			disable();
		} else {
			enable();
		}
	}

	public final boolean isEnabled() {
		return enabled;
	}

	public final boolean isRunning() {
		return enabled && !paused;
	}

	public final boolean isPaused() {
		return paused;
	}

	public final long getUptimeMillis() {
		long elapsed = enabled && !paused ? System.nanoTime() - enabledAtNanos : 0L;
		return (accumulatedUptimeNanos + elapsed) / 1_000_000L;
	}

	public final boolean hasTimerEnded() {
		return timer.isScheduled() && timer.passed();
	}

	public final boolean isTimerRunning() {
		return timer.isScheduled() && !timer.passed();
	}

	public final void scheduleTimer(long milliseconds) {
		timer.schedule(milliseconds);
	}

	public void onEnable() {
	}

	public void onDisable() {
	}

	public void onPause() {
	}

	public void onResume() {
	}

	public void onTick(Minecraft client) {
	}

	public void onWorldRender() {
	}

	public void onChat(String message) {
	}

	public void onTablistUpdate(List<String> rows) {
	}

	public void onOverlayRender(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
	}

	public void onReceivePacket(Packet<?> packet) {
	}

	protected final void log(String message) {
		com.example.blockminer.util.Logger.sendLog(formatMessage(message));
	}

	protected final void send(String message) {
		com.example.blockminer.util.Logger.sendMessage(formatMessage(message));
	}

	protected final void error(String message) {
		com.example.blockminer.util.Logger.sendError(formatMessage(message));
	}

	protected final void warn(String message) {
		com.example.blockminer.util.Logger.sendWarning(formatMessage(message));
	}

	protected final void reportStateTransition(String previous, String next) {
		send("State: " + previous + " -> " + next);
	}

	protected final boolean isLobbyChangeRequestedByFailsafe() {
		// TODO: Failsafe behavior is intentionally excluded from this port.
		return false;
	}

	protected final String formatMessage(String message) {
		return "[" + getName() + "] " + message;
	}
}
