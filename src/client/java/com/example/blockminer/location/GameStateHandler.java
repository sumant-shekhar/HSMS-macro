package com.example.blockminer.location;

import com.example.blockminer.commission.CommissionTabListParser;

import java.util.List;

public final class GameStateHandler {
	private Location currentLocation = Location.KNOWHERE;
	private SubLocation currentSubLocation = SubLocation.KNOWHERE;
	private String rawAreaLine;
	private String rawSubLocationLine;
	private String cleanedSubLocation;
	private String areaName;
	private String serverIp = "";
	private String scoreboardTitle = "";
	private List<String> tabList = List.of();
	private List<String> scoreboardLines = List.of();
	private CommissionTabListParser.ParseResult commissionParse =
			CommissionTabListParser.parse(List.of());
	private boolean godpotActive;
	private boolean cookieActive;
	private int cold;

	public void update(
			List<String> tabList,
			List<String> footerLines,
			String scoreboardTitle,
			List<String> scoreboardLines,
			boolean inventoryEmpty
	) {
		this.tabList = List.copyOf(tabList);
		this.scoreboardLines = List.copyOf(scoreboardLines);
		this.scoreboardTitle = scoreboardTitle;
		commissionParse = CommissionTabListParser.parse(tabList);

		LocationTextParser.ParsedText parsed = LocationTextParser.parse(tabList, scoreboardLines);
		rawAreaLine = parsed.rawAreaLine();
		areaName = parsed.areaName();
		rawSubLocationLine = parsed.rawSubLocationLine();
		cleanedSubLocation = parsed.cleanedSubLocation();
		currentSubLocation = parsed.subLocation() == null ? SubLocation.KNOWHERE : parsed.subLocation();
		cold = LocationTextParser.parseCold(scoreboardLines);

		if (tabList.size() == 1 && inventoryEmpty) {
			currentLocation = Location.LIMBO;
			currentSubLocation = SubLocation.KNOWHERE;
		} else if (rawAreaLine != null) {
			currentLocation = parsed.location();
		} else if (isLobby(scoreboardTitle, scoreboardLines)) {
			currentLocation = Location.LOBBY;
			currentSubLocation = SubLocation.KNOWHERE;
		} else {
			currentLocation = Location.KNOWHERE;
			currentSubLocation = SubLocation.KNOWHERE;
		}

		updateFooterFlags(footerLines);
	}

	public void reset() {
		currentLocation = Location.KNOWHERE;
		currentSubLocation = SubLocation.KNOWHERE;
		rawAreaLine = null;
		rawSubLocationLine = null;
		cleanedSubLocation = null;
		areaName = null;
		scoreboardTitle = "";
		tabList = List.of();
		scoreboardLines = List.of();
		commissionParse = CommissionTabListParser.parse(List.of());
		godpotActive = false;
		cookieActive = false;
		cold = 0;
	}

	void setServerIp(String serverIp) {
		this.serverIp = serverIp;
	}

	public void onMessage(String message) {
		if (LocationTextParser.isColdResetMessage(message)) {
			cold = 0;
		}
	}

	private static boolean isLobby(String scoreboardTitle, List<String> scoreboardLines) {
		return !LocationTextParser.sanitizeScoreboardLine(scoreboardTitle).contains("SKYBLOCK")
				&& !scoreboardLines.isEmpty()
				&& scoreboardLines.getLast().equalsIgnoreCase("www.hypixel.net");
	}

	private void updateFooterFlags(List<String> footerLines) {
		godpotActive = false;
		cookieActive = false;
		for (int index = 0; index < footerLines.size(); index++) {
			String line = footerLines.get(index);
			if (line.contains("Active Effects") && index + 1 < footerLines.size()) {
				godpotActive = footerLines.get(index + 1).contains("You have a God Potion active!");
			}
			if (line.contains("Cookie Buff") && index + 1 < footerLines.size()) {
				cookieActive = !footerLines.get(index + 1).contains("Not active!");
			}
		}
	}

	public Location getCurrentLocation() {
		return currentLocation;
	}

	public SubLocation getCurrentSubLocation() {
		return currentSubLocation;
	}

	public String getGameState() {
		if (currentLocation == Location.LIMBO) {
			return "Limbo";
		}
		if (currentLocation == Location.LOBBY) {
			return "Lobby";
		}
		return currentLocation.isSkyBlock() ? "SkyBlock" : "Unknown";
	}

	public boolean isPlayerInSkyBlock() {
		return currentLocation.isSkyBlock();
	}

	public boolean inDwarvenMines() {
		return currentLocation == Location.DWARVEN_MINES;
	}

	public String getRawAreaLine() {
		return rawAreaLine;
	}

	public String getAreaName() {
		return areaName;
	}

	public String getServerIp() {
		return serverIp;
	}

	public String getRawSubLocationLine() {
		return rawSubLocationLine;
	}

	public String getCleanedSubLocation() {
		return cleanedSubLocation;
	}

	public String getScoreboardTitle() {
		return scoreboardTitle;
	}

	public List<String> getTabList() {
		return tabList;
	}

	public CommissionTabListParser.ParseResult getCommissionParse() {
		return commissionParse;
	}

	public List<String> getScoreboardLines() {
		return scoreboardLines;
	}

	public boolean isGodpotActive() {
		return godpotActive;
	}

	public boolean isCookieActive() {
		return cookieActive;
	}

	public int getCold() {
		return cold;
	}
}
