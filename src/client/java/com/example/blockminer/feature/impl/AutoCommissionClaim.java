package com.example.blockminer.feature.impl;

import com.example.blockminer.Config;
import com.example.blockminer.RotationConfiguration;
import com.example.blockminer.RotationHandler;
import com.example.blockminer.Target;
import com.example.blockminer.feature.AbstractFeature;
import com.example.blockminer.util.CommissionUtil;
import com.example.blockminer.util.EntityUtil;
import com.example.blockminer.util.FeatureInventory;
import com.example.blockminer.util.Strings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;

import java.util.List;
import java.util.Optional;

public final class AutoCommissionClaim extends AbstractFeature {
	private static final AutoCommissionClaim INSTANCE = new AutoCommissionClaim();
	private State state = State.STARTING;
	private ClaimError claimError = ClaimError.NONE;
	private Optional<AbstractClientPlayer> emissary = Optional.empty();
	private List<String> nextCommissions = List.of();
	private int retryCount;
	private int claimMethod;
	private long openingStartedAt;
	private boolean waitingForPigeonCooldown;

	private AutoCommissionClaim() {
	}

	public static AutoCommissionClaim getInstance() {
		return INSTANCE;
	}

	@Override
	public String getName() {
		return "AutoCommissionClaim";
	}

	public void start() {
		stop();
		claimMethod = Config.commClaimMethod;
		if (claimMethod != 0 && claimMethod != 1) {
			claimError = ClaimError.INACCESSIBLE_NPC;
			sendError("Commission claim method must be 0 (emissary) or 1 (Royal Pigeon).");
			return;
		}
		claimError = ClaimError.NONE;
		nextCommissions = List.of();
		retryCount = 0;
		waitingForPigeonCooldown = false;
		enabled = true;
		state = State.STARTING;
		timer.reset();
	}

	@Override
	public void stop() {
		if (enabled) {
			RotationHandler.getInstance().stop();
		}
		enabled = false;
		emissary = Optional.empty();
		timer.reset();
		state = State.STARTING;
	}

	public void stop(ClaimError error) {
		claimError = error;
		stop();
	}

	public boolean succeeded() {
		return !enabled && claimError == ClaimError.NONE;
	}

	public ClaimError claimError() {
		return claimError;
	}

	public List<String> getNextCommissions() {
		return nextCommissions;
	}

	@Override
	protected void onChat(String message) {
		if (!enabled || claimMethod != 1 || state != State.CLAIMING
				|| !message.startsWith(Strings.COMMISSION_PIGEON_COOLDOWN_PREFIX)) {
			return;
		}
		if (++retryCount > 3) {
			stop(ClaimError.TIMEOUT);
			return;
		}
		log("Royal Pigeon is on cooldown; retrying after five seconds.");
		state = State.OPENING;
		openingStartedAt = System.nanoTime();
		waitingForPigeonCooldown = true;
		timer.schedule(5_000L);
	}

	@Override
	protected void onTick(Minecraft client) {
		if (client.player == null || client.level == null || client.gameMode == null) {
			stop(ClaimError.INACCESSIBLE_NPC);
			return;
		}
		switch (state) {
			case STARTING -> begin(client);
			case ROTATING -> rotateToNpc(client);
			case SWAPPING_TO_ALT -> swapToAlternateTool();
			case OPENING -> openCommissionMenu(client);
			case VERIFYING_GUI -> verifyCommissionMenu();
			case CLAIMING -> claimCommissions(client);
			case NEXT_COMM -> readCommissions();
			case ENDING -> finish(client);
		}
	}

	private void begin(Minecraft client) {
		if (claimMethod == 1) {
			if (!FeatureInventory.holdHotbarItem(client, Strings.ROYAL_PIGEON_ITEM_NAME)) {
				stop(ClaimError.NO_ITEMS);
				return;
			}
			state = State.OPENING;
			openingStartedAt = System.nanoTime();
		} else {
			state = State.ROTATING;
		}
	}

	private void rotateToNpc(Minecraft client) {
		if (isTimerRunning()) {
			return;
		}
		emissary = CommissionUtil.getClosestEmissary();
		if (emissary.isEmpty()) {
			stop(ClaimError.NPC_NOT_UNLOCKED);
			return;
		}
		if (client.player.distanceToSqr(emissary.get()) > 16.0D) {
			sendError("Emissary is too far away to interact with.");
			stop(ClaimError.INACCESSIBLE_NPC);
			return;
		}
		RotationHandler.getInstance().easeTo(new RotationConfiguration(
				new Target(emissary.get()), 500L, RotationConfiguration.RotationType.CLIENT, null));
		state = State.SWAPPING_TO_ALT;
		timer.schedule(1_000L);
	}

	private void swapToAlternateTool() {
		if (isTimerRunning()) {
			return;
		}
		if (Config.commSwapBeforeClaiming
				&& !FeatureInventory.holdHotbarItem(mc, Config.altMiningTool)) {
			stop(ClaimError.NO_ITEMS);
			sendError("The configured alternate mining tool is not in the hotbar.");
			return;
		}
		state = State.OPENING;
		openingStartedAt = System.nanoTime();
		timer.reset();
	}

	private void openCommissionMenu(Minecraft client) {
		if (waitingForPigeonCooldown && isTimerRunning()) {
			return;
		}
		waitingForPigeonCooldown = false;
		if (claimMethod == 0) {
			if (RotationHandler.getInstance().isEnabled()) {
				if (System.nanoTime() - openingStartedAt > 5_000_000_000L) {
					stop(ClaimError.INACCESSIBLE_NPC);
				}
				return;
			}
			Entity lookingAt = EntityUtil.getEntityLookingAt().orElse(null);
			if (emissary.isEmpty() || lookingAt != emissary.get()) {
				if (emissary.isEmpty()) {
					stop(ClaimError.NPC_NOT_UNLOCKED);
				} else if (System.nanoTime() - openingStartedAt > 5_000_000_000L) {
					stop(ClaimError.INACCESSIBLE_NPC);
				}
				return;
			}
			client.gameMode.interact(client.player, emissary.get(),
					new EntityHitResult(emissary.get()), InteractionHand.MAIN_HAND);
		} else {
			client.gameMode.useItem(client.player, InteractionHand.MAIN_HAND);
		}
		state = State.VERIFYING_GUI;
		timer.schedule(5_000L);
	}

	private void verifyCommissionMenu() {
		if (FeatureInventory.menu(mc) != null
				&& FeatureInventory.title(mc).contains(Strings.COMMISSION_MENU_TITLE)) {
			state = State.CLAIMING;
			timer.schedule(500L);
			return;
		}
		if (hasTimerEnded()) {
			sendError("Could not open the commission menu; current screen: " + FeatureInventory.title(mc));
			stop(ClaimError.INACCESSIBLE_NPC);
		}
	}

	private void claimCommissions(Minecraft client) {
		if (isTimerRunning()) {
			return;
		}
		if (FeatureInventory.menu(client) == null
				|| !FeatureInventory.title(client).contains(Strings.COMMISSION_MENU_TITLE)) {
			stop(ClaimError.INACCESSIBLE_NPC);
			return;
		}
		int slot = CommissionUtil.getClaimableCommissionSlot();
		if (slot == -1) {
			state = State.NEXT_COMM;
			timer.schedule(500L);
			return;
		}
		if (!FeatureInventory.clickContainerSlot(client, slot,
				net.minecraft.world.inventory.ContainerInput.PICKUP, 0)) {
			stop(ClaimError.INACCESSIBLE_NPC);
			return;
		}
		timer.schedule(500L);
	}

	private void readCommissions() {
		if (isTimerRunning()) {
			return;
		}
		if (FeatureInventory.menu(mc) != null) {
			nextCommissions = CommissionUtil.getCommissionNamesFromContainer();
		}
		state = State.ENDING;
		timer.schedule(1_000L);
	}

	private void finish(Minecraft client) {
		if (isTimerRunning()) {
			return;
		}
		if (FeatureInventory.menu(client) != null) {
			client.setScreenAndShow(null);
		}
		if (Config.commSwapBeforeClaiming
				&& !FeatureInventory.holdHotbarItem(client, Config.miningTool)) {
			stop(ClaimError.NO_ITEMS);
			sendError("The configured mining tool is not in the hotbar.");
			return;
		}
		stop();
	}

	public enum ClaimError {
		NONE,
		INACCESSIBLE_NPC,
		NO_ITEMS,
		TIMEOUT,
		NPC_NOT_UNLOCKED
	}

	private enum State {
		STARTING,
		ROTATING,
		SWAPPING_TO_ALT,
		OPENING,
		VERIFYING_GUI,
		CLAIMING,
		NEXT_COMM,
		ENDING
	}
}
