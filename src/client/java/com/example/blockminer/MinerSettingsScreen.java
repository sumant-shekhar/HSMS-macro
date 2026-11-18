package com.example.blockminer;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import com.example.blockminer.MiningTargets.Mode;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class MinerSettingsScreen extends Screen {
	private static final float MIN_SPEED = 1.0F;
	private static final float MAX_SPEED = 30.0F;
	private static final double VIEW_ANGLE_RANGE = MiningTargets.MAX_VIEW_ANGLE - MiningTargets.MIN_VIEW_ANGLE;
	private static final int PANEL_WIDTH = 360;
	private static final int PANEL_HEIGHT = 248;
	private final MinerState miner;
	private final List<Button> modeButtons = new ArrayList<>();
	private Tab activeTab = Tab.MINER;

	MinerSettingsScreen(MinerState miner) {
		this(miner, false);
	}

	MinerSettingsScreen(MinerState miner, boolean openModeTab) {
		super(Component.literal("Block Miner Settings"));
		this.miner = miner;
		if (openModeTab) {
			activeTab = Tab.MODE;
		}
	}

	@Override
	protected void init() {
		int left = panelLeft();
		int top = panelTop();
		int panelWidth = panelWidth();
		int tabGap = 6;
		int tabWidth = (panelWidth - 24 - tabGap * 3) / 4;
		int tabY = top + 39;

		for (int index = 0; index < Tab.values().length; index++) {
			Tab tab = Tab.values()[index];
			int tabX = left + 12 + index * (tabWidth + tabGap);
			addRenderableWidget(Button.builder(Component.literal(
					(activeTab == tab ? "> " : "") + tab.label), button -> showTab(tab))
					.bounds(tabX, tabY, tabWidth, 22)
					.build());
		}

		modeButtons.clear();
		switch (activeTab) {
			case MINER -> addMinerControls(left, top);
			case MODE -> addModeControls(left, top);
			case CLICKER -> addClickerControls(left, top);
			case SETTINGS -> addSettingsControls(left, top);
		}

		addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
				.bounds(width / 2 - 40, top + 218, 80, 20)
				.build());
	}

	@Override
	public void extractBackground(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(graphics, mouseX, mouseY, partialTick);
		int left = panelLeft();
		int top = panelTop();
		int right = left + panelWidth();
		int bottom = top + PANEL_HEIGHT;
		graphics.fill(left - 1, top - 1, right + 1, bottom + 1, 0xFF55D6B0);
		graphics.fill(left, top, right, bottom, 0xF011171B);
		graphics.fill(left, top, right, top + 34, 0xFF1B292B);
		graphics.fill(left + 12, top + 69, right - 12, top + 70, 0xFF334447);
		graphics.fill(left + 12, top + 39, left + 15, top + 61, 0xFF55D6B0);
		graphics.text(font, "BLOCK MINER", left + 18, top + 10, 0xFFF1F6F4, false);
		graphics.text(font, "CLIENT CONTROL PANEL", left + 18, top + 21, 0xFF9BB0AC, false);
		drawTabDetails(graphics, left, top);
	}

	private void addMinerControls(int left, int top) {
		addRenderableWidget(Button.builder(Component.literal("Start mining"), button -> {
			miner.start(Minecraft.getInstance());
			onClose();
		}).bounds(left + 24, top + 143, 148, 24).build());
		addRenderableWidget(Button.builder(Component.literal("Stop mining"), button -> {
			miner.stop(Minecraft.getInstance());
		}).bounds(left + 188, top + 143, 148, 24).build());
	}

	private void addModeControls(int left, int top) {
		@NonNull Mode[] modes = Mode.values();
		for (int index = 0; index < modes.length; index++) {
			@NonNull Mode mode = modes[index];
			int column = index % 2;
			int row = index / 2;
			Button modeButton = Button.builder(modeLabel(Objects.requireNonNull(mode)), button -> {
				miner.selectMode(Minecraft.getInstance(), Objects.requireNonNull(mode));
				refreshModeButtons();
			}).bounds(left + 24 + column * 162, top + 79 + row * 29, 150, 22).build();
			modeButtons.add(modeButton);
			addRenderableWidget(modeButton);
		}
	}

	private void addClickerControls(int left, int top) {
		String label = BlockMinerClient.isAutoClickEnabled() ? "Turn clicker off" : "Turn clicker on";
		addRenderableWidget(Button.builder(Component.literal(label), button -> {
			BlockMinerClient.toggleAutoClick(Minecraft.getInstance());
			button.setMessage(Component.literal(
					BlockMinerClient.isAutoClickEnabled() ? "Turn clicker off" : "Turn clicker on"));
			if (BlockMinerClient.isAutoClickEnabled()) {
				onClose();
			}
		}).bounds(left + 86, top + 145, 188, 24).build());
	}

	private void addSettingsControls(int left, int top) {
		float initialSpeed = RotationHelper.getMaxStepPerTick();
		addRenderableWidget(new AbstractSliderButton(
				left + 60,
				top + 132,
				240,
				20,
				Component.literal("Camera speed: " + String.format("%.1f", initialSpeed)),
				(initialSpeed - MIN_SPEED) / (MAX_SPEED - MIN_SPEED)
		) {
			@Override
			protected void updateMessage() {
				float speed = speedFromValue(value);
				setMessage(Component.literal("Camera speed: " + String.format("%.1f", speed)));
			}

			@Override
			protected void applyValue() {
				RotationHelper.setMaxStepPerTick(speedFromValue(value));
			}
		});
		double initialAngle = MiningTargets.getMaxViewAngle();
		addRenderableWidget(new AbstractSliderButton(
				left + 60,
				top + 162,
				240,
				20,
				Component.literal("View cone: " + Math.round(initialAngle) + " degrees"),
				(initialAngle - MiningTargets.MIN_VIEW_ANGLE) / VIEW_ANGLE_RANGE
		) {
			@Override
			protected void updateMessage() {
				setMessage(Component.literal("View cone: " + Math.round(viewAngleFromValue(value)) + " degrees"));
			}

			@Override
			protected void applyValue() {
				MiningTargets.setMaxViewAngle(viewAngleFromValue(value));
			}
		});
	}

	private void drawTabDetails(GuiGraphicsExtractor graphics, int left, int top) {
		switch (activeTab) {
			case MINER -> {
				graphics.text(font, "Mining", left + 24, top + 82, 0xFFF1F6F4, false);
				graphics.text(font, "Status: " + (miner.isEnabled() ? "ARMED" : "STOPPED"),
						left + 24, top + 103, miner.isEnabled() ? 0xFF70E0A7 : 0xFFB2C0BE, false);
				graphics.text(font, "Mode: " + miner.getSelectedMode().displayName(),
						left + 24, top + 120, 0xFF9BB0AC, false);
				graphics.text(font, "Command: !mithril", left + 24, top + 184, 0xFF718783, false);
				graphics.text(font, "Mining resumes when this panel closes", left + 24, top + 198,
						0xFF718783, false);
			}
			case MODE -> {
				graphics.text(font, "Choose a block mode to search for", left + 24, top + 78, 0xFF9BB0AC, false);
				graphics.text(font, "Command: !ore", left + 24, top + 198, 0xFF718783, false);
			}
			case CLICKER -> {
				graphics.text(font, "Auto clicker", left + 24, top + 88, 0xFFF1F6F4, false);
				graphics.text(font, BlockMinerClient.isAutoClickEnabled() ? "Status: ON" : "Status: OFF",
						left + 24, top + 111,
						BlockMinerClient.isAutoClickEnabled() ? 0xFF70E0A7 : 0xFFB2C0BE, false);
				graphics.text(font, "Right-click every 5s after closing this panel", left + 24, top + 128,
						0xFF9BB0AC, false);
				graphics.text(font, "Command: !click", left + 24, top + 184, 0xFF718783, false);
			}
			case SETTINGS -> {
				graphics.text(font, "Camera speed and target view", left + 24, top + 94, 0xFFF1F6F4, false);
				graphics.text(font, "View cone range: 30 to 180 degrees", left + 24, top + 112,
						0xFF9BB0AC, false);
			}
		}
	}

	private void showTab(Tab tab) {
		if (activeTab != tab) {
			activeTab = tab;
			clearWidgets();
			init();
		}
	}

	private void refreshModeButtons() {
		@NonNull Mode[] modes = Mode.values();
		for (int index = 0; index < modeButtons.size(); index++) {
			modeButtons.get(index).setMessage(modeLabel(modes[index]));
		}
	}

	private @NonNull Component modeLabel(@NonNull Mode mode) {
		return Component.literal((miner.getSelectedMode() == mode ? "* " : "  ") + mode.displayName());
	}

	private int panelWidth() {
		return Math.min(PANEL_WIDTH, Math.max(240, width - 16));
	}

	private int panelLeft() {
		return (width - panelWidth()) / 2;
	}

	private int panelTop() {
		return Math.max(8, (height - PANEL_HEIGHT) / 2);
	}

	private static float speedFromValue(double value) {
		return (float) (MIN_SPEED + value * (MAX_SPEED - MIN_SPEED));
	}

	private static double viewAngleFromValue(double value) {
		return MiningTargets.MIN_VIEW_ANGLE + value * VIEW_ANGLE_RANGE;
	}

	private enum Tab {
		MINER("Miner"),
		MODE("Mode"),
		CLICKER("Clicker"),
		SETTINGS("Settings");

		private final String label;

		Tab(String label) {
			this.label = label;
		}
	}
}