package com.example.blockminer.macro;

import com.example.blockminer.Config;
import com.example.blockminer.feature.FeatureManager;
import com.example.blockminer.macro.impl.MiningMacro;
import com.example.blockminer.macro.impl.RouteMiner.RouteMinerMacro;
import com.example.blockminer.macro.impl.CommissionMacro.CommissionMacro;
import com.example.blockminer.macro.impl.GlacialMacro.GlacialMacro;
import com.example.blockminer.util.Logger;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;

import java.util.List;

public final class MacroManager {
	private static final MacroManager INSTANCE = new MacroManager();
	private AbstractMacro currentMacro;

	private MacroManager() {
	}

	public static MacroManager getInstance() {
		return INSTANCE;
	}

	public AbstractMacro getCurrentMacro() {
		return currentMacro;
	}

	public int getMacroType() {
		return Config.macroType;
	}

	public void setMacroType(int macroType) {
		if (isEnabled()) {
			throw new IllegalStateException("Stop the active macro before changing its type.");
		}
		if (macroType < 0 || macroType > 3) {
			throw new IllegalArgumentException("Macro type must be between 0 and 3.");
		}
		Config.macroType = macroType;
	}

	public void toggle() {
		if (isEnabled()) {
			disable();
		} else {
			enable();
		}
	}

	public void enable() {
		if (isEnabled()) {
			return;
		}
		currentMacro = createMacro(Config.macroType);
		if (currentMacro == null) {
			Logger.sendError("Invalid macro type: " + Config.macroType);
			return;
		}
		FeatureManager.getInstance().enableAll();
		currentMacro.enable();
		if (!currentMacro.isEnabled()) {
			currentMacro = null;
			FeatureManager.getInstance().disableAll();
			return;
		}
		Logger.sendMessage(currentMacro.getName() + " enabled.");
	}

	public void disable() {
		if (currentMacro == null) {
			return;
		}
		AbstractMacro stopping = currentMacro;
		stopping.disable();
		FeatureManager.getInstance().disableAll();
		currentMacro = null;
		Logger.sendMessage(stopping.getName() + " stopped.");
	}

	public void pause() {
		if (currentMacro == null || !currentMacro.isRunning()) {
			return;
		}
		currentMacro.pause();
		FeatureManager.getInstance().pauseAll();
		Logger.sendMessage(currentMacro.getName() + " paused.");
	}

	public void resume() {
		if (currentMacro == null || !currentMacro.isPaused()) {
			return;
		}
		currentMacro.resume();
		FeatureManager.getInstance().resumeAll();
		Logger.sendMessage(currentMacro.getName() + " resumed.");
	}

	public boolean isEnabled() {
		return currentMacro != null && currentMacro.isEnabled();
	}

	public boolean isRunning() {
		return currentMacro != null && currentMacro.isRunning();
	}

	public void onTick(Minecraft client) {
		if (!isRunning() || client.player == null || client.level == null) {
			return;
		}
		currentMacro.onTick(client);
		if (currentMacro != null && !currentMacro.isEnabled()) {
			FeatureManager.getInstance().disableAll();
			currentMacro = null;
		}
	}

	public void onChat(String message) {
		if (isRunning()) {
			currentMacro.onChat(message);
		}
	}

	public void onTablistUpdate(List<String> rows) {
		if (isRunning()) {
			currentMacro.onTablistUpdate(List.copyOf(rows));
		}
	}

	public void onWorldRender() {
		if (isRunning()) {
			currentMacro.onWorldRender();
		}
	}

	public void onOverlayRender(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		if (isRunning()) {
			currentMacro.onOverlayRender(graphics, deltaTracker);
		}
	}

	public void onReceivePacket(Packet<?> packet) {
		if (isRunning()) {
			currentMacro.onReceivePacket(packet);
		}
	}

	private static AbstractMacro createMacro(int macroType) {
		return switch (macroType) {
			case 0 -> CommissionMacro.getInstance();
			case 1 -> GlacialMacro.getInstance();
			case 2 -> MiningMacro.getInstance();
			case 3 -> RouteMinerMacro.getInstance();
			default -> null;
		};
	}
}
