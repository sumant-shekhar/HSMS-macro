package com.example.blockminer.macro.impl.CommissionMacro;

import com.example.blockminer.Config;
import com.example.blockminer.feature.impl.AutoGetStats.AutoGetStats;
import com.example.blockminer.location.LocationTracker;
import com.example.blockminer.macro.AbstractMacro;
import com.example.blockminer.macro.impl.CommissionMacro.states.CommissionMacroState;
import com.example.blockminer.macro.impl.CommissionMacro.states.NewLobbyState;
import com.example.blockminer.macro.impl.CommissionMacro.states.StartingState;
import com.example.blockminer.macro.impl.CommissionMacro.states.WarpingState;
import com.example.blockminer.util.CommissionUtil;
import com.example.blockminer.util.FeatureInventory;
import com.example.blockminer.util.Strings;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CommissionMacro extends AbstractMacro {
	private static final CommissionMacro INSTANCE = new CommissionMacro();
	private CommissionMacroState currentState;
	private Commission currentCommission;
	private int miningSpeed;
	private AutoGetStats.PickaxeAbility pickaxeAbility = AutoGetStats.PickaxeAbility.NONE;
	private int commissionCounter;
	private boolean fuelLoreWarningSent;
	private static final Pattern DRILL_FUEL_PATTERN = Pattern.compile(Strings.DRILL_FUEL_LORE_PATTERN);

	private CommissionMacro() {
	}

	public static CommissionMacro getInstance() {
		return INSTANCE;
	}

	@Override
	public String getName() {
		return "Commission Macro";
	}

	@Override
	public List<String> getNecessaryItems() {
		List<String> items = new ArrayList<>();
		if (!Config.miningTool.isBlank()) {
			items.add(Config.miningTool);
		}
		if (!Config.slayerWeapon.isBlank()) {
			items.add(Config.slayerWeapon);
		}
		if (Config.commClaimMethod == 1) {
			items.add(Strings.ROYAL_PIGEON_ITEM_NAME);
		}
		if (Config.drillRefuel) {
			items.add(Strings.ABIPHONE_ITEM_NAME);
		}
		return List.copyOf(items);
	}

	@Override
	public void onEnable() {
		currentCommission = null;
		miningSpeed = 0;
		fuelLoreWarningSent = false;
		pickaxeAbility = AutoGetStats.PickaxeAbility.NONE;
		transitionTo(new StartingState());
		onTablistUpdate(LocationTracker.getGameState().getTabList());
	}

	@Override
	public void onDisable() {
		if (currentState != null) {
			currentState.onEnd(this);
			currentState = null;
		}
		AutoGetStats.getInstance().stop();
	}

	@Override
	public void onTick(net.minecraft.client.Minecraft client) {
		if (isLobbyChangeRequestedByFailsafe()) {
			transitionTo(new NewLobbyState());
		}
		if (currentState == null || isTimerRunning()) {
			return;
		}
		CommissionMacroState activeState = currentState;
		CommissionMacroState nextState = activeState.onTick(this);
		if (isEnabled() && currentState == activeState && nextState != activeState && nextState != null) {
			transitionTo(nextState);
		}
	}

	@Override
	public void onChat(String message) {
		if (message.contains(Strings.COMMISSION_COMPLETE_CHAT)) {
			commissionCounter++;
			log("Commission completion detected. Total completed: " + commissionCounter);
		}
	}

	@Override
	public void onTablistUpdate(List<String> rows) {
		if (currentState == null || currentState instanceof WarpingState || currentState instanceof NewLobbyState) {
			return;
		}
		Commission next = CommissionUtil.getCurrentCommissionsFromTablist().entries().stream()
				.map(entry -> Commission.getCommission(entry.name()))
				.filter(java.util.Objects::nonNull)
				.findFirst()
				.orElse(null);
		if (next != currentCommission) {
			currentCommission = next;
			if (next != null) {
				log("Active commission: " + next.getName());
			}
		}
	}

	public void transitionTo(CommissionMacroState nextState) {
		if (nextState == currentState) {
			return;
		}
		String previous = currentState == null ? "None" : currentState.getClass().getSimpleName();
		if (currentState != null) {
			currentState.onEnd(this);
		}
		currentState = nextState;
		if (nextState != null) {
			reportStateTransition(previous, nextState.getClass().getSimpleName());
			nextState.onStart(this);
		}
	}

	public Commission getCurrentCommission() {
		return currentCommission;
	}

	public void setCurrentCommission(Commission currentCommission) {
		this.currentCommission = currentCommission;
	}

	public int getMiningSpeed() {
		return miningSpeed;
	}

	public void setMiningSpeed(int miningSpeed) {
		this.miningSpeed = miningSpeed;
	}

	public AutoGetStats.PickaxeAbility getPickaxeAbility() {
		return pickaxeAbility;
	}

	public void setPickaxeAbility(AutoGetStats.PickaxeAbility pickaxeAbility) {
		this.pickaxeAbility = pickaxeAbility;
	}

	public int getCommissionCounter() {
		return commissionCounter;
	}

	public Integer getDrillRemainingFuel() {
		int slot = FeatureInventory.findInventoryItem(mc, Config.miningTool);
		if (slot == -1 || mc.player == null) {
			return null;
		}
		ItemLore lore = mc.player.getInventory().getItem(slot).get(DataComponents.LORE);
		if (lore != null) {
			for (var line : lore.lines()) {
				Matcher matcher = DRILL_FUEL_PATTERN.matcher(line.getString());
				if (matcher.find()) {
					try {
						return Integer.parseInt(matcher.group(1).replace(",", ""));
					} catch (NumberFormatException exception) {
						fuelLoreWarningSent = true;
						warn("Could not parse remaining drill fuel from item lore.");
						return null;
					}
				}
			}
		}
		if (!fuelLoreWarningSent && Config.miningTool.toLowerCase(java.util.Locale.ROOT).contains("drill")) {
			fuelLoreWarningSent = true;
			warn("Drill fuel lore did not match the configured pattern; auto-refuel detection is unavailable.");
		}
		return null;
	}
}
