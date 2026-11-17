# SkyHanni TAB LIST, scoreboard, and commission findings

This is read-only research to inform our independent implementation. `Skyhanni_code/` is reference material only; its code was not copied.

## License and version

- The repository's [LICENSE](../Skyhanni_code/LICENSE) identifies itself as the GNU Lesser General Public License, version 2.1 (LGPL-2.1), at lines 1-3. Follow that license for any use of the repository. This report quotes only short pattern literals for identification; it does not reproduce implementation code.
- The checkout declares support for Minecraft 26.1 and 26.2, with `26.2` as the VCS version in [settings.gradle.kts](../Skyhanni_code/settings.gradle.kts#L49-L52); [stonecutter.gradle.kts](../Skyhanni_code/stonecutter.gradle.kts#L29) marks 26.2 active. The root [gradle.properties](../Skyhanni_code/gradle.properties) contains no Minecraft version or mapping selection.
- Source imports use Mojang-style names, for example `net.minecraft.client.multiplayer.PlayerInfo` and `net.minecraft.network.chat.Component` in [TabListData.kt](../Skyhanni_code/src/main/java/at/hannibal2/skyhanni/utils/TabListData.kt#L17-L22). The inspected build metadata does not explicitly declare Mojang versus Yarn/intermediary mappings. Therefore the source imports are evidence of the namespace used in source, not a mapping declaration.
- SkyHanni's active version matches our Minecraft 26.2 target. It is still unsafe to copy API snippets verbatim: SkyHanni supports more than one Minecraft version, uses Kotlin and compatibility wrappers, and its APIs and source structure may differ. Our project targets 26.2 with Java 25 and Mojang names; verify any API against our own build and current 26.2 sources.

## Tab list reading

- [TabListData.kt](../Skyhanni_code/src/main/java/at/hannibal2/skyhanni/utils/TabListData.kt#L28-L31) reads the local connection's `onlinePlayers`, sorts the entries with `TabPlayerComparator`, and obtains each row through `getNameForDisplay()`. [TabPlayerComparator](../Skyhanni_code/src/main/java/at/hannibal2/skyhanni/utils/TabListData.kt#L64-L72) orders non-spectators first, then by team name and profile name. The result is capped at 80 entries; if it is shorter than 80, its last entry is dropped.
- Player-info updates mark the cache dirty. A tick rereads the list and posts an update event only when its content changes; it separately reads header and footer from the tab overlay. A footer change has its own update event. See [TabListData.kt](../Skyhanni_code/src/main/java/at/hannibal2/skyhanni/utils/TabListData.kt#L75-L106). If the initial read happens off Hypixel, the code schedules a retry.
- `TabWidget` recognizes widget sections in the tab list. It defines separate `AREA` and `COMMISSIONS` header patterns and assigns the lines between widget headers to each section. See [TabWidget.kt](../Skyhanni_code/src/main/java/at/hannibal2/skyhanni/data/model/TabWidget.kt#L41-L45), [TabWidget.kt](../Skyhanni_code/src/main/java/at/hannibal2/skyhanni/data/model/TabWidget.kt#L184-L187), and [TabWidget.kt](../Skyhanni_code/src/main/java/at/hannibal2/skyhanni/data/model/TabWidget.kt#L488-L512). When matching a widget header, it removes color from the first line ([TabWidget.kt](../Skyhanni_code/src/main/java/at/hannibal2/skyhanni/data/model/TabWidget.kt#L430-L434)).
- The exact widget regexes are:

  ```text
  AREA:        (Area|Dungeon): (?<island>.*)
  COMMISSIONS: Commissions:
  ```

  Source: [TabWidget.kt](../Skyhanni_code/src/main/java/at/hannibal2/skyhanni/data/model/TabWidget.kt#L41-L45) and [TabWidget.kt](../Skyhanni_code/src/main/java/at/hannibal2/skyhanni/data/model/TabWidget.kt#L184-L187). The widget parser treats these as section boundaries; the `COMMISSIONS` header pattern does not parse each commission or its progress.
- `TabWidget` also offers an optional on-screen widget display. That GUI display is disabled by default and its configuration warns that the information must already be present in the tab list; it is not the source of the tab data. See [TabWidgetConfig.kt](../Skyhanni_code/src/main/java/at/hannibal2/skyhanni/config/features/gui/TabWidgetConfig.kt#L15-L38) and [TabWidgetDisplay.kt](../Skyhanni_code/src/main/java/at/hannibal2/skyhanni/features/gui/TabWidgetDisplay.kt#L55-L57).
- SkyHanni's general tab-list read path does not split data into multiple columns in the code examined. It works with the ordered row list and detects section-header lines.

## Scoreboard and area

- [ScoreboardData.kt](../Skyhanni_code/src/main/java/at/hannibal2/skyhanni/data/ScoreboardData.kt#L121-L160) reads the sidebar objective's score entries, converts their names to formatted text, and keeps raw and formatted update paths separate. It reverses the fetched list before publishing it.
- The `skyblock.area` repo-pattern fallback used by [HypixelData.kt](../Skyhanni_code/src/main/java/at/hannibal2/skyhanni/data/HypixelData.kt#L61-L69) is:

  ```text
  \s*§\d(?<symbol>.) §(?<color>.)(?<area>.*)
  ```

  The source documents examples with the `⏣` and `ф` markers at [HypixelData.kt](../Skyhanni_code/src/main/java/at/hannibal2/skyhanni/data/HypixelData.kt#L61-L64).
- [HypixelData.kt](../Skyhanni_code/src/main/java/at/hannibal2/skyhanni/data/HypixelData.kt#L183-L200) scans formatted sidebar lines while the client is on Hypixel SkyBlock. For the first matching row it strips color from the captured area. If `LocationFixData.fixLocation(...)` supplies a value for the current island, it uses that corrected name. It stores the symbol-bearing line and emits an area-change event only when the normalized area changes. The value is cleared on world change or disconnect ([HypixelData.kt](../Skyhanni_code/src/main/java/at/hannibal2/skyhanni/data/HypixelData.kt#L142-L151)).
- `IslandType` supplies fallback display names and API IDs and can also load repo-provided island data ([IslandType.kt](../Skyhanni_code/src/main/java/at/hannibal2/skyhanni/data/IslandType.kt#L16-L66), [IslandType.kt](../Skyhanni_code/src/main/java/at/hannibal2/skyhanni/data/IslandType.kt#L82-L90)). Its lookup helpers do not all handle unknown names identically: depending on the helper, an unknown name can yield `null`, `UNKNOWN`, or an exception ([IslandType.kt](../Skyhanni_code/src/main/java/at/hannibal2/skyhanni/data/IslandType.kt#L110-L114)).

## Commissions (patterns, names, locations)

### What SkyHanni parses

- SkyHanni's inspected tab-list code does **not** implement a general parser for commission name plus numeric progress. Its `COMMISSIONS` widget pattern recognizes the header only. The commission-specific feature found is [HighlightMiningCommissionMobs](../Skyhanni_code/src/main/java/at/hannibal2/skyhanni/features/mining/HighlightMiningCommissionMobs.kt#L31-L67): it matches tab-list lines by commission-name prefix after removing color and trimming, then considers a match active unless the original line ends in `DONE` ([HighlightMiningCommissionMobs.kt](../Skyhanni_code/src/main/java/at/hannibal2/skyhanni/features/mining/HighlightMiningCommissionMobs.kt#L79-L84)). It does not parse a percentage.
- Its name-to-entity-predicate list is: `Goblin Slayer`, `Star Sentry Puncher`, `Glacite Walker Slayer`, `Golden Goblin Slayer`, `Treasure Hoarder Puncher`, `Automaton Slayer`, `Team Treasurite Member Slayer`, `Yog Slayer`, `Thyst Slayer`, `Corleone Slayer`, and `Sludge Slayer` ([HighlightMiningCommissionMobs.kt](../Skyhanni_code/src/main/java/at/hannibal2/skyhanni/features/mining/HighlightMiningCommissionMobs.kt#L31-L52)). The class comments group entries under Dwarven Mines and Crystal Hollows, but its implemented feature-level island gate allows either island ([HighlightMiningCommissionMobs.kt](../Skyhanni_code/src/main/java/at/hannibal2/skyhanni/features/mining/HighlightMiningCommissionMobs.kt#L106-L107)); this is not a precise per-commission area mapping.
- `TunnelsMaps` contains repo-pattern fallbacks for commission-related **inventory/lore**, not for the tab-list progress row. The source fallback string values (as escaped in source) are:

  ```text
  mining.commisson.collector.colorless        (?<what>\\w+(?: \\w+)?) Collector
  mining.commisson.reward.glacite.colorless   - [\\d,]+ Glacite Powder
  mining.commisson.collector.invalid          Glacite|Scrap
  mining.commisson.completed.colorless        COMPLETED
  mining.commission.inventory                 Commissions
  ```

  Source: [TunnelsMaps.kt](../Skyhanni_code/src/main/java/at/hannibal2/skyhanni/features/mining/TunnelsMaps.kt#L118-L144). The first four keys visibly spell `commisson` with one `i` after `ss`. These literals are fallback values supplied to `RepoPattern`; they do not prove which value an external repository overrides at runtime. The source includes examples such as `Glacite Collector` and `- 1,010 Glacite Powder` ([TunnelsMaps.kt](../Skyhanni_code/src/main/java/at/hannibal2/skyhanni/features/mining/TunnelsMaps.kt#L119-L133)).

### Commission-to-location relationships

- A complete SkyHanni commission-name-to-area, waypoint, or map-coordinate table was **not found in Skyhanni_code**. `HighlightMiningCommissionMobs` maps some commission labels to entity predicates, not to route destinations.
- There is an indirect collector-map flow in [TunnelsMaps.kt](../Skyhanni_code/src/main/java/at/hannibal2/skyhanni/features/mining/TunnelsMaps.kt#L159-L162): it strips color from a collector name and finds a graph node name containing that uppercase value. It also loads a Glacite Tunnels graph ([TunnelsMaps.kt](../Skyhanni_code/src/main/java/at/hannibal2/skyhanni/features/mining/TunnelsMaps.kt#L233-L238)). The checked-in source does not establish a complete commission-to-area mapping or the active external graph/pattern values.
- SkyHanni's `IslandGraphs.kt` mentions “commission areas” in a planning comment, not in an implemented commission mapping ([IslandGraphs.kt](../Skyhanni_code/src/main/java/at/hannibal2/skyhanni/data/IslandGraphs.kt#L71-L73)).

### Comparison with our legacy commission references

- Our legacy [TablistUtil.java](../reference/03_location/TablistUtil.java#L93-L124) has `getGlaciteComs()`, with its own collector-progress regex and a material conversion list of 13 values: Amber, Sapphire, Amethyst, Ruby, Jade, Aquamarine, Onyx, Peridot, Citrine, Topaz, Tungsten, Umber, and Glacite. Unlike SkyHanni's code above, this legacy function parses percent progress and maps `DONE` to 100. Its callers are the legacy [GlacialMacro.java](../reference/07_macros/macro/impl/GlacialMacro/GlacialMacro.java#L141-L143), [PathfindingState.java](../reference/07_macros/macro/impl/GlacialMacro/states/PathfindingState.java#L48-L50), and [MiningState.java](../reference/07_macros/macro/impl/GlacialMacro/states/MiningState.java#L41-L43).

  The exact legacy Java pattern is:

  ```text
  (.+?) (Gemstone )?Collector: ?(\d{1,3}(\.\d+)?%|DONE)
  ```

  Source: [TablistUtil.java](../reference/03_location/TablistUtil.java#L93-L95). This pattern is MightyMiner reference code, not a SkyHanni pattern.
- Our legacy [CommissionUtil.java](../reference/08_features_and_handlers/util/CommissionUtil.java#L73-L95) searches for `Commissions:`, treats any following line containing `DONE` as `Claim Commission`, otherwise takes the text before `": "` and exact-matches it against the legacy [Commission.java](../reference/07_macros/macro/impl/CommissionMacro/Commission.java#L13-L31) name table. The legacy enum maps commission names to `SubLocation` and maintains route waypoint coordinates in [Commission.java](../reference/07_macros/macro/impl/CommissionMacro/Commission.java#L36-L72). These are our reference implementation, not SkyHanni behavior.
- `getGlaciteComs()` is not present in the current `src/` port; the above call sites are still in `reference/`. When Glacial Macro is ported, its callers need an equivalent parser or an explicit replacement, but there is no current `src/` caller to restore yet.

## Required game settings

- A Minecraft language setting, required scoreboard option, or requirement to enable a particular tab-list widget was **not found in Skyhanni_code**. The inspected patterns are English (`Area`, `Dungeon`, `Commissions:`), so those matchers assume English text; the code does not show a language guard ([TabWidget.kt](../Skyhanni_code/src/main/java/at/hannibal2/skyhanni/data/model/TabWidget.kt#L41-L45), [TabWidget.kt](../Skyhanni_code/src/main/java/at/hannibal2/skyhanni/data/model/TabWidget.kt#L184-L187)).
- The optional SkyHanni GUI widget-display toggle defaults to false. Its warning says the data must already exist in the tab list; it does not say the GUI display toggle makes the underlying tab data appear ([TabWidgetConfig.kt](../Skyhanni_code/src/main/java/at/hannibal2/skyhanni/config/features/gui/TabWidgetConfig.kt#L15-L38)).

## Differences from our code

- Our current [LocationInputAdapter.java](../src/client/java/com/example/blockminer/location/LocationInputAdapter.java#L21-L33) reads listed online players and gets each rendered display name. Unlike SkyHanni's cited path, it does not explicitly sort those entries with the spectator/team/profile comparator, apply the 80-entry cap, or drop the last row. It also does not produce widget sections or a cached tab update event; it rereads from `LocationTracker`'s tick hook ([LocationTracker.java](../src/client/java/com/example/blockminer/location/LocationTracker.java#L15-L24), [LocationTracker.java](../src/client/java/com/example/blockminer/location/LocationTracker.java#L32-L48)).
- Our [LocationTextParser.java](../src/client/java/com/example/blockminer/location/LocationTextParser.java#L7-L22) recognizes only a line beginning exactly `Area:` after trimming and formatting removal. SkyHanni's header allows both `Area:` and `Dungeon:` ([TabWidget.kt](../Skyhanni_code/src/main/java/at/hannibal2/skyhanni/data/model/TabWidget.kt#L41-L45)). Our location names are static exact matches ([Location.java](../src/client/java/com/example/blockminer/location/Location.java#L7-L52)); SkyHanni can apply repo-provided location corrections before publishing an area ([HypixelData.kt](../Skyhanni_code/src/main/java/at/hannibal2/skyhanni/data/HypixelData.kt#L183-L200)).
- Our scoreboard adapter sorts visible entries by score and truncates to 15 ([LocationInputAdapter.java](../src/client/java/com/example/blockminer/location/LocationInputAdapter.java#L35-L54)). Its parser selects the first line containing `⏣` or `ф`, strips formatting, and then drops all non-ASCII characters ([LocationTextParser.java](../src/client/java/com/example/blockminer/location/LocationTextParser.java#L23-L59)). This can discard meaningful Unicode letters in a location name; it also depends on those two exact markers. SkyHanni instead matches a symbol/color layout and extracts the named `area` group from the scoreboard line.
- Our formatting remover skips any character after `§` ([LocationTextParser.java](../src/client/java/com/example/blockminer/location/LocationTextParser.java#L61-L72)); the scoreboard sanitizer then keeps only ASCII ([LocationTextParser.java](../src/client/java/com/example/blockminer/location/LocationTextParser.java#L49-L59)). The legacy tab-list helper also assumes a rank-like `]` prefix, removes the first row as `"Players (x)"`, strips non-ASCII, and stops on a duplicate first player ([TablistUtil.java](../reference/03_location/TablistUtil.java#L57-L90)). Those assumptions can lose legitimate data if row order or display format changes.
- Our [GameStateHandler.java](../src/client/java/com/example/blockminer/location/GameStateHandler.java#L30-L49) chooses the location from the tab list's `Area:` row, with a special sidebar test for the lobby. Unknown area/sub-location names fall back to `KNOWHERE` ([LocationTextParser.java](../src/client/java/com/example/blockminer/location/LocationTextParser.java#L39-L46), [GameStateHandler.java](../src/client/java/com/example/blockminer/location/GameStateHandler.java#L35-L49)). SkyHanni's cited area path comes from the scoreboard and corrects changed display names when available.
- The current port has no commission parser in `src/`. The legacy parser and Glacite progress helper are in `reference/` only, and the future Glacial Macro has the three call sites listed above. The existing location parser tests use explicitly synthetic fixtures, not confirmed live-server samples ([LocationTextParserSelfTest.java](../src/test/java/com/example/blockminer/location/LocationTextParserSelfTest.java#L14-L57)).

## Proposed changes

1. **`src/client/java/com/example/blockminer/location/LocationInputAdapter.java`** — Add a clearly scoped tab-row/widget extraction layer that retains component text and row order, and can identify section headers without blindly discarding rows. Consider packet/update-driven caching if tick rereads prove unnecessary. **Why:** SkyHanni's tab list is an ordered list with separate header/footer and parsed widgets, while the legacy helper discards a leading row. **Risk:** server formatting and ordering can change; in-game verification and fixtures from actual captured rows are needed before relying on an exact sequence.
2. **`src/client/java/com/example/blockminer/location/LocationTextParser.java`** — Accept the verified area header variants (`Area:` and `Dungeon:`) and recognize scoreboard area lines based on their formatted marker structure; remove known formatting and markers without deleting all non-ASCII letters. **Why:** the existing parser is English-header/marker-specific and strips Unicode from scoreboard names. **Risk:** broad matching can select unrelated lines or mistake marker text; constrain patterns and retain raw lines for diagnostics.
3. **`src/client/java/com/example/blockminer/location/GameStateHandler.java`** and **`Location.java`** — Keep unknown names explicit and add a deliberate alias/correction mechanism for server-renamed area names rather than silently relying only on a fixed exact-name map. **Why:** SkyHanni's `LocationFixData` has a correction point and its lookup APIs distinguish unknown cases. **Risk:** stale aliases may misclassify areas; all aliases need provenance and test coverage.
4. **A new `src/client/java/com/example/blockminer/commission/CommissionTabListParser.java` (when commission porting begins)** — Keep section detection separate from parsing commission identity and progress; model `DONE` and numeric progress explicitly, and add an equivalent only for Glacite progress if the future Glacial Macro still needs it. **Why:** SkyHanni's widget header is not a progress parser, while legacy Glacial Macro callers consume `getGlaciteComs()`. **Risk:** exact line grammar may change; preserve raw text and avoid treating any arbitrary `DONE` in the section as a claimable commission.
5. **`src/test/java/com/example/blockminer/location/LocationTextParserSelfTest.java`** — Add tests for captured/verified colored area headers, `Dungeon:`, both scoreboard markers, Unicode names, unknown aliases, absent widgets, and tab row ordering. Add commission fixtures separately with percent and `DONE` cases. **Why:** current fixtures are synthetic and do not test server-observed formats. **Risk:** overfitting fixtures to one captured language/server version; label fixture provenance and include negative cases.

## Things SkyHanni does NOT answer

- A complete tab-list commission-name/progress parser, exact percentage row grammar for all commissions, or a full authoritative commission list was **not found in Skyhanni_code**.
- A complete commission-name-to-area/waypoint/coordinate mapping was **not found in Skyhanni_code**. The indirect Glacite graph-name lookup is not a general mapping.
- Active values for externally supplied `RepoPattern` overrides or the actual external graph-node names were not established from this checkout.
- A required Minecraft language, scoreboard setting, or tab-list visibility setting was **not found in Skyhanni_code**.
- Whether the current Hypixel server still supplies the same tab widgets, scoreboard rows, translations, or ordering in-game is not proven by this code inspection.
