package com.example.blockminer.location;

import com.example.blockminer.mixin.location.PlayerTabOverlayAccessor;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.world.level.GameType;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.Comparator;
import java.util.List;

public final class LocationInputAdapter {
	private LocationInputAdapter() {
	}

	public static List<String> readPlayerList(Minecraft client) {
		return readPlayerListRows(client).stream().map(TabListRow::plainText).toList();
	}

	public static List<TabListRow> readPlayerListRows(Minecraft client) {
		ClientPacketListener connection = client.getConnection();
		if (connection == null) {
			return List.of();
		}

		// TODO(verify): Vanilla's sorted accessor and comparator are private in 26.2; confirm this mirrored order against the rendered tab list in-game.
		PlayerTabOverlay tabOverlay = client.gui.hud.getTabList();
		return VanillaTabListOrder.order(connection.getListedOnlinePlayers(), LocationInputAdapter::vanillaOrderKey).stream()
				.map(tabOverlay::getNameForDisplay)
				.map(LocationInputAdapter::tabListRow)
				.toList();
	}

	private static VanillaTabListOrder.Key vanillaOrderKey(PlayerInfo info) {
		PlayerTeam team = info.getTeam();
		return new VanillaTabListOrder.Key(
				info.getTabListOrder(),
				info.getGameMode() == GameType.SPECTATOR,
				team == null ? "" : team.getName(),
				info.getProfile().name()
		);
	}

	private static TabListRow tabListRow(Component component) {
		String rawComponentJson = ComponentSerialization.CODEC
				.encodeStart(JsonOps.INSTANCE, component)
				.getOrThrow()
				.toString();
		return new TabListRow(rawComponentJson, component.getString());
	}

	public static List<String> readSidebarLines(Minecraft client) {
		if (client.level == null) {
			return List.of();
		}
		Scoreboard scoreboard = client.level.getScoreboard();
		Objective sidebar = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
		if (sidebar == null) {
			return List.of();
		}

		return scoreboard.listPlayerScores(sidebar).stream()
				.filter(entry -> !entry.isHidden())
				.sorted(Comparator.comparingInt(PlayerScoreEntry::value).reversed()
						.thenComparing(PlayerScoreEntry::owner, String.CASE_INSENSITIVE_ORDER))
				.limit(15)
				.map(entry -> PlayerTeam.formatNameForTeam(
						scoreboard.getPlayersTeam(entry.owner()),
						entry.ownerName()
				).getString())
				.toList();
	}

	public static String readSidebarTitle(Minecraft client) {
		if (client.level == null) {
			return "";
		}
		Objective sidebar = client.level.getScoreboard().getDisplayObjective(DisplaySlot.SIDEBAR);
		return sidebar == null ? "" : sidebar.getDisplayName().getString();
	}

	public static List<String> readTabFooter(Minecraft client) {
		Component footer = ((PlayerTabOverlayAccessor) client.gui.hud.getTabList()).blockminer$getFooter();
		if (footer == null) {
			return List.of();
		}
		return footer.getString().lines().toList();
	}

	public static String readTabFooterRaw(Minecraft client) {
		Component footer = ((PlayerTabOverlayAccessor) client.gui.hud.getTabList()).blockminer$getFooter();
		return footer == null ? "" : ComponentSerialization.CODEC
				.encodeStart(JsonOps.INSTANCE, footer)
				.getOrThrow()
				.toString();
	}

	public record TabListRow(String rawText, String plainText) {
	}
}
