package com.example.blockminer.hud;

import com.example.blockminer.Config;
import com.example.blockminer.location.LocationTracker;
import com.example.blockminer.macro.MacroManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

public final class DebugHudOverlay {
	private DebugHudOverlay() {
	}

	public static void render(GuiGraphicsExtractor graphics) {
		if (!Config.debugMode) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.gui.screen() != null) {
			return;
		}
		List<String> lines = new ArrayList<>();
		lines.add("Location: " + LocationTracker.getGameState().getCurrentLocation());
		lines.add("Sub-location: " + LocationTracker.getGameState().getCurrentSubLocation());
		if (client.player != null) {
			lines.add(String.format("Position: %.1f, %.1f, %.1f",
					client.player.getX(), client.player.getY(), client.player.getZ()));
		}
		if (MacroManager.getInstance().getCurrentMacro() != null) {
			lines.add("Macro: " + MacroManager.getInstance().getCurrentMacro().getName());
		}
		HudOverlayRenderer.draw(graphics, client, 8, 128, 0xffffb84d, "Block Miner Debug", lines);
	}
}
