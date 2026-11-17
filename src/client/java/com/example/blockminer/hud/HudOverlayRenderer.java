package com.example.blockminer.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;

final class HudOverlayRenderer {
	private HudOverlayRenderer() {
	}

	static void draw(GuiGraphicsExtractor graphics, Minecraft client, int x, int y, int accent,
			String title, List<String> lines) {
		int width = client.font.width(title);
		for (String line : lines) {
			width = Math.max(width, client.font.width(line));
		}
		width += 14;
		int height = 10 + (lines.size() + 1) * 11;
		graphics.fill(x, y, x + width, y + height, 0xb910151a);
		graphics.fill(x, y, x + 2, y + height, accent);
		graphics.text(client.font, title, x + 7, y + 5, accent, true);
		for (int index = 0; index < lines.size(); index++) {
			graphics.text(client.font, lines.get(index), x + 7, y + 16 + index * 11, 0xffe6ecef, true);
		}
	}
}
