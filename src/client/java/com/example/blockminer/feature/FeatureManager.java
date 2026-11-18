package com.example.blockminer.feature;

import com.example.blockminer.feature.impl.AutoGetStats.AutoGetStats;
import com.example.blockminer.feature.impl.AutoChestUnlocker;
import com.example.blockminer.feature.impl.AutoCommissionClaim;
import com.example.blockminer.feature.impl.AutoDrillRefuel.AutoDrillRefuel;
import com.example.blockminer.feature.impl.AutoMobKiller.AutoMobKiller;
import com.example.blockminer.feature.impl.AutoSell;
import com.example.blockminer.feature.impl.AutoWarp;
import com.example.blockminer.feature.impl.LagDetector;
import com.example.blockminer.feature.impl.RouteBuilder;
import com.example.blockminer.feature.impl.RouteNavigator;
import com.example.blockminer.feature.impl.WorldScanner;
import com.example.blockminer.handler.GraphHandler;
import com.example.blockminer.handler.RouteHandler;
import com.example.blockminer.location.LocationTracker;
import com.example.blockminer.macro.MacroManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;

import java.util.LinkedHashSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class FeatureManager {
	private static final FeatureManager INSTANCE = new FeatureManager();
	public final Set<AbstractFeature> allFeatures = new LinkedHashSet<>();
	private final Set<AbstractFeature> pausedFeatures = new HashSet<>();
	private List<String> previousTablist = List.of();

	private FeatureManager() {
		allFeatures.add(AutoGetStats.getInstance());
		allFeatures.add(AutoWarp.getInstance());
		allFeatures.add(RouteNavigator.getInstance());
		allFeatures.add(AutoMobKiller.getInstance());
		allFeatures.add(AutoCommissionClaim.getInstance());
		allFeatures.add(AutoChestUnlocker.getInstance());
		allFeatures.add(AutoDrillRefuel.getInstance());
		allFeatures.add(LagDetector.getInstance());
		allFeatures.add(RouteBuilder.getInstance());
		allFeatures.add(AutoSell.getInstance());
		allFeatures.add(WorldScanner.getInstance());
	}

	public static FeatureManager getInstance() {
		return INSTANCE;
	}

	public void registerHooks() {
		RouteHandler.getInstance().loadData();
		GraphHandler.getInstance().loadBundledGraphs();
		ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
		ClientReceiveMessageEvents.CHAT.register((message, playerChatMessage, sender, boundChatType, timeStamp) ->
				onChat(message.getString()));
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> onChat(message.getString()));
	}

	public void enableAll() {
		allFeatures.forEach(feature -> {
			if (feature.shouldStartAtLaunch()) {
				feature.start();
			}
		});
	}

	public void disableAll() {
		allFeatures.stream().filter(AbstractFeature::isRunning).forEach(AbstractFeature::stop);
		pausedFeatures.clear();
	}

	public void pauseAll() {
		allFeatures.stream().filter(AbstractFeature::isRunning).forEach(feature -> {
			pausedFeatures.add(feature);
			feature.pause();
		});
	}

	public void resumeAll() {
		pausedFeatures.forEach(AbstractFeature::resume);
		pausedFeatures.clear();
	}

	public void onChat(String message) {
		allFeatures.forEach(feature -> feature.receiveChat(message));
	}

	public void onTablistUpdate(List<String> rows) {
		List<String> snapshot = List.copyOf(rows);
		if (snapshot.equals(previousTablist)) {
			return;
		}
		previousTablist = snapshot;
		allFeatures.forEach(feature -> feature.updateTablist(snapshot));
		MacroManager.getInstance().onTablistUpdate(snapshot);
	}

	private void onTick(Minecraft client) {
		if (client.player == null || client.level == null || client.gameMode == null) {
			return;
		}
		allFeatures.forEach(feature -> feature.tick(client));
		onTablistUpdate(LocationTracker.getGameState().getTabList());
	}
}
