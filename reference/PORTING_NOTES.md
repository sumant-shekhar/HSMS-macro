# MightyMiner core logic — porting bundle (Forge 1.8.9 → Fabric 26.2)

Source: MightyMinerV2 v2.7.3 (Kotlin + Java, Forge 1.8.9, Mixins, OneConfig, Lombok).
This bundle holds the parts that carry the real logic: **pathfinding, location detection, block selection, GUI, rotation/input control, macros, features and routes**.
**Not included on purpose:** the whole `failsafe/` package, `AudioManager`, and the unrelated mixins (FML handshake, sound manager, scoreboard log-spam patch). Macro and feature files that call failsafe code need those calls stubbed; see section 10.3.

Companion files in this folder: `MAPPING_1.8.9_to_26.2.md` (name translation table) and `FABRIC_26.2_GUIDE.md` (setup, API cheat sheet, doc links).

> **Read this first.** Nothing here compiles as-is on 26.2. Folder `01` ports with small edits; everything else is a rewrite that uses the old code as a spec, not as a base.
> Neither the author of this bundle nor an AI assistant could run the game, so every 26.2 API name below is a hint to verify against the current Fabric docs and the project's mappings. Do not let the AI guess class names.

---

## 0. How to use this with Cursor / Copilot

1. Create a fresh Fabric 26.2 mod from the official template. Get it building and launching first.
2. Put this folder inside the project (e.g. `/reference/`). Do not put it under `src/`.
3. Port **one module at a time**, in this order: `01` → `06` (camera + key presses first) → `04 (pure parts)` → `03` → `02` → `08` → `07` → `05`. Use `MAPPING_1.8.9_to_26.2.md` for names and `FABRIC_26.2_GUIDE.md` for project setup.
4. For each module, give the AI: the module's files, the section for it below, and the Fabric docs. Ask it to write new files under `src/`, not edit the originals.
5. After every module: build, run, fix errors, commit. Paste real compiler errors back in; do not ask it to "fix everything" in one go.

Suggested starter prompt:

```
I'm porting a Forge 1.8.9 mod to Fabric for Minecraft 26.2. The old code is in /reference.
Port module <NAME> only. Use /reference/PORTING_NOTES.md section <N> as the spec.
Rules: use the Mojang/Fabric names used by this project; if you are not sure an API exists in 26.2, say so
and leave a TODO instead of guessing. Keep the algorithm identical unless the notes list it as a known bug.
Write new files under src/main/java (or kotlin). Do not touch /reference.
```

---

## 1. Folder map

| Folder | What it is | Port effort |
|---|---|---|
| `01_pathfinding_pure` | A* search, open set, goal/heuristic, move cost model, move enum | **Low** — mostly keeps working |
| `02_pathfinding_mc_adapter` | World access, "can stand/walk here" rules, per-move rules, path smoothing, path follower | **High** — rewrite |
| `03_location` | Where the player is (area / sub-area), parsed from tab list + scoreboard | **Medium** — logic stays, data sources change |
| `04_block_selection` | Choose which block to mine next; scoring; visibility/raycast; mining state machine | **Medium** — scoring is portable, world calls are not |
| `05_gui` | Config screen, HUD overlays, inventory/container GUI helpers, drawing | **High** — different config + render systems |
| `06_rotation_and_input` | Smooth camera rotation (client or server-side), key presses, movement-yaw override, mouse ungrab and their mixins | **High** — hooks into player/movement internals |
| `07_macros` | Macro framework and the 4 macros (Commission, Glacial, Mining, Route Miner) as state machines | **Medium** — logic stays, strings/flows need re-checking |
| `08_features_and_handlers` | Feature framework, auto-warp/mob-kill/claim/etc., route and graph handling, entry point, shipped route JSONs | **Medium** |
| `00_original_build_reference` | Original Gradle, properties and mixin list | Reference only; do not reuse |

Folders `01`–`06` are flattened (no package folders; names are unique within each). `07` and `08` keep their original package sub-folders because file names collide (several `StartingState`, `MiningState`, …).

---

## 2. Module 01 — Pathfinding, pure algorithm (`01_pathfinding_pure`)

**Mechanism.** Baritone-style A* over integer block coordinates.
- `AStarPathFinder` — main loop. Pops the cheapest node from the open set, stops when `goal.isAtGoal`, otherwise asks each entry of `Moves` for a cost and a destination, and relaxes the neighbour. Nodes are cached in a `Long2ObjectOpenHashMap` keyed by `PathNode.longHash(x,y,z)`. `requestStop()` lets another thread cancel it.
- `BinaryHeapOpenSet` — 1-indexed binary heap that stores its position inside each node (`heapPosition`) so `relocate` is O(log n).
- `PathNode` — `costSoFar` (g), `costToEnd` (h), `totalCost` (f), `parentNode`, `heapPosition`.
- `Goal` / `IGoal` — exact-block goal. Heuristic = octile distance (straight + diagonal×√2) × sprint cost per block, plus a vertical term: climbing costs `6.2344` per block, descending uses the 2-block-fall cost ÷ 2.
- `ActionCosts` (file `ActionCost.kt`) — all costs are in **ticks**, derived from 1.8.9 movement physics: walk/sprint/sneak speed from movement factors (0.1 / 0.13 / 0.03), jump cost simulated tick by tick (jump boost aware), fall cost for 1..256 blocks (`N_BLOCK_FALL_COST`), ladder, water and soul-sand costs. `INF_COST = 1e6` means "impossible".
- `Moves` — enum of 16 moves: 4 traverse, 4 ascend, 4 descend, 4 diagonal. Each entry calls a static `calculateCost` on a movement class (which lives in folder 02).
- `MovementResult` — mutable output holder (`x,y,z,cost`) reused across calls to avoid allocation.

**Depends on Minecraft:** only `net.minecraft.util.BlockPos` in `PathNode.getBlock()` and `MovementResult.getDest()`. Replace with the 26.2 block position class.
**Depends on other libs:** fastutil (`Long2ObjectOpenHashMap`) — Minecraft already ships it, but confirm it's on the compile classpath.

**Physics warning.** The numbers in `ActionCosts` (gravity 0.08, drag 0.98, jump 0.42, friction constants) are the 1.8.9 values. Movement in newer versions differs (sprint-jump, swimming, new sneak speed, step height and others). The search still works with wrong costs, but paths will be less optimal and some "jump" moves may be mispriced. Re-derive or re-tune them.

**Known issue.** `Path.kt` (folder 02) has two near-duplicate smoothing routines (`getSmoothedPath` and the unused `reconstructPath`). Port `getSmoothedPath` only.

---

## 3. Module 02 — Pathfinding, Minecraft-bound (`02_pathfinding_mc_adapter`)

This is where 1.8.9 and 26.2 differ most.

### 3.1 World snapshot — `BlockStateAccessor`, `BlockStateInterfaceAccessWrapper`, `IChunkProviderClient`, `MixinChunkProviderClient`, `CalculationContext`
- At the start of a search, the code copies the client's loaded chunk list into a hash map (via an accessor mixin on `ChunkProviderClient.chunkListing`) and answers `get(x,y,z)` from that. Reason: the search runs on a **worker thread**, so it must not hit the live world.
- `BlockStateInterfaceAccessWrapper` is a fake `IBlockAccess` so vanilla methods like `isPassable` can be called against the snapshot.
- `CalculationContext` bundles the snapshot, the player, costs, and `maxFallHeight = 20`. It reads the player's jump-boost level.

**Port plan:** keep the idea (thread-safe read-only block lookup). On 26.2, look for a supported way to read chunks from a worker thread; the old mixin target almost certainly does not exist. Do not assume the world can be read from another thread safely. If unsure, take a snapshot on the main thread (e.g. a region around start→goal) and search that.

### 3.2 Walk rules — `MovementHelper`
Pure rule functions on block states. They decide:
- `canWalkThrough` — air, open doors/gates, carpet and snow (position dependent), still water (but not flowing or under lily/liquid), blocked by fire/web/tripwire/cocoa/skulls/trapdoors/iron doors/cauldrons/ladders.
- `canStandOn` — full cubes, stairs, slabs, glass, chests, farmland/grass, sea lantern, ladders, redstone block, and water **only if** a lily pad/carpet sits on it. Lava is never stand-on.
- Stair/slab helpers (`isBottomSlab`, `isValidStair`, `hasTop`) and ladder helpers (`canWalkIntoLadder`).

**Port plan:** the *rules* carry over; the *block checks* do not. Replace `block == Blocks.X`/`is BlockStairs` with 26.2 equivalents (tags such as stairs/slabs/doors/carpets/trapdoors are worth using). `getValue(BlockLiquid.LEVEL)`, `BlockSlab.HALF`, `BlockStairs.FACING` become block-state properties with new names. Many 1.8.9 blocks are split or renamed (wool/glass/prismarine variants no longer use metadata). `Block.isPassable(...)` has no direct equivalent; use collision shape emptiness.

### 3.3 Per-move cost — `MovementTraverse`, `MovementAscend`, `MovementDescend`, `MovementDiagonal`, `MovementFall`, `Movement`, `IMovement`
Each exposes `calculateCost(ctx, x, y, z, destX, destZ, res)`. Common pattern: check there is a standable block at the destination and 2 blocks of headroom, then choose a cost from the **height difference of collision boxes**:
- Traverse: `diff <= 0.5` → sprint cost; `<= 1.0` → jump cost; else impossible.
- Ascend: same with a `1.125` limit; also needs headroom above the source (y+3).
- Descend: reads `sourceHeight - destHeight`; if the block below the destination is not standable it falls through to `freeFallCost`.
- Diagonal: composed from the above and flagged in the source comments as a rewrite candidate ("Todo: rewrite its shit", "IS PROBABLY FUCKED") — treat it as the least trustworthy piece.
- `MovementFall` just reuses descend with `y-1`.

`Movement` and `IMovement` instance classes take a `MightyMiner mm` field and are **not used by the search** (it uses the static `calculateCost` through `Moves`). You can drop `Movement`/`IMovement`/instance constructors.

**Port plan:** the collision height comes from `getCollisionBoundingBox(...).maxY`; on 26.2 use the block's collision shape max Y. Keep the thresholds, then test on stairs, slabs, snow layers and carpets, because those are exactly the cases the thresholds were tuned for.

### 3.4 Line-of-walk smoothing — `Path.kt`, `pathfinder/util/BlockUtil.kt`
- `Path` rebuilds the node chain into a block list, then `getSmoothedPath()` greedily skips ahead: from the current point, find the **farthest** later point reachable by a straight line that `BlockUtil.bresenham(ctx, a, b)` accepts.
- `bresenham` walks the voxel line between two points and rejects it if any cell is not walkable or the height change is too large. Also in this file: `canWalkOn`, `getDirectionToWalkOnStairs` (uses numeric metadata — will not port), `neighbourGenerator`.
- Smoothing makes paths look natural but it is the main source of "walks into a wall/corner" bugs. Keep it behind a switch so you can disable it while debugging.

### 3.5 Path follower — `Pathfinder.java`, `PathExecutor.java`
- `Pathfinder` is the feature wrapper. It keeps a queue of (start,end) pairs, runs `AStarPathFinder` on an executor thread (`MightyMiner.executor()`), hands finished paths to `PathExecutor`, and draws the path in the world render event. Walk speed is read from the player (`getAIMoveSpeed`) and passed into `CalculationContext` as sprint = ×1.3, walk = ×1, sneak = ×0.3.
- `PathExecutor` follows the smoothed path every tick: figures out which node the player is closest to, advances the target when the player has passed it, turns the camera towards a node at least 5 blocks ahead, presses forward/sprint/jump keys, and declares failure if horizontal speed stays below 0.05 for 1 second.

**What PathExecutor needs that is not in this bundle:** `RotationHandler` (smooth camera movement), `StrafeUtil`, `KeyBindUtil`, `AngleUtil` (included in 04), `Clock` (included in 04), config values (`rottime`, `rotmult`, `fixrot`). On Fabric you will need a new equivalent for turning the camera and simulating key presses. **That part is a rewrite and you will have to tune it by hand in-game.**

Also note `PlayerUtil.getBlockStandingOn()` (in 04) uses `ceil(y - 0.25) - 1` — a hand-tuned snow/slab hack ("requires more testing"). Re-verify.

---

## 4. Module 03 — Location (`03_location`)

**Mechanism.**
- `Location` enum (18 values): top-level island/area (Hub, Dwarven Mines, Crystal Hollows, Dungeon, …) mapped from the tab-list line `Area: <name>`. Unknown names map to `KNOWHERE`. **The order of the last three constants (`LIMBO`, `LOBBY`, `KNOWHERE`) matters:** `GameStateHandler.isPlayerInSkyBlock()` uses `ordinal() < values().length - 3`. Do not reorder.
- `SubLocation` enum: the ~217 sub-areas (Hub buildings, Dwarven Mines areas, Crystal Hollows zones, Crimson Isle, Rift, …), mapped from the scoreboard line that contains `⏣` or `ф`, after stripping colour codes and non-ASCII characters. Note `fromName` returns **null** for unknown names (unlike `Location`, which returns `KNOWHERE`) — `DebugHUD` calls `getName()` on it, so guard against null when porting.
- `GameStateHandler` — listens for tab-list, tab footer and scoreboard events and keeps `currentLocation`, `currentSubLocation`, `godpotActive`, `cookieActive`. It detects **Limbo** (tab list has 1 line and the inventory is empty) and **Lobby** (sidebar title not "SKYBLOCK" and last line is `www.hypixel.net`).
- `ScoreboardUtil` — caches the sidebar by objective, `sanitizeString`, and parses a `Cold:` value.
- `TablistUtil` — sorted tab list (copy of vanilla's player ordering), cached lines and footer.
- Events: `UpdateTablistEvent`, `UpdateTablistFooterEvent`, `UpdateScoreboardEvent`, `UpdateScoreboardLineEvent` are plain event objects fired from `MixinNetHandlerPlayClient`.

**Only these parts of `MixinNetHandlerPlayClient` matter for this module:** `handlePlayerListItem` → tablist event, `handlePlayerListHeaderFooter` → footer event, the scoreboard injections (`handleDisplayScoreboard`, `handleScoreboardObjective`, `handleUpdateScore`, `handleTeams`) → scoreboard events, and `handleParticles` → particle event (used by block selection, module 04). The rest of that file is entity-tracking code for other features; ignore it.

**Port plan:**
- Keep both enums and the parsing logic as-is (strings only, no Minecraft types). Add a unit test with a few real tab-list and scoreboard samples.
- Replace the packet mixins with whatever 26.2 gives you for tab-list/scoreboard changes; Fabric API may not expose them as events, so a small mixin on the client network handler is likely still needed.
- **Text format risk:** the parsing relies on `§` colour codes and on legacy scoreboard "teams" tricks (Hypixel puts sidebar text in team prefixes/suffixes). Modern clients represent text as components, so `§`-based stripping may behave differently. Log the raw strings first, then adapt the regexes.
- Hypixel's wording (`Area: …`, sub-area names) can change at any time; this is data, not logic.

---

## 5. Module 04 — Block selection (`04_block_selection`)

### 5.1 What "block selection" does
`MineableBlock` maps a named ore/gem to a list of **1.8.9 block state IDs** (`id | meta << 12`), e.g. `GREEN_MITHRIL(168, 4264, 8360)`, `DIAMOND(57)`. `BlockMiner.start(...)` turns the user's chosen blocks + priorities into `Map<stateId, priority>`.

`BlockUtil.findMineableBlocksFromAccessiblePositions(priorities, ignorePos, miningSpeed)`:
1. Take the 3×3 area around the block the player stands on and keep positions that are standable with 2 blocks of headroom (`getWalkableBlocksAround`).
2. For each such position, scan a box of ±5 horizontal and −3..+4 vertical blocks around the eye position (`findMineableBlocksAroundPoint`), and keep blocks within **4 blocks** of that eye position.
3. A candidate must be in the priority map with priority > 0 and must have at least one **visible side** (ray cast to six side centres, `hasVisibleSide`).
4. Score = `(hardness / miningSpeed × miningCoefficient + angleChange × angleCoefficient + distSq × distanceCoefficient) / priority`. **Lower is better.** The three coefficients come from config.
5. Put candidates into a min-heap. `ChoosingBlockState` takes `blocks.get(0)` as the target.

`BlockUtil.getBlockStrength(stateId)` is a hard-coded hardness table (e.g. 600 for ore blocks, 2000 for titanium, 5000 default). `getMiningTime = ceil(strength × 30 / miningSpeed) + tickGlideOffset`.

Other helpers: side centres (`BLOCK_SIDES`), `canSeeSide`, `getClosestVisibleSide`, `bestPointsOnBestSide` (20 random points per side, filtered by ray cast, sorted by needed head turn), `RaytracingUtil` (entities first, then `rayTraceBlocks`), `AngleUtil` (yaw/pitch maths, wrap, needed change), `canWalkBetween`.

### 5.2 State machine — `BlockMiner`, `StartingState`, `ChoosingBlockState`, `BreakingState`
`Starting` → (`ApplyAbility` if available) → `Choosing` → `Breaking` → back to `Starting` once the block changes. Failure exits: no target blocks, no tool, no points on the block, "stuck" (breaking for longer than `miningTime + 40` ticks), looking away from the target for > 500 ms, and no blocks found within `waitThreshold`. `ApplyAbilityState` is **not** in this bundle (it is a pickaxe-ability feature, outside what you asked for), so `StartingState` references a class that is missing; stub it out or remove that branch.

`BlockMiner` also listens to chat to track ability cooldown and to crit-particle spawns for the "precision miner" option (particle position inside the target block's box → aim point).

### 5.3 Known issues in the original (do not port these blindly)
1. **`MinHeap.add` is wrong.** It calls `heapUp(size - 1)` after inserting at index `size`, so the new element is never bubbled up. I simulated the original insert logic on 2,000 random cost sets of 3–30 items: **the root was not the true minimum in 195 of them (~10%)**. Because `getBlocks()` returns raw heap order, the "best block" is sometimes not the best and the remaining order is not sorted. Replace the heap with `PriorityQueue` or a plain sort.
2. **Visited-before-range-check.** In `findMineableBlocksAroundPoint`, each position is added to the shared ignore set *before* the 4-block range test. A block that is out of range from the first walkable position is therefore skipped when a later walkable position could reach it. Decide deliberately whether to fix this (move the `add` after the range check).
3. **`getCost()` in `MinHeap`** is a linear search used when merging batches; fine for hundreds of blocks, wasteful if you widen the scan.
4. **Hard-coded numeric state IDs** (`MineableBlock`, `getBlockStrength`). These are meaningless on 26.2. Replace with block/state matching (by block type or tag) and re-key the hardness table. Hypixel SkyBlock disguises some resources as other blocks (e.g. wool, prismarine, stained clay variants for mithril; stained glass for gemstones), so the *server-sent* block must be matched, not the "real" ore name. Print the blocks you actually see in-game before building the table.
5. `getBlockStrength` has `57504` and `57439` for ruby, while `MineableBlock.RUBY` lists `57504, 57436`. One of these looks like a typo; check in game.
6. `bestPointsOnBestSide` uses `new Random()` per point and picks random points on a side; `BreakingState` also picks a random point in some cases. Fine, but make randomness seedable if you want repeatable tests.

### 5.4 Supporting pieces
`MinHeap`/`HeapNode` (see issue 1), `Clock` (simple ms timer), `Angle` (yaw/pitch holder), `Target` (block/vec/entity/angle → angle), `PlayerUtil` (eye pos, standing block, suffocation check), `MixinChunk` + `BlockChangeEvent` (fires an event when a block changes; `BreakingState` instead polls the block each tick, so this is optional), `SpawnParticleEvent` (from `handleParticles`).

**What `BreakingState` needs that is not in this bundle:** `RotationHandler`, `RotationConfiguration`, `KeyBindUtil`, `MightyMinerConfig` values. Same note as module 02.

---

## 6. Module 05 — GUI (`05_gui`)

### 6.1 Config screen — `MightyMinerConfig`
OneConfig annotation-driven screen: `@Dropdown`, `@Switch`, `@Slider`, `@Number`, `@Text`, `@Button`, `@KeyBind`, `@DualOption`, plus categories (General, Scheduler, Commission, Glacial, Mining Macro, Route Miner, Delays, Auto Sell, Failsafe, Debug, Discord). ~890 lines, ~98 static option fields. Static fields are read everywhere (`MightyMinerConfig.miningCoefficient`, etc.).
- It also holds small helpers like `getRandomRotationTime()` and a "Set Mining Tool" button that stores the held item's name.
- It contains failsafe/Discord/sound options that belong to features not in this bundle. When porting, only keep the options the four modules above read: `miningCoefficient`, `angleCoefficient`, `distanceCoefficient`, `tickGlideOffset`, `sneakWhileMining`, `precisionMiner`, `randomizedRotations`, `rotationTime(+Randomizer)`, `miningTool`, `rottime`, `rotmult`, `fixrot`, and the macro/keybind options.

**Port plan:** OneConfig's 1.8.9 build will not work on Fabric. Pick a Fabric config library (check that it supports 26.2 before committing), and make a plain `Config` class with the same field names so the rest of the port can reference them unchanged.

### 6.2 HUDs — `DebugHUD`, `CommissionHUD`, `GlacialCommissionHUD`
Each extends OneConfig's `TextHud` and returns a list of text lines. `DebugHUD` shows scoreboard title/lines, `Location`/`SubLocation` and focus state. The logic is trivial (lines of text); only the container and drawing are tied to OneConfig. Rebuild with Fabric's HUD render callback and a text draw call.

### 6.3 Container / inventory GUI logic — `InventoryUtil`, `AbstractInventoryTask`, `TaskStatus`, `MiningSpeedRetrievalTask`
- `InventoryUtil`: find items by display name, hold a hotbar item, read container title, click slots (`windowClick`), read item lore from NBT (`display.Lore`), count items, read drill fuel from lore, check that a chest GUI has finished loading (last slot non-empty).
- `AbstractInventoryTask<T>` + `TaskStatus`: tiny task framework — `init() / onTick() / end() / getResult()` with `PENDING/RUNNING/SUCCESS/FAILURE`.
- `MiningSpeedRetrievalTask`: opens `/sbmenu`, waits up to 1 s, finds "Your SkyBlock Profile", and regex-parses `Mining Speed ([\d,]+\.?\d*)` from lore.

**Port plan:** the *flow* is portable (it is string matching on item names and lore). The *item API* is not: display names and lore on modern versions live in **data components**, not the old NBT `display` tag, so `getItemLore` / `getDisplayName` / `ExtraAttributes.id` need a full rewrite. Slot clicks go through the interaction manager with new arguments. Verify what Hypixel actually sends before trusting the regex.

### 6.4 Container GUI mixins — `MixinGuiContainer`, `MixinInventoryPlayer`
Cancel slot clicks (`handleMouseClick`) and hotbar scrolling (`changeCurrentItem`) while a macro is running. Both depend on `MacroManager.isRunning()`, which is not in this bundle. Easy to redo on Fabric with a mixin or Fabric API callback, but targets and method names will differ.

### 6.5 Drawing — `RenderUtil`
`drawBlock`, `outlineBlock`, `drawBox`, `drawLine`, `drawPoint`, `drawTracer`, `drawText` in world space, plus overlay text helpers. Written with 1.8.9's immediate-mode OpenGL (`GL11`, `Tessellator`). **Cannot be translated line by line**; rebuild on the current rendering API using `RenderUtil`'s method names as the interface, so the pathfinder's debug path drawing and block highlights keep working.

---

## 7. Dependency map (who needs what)

```
Pathfinder ──► AStarPathFinder ──► Moves ──► Movement* ──► MovementHelper ──► BlockStateAccessor ◄── ChunkProvider mixin
    │                 │                                       ▲
    │                 └─► Goal, ActionCosts, BinaryHeapOpenSet│
    └─► PathExecutor ──► Path(smoothing, bresenham) ──────────┘
              └─► RotationHandler*, StrafeUtil*, KeyBindUtil*, AngleUtil, Clock, Config

BlockMiner ──► StartingState ► ChoosingBlockState ► BreakingState
                                   │                      └─► RotationHandler*, KeyBindUtil*, Target, AngleUtil, Config
                                   └─► BlockUtil(select) ─► MinHeap, RaytracingUtil, MovementHelper, BlockStateAccessor

GameStateHandler ──► Location, SubLocation, ScoreboardUtil, TablistUtil, InventoryUtil
        ▲
        └── events from MixinNetHandlerPlayClient

(* = not included in this bundle)
```

---

## 8. Realistic effort estimate

| Module | Keeps working | Needs rewrite |
|---|---|---|
| 01 Pathfinding pure | ~90% | position class, fastutil check, re-tuned physics numbers |
| 02 Pathfinding adapter | ~30% (the rules and thresholds) | all world access, block checks, collision heights, mixins, player control |
| 03 Location | ~70% (enums, parsing) | event sources, text handling |
| 04 Block selection | ~50% (scoring formula, state machine) | block IDs, ray casts, heap bug, rotation, key presses |
| 05 GUI | ~20% (what to show, string matching) | config library, HUD, rendering, item/lore API, clicks |

Expect repeated compile → run → fix cycles and in-game testing for each module. The pathfinder and the executor (turning and walking along the path) are the pieces most likely to take the longest to get feeling right.

---

## 9. Module 06 — Rotation and input control (`06_rotation_and_input`)

This is the code that turns the camera, presses keys, and decouples "where you look" from "where you move". The pathfinder follower, block breaking and every macro depend on it, so port it early (it is how you get anything to move in-game).

### 9.1 `RotationHandler` + `RotationConfiguration` (+ `Angle`, `Target`, `AngleUtil` in 04)
- A queue of `RotationConfiguration` jobs. Each job has a **target** (an angle, a block/vec point, or an entity), a duration in ms, a type (`CLIENT` or `SERVER`), an optional callback, and flags `followTarget` and `easeBackToClientSide`.
- `easeTo(config)` computes the needed yaw/pitch change from the start angle to the target, and sets an end time = duration scaled by distance: ×0.65 under 25° of total change, ×0.77 under 45°, ×0.9 under 80°, ×1.0 up to 100°, ×1.1 above.
- Each frame/tick it computes progress = `easeFunction(timeFraction)` and moves along a **cubic Bézier curve** (start at 0, end at the needed change). The two control points are offsets of 5%/10% of the yaw change times a random sign multiplier (`MightyMinerConfig.cost4` is the magnitude). The yaw/pitch it applies each step is the *difference* from the previous step.
- `CLIENT` mode: applies the delta to the real camera (`rotationYaw/Pitch`) in the world-render event.
- `SERVER` mode: keeps a separate `serverSideYaw/Pitch` and only changes the rotation reported in the movement packet (via `MotionUpdateEvent`, fired from `MixinEntityPlayerSP`). The camera is left alone. `MixinModelBiped` makes the player model's head match that reported rotation. With `easeBackToClientSide` it eases back afterwards.
- `followTarget` re-runs the ease towards a moving target every time one finishes.
- `stop()` only sets flags; the real reset happens on the next tick (a comment says this avoids crashes from other threads).

**Quirks to decide on, not copy:**
1. `RotationConfiguration`'s default ease is `Ease.values()[nextInt(Ease.values().length - 1)]`, so the **last** `Ease` constant (`EASE_OUT_CIRC`) is never chosen (off-by-one, or deliberate). The `Ease` enum is nested in `RotationConfiguration`: five easing functions (sine out, sine in-out, quad out, cubic out, circ out), pure maths.
2. `serverSideYaw == 0 && serverSidePitch == 0` is used as "not initialised". If the real rotation is exactly (0,0) the start angle is wrong.
3. The pitch control points are computed from the **yaw** change (`bezierEnd.getYaw() * 0.1f`), probably a copy/paste slip.
4. `randomMultiplier1 = randomMultiplier2` (same value), so the two control points are mirror images; "random" is only the sign.
5. Timing uses wall-clock `System.currentTimeMillis()`, not game ticks, so it behaves differently under lag.

**Port plan:** the maths (Bézier, scaling, angle wrap in `AngleUtil`) ports as-is. `CLIENT` mode needs a hook that runs once per rendered frame (a level-render or tick-with-partial-time event) and the player's `setYRot/setXRot`. `SERVER` mode needs a new injection point in `LocalPlayer` where the movement packet is built, plus a new render-state hook for the head. **Build `CLIENT` mode first; `SERVER` mode is the risky part.**

### 9.2 `KeyBindUtil`
Thin helper over key bindings: set a key's pressed state, hold a set, release all, stop movement, left/right/middle click through `MinecraftAccessor` (private click methods), and `getNeededKeyPresses(yaw)` which turns a desired heading into a W/A/S/D combination. **It switches on LWJGL 2 key codes (17/30/31/32).** These are not GLFW codes; on 26.2 compare `KeyMapping` objects instead.

### 9.3 `StrafeUtil` + `MixinEntityLivingBase`
`StrafeUtil` is three static fields (`enabled`, `forceStop`, `yaw`). The mixin (credited "From Baritone" in the source) redirects the yaw used by `jump` (sprint-jump boost direction) and by `moveEntityWithHeading → moveFlying` to `StrafeUtil.yaw` when enabled. Effect: the player moves relative to a chosen yaw while the camera points elsewhere. `PathExecutor` uses it. On 26.2 the equivalent code paths (jump boost, input-to-velocity conversion) were restructured; find them in `LivingEntity`/`Entity` and redirect the yaw argument.

### 9.4 `MixinEntityPlayerSP`, `MixinMinecraft`, accessors
- `MixinEntityPlayerSP`: fires `MotionUpdateEvent` at the head of `onUpdateWalkingPlayer`, redirects the yaw/pitch reads in that method to the event's values, and cancels `dropOneItem` while a macro runs.
- `MixinMinecraft`: while a macro runs, redirects the 3rd `KeyBinding.isPressed()` call in `runTick` to `isKeyDown() && !macroRunning`, and redirects `Display.isActive()` in `setIngameFocus` so focus is forced while a macro runs. (Which key the 3rd call is can't be read from the source; check 1.8.9's `runTick` if you need to know.)
- `MinecraftAccessor` exposes the timer and click methods; `EntityPlayerSPAccessor` exposes last-reported yaw/pitch.

### 9.5 `MouseUngrab`
Replaces `mc.mouseHelper` with a no-op version so the cursor is released and mouse movement doesn't turn the camera while a macro runs; it restores the old helper afterwards. On 26.2 the cursor is managed by GLFW through `MouseHandler`; the object-swap trick will not work.

---

## 10. Module 07 — Macros (`07_macros`)

Keeps the original `macro/…` package layout.

### 10.1 Framework
- `AbstractMacro`: lifecycle `enable / disable / pause / resume / toggle`, overridable hooks `onEnable`, `onDisable`, `onPause`, `onResume`, `onTick`, `onWorldRender`, `onChat(String)`, `onTablistUpdate`, `onOverlayRender`, `onReceivePacket`, plus two `Clock` timers (`timer`, `uptime`) and `getNecessaryItems()`.
- `MacroManager` (singleton): picks the active macro from `MightyMinerConfig.macroType` (0 = Commission, 1 = Glacial, 2 = Mining, 3 = Route Miner), exposes `toggle/enable/disable/pause/resume/isEnabled/isRunning`, and **forwards** the client tick, chat, tab-list, world-render, overlay-render and packet-received events to the current macro. `isRunning()` is what the mixins check.
- Each macro is a **state machine**: a `…MacroState` interface with `onStart(macro)`, `onTick(macro) → nextState`, `onEnd(macro)`; the macro calls `transitionTo(next)` each tick (see `CommissionMacro`).

### 10.2 The four macros
| Macro | What it does | States (as files) |
|---|---|---|
| `MiningMacro` | Simplest loop: read mining speed, then `BlockMiner` on the chosen block set | (single class) |
| `CommissionMacro` | Dwarven-Mines commission loop: reads the current commissions from the tab list (`CommissionUtil` + `Commission` enum), paths to the target, mines or kills mobs, claims the reward, refuels the drill, warps/changes lobby | Starting, GettingStats, Pathing, Mining, MobKilling, ClaimingCommission, Refuel, Warping, NewLobby |
| `GlacialMacro` | Glacite-Tunnels loop: picks a vein type (`GlaciteVeins` enum, 600+ lines of waypoint data and block lists), paths to it, mines, handles commissions and lobby changes | Starting, GettingStats, Pathfinding, Mining, ClaimingCommission, Teleporting, ErrorHandling, NewLobby |
| `RouteMinerMacro` | Follows a user-built route of `RouteWaypoint`s (`WALK`, `AOTV`, `ETHERWARP`, `MINE`) and mines at `MINE` nodes | Starting, GettingStats, Moving, Mining |

I read `MacroManager`, `AbstractMacro`, `MiningMacro` and the `CommissionMacro` core and its Starting/Mining/Pathing/MobKilling/NewLobby states in full, and the `GlacialMacro` and `RouteMiner` classes by structure. The Glacial and RouteMiner states follow the same pattern but I did not trace every transition. Have your AI read each state file before porting it.

### 10.3 What depends on what is **not** here
`CommissionMacro`, `GlacialMacro` and `RouteMinerMacro` call `NameMentionFailsafe` (and `AbstractFeature`/`FeatureManager`/`AutoWarp`/`AutoDrillRefuel`/`AutoCommissionClaim`/`RouteNavigator` import `failsafe.*` types). The `failsafe/` package was **left out on purpose** because it is outside what you asked for. When porting, replace those calls with stubs (e.g. `isLobbyChangeRequested() → false`) or delete them, and remove the `failsafesToIgnore` / `shouldNotCheckForFailsafe()` plumbing in `AbstractFeature` and `FeatureManager`.

Grep of the bundle: `failsafe.*` imports are `AbstractFailsafe.Failsafe` (4 files), `NameMentionFailsafe` (3 files) and `FailsafeManager` (1 file).

Also missing: `BlockMiner` and `04_block_selection/StartingState` reference an `ApplyAbilityState` (applies the mining ability before breaking) that is **not in the bundle**. Stub it or have your AI write it from the way `StartingState` uses it.

### 10.4 Server-specific logic
All four macros are written for a specific server's game mode (menus, item names, warp commands, tab-list wording, NPC names, commissions). That text is data that can change at any time. Port the *structure* first and treat every string match as something to verify in-game.

---

## 11. Module 08 — Features, handlers, routes, entry point (`08_features_and_handlers`)

Keeps the original package layout (`feature/…`, `handler/…`, `util/…`, `command/…`, `event/…`).

| Piece | What it does |
|---|---|
| `AbstractFeature`, `FeatureManager` | Base class + registry for features: `start/stop/pause/resume`, a `Clock`, `onTick/onRender/onChat/onTablistUpdate/onOverlayRender` hooks, `getName()`, `resetStatesAfterStop()`. The manager holds `allFeatures` and fans events out. |
| `AutoGetStats` (+ tasks) | Opens the SkyBlock menu and reads mining speed (see 05). |
| `AutoWarp` | Warps to a location and watches chat for "You haven't unlocked this fast travel destination!". (255 lines; I did not trace every state.) |
| `AutoMobKiller` | Finds target mobs and uses `Pathfinder` to reach them, then attacks through `RotationHandler` and `KeyBindUtil`. |
| `AutoCommissionClaim` | Claims a commission reward: eases the camera to the emissary NPC entity (500 ms, `CLIENT` rotation) and interacts with it. (300 lines; I did not trace every state.) |
| `AutoChestUnlocker` | Handles chest unlocking; listens to `SpawnParticleEvent` and tracks a particle position. (300 lines; I did not trace the full logic.) |
| `AutoDrillRefuel` | Refuels a drill from fuel items via the inventory GUI. |
| `AutoSell`, `WorldScanner`, `LagDetector` | Small utilities. `AutoSell` and `WorldScanner` are 16-line stubs; `LagDetector` is ~120 lines. |
| `RouteNavigator` | Walks a `Route` waypoint by waypoint using the rotation handler and key presses; waypoint types `WALK/AOTV/ETHERWARP`. |
| `RouteBuilder`, `RouteBuilderCommand` | In-game route recording: add/remove waypoints with commands or keys. |
| `RouteHandler`, `GraphHandler` | Load/save routes and graphs as JSON (`./config/…`), selected route, edit mode, drawing in the world. |
| `Route`, `RouteWaypoint`, `WaypointType`, `TransportMethod`, `Graph`, `GraphSerializer` | Data types. `RouteWaypoint` is `(x,y,z,type)`; `Graph` is a generic node/edge graph serialised with Gson. |
| `resources_route_graphs/*.json` | The shipped "Commission Macro" and "Glacial Macro" route graphs. Pure data; reusable as they are. |
| `MightyMiner.java` | Entry point: copies the route JSONs into `./config`, loads graphs, registers every listener/feature, registers the command. Use it as a **wiring checklist** for your `ClientModInitializer`; it also forces fullbright (`gammaSetting = 1000`) and disables pause-on-lost-focus. |
| `Logger`, `EntityUtil`, `CommissionUtil`, `ReflectionUtils` | Chat output with prefix/colour codes, entity queries, commission parsing from tab list, "is package X installed" check. |
| `PacketEvent`, `BlockDestroyEvent`, `UpdateEntityEvent` | Event data classes fired by the mixins in 03. |

**Port plan.** Port `AbstractFeature`/`FeatureManager` (without failsafe types), `Route*`/`Graph*` data classes and JSON I/O first: that part is plain Java + Gson and ports almost unchanged (`BlockPos`/`Vec3` swapped). Then port features one at a time in the order `AutoGetStats → AutoWarp → RouteNavigator → AutoMobKiller → the rest`. Replace `mc.thePlayer.sendChatMessage("/warp x")` with the modern command-send call and re-check every GUI/lore string.

---

## 12. Dependency map for the added modules

```
MacroManager ──► current AbstractMacro (Commission | Glacial | Mining | RouteMiner)
                     └─► state classes ──► Features (AutoWarp, AutoMobKiller, AutoGetStats, AutoCommissionClaim, RouteNavigator, ...)
                                                └─► Pathfinder / PathExecutor (02), BlockMiner (04)
                                                └─► RotationHandler + KeyBindUtil + StrafeUtil (06)
                                                └─► GameStateHandler / CommissionUtil (03)
MixinEntityPlayerSP ──► MotionUpdateEvent ──► RotationHandler (SERVER mode)
MixinEntityLivingBase ──► StrafeUtil.yaw (set by PathExecutor)
MixinMinecraft / MixinEntityPlayerSP / MixinGuiContainer / MixinInventoryPlayer ──► MacroManager.isRunning()
MightyMiner.java ──► registers everything above
```

---

## 13. Updated effort estimate (all modules)

| Module | Keeps working | Needs rewrite |
|---|---|---|
| 06 Rotation and input | ~50% (Bézier, scaling, angle maths, key helper structure) | camera apply hook, movement-packet rotation, jump/strafe yaw hook, mouse grab, key codes |
| 07 Macros | ~60% (state machines, decision logic) | every string match, server-specific flows, failsafe calls (removed), anything using the modules above |
| 08 Features / handlers | ~60% (route/graph data + JSON, feature framework) | GUI/lore parsing, chat commands, rendering of routes, config access |

**Honest summary:** the *structure* of this mod (A* pathfinder, state-machine macros, route/graph data, scoring) carries over well. The *game-facing layer* (world access, rendering, input, packets, items, scoreboard) is where nearly all of the work is, and it can only be finished by running the game and fixing what you see. Budget weeks, not hours, for the full thing; a working "walk to a block and mine it" slice is a realistic first milestone.

See also: `MAPPING_1.8.9_to_26.2.md` (name translation) and `FABRIC_26.2_GUIDE.md` (setup, API cheat sheet, doc links).
