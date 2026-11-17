# Macro state flows

These diagrams describe the translated client state machines. They are code-flow documentation, not evidence of in-game behavior.

## Route Miner

```text
enable
  -> validate selected route, mining tool, and any travel item used by the route
  -> Starting
  -> GettingStats
  -> Moving
       -> choose (routeIndex + 1), wrap at end
       -> navigate to the waypoint with RouteNavigator
       -> MINE waypoint -> Mining
       -> other waypoint -> save its index -> Moving
  -> Mining
       -> no mineable points -> retry same waypoint
       -> not enough blocks -> save the mine waypoint index -> Moving
       -> invalid target set / stuck / unexpected stop -> stop with an error
```

WALK and MINE waypoints use the world-backed walking path; AOTV and ETHERWARP use the `RouteNavigator` travel-item path. Unlike the legacy state, MINE waypoints are reached before mining starts.

## Glacial Macro

```text
enable -> Starting
  -> outside Dwarven Base Camp -> Teleporting -> Starting
  -> validate items at base -> GettingStats -> Pathfinding

Pathfinding
  -> completed commission in tab data -> ClaimingCommission
  -> cold threshold reached -> Teleporting -> Pathfinding
  -> choose nearest non-blacklisted vein for an incomplete commission
  -> already within range -> Mining
  -> route found -> RouteNavigator -> Mining
  -> route missing / route fails -> blacklist vein and try another
  -> no candidates or five failed paths -> NewLobby (Hub -> Base Camp) -> GettingStats

Mining
  -> completed commission -> ClaimingCommission
  -> cold threshold reached -> Teleporting -> Pathfinding
  -> no points -> restart BlockMiner
  -> not enough blocks -> retry after 5 seconds (up to 3); then blacklist vein -> Pathfinding
  -> no target blocks / stuck / unexpected stop -> stop with an error

ClaimingCommission
  -> claim succeeds -> refresh commission progress -> Pathfinding
  -> claim fails -> retry up to 3 times -> ErrorHandling (disable and report)
```

The legacy waypoint tables remain data in `GlaciteVeins`; the unsupported legacy numeric block-state-ID and mining-tick helpers were removed. The current `BlockMiner` does not accept a pickaxe-ability parameter, so Glacial/Commission stat retrieval retains the ability result but does not apply it.

## Commission Macro

```text
enable -> Starting -> GettingStats -> Pathing
  -> selected commission target reached
       -> mining commission -> Mining
       -> slayer commission -> MobKilling
       -> claim commission -> ClaimingCommission
       -> refuel entry -> Refuel
  -> path failures (3) -> NewLobby (Hub -> Forge) -> Starting
  -> claim inaccessible -> Warping (Forge) -> Starting

Mining / MobKilling
  -> commission changes or claim requested -> Pathing
  -> mining block shortage / no target mobs -> Starting
  -> fatal task/feature error -> stop and report

ClaimingCommission
  -> claim succeeds -> accept next recognized commission -> Starting
  -> inaccessible NPC -> Warping
  -> timeout -> Starting
  -> other claim error -> stop and report
```

## Client commands

- `/mm type commission|glacial|mining|route` selects the macro while stopped.
- `/mm start`, `/mm stop`, `/mm pause`, and `/mm resume` control its lifecycle.
- `/mm tool <item name>` and `/mm weapon <item name>` configure the mining tool and slayer weapon.
- `/mm ore mithril|diamond|emerald|redstone|lapis|gold|iron|coal` selects the Mining/Route Miner target.
- `/mm claim emissary|pigeon`, `/mm refuel on|off`, and `/mm fuel volta|oil_barrel` configure Commission Macro options.

These settings are held in memory and reset when the game restarts; configure them again after launching.

All macro state changes send a client chat message. Server text and item-name matches are centralized in `Strings.java` and marked for in-game verification.
