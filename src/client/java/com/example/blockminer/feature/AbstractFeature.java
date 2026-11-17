package com.example.blockminer.feature;

import com.example.blockminer.Clock;
import com.example.blockminer.util.Logger;
import net.minecraft.client.Minecraft;

import java.util.List;

public abstract class AbstractFeature {
	protected final Minecraft mc = Minecraft.getInstance();
	protected final Clock timer = new Clock();
	protected boolean enabled;

	public abstract String getName();

	public boolean isRunning() {
		return enabled;
	}

	public boolean isEnabled() {
		return true;
	}

	public void start() {
	}

	public void stop() {
		enabled = false;
		resetStatesAfterStop();
	}

	public void pause() {
		enabled = false;
	}

	public void resume() {
		enabled = true;
	}

	public void resetStatesAfterStop() {
	}

	public boolean shouldStartAtLaunch() {
		return false;
	}

	protected boolean isTimerRunning() {
		return timer.isScheduled() && !timer.passed();
	}

	protected boolean hasTimerEnded() {
		return timer.isScheduled() && timer.passed();
	}

	public final void tick(Minecraft client) {
		if (enabled) {
			onTick(client);
		}
	}

	public final void receiveChat(String message) {
		if (enabled) {
			onChat(message);
		}
	}

	public final void updateTablist(List<String> rows) {
		if (enabled) {
			onTablistUpdate(rows);
		}
	}

	protected void onTick(Minecraft client) {
	}

	protected void onChat(String message) {
	}

	protected void onTablistUpdate(List<String> rows) {
	}

	protected void log(String message) {
		Logger.sendLog(formatMessage(message));
	}

	protected void send(String message) {
		Logger.sendMessage(formatMessage(message));
	}

	protected void logError(String message) {
		Logger.sendLog(formatMessage("Error: " + message));
	}

	protected void sendError(String message) {
		Logger.sendError(formatMessage(message));
	}

	protected void warn(String message) {
		Logger.sendWarning(formatMessage(message));
	}

	protected void note(String message) {
		Logger.sendNote(formatMessage(message));
	}

	protected String formatMessage(String message) {
		return "[" + getName() + "] " + message;
	}
}
