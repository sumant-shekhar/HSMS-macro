package com.example.blockminer.hud;

import com.example.blockminer.Config;
import com.example.blockminer.location.LocationTracker;
import com.example.blockminer.location.SubLocation;
import com.example.blockminer.macro.impl.GlacialMacro.GlacialMacro;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;

public final class GlacialCommissionHudOverlay {
	private GlacialCommissionHudOverlay() {
	}

	public static void render(GuiGraphicsExtractor graphics) {
		Minecraft client = Minecraft.getInstance();
		if (client.gui.screen() != null) {
			return;
		}
		SubLocation subLocation = LocationTracker.getGameState().getCurrentSubLocation();
		boolean inGlacialArea = subLocation == SubLocation.GLACITE_TUNNELS
				|| subLocation == SubLocation.DWARVEN_BASE_CAMP;
		if (Config.macroType != 1 || !Config.showGlacialHUDOutside && !inGlacialArea) {
			return;
		}

		GlacialMacro macro = GlacialMacro.getInstance();
		long uptimeSeconds = macro.getUptimeMillis() / 1_000L;
		int completed = macro.getCommissionCounter();
		int perHour = uptimeSeconds == 0L ? 0 : (int) (completed * 3_600L / uptimeSeconds);
		List<String> lines = List.of(
				"Runtime: " + String.format("%02d:%02d:%02d", uptimeSeconds / 3_600L,
						uptimeSeconds / 60L % 60L, uptimeSeconds % 60L) + (macro.isPaused() ? " (Paused)" : ""),
				"Commissions: " + completed + " (" + perHour + "/h)",
				"HOTM XP: " + formatNumber((long) completed * 750L)
		);
		HudOverlayRenderer.draw(graphics, client, 8, 34,
				0xff39c6ff, "Glacial Commission Macro", lines);
	}

	private static String formatNumber(long value) {
		return value >= 1_000L ? String.format("%.1fk", value / 1_000.0D).replace(".0k", "k")
				: Long.toString(value);
	}
}
