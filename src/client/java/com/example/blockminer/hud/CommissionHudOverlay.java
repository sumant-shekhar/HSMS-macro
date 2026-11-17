package com.example.blockminer.hud;

import com.example.blockminer.Config;
import com.example.blockminer.location.LocationTracker;
import com.example.blockminer.macro.impl.CommissionMacro.Commission;
import com.example.blockminer.macro.impl.CommissionMacro.CommissionMacro;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

public final class CommissionHudOverlay {
	private CommissionHudOverlay() {
	}

	public static void render(GuiGraphicsExtractor graphics) {
		Minecraft client = Minecraft.getInstance();
		if (client.gui.screen() != null) {
			return;
		}
		if (Config.macroType != 0 || !Config.showDwarvenCommHUDOutside
				&& !LocationTracker.getGameState().inDwarvenMines()) {
			return;
		}

		CommissionMacro macro = CommissionMacro.getInstance();
		long uptimeSeconds = macro.getUptimeMillis() / 1_000L;
		int completed = macro.getCommissionCounter();
		int perHour = uptimeSeconds == 0L ? 0 : (int) (completed * 3_600L / uptimeSeconds);
		List<String> lines = new ArrayList<>();
		lines.add("Runtime: " + formatTime(uptimeSeconds) + (macro.isPaused() ? " (Paused)" : ""));
		lines.add("Commissions: " + completed + " (" + perHour + "/h)");
		lines.add("HOTM XP: " + formatNumber((long) completed * 400L));
		Commission current = macro.getCurrentCommission();
		if (macro.isEnabled() && current != null) {
			lines.add("Current: " + current.getName());
		}
		HudOverlayRenderer.draw(graphics, client, 8, 34,
				0xff6d82ff, "Dwarven Commission Macro", lines);
	}

	private static String formatTime(long seconds) {
		return String.format("%02d:%02d:%02d", seconds / 3_600L, seconds / 60L % 60L, seconds % 60L);
	}

	private static String formatNumber(long value) {
		return value >= 1_000L ? String.format("%.1fk", value / 1_000.0D).replace(".0k", "k")
				: Long.toString(value);
	}
}
