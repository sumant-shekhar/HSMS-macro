# Block Miner

Block Miner is a client-side Fabric mod targeting Minecraft 26.2. This checkout is a code port of MightyMiner features; compiling successfully does not mean the features have been verified in-game.

## Requirements

- JDK 25
- The project Gradle wrapper
- Minecraft 26.2 and Fabric Loader/Fabric API versions declared in `gradle.properties` and `fabric.mod.json`

## Build and test

Build and run the project's synthetic checks with:

```sh
JAVA_HOME=/path/to/jdk-25 PATH="/path/to/jdk-25/bin:$PATH" ./gradlew clean build
```

On the configured development machine, the equivalent is:

```sh
JAVA_HOME=/tmp/macro-jdk25 PATH="/tmp/macro-jdk25/bin:$PATH" ./gradlew clean build
```

The `check` task runs self-tests for configuration JSON round-tripping, pathfinding and its world adapter, the block-selection heap, location and commission parsing, and bundled route/graph data.

To launch the development client:

```sh
JAVA_HOME=/path/to/jdk-25 PATH="/path/to/jdk-25/bin:$PATH" ./gradlew runClient
```

## In-game entry points

- `/mmconfig` opens the settings screen. Settings are stored at `<gameDirectory>/config/blockminer.json` and saved when the screen closes.
- `/mm start`, `/mm stop`, `/mm pause`, and `/mm resume` control the selected macro. `/mm type commission|glacial|mining|route` selects one.
- `/mmstats` requests mining-stat retrieval; `/mmwarp` and `/mmroute` expose navigation helpers.
- `/mmpath <x> <y> <z>` searches for a path; `/mmwalk <x> <y> <z>` follows one and `/mmwalk stop` interrupts it.
- `/mmfeature` and `/rb` expose feature and route-builder controls; run them without arguments for command usage.
- `/mmloc` reports parsed location data and `/mmdump <label>` captures diagnostic client state.

Command behavior, server text matching, HUD placement, inventory interactions, and movement still require visual/live testing. See [PORT_STATUS.md](PORT_STATUS.md) for module status, API notes, known limitations, and the complete in-game checklist. Reference materials are under [reference/](reference/).
