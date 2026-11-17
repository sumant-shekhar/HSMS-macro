package com.example.blockminer.macro.impl.GlacialMacro;

import com.example.blockminer.Config;
import com.example.blockminer.MineableBlock;
import com.example.blockminer.PlayerUtil;
import com.example.blockminer.feature.impl.AutoGetStats.AutoGetStats;
import com.example.blockminer.location.LocationTracker;
import com.example.blockminer.macro.AbstractMacro;
import com.example.blockminer.macro.impl.GlacialMacro.states.GlacialMacroState;
import com.example.blockminer.macro.impl.GlacialMacro.states.StartingState;
import com.example.blockminer.macro.impl.GlacialMacro.states.MiningState;
import com.example.blockminer.macro.impl.GlacialMacro.states.PathfindingState;
import com.example.blockminer.macro.impl.GlacialMacro.states.ClaimingCommissionState;
import com.example.blockminer.macro.impl.GlacialMacro.states.NewLobbyState;
import com.example.blockminer.util.Strings;
import com.example.blockminer.util.helper.route.RouteWaypoint;
import com.example.blockminer.handler.GraphHandler;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.phys.Vec3;

public final class GlacialMacro extends AbstractMacro {
	private static final GlacialMacro INSTANCE = new GlacialMacro();
	private final List<GlaciteVeins> typeToMine = new ArrayList<>();
	private final Map<VeinTarget, Long> previousVeins = new HashMap<>();
	private GlacialMacroState currentState;
	private VeinTarget currentVein;
	private AutoGetStats.PickaxeAbility pickaxeAbility = AutoGetStats.PickaxeAbility.NONE;
	private int miningSpeed;
	private int commissionCounter;

	private GlacialMacro() {
	}

	public static GlacialMacro getInstance() {
		return INSTANCE;
	}

	@Override
	public String getName() {
		return "Glacial Macro";
	}

	@Override
	public List<String> getNecessaryItems() {
		List<String> items = new ArrayList<>();
		if (!Config.miningTool.isBlank()) {
			items.add(Config.miningTool);
		}
		items.add(Strings.ASPECT_OF_THE_VOID_ITEM_NAME);
		items.add(Strings.ROYAL_PIGEON_ITEM_NAME);
		if (Config.drillRefuel) {
			items.add(Strings.ABIPHONE_ITEM_NAME);
		}
		return List.copyOf(items);
	}

	@Override
	public void onEnable() {
		typeToMine.clear();
		previousVeins.clear();
		currentVein = null;
		miningSpeed = 0;
		pickaxeAbility = AutoGetStats.PickaxeAbility.NONE;
		transitionTo(new StartingState());
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
		GlacialMacroState activeState = currentState;
		GlacialMacroState nextState = activeState.onTick(this);
		if (isEnabled() && currentState == activeState && nextState != activeState && nextState != null) {
			transitionTo(nextState);
		}
	}

	@Override
	public void onChat(String message) {
		if (message.contains(Strings.GLACIAL_COMMISSION_COMPLETED_CHAT)
				&& !message.contains(":")
				&& (currentState instanceof MiningState
				|| currentState instanceof PathfindingState)) {
			transitionTo(new ClaimingCommissionState());
		}
	}

	public void transitionTo(GlacialMacroState nextState) {
		if (nextState == currentState) {
			return;
		}
		timer.reset();
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

	public void updateMiningTasks() {
		long now = System.currentTimeMillis();
		previousVeins.entrySet().removeIf(entry -> now - entry.getValue() > 5 * 60_000L);
		typeToMine.clear();
		for (var entry : LocationTracker.getGameState().getCommissionParse().entries()) {
			GlaciteVeins vein = GlaciteVeins.getVeinFromName(entry.name());
			if (vein == null || entry.progress().type() == com.example.blockminer.commission.CommissionTabListParser.ProgressType.DONE) {
				continue;
			}
			Double progress = entry.progress().percent();
			if (progress != null && progress < 100.0D && !typeToMine.contains(vein)) {
				typeToMine.add(vein);
			}
		}
	}

	public VeinTarget findBestVein() {
		updateMiningTasks();
		if (typeToMine.isEmpty() || mc.player == null) {
			return null;
		}
		var start = PlayerUtil.getBlockStandingOn(mc.player);
		return typeToMine.stream()
				.flatMap(vein -> java.util.Arrays.stream(GlaciteVeins.getVeins(vein))
						.map(waypoint -> new VeinTarget(vein, waypoint)))
				.filter(target -> !previousVeins.containsKey(target))
				.min(Comparator.comparingDouble(target -> {
					double graphDistance = GraphHandler.getInstance().distance(
							getName(), start, target.waypoint());
					return graphDistance >= 0.0D ? graphDistance
							: target.waypoint().toVec3().distanceToSqr(
									new Vec3(start.getX(), start.getY(), start.getZ()));
				}))
				.orElse(null);
	}

	public List<MineableBlock> getBlocksToMine() {
		if (typeToMine.isEmpty()) {
			updateMiningTasks();
		}
		return typeToMine.stream()
				.flatMap(vein -> vein.getMineableBlocks().stream())
				.distinct()
				.toList();
	}

	public Map<Block, Integer> getBlockPriorities() {
		Map<Block, Integer> priorities = new LinkedHashMap<>();
		for (MineableBlock type : getBlocksToMine()) {
			type.blocks().forEach(block -> priorities.put(block, 1));
		}
		return Map.copyOf(priorities);
	}

	public boolean hasCompletedCommission() {
		return LocationTracker.getGameState().getCommissionParse().entries().stream()
				.anyMatch(entry -> entry.progress().type()
						== com.example.blockminer.commission.CommissionTabListParser.ProgressType.DONE
						|| entry.progress().percent() != null && entry.progress().percent() >= 100.0D);
	}

	public int getCold() {
		return LocationTracker.getGameState().getCold();
	}

	public int getColdThreshold() {
		return Config.coldThreshold;
	}

	public VeinTarget getCurrentVein() {
		return currentVein;
	}

	public void setCurrentVein(VeinTarget currentVein) {
		this.currentVein = currentVein;
	}

	public Map<VeinTarget, Long> getPreviousVeins() {
		return previousVeins;
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

	public void incrementCommissionCounter() {
		commissionCounter++;
	}

	public record VeinTarget(GlaciteVeins type, RouteWaypoint waypoint) {
	}
}
