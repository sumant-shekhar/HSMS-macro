package com.example.blockminer.feature.impl.AutoMobKiller;

import com.example.blockminer.Config;
import com.example.blockminer.KeyBindUtil;
import com.example.blockminer.RotationHandler;
import com.example.blockminer.feature.AbstractFeature;
import com.example.blockminer.feature.impl.AutoMobKiller.states.AutoMobKillerState;
import com.example.blockminer.feature.impl.AutoMobKiller.states.StartingState;
import com.example.blockminer.pathfinder.AStarPathFinder;
import com.example.blockminer.pathfinder.ActionCosts;
import com.example.blockminer.pathfinder.Goal;
import com.example.blockminer.pathfinder.PathExecutor;
import com.example.blockminer.pathfinder.PathfindingSnapshot;
import com.example.blockminer.pathfinder.WorldMoveCalculator;
import com.example.blockminer.util.EntityUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AutoMobKiller extends AbstractFeature {
	private static final AutoMobKiller INSTANCE = new AutoMobKiller();
	private static final ExecutorService PATH_SEARCH = Executors.newSingleThreadExecutor(task -> {
		Thread thread = new Thread(task, "blockminer-mob-path-search");
		thread.setDaemon(true);
		return thread;
	});

	private final Set<String> mobsToKill = new HashSet<>();
	private final Set<LivingEntity> blacklistedMobs = new HashSet<>();
	private AutoMobKillerState currentState;
	private MKError error = MKError.NONE;
	private LivingEntity targetMob;
	private net.minecraft.world.phys.Vec3 targetMobOriginalPos;
	private long pathGeneration;
	private boolean pathSearchPending;

	private AutoMobKiller() {
	}

	public static AutoMobKiller getInstance() {
		return INSTANCE;
	}

	@Override
	public String getName() {
		return "AutoMobKiller";
	}

	public void start(Collection<String> mobNames, String weaponName) {
		Objects.requireNonNull(mobNames, "mobNames");
		if (mobNames.isEmpty()) {
			sendError("Specify at least one mob name.");
			return;
		}
		if (mc.player == null || !holdWeapon(weaponName)) {
			sendError("The requested weapon must be in the hotbar.");
			stop();
			return;
		}
		stop();
		mobsToKill.addAll(mobNames.stream().filter(name -> name != null && !name.isBlank()).toList());
		if (mobsToKill.isEmpty()) {
			sendError("Specify at least one non-empty mob name.");
			return;
		}
		error = MKError.NONE;
		enabled = true;
		transitionTo(new StartingState());
		log("Mob killer started.");
	}

	public void stop() {
		pathGeneration++;
		pathSearchPending = false;
		enabled = false;
		if (currentState != null) {
			currentState.onEnd(this);
		}
		currentState = null;
		PathExecutor.getInstance().stop();
		RotationHandler.getInstance().stop();
		KeyBindUtil.stopMovement();
		resetStatesAfterStop();
	}

	@Override
	public void resetStatesAfterStop() {
		mobsToKill.clear();
		blacklistedMobs.clear();
		targetMob = null;
		targetMobOriginalPos = null;
	}

	public MKError getError() {
		return error;
	}

	public void setError(MKError error) {
		this.error = Objects.requireNonNull(error);
	}

	public Set<String> getMobsToKill() {
		return Set.copyOf(mobsToKill);
	}

	public LivingEntity getTargetMob() {
		return targetMob;
	}

	public void setTargetMob(LivingEntity targetMob) {
		this.targetMob = targetMob;
	}

	public net.minecraft.world.phys.Vec3 getTargetMobOriginalPos() {
		return targetMobOriginalPos;
	}

	public void setTargetMobOriginalPos(net.minecraft.world.phys.Vec3 position) {
		targetMobOriginalPos = position;
	}

	public Set<LivingEntity> getBlacklistedMobs() {
		return blacklistedMobs;
	}

	public boolean requestPathToTarget() {
		Minecraft client = Minecraft.getInstance();
		LivingEntity target = targetMob;
		ClientLevel level = client.level;
		if (client.player == null || level == null || target == null || !target.isAlive()) {
			return false;
		}

		BlockPos start = com.example.blockminer.PlayerUtil.getBlockStandingOn(client.player);
		BlockPos destination = EntityUtil.nearbyBlock(target);
		PathfindingSnapshot snapshot;
		try {
			snapshot = PathfindingSnapshot.capture(level, start, destination);
		} catch (IllegalArgumentException | IllegalStateException exception) {
			logError("Could not prepare a path to the target: " + exception.getMessage());
			return false;
		}
		long generation = ++pathGeneration;
		pathSearchPending = true;
		CompletableFuture.supplyAsync(() -> {
			ActionCosts costs = new ActionCosts();
			return new AStarPathFinder(start.getX(), start.getY(), start.getZ(),
					new Goal(destination.getX(), destination.getY(), destination.getZ(), costs),
					new WorldMoveCalculator(snapshot, costs)).calculatePath();
		}, PATH_SEARCH).whenComplete((result, failure) -> client.execute(() -> {
			if (!enabled || generation != pathGeneration || target != targetMob || client.level != level) {
				return;
			}
			pathSearchPending = false;
			if (failure != null) {
				Throwable cause = failure instanceof CompletionException && failure.getCause() != null
						? failure.getCause()
						: failure;
				logError("Path search failed: " + cause.getMessage());
				return;
			}
			if (result == null) {
				logError("No path was found to the selected mob.");
				return;
			}
			PathExecutor.getInstance().start(result);
		}));
		return true;
	}

	public boolean isPathSearchPending() {
		return pathSearchPending;
	}

	public boolean holdWeapon(String name) {
		if (name == null || name.isBlank() || mc.player == null) {
			return false;
		}
		int slot = com.example.blockminer.util.FeatureInventory.findHotbarItem(mc, name);
		if (slot == -1) {
			return false;
		}
		mc.player.getInventory().setSelectedSlot(slot);
		return true;
	}

	public void transitionTo(AutoMobKillerState nextState) {
		if (currentState == nextState) {
			return;
		}
		if (currentState != null) {
			currentState.onEnd(this);
		}
		currentState = nextState;
		if (currentState == null) {
			enabled = false;
			PathExecutor.getInstance().stop();
			KeyBindUtil.stopMovement();
			resetStatesAfterStop();
			return;
		}
		currentState.onStart(this);
	}

	@Override
	protected void onTick(Minecraft client) {
		if (client.player == null || client.level == null) {
			stop();
			return;
		}
		if (client.gui.screen() != null) {
			PathExecutor.getInstance().stop();
			KeyBindUtil.stopMovement();
			return;
		}
		if (currentState != null) {
			AutoMobKillerState activeState = currentState;
			AutoMobKillerState nextState = activeState.onTick(this);
			if (enabled && currentState == activeState) {
				transitionTo(nextState);
			}
		}
	}

	public enum MKError {
		NONE,
		NO_ENTITIES
	}
}
