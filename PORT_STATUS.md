# MightyMiner Port Status

Port order: `01` → `06` → `04` → `03` → `02` → `08` → `07` → `05`.

Module 01's pure algorithm has been ported and its synthetic self-tests pass, but verification is PARTIAL: move-cost dispatch remains pending in module 02, so the pathfinder is not connected to a real movement adapter. Module 06 Phase A is marked complete after a clean build and test verification; its in-game behavior is still untested. No module behavior has been verified in-game.

| Done | Module | Status |
|---|---|---|
| [x] | 01 — Pathfinding, pure algorithm | pure algorithm done; Moves cost dispatch remains pending in module 02 |
| [x] | 06 — Rotation and input control | Phase A complete: client camera rotation, key presses, and debug commands build and pass project checks; in-game testing needed; Phase B pending |
| [ ] | 04 — Block selection | PARTIAL: `/mmmine start` and `/mmmine stop` are wired; full-block target defaults are restricted to the seven requested vanilla blocks; user in-game verification remains |
| [ ] | 03 — Location | PARTIAL: Area/Dungeon and Unicode parsing, vanilla-equivalent ordered TAB rows, pure commission parsing, and /mmdump compile; synthetic tests pass; live-server behavior is NOT PROVEN |
| [ ] | 02 — Pathfinding, Minecraft-bound | Not started; verification PARTIAL: all 21 reference files lack a Module 02 port in src; required modules 04 and 03 remain incomplete |
| [ ] | 08 — Features, handlers, routes, entry point | Not started |
| [ ] | 07 — Macros | Not started |
| [ ] | 05 — GUI | Not started |

## Build and test notes

- The earlier `./gradlew clean build` passed on Gradle 9.7.1 / Java 27 with a JVM restricted-method warning and a Gradle immutable-cache warning. The latest clean build details and warning count are recorded in the module 06 verification below.
- `./gradlew pathfindingSelfTest` passed all six synthetic scenarios: heap ordering/relocation, A* obstacle detour, flat-ground straight line, one-block ascent, one-block descent, and unreachable-goal termination/failure result (6 passed, 0 failed).
- Preserved the 1.8.9-derived movement-cost constants; they need retuning against 26.2 physics.
- Fixed the heap's right-child boundary condition, made heap relocation support changed priorities in either direction, and replaced the coordinate-hash map key with an equality-checked coordinate key.
- Fixed `PathNode.equals` to safely reject unrelated objects.
- The standalone path result and `MoveCalculator` boundary avoid importing module 02's world context and movement classes; those integration points remain unported.
- Module 01 has no stubs or `TODO(verify)` items. Module 06 adds the tick-hook `TODO(verify)` documented below. `BlockPos` compiles against the 26.2 project dependency, but was not separately checked in IDE autocomplete or decompiled source.
- A source-wide old-name scan found `org.lwjgl.glfw.GLFW` in the pre-existing `BlockMinerClient.java`; this is modern GLFW rather than an LWJGL 2 API and is outside module 01.
- In-game path execution, movement-cost tuning, and integration with module 02 remain untested.

## Module 06 — Rotation and input control (Phase A)

- Implemented in client source: `Angle`, `AngleUtil`, `Target`, `RotationConfiguration` (including its nested `Ease` enum), `RotationHandler`, `KeyBindUtil`, and `RotationDebugCommands`; `BlockMinerClient` drives rotation and timed key releases from `ClientTickEvents.END_CLIENT_TICK`.
- `./gradlew build` passes, including all six existing pathfinding self-test scenarios. This verifies compilation only; neither debug command has been run in-game.
- In-game checks: run `/mmrot <yaw> <pitch> <ms>` in a world and verify the camera eases to the requested yaw/pitch; run `/mmkey <w|a|s|d|jump> <ms>` and verify the selected input is held for the requested duration, then released. Both commands accept durations from 1 through 60,000 ms; pitch is limited to -90 through 90 degrees.
- Fixed the Ease selection off-by-one so all five easing functions, including `EASE_OUT_CIRC`, can be selected. CLIENT rotations do not use `(0, 0)` as an initialization sentinel. Pitch Bezier control points now use pitch distance, the two control points get independent random signs, and elapsed time uses monotonic `System.nanoTime()` rather than wall-clock time.
- The server-side `(0, 0)` initialization behavior is absent from Phase A because server rotation is not implemented. `RotationType.SERVER` remains a configuration value, but `RotationHandler` rejects it explicitly until Phase B.
- `TODO(verify)`: the guide's documented `END_CLIENT_TICK` callback has no partial-tick input. Confirm in-game that tick-stepped camera easing is acceptable, or replace it with a suitable 26.2 frame hook if smoothness requires it.
- Phase B pending: server rotation through movement packets and player head model; `StrafeUtil` and jump/strafe-yaw mixin; `MouseUngrab`; player and Minecraft macro-running mixins. The legacy click/accessor helpers are also not part of this client key-press slice.
- Reference-layout note: this checkout stores module files in `reference/06_rotation_and_input/` (not `reference/MightyMiner_core_for_port/06_rotation_and_input/`).

## Module 06 Phase A verification (2026-11-17)

- **Verdict: PASS** for the Phase A build/test/coverage/leftover checks requested. This is not an in-game behavior claim.
- `./gradlew clean build`: `BUILD SUCCESSFUL`; 9 actionable tasks executed. One JVM restricted-method warning group was printed (four `WARNING:`-prefixed lines); no Gradle warning was printed during this run.
- `./gradlew test --rerun-tasks pathfindingSelfTest`: `BUILD SUCCESSFUL`. The Gradle test report records 1 test, 0 failures, 0 skipped. The pathfinding self-test separately reports 6 scenarios, 0 failures. Module 06 has no dedicated automated tests.
- All 12 files in `reference/06_rotation_and_input/` and `Angle.java`, `AngleUtil.java`, `Target.java` in `reference/04_block_selection/` are accounted for: the Phase A classes are ported, and the remaining mixins/accessors/features are listed as Phase B or intentionally skipped above; no reference file is MISSING from the inventory. See the verification report for the per-file mapping.
- Source scans found no Forge, Polyfrost, 1.8.9, old entity/player/keybinding/rotation names, Yarn-style names, or LWJGL 2 key-code cases. The only `org.lwjgl` import is the pre-existing `org.lwjgl.glfw.GLFW` import in `BlockMinerClient.java`, which is modern GLFW and allowed by the check.
- `javap` against the mapped 26.2 client/common jars verified the `LocalPlayer` rotation setters/getters, `KeyMapping` state methods, Options key fields, and used vector/math signatures. `javap` against Fabric API jars verified the `END_CLIENT_TICK` callback and client command callback/source feedback signatures. These checks verify API names/signatures, not in-game behavior.
- The only `TODO(verify)` is whether tick-stepped `END_CLIENT_TICK` easing has acceptable smoothness; the callback signature was confirmed by `javap` and carries no partial-tick argument. This is an unresolved behavior choice, not a guessed API name.
- The one explicit unimplemented guard is the intentional `UnsupportedOperationException` for `RotationType.SERVER` in `RotationHandler`, pending Phase B; no other module 06 stubs were found. The `return null` in `AStarPathFinder` is the documented no-path result, not a stub.
- **Still needs in-game testing:** `/mmrot <yaw> <pitch> <ms>` should ease the camera to the requested rotation; `/mmkey <w|a|s|d|jump> <ms>` should hold then release the requested mapped key. Neither has been run in-game.

## Module 01 type translations

| Reference file | Minecraft/Forge or adjacent types | 26.2 translation |
|---|---|---|
| `AStarPathFinder.kt` | `Long2ObjectMap`, `Long2ObjectOpenHashMap` (fastutil); `Path`, `CalculationContext` from module 02 | `HashMap<PositionKey, PathNode>` avoids lossy coordinate hashing; pure `PathResult`; `MoveCalculator` boundary |
| `ActionCost.kt` | None | Pure Java implementation; Kotlin math translated to `Math` |
| `BinaryHeapOpenSet.kt` | None | Pure Java implementation |
| `Goal.kt` | `CalculationContext` from module 02 | Receives the pure `ActionCosts` model |
| `IGoal.kt` | None | No type replacement needed |
| `MovementResult.kt` | `net.minecraft.util.BlockPos` | `net.minecraft.core.BlockPos` (mapping marks the name unverified; confirmed by compilation) |
| `Moves.kt` | `CalculationContext` and movement implementations from module 02 | Move values retain their offsets/types. The reference enum's cost-dispatch calls are intentionally deferred until module 02; `MoveCalculator` is the current boundary |
| `PathNode.kt` | `net.minecraft.util.BlockPos` | `net.minecraft.core.BlockPos` (mapping marks the name unverified; confirmed by compilation) |

No Forge types occur in module 01. The pure algorithm is accepted as done; movement-cost dispatch remains explicitly assigned to module 02. No unverified API names or in-game behavior are claimed as proven.

## Module 04 — Block selection (verification: PARTIAL)

- **Status: PARTIAL.** The module compiles and its tests pass. `/mmmine start [block_id ...] [priority]` now invokes `BlockMiner.start(...)`, and `/mmmine stop` invokes `stop()`. With no IDs, both `/mmmine start` and `/mmblocks` use only coal block, iron block, gold block, redstone block, lapis block, diamond block and emerald block, each at priority 1 by default. IDs accept either `minecraft:block_name` or `block_name`; `/mmmine start` accepts a trailing positive integer priority applied to every selected block. Old Mithril/gemstone enum mappings remain available but are not part of either default set. User in-game verification is still required; module 04 remains unchecked.
- Added `Config` with plain fields in place of module 04's `MightyMinerConfig` references. Values are in-memory defaults, not persisted or exposed in a settings screen.
- Re-keyed the legacy 1.8.9 state-ID list to native 26.2 block registry identifiers in `MineableBlock`. The candidate scanner scores `(hardness / miningSpeed * miningCoefficient + angleChange * angleCoefficient + distanceSquared * distanceCoefficient) / priority`, lowest first. The legacy hard-coded hardness table was replaced with `BlockState#getDestroySpeed(BlockGetter, BlockPos)`; `javap` confirmed the mapped method signature. `./gradlew build` also compiled the modern collision-shape and `ClientLevel.clip(ClipContext)` calls.
- The scanner checks actual 26.2 `VoxelShape` collision bounds for support and two-block headroom, checks a six-face visible point with collider raycasts, retains the ±5 horizontal / −3..+4 vertical scan and 4-block center range, and moves the visited-position mark after the range check (fixing the original visited-before-range bug). The root heap bug is fixed; results are returned in increasing cost order.
- `/mmblocks [block_id ...]` scans selected native registry blocks around the player and logs up to five candidates with registry ID, coordinates, score, hardness, angle and distance-squared. Default targets are only the seven full blocks above; stone, ores and obsidian are not defaults but can be passed explicitly as vanilla IDs.
- **Mixin decisions:** `MixinChunk` and `BlockChangeEvent` are omitted because `BreakingState` polls the current block each client tick, so the event is unnecessary to this state machine. `MixinBlockStainedGlassPane` is implemented as a collision-shape Mixin for stained panes when `Config.miscFullBlock` is enabled. A plain check/event cannot change the actual engine collision shape. Both 26.2 collision-shape overload descriptors and Mixin's `JAVA_25` compatibility level were verified against the mapped jars; actual injection behavior is still untested in-game.
- `ApplyAbilityState` is a stub that immediately continues to block selection; ability activation, cooldown chat tracking and retries are not implemented. The legacy precision-miner crit-particle aim callback is also not ported because the bundle supplies no particle hook or directly equivalent Fabric event. `Config.precisionMiner` is retained but currently has no effect.
- `canWalkBetween`'s module 02 pathfinder integration and the old walk-toward-target behavior are not reproduced. Selection is constrained to blocks within 4 blocks of accessible nearby standing positions instead. The old randomized 20-points-per-face sampling and face-render checks are replaced by deterministic 4×4 face samples and 26.2 collider raycasts.
- **TODO(verify):** Confirm Hypixel/SkyBlock's actual server-sent block identifiers for the listed Mithril/gemstone disguises; verify native hardness-based ranking against real block break times; test the stained-pane full-collision toggle, candidate visibility/selection and `/mmblocks` in a 26.2 client. These cannot be established by compilation alone.
- **Verification (2026-11-17):** After adding the command integration, `./gradlew clean build` passed using Java 25, including `minHeapSelfTest` (2,000 randomized trials plus duplicate/negative-cost checks) and `pathfindingSelfTest` (6 scenarios, 0 failures). This confirms compilation and synthetic tests, not in-game behavior.
- **Build setup:** This project targets Java 25. The wrapper script invokes `java` from `PATH`, so set both `JAVA_HOME` to a JDK 25 installation and prepend `$JAVA_HOME/bin` to `PATH`; setting only `JAVA_HOME` did not override the machine's default Java 27 in this environment.
- **Remaining verification:** Confirm command argument handling, target selection, native destroy-speed ranking and candidate raycasts in-game; test `/mmblocks` and `/mmmine` in creative singleplayer, and separately test the stained-pane full-collision toggle and Mixin injection. Verify legacy SkyBlock disguised block mappings only if they will be used; they are not in the requested defaults. Module 04 remains PARTIAL until the user completes in-game testing.

## Module 03 — Location (verification: PARTIAL)

- Implemented in `src/client/java/com/example/blockminer/location/`: `Location`, `SubLocation`, pure `LocationTextParser`, `GameStateHandler`, `LocationInputAdapter`, `LocationTracker`, `/mmloc`, and `/mmdump <label>`. Added pure `commission/CommissionTabListParser`. The requested path `reference/MightyMiner_core_for_port/03_location/` is absent in this checkout; its 11 source files are under `reference/03_location/`.
- **Clean build (2026-11-17):** Ran `./gradlew clean build`: `BUILD SUCCESSFUL in 9s`; 11 actionable tasks, all 11 executed. Final lines: `> Task :build`, `BUILD SUCCESSFUL in 9s`, `11 actionable tasks: 11 executed`, `Configuration cache entry stored.` Four lines start with `WARNING:` (one restricted-method warning group); zero Gradle warnings.
- **Module tests (2026-11-17):** `LocationTextParserSelfTest` reports 7 synthetic scenarios; `CommissionTabListParserSelfTest` reports 3 synthetic scenarios. Fixtures are synthetic, not claimed to match live-server text.
- **Reference coverage audit:** `Location.java` → `location/Location.java`; `SubLocation.java` → `location/SubLocation.java`. `GameStateHandler.java` → `location/GameStateHandler.java` (server-IP tracking from the reference is not ported). `MixinNetHandlerPlayClient.java` → tab/footer data is polled by `LocationTracker` through `LocationInputAdapter`; particle/entity hooks are intentionally outside location scope. `MixinNetworkManager.java` → sidebar is read by `LocationInputAdapter` each tick; generic packet events and the packet-triggered line event are intentionally replaced by polling. `ScoreboardUtil.java` → cold parsing/reset in `LocationTextParser` and `GameStateHandler`; objective cache is replaced by direct sidebar reads. `UpdateScoreboardEvent.java`, `UpdateScoreboardLineEvent.java`, and `UpdateTablistEvent.java` → intentionally skipped as event classes; tick polling supplies their needed location inputs. `UpdateTablistFooterEvent.java` → footer is read by the adapter. `TablistUtil.java` → player/footer reads are adapted; legacy caching and `getGlaciteComs()` remain absent. The latter is a reference caller dependency for future macro work, not part of this follow-up.
- **26.2 API evidence:** Local mapped 26.2 jars and `javap` confirmed `ClientPacketListener#getListedOnlinePlayers()`, `PlayerInfo#getTabListOrder()`, `PlayerInfo#getGameMode()`, `PlayerInfo#getTeam()`, `PlayerInfo#getProfile()`, `PlayerTeam#getName()`, and `PlayerTabOverlay#getNameForDisplay(PlayerInfo)`. `PlayerTabOverlay.getPlayerInfos()` and `PLAYER_COMPARATOR` are private, so `VanillaTabListOrder` mirrors the bytecode rule: order descending, spectators later, team name, then case-insensitive profile name; it also applies vanilla's 80-entry limit. Its in-game equivalence remains `TODO(verify)`. The sidebar objective, score-entry formatting, footer accessor, and inventory checks remain as before.
- **Events/mixin choice:** Verified Fabric API JOIN/DISCONNECT, CHAT/GAME receive, and END_CLIENT_TICK callback signatures. `ClientPlayNetworking` registers custom-payload handlers only; it has no generic callback for vanilla scoreboard/player-info packets. Therefore the tracker polls current client state every tick instead of adding those packet mixins. The footer accessor is the sole module mixin; its target field is present in the mapped 26.2 client. Mixin application at game startup is NOT PROVEN.
- **Quirks:** Preserved the 18-location order with `LIMBO`, `LOBBY`, `KNOWHERE` last; unknown top-level areas map to `KNOWHERE`, unknown sub-locations remain nullable and are guarded; Limbo remains one player-list row plus empty inventory; Lobby remains non-`SKYBLOCK` title plus final `www.hypixel.net` line. Location text now accepts `Area:` and `Dungeon:`; scoreboard cleanup removes only the `⏣`/`ф` markers and formatting, preserving other Unicode. The legacy event/cache model was replaced by polling. Legacy `getGlaciteComs()` and server-IP tracking remain unimplemented.
- **Source audit:** One strict old-API scan hit: `src/client/java/com/example/blockminer/BlockMinerClient.java` imports `org.lwjgl.glfw.GLFW`; it is modern GLFW rather than an LWJGL 2 call, and is outside this module. No other requested legacy/Yarn-name patterns were found. No module-local stubs or uncertain Minecraft/Fabric API names were found.
- **TODO(verify):** Verify the mirrored row ordering against vanilla-rendered data, actual Area/Dungeon and sidebar strings, Unicode locations, widget boundaries, collector/generic commission progress formats, footer conventions, `/mmdump` output/path permissions, and actual mixin application in-game. The commission collector regex is a reference-derived unproven starting point; any `DONE` row remains only parsed progress and is not a claim signal.
- **Verdict: PARTIAL.** Build and synthetic tests pass, but legacy `getGlaciteComs()` and server-IP tracking remain missing/deferred, the unrelated strict-scan `org.lwjgl.glfw.GLFW` hit remains, and live server/mixin behavior is unverified. Do not mark Module 03 done.

## Module 02 — Pathfinding, Minecraft-bound (verification: PARTIAL; not ported)

- **Current state:** No Module 02 implementation was made. Prerequisite modules 04 and 03 are still marked incomplete above, so Module 02 remains not started and not done.
- **Clean build (2026-11-17):** Ran `./gradlew clean build`: `BUILD SUCCESSFUL in 6s`; 11 actionable tasks, all 11 executed. Final output:
  ```
  > Task :build

  BUILD SUCCESSFUL in 6s
  11 actionable tasks: 11 executed
  Configuration cache entry reused.
  ```
  Four `WARNING:`-prefixed lines were printed for one JVM restricted-method warning group; no Gradle warning was printed.
- **Tests:** There are no Module 02 tests because no Module 02 implementation/tests exist. `./gradlew pathfindingSelfTest` passed the existing Module 01 synthetic suite: 6 scenarios, 0 failures. The clean build also ran location (7 synthetic scenarios), commission parsing (3 synthetic scenarios), and heap (2,000 randomized trials plus duplicate-cost checks) self-tests; these do not verify Module 02.
- **Reference inventory:** The user-specified `reference/MightyMiner_core_for_port/02_pathfinding_mc_adapter/` directory does not exist in this checkout; its 21 files are under `reference/02_pathfinding_mc_adapter/`. Every listed file lacks a Module 02 port in `src/`: `BlockStateAccessor.kt`, `BlockStateInterfaceAccessWrapper.kt`, `BlockUtil.kt`, `CalculationContext.kt`, `Extension.kt`, `IChunkProviderClient.java`, `IMovement.kt`, `IPlayerContext.kt`, `MixinChunkProviderClient.java`, `Movement.kt`, `MovementAscend.kt`, `MovementDescend.kt`, `MovementDiagonal.kt`, `MovementFall.kt`, `MovementHelper.kt`, `MovementTraverse.kt`, `Path.kt`, `PathExecutor.java`, `Pathfinder.java`, `PlayerContext.kt`, and `Ref.kt`. The similarly named pure pathfinder files under `src/client/java/com/example/blockminer/pathfinder/` are Module 01 and do not implement these references.
- **Source checks:** No Module 02-specific old Minecraft/Forge/Yarn APIs, stubs, or `TODO(verify)` markers exist because no Module 02 source was added. The workspace scan did find `org.lwjgl.glfw.GLFW` in `BlockMinerClient.java`; this is modern GLFW but matches the requested broad `org.lwjgl` search. No other searched legacy names or targeted Yarn-style package/class names were found.
- **Quirks not dealt with:** The duplicate smoothing routines in `Path.kt`, suspect diagonal movement calculation, legacy collision-height thresholds and 1.8.9-tuned movement costs, smoothing corner/wall risk, and the `PlayerUtil.getBlockStandingOn()` snow/slab offset assumption have not been ported, fixed, or validated.
- **In-game verification:** None. No `/mmpath` or `/mmwalk` commands exist in `src/`.
- **Verdict: PARTIAL.** The project clean-builds and the unrelated pure pathfinding self-test passes, but the entire 21-file adapter is missing, no Module 02 tests exist, and prerequisite modules 04 and 03 are incomplete. Do not mark Module 02 done.
