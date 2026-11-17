package com.example.blockminer.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.awt.Color;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class RenderUtil {
	private static final int MAX_PENDING_REQUESTS = 4_096;
	private static final ConcurrentLinkedQueue<DrawRequest> PENDING = new ConcurrentLinkedQueue<>();
	private static final AtomicInteger PENDING_COUNT = new AtomicInteger();
	private static final AtomicBoolean OVERFLOW_REPORTED = new AtomicBoolean();
	private static final AtomicBoolean SCALE_NOTICE_REPORTED = new AtomicBoolean();

	private RenderUtil() {
	}

	public static void drawPoint(Vec3 position, Color color) {
		enqueue(new PointRequest(position, color.getRGB(), 0.1F));
	}

	public static void drawLine(Vec3 start, Vec3 end, Color color) {
		enqueue(new LineRequest(start, end, color.getRGB(), 1.5F));
	}

	public static void outlineBlock(BlockPos position, Color color) {
		enqueue(new BoxRequest(new AABB(position), color.getRGB(), false));
	}

	public static void outlineBox(AABB box, Color color) {
		enqueue(new BoxRequest(box, color.getRGB(), false));
	}

	public static void drawBlock(BlockPos position, Color color) {
		enqueue(new BoxRequest(new AABB(position), color.getRGB(), true));
	}

	public static void drawBox(AABB box, Color color) {
		enqueue(new BoxRequest(box, color.getRGB(), true));
	}

	public static void drawMultiLineText(List<String> lines, GuiGraphicsExtractor graphics, Color color, float scale) {
		Minecraft client = Minecraft.getInstance();
		int screenWidth = client.getWindow().getGuiScaledWidth();
		int y = 50;
		for (String line : lines) {
			graphics.text(client.font, line, (screenWidth - client.font.width(line)) / 2, y,
					color.getRGB(), true);
			y += 22;
		}
		reportUnsupportedHudScale(scale);
	}

	public static void drawCenterTopText(String text, GuiGraphicsExtractor graphics, Color color) {
		drawCenterTopText(text, graphics, color, 3.0F);
	}

	public static void drawCenterTopText(String text, GuiGraphicsExtractor graphics, Color color, float scale) {
		Minecraft client = Minecraft.getInstance();
		int x = (client.getWindow().getGuiScaledWidth() - client.font.width(text)) / 2;
		graphics.text(client.font, text, x, 50, color.getRGB(), true);
		reportUnsupportedHudScale(scale);
	}

	public static void drawText(String text, double x, double y, double z, float scale) {
		if (!Float.isFinite(scale) || scale <= 0.0F) {
			Logger.sendWarning("Ignored world text with a nonpositive or nonfinite scale.");
			return;
		}
		enqueue(new TextRequest(text, new Vec3(x, y, z), Color.WHITE.getRGB(), scale));
	}

	public static void drawTracer(Vec3 target, Color color) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			Logger.sendLog("Skipped tracer because there is no local player.");
			return;
		}
		drawLine(client.player.getEyePosition(), target, color);
	}

	public static void renderQueued() {
		DrawRequest request;
		while ((request = PENDING.poll()) != null) {
			PENDING_COUNT.decrementAndGet();
			request.render();
		}
	}

	private static void enqueue(DrawRequest request) {
		Objects.requireNonNull(request);
		if (PENDING_COUNT.incrementAndGet() > MAX_PENDING_REQUESTS) {
			PENDING_COUNT.decrementAndGet();
			if (OVERFLOW_REPORTED.compareAndSet(false, true)) {
				Logger.sendWarning("World-render request queue is full; further requests are dropped.");
			}
			return;
		}
		PENDING.add(request);
	}

	private static void reportUnsupportedHudScale(float scale) {
		if (scale != 1.0F && SCALE_NOTICE_REPORTED.compareAndSet(false, true)) {
			Logger.sendLog("HUD helper text uses the native 26.2 font size; the extractor has no verified scale operation.");
		}
	}

	private sealed interface DrawRequest permits BoxRequest, LineRequest, PointRequest, TextRequest {
		void render();
	}

	private record BoxRequest(AABB box, int color, boolean filled) implements DrawRequest {
		@Override
		public void render() {
			GizmoStyle style = filled
					? GizmoStyle.strokeAndFill(color, 2.0F, color)
					: GizmoStyle.stroke(color, 2.0F);
			Gizmos.cuboid(box, style);
		}
	}

	private record LineRequest(Vec3 start, Vec3 end, int color, float width) implements DrawRequest {
		@Override
		public void render() {
			Gizmos.line(start, end, color, width);
		}
	}

	private record PointRequest(Vec3 position, int color, float size) implements DrawRequest {
		@Override
		public void render() {
			Gizmos.point(position, color, size);
		}
	}

	private record TextRequest(String text, Vec3 position, int color, float scale) implements DrawRequest {
		@Override
		public void render() {
			Gizmos.billboardText(text, position, TextGizmo.Style.forColorAndCentered(color).withScale(scale));
		}
	}
}
