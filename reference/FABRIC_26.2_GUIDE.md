# Fabric + Minecraft 26.2: setup guide and doc index for this port

Researched 2026-11-17 from the official Fabric docs and release posts. Items marked **[verified]** were read in those pages. Items marked **[check]** are my knowledge of standard Fabric practice and must be confirmed against what the template generator produces. When a value here disagrees with the generator, **the generator is right**.

---

## 1. What changed in 26.x that affects you most

1. **Version names are year-based** (26.1, 26.2, …). **[verified]**
2. **Unobfuscated game, Mojang names, no mappings step.** Yarn is not used for 26.x. Class names come straight from the game ("Mojang names"). **[verified]** → `MAPPING_1.8.9_to_26.2.md`
3. **Java 25** is required (the docs say "JDK 25" for 26.1). **[verified for 26.1; 26.2 uses the same toolchain per the release post, confirm in the generator]**
4. **Loom plugin id is `net.fabricmc.fabric-loom`** (not the old `fabric-loom`). **[verified]**
5. **Raw OpenGL is gone** from mod code; rendering goes through the Blaze3D API. `Tesselator` and `MultiBufferSource` were removed. **[verified]**
6. **GUI/HUD uses an "extract" model:** `GuiGraphics` → `GuiGraphicsExtractor`, `render…` → `extract…`. **[verified]**
7. **Screens moved onto `Gui`** in 26.2: `Minecraft.getInstance().screen` → `Minecraft.getInstance().gui.screen()`. **[verified from the NeoForge 26.2 primer summary]**
8. **Fabric API was renamed to match Mojang names** (see table in section 5).

## 2. Versions to use for 26.2

| Component | Value | Source |
|---|---|---|
| Minecraft | 26.2 | [Fabric 26.2 release post](https://fabricmc.net/2026/06/15/262.html) |
| Fabric Loader | 0.19.3 | same post **[verified]** |
| Fabric Loom | 1.17 | same post **[verified]** |
| Gradle | 9.5.1 | same post **[verified]** |
| Java | 25 | [Setting up](https://docs.fabricmc.net/develop/getting-started/setting-up) **[verified for 26.1]** |
| Fabric API | take the version the generator prints | [Template generator](https://fabricmc.net/develop/template/) |

These were current when the 26.2 post was published (June 2026). Newer patch versions may exist; the generator picks the current ones for you. A 26.3 primer exists on the NeoForge docs site, so check whether a newer Minecraft version has shipped; your target stays 26.2 unless you decide otherwise.

## 3. Create the project (do this before touching any ported code)

1. Install **JDK 25** and IntelliJ IDEA (recommended) or VS Code. [Setting up your environment](https://docs.fabricmc.net/develop/getting-started/setting-up)
2. Open the **template generator**: <https://fabricmc.net/develop/template/>. Fill in mod name, package name (lowercase, dot-separated, e.g. `com.yourname.miner`) and Minecraft version **26.2**. Under "Advanced Options" you can pick Kotlin if you want to keep the pathfinder in Kotlin. [Creating a project](https://docs.fabricmc.net/develop/getting-started/creating-a-project)
3. Unzip into a path **without spaces, non-ASCII characters, or cloud-sync folders**. **[verified]**
4. Open in your IDE, let Gradle import, run `runClient` (the Gradle task, or the generated run configuration). You should reach the title screen with your mod listed. **Do not continue until this works.**
5. Commit that empty project. Then start porting.

Things to confirm in the generated files **[check]**:
- `build.gradle` uses plugin `net.fabricmc.fabric-loom` and depends on `fabric-api`.
- Because the game is not remapped, dependency configurations are `implementation`, not the old `modImplementation`. Copy whatever the generator emits.
- `fabric.mod.json` has a `client` entrypoint, a `mixins` entry pointing at a `*.mixins.json`, and `"environment": "client"` for a client-only mod.

## 4. Suggested layout for the ported mod

```
src/client/java/<your.package>/            (or src/main, if the template has no split)
  MinerClient.java                  ← ClientModInitializer: registers events, key mappings, HUD
  config/Config.java                ← plain fields, same names as MightyMinerConfig
  pathfinder/…                      ← module 01 (+ rewritten 02)
  location/…                        ← module 03
  mining/…                          ← module 04
  rotation/…                        ← module 06
  macro/…, feature/…                ← modules 07, 08
  mixin/…, mixin/accessor/…
src/client/resources/<modid>.mixins.json
```

Port in this order: **01 → 06 (key mappings + camera only) → 04 → 03 → 02 → 08 → 07 → 05**. The reason for putting 06 early: until you can turn the camera and press keys, you can't test pathfinding or mining on the real game.

## 5. Forge → Fabric API cheat sheet (names from the official docs)

These come from [Fabric API porting (26.1)](https://docs.fabricmc.net/26.1.2/develop/porting/fabric-api), the [key mapping](https://docs.fabricmc.net/develop/key-mappings) and [HUD](https://docs.fabricmc.net/develop/rendering/hud) pages. Always copy the final code from the doc page, not from this table.

| Need | Fabric API (26.x) | Status |
|---|---|---|
| Every client tick | `ClientTickEvents.END_CLIENT_TICK` (and `START_CLIENT_TICK`) | **[verified]** END; START **[check]** |
| Level tick (renamed) | `ClientTickEvents.START_LEVEL_TICK` / `END_LEVEL_TICK` (was `…WORLD_TICK`) | **[verified]** |
| World/level changed | `ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE` | **[verified]** |
| Draw in the 3D world | `LevelRenderEvents` (renamed from `WorldRenderEvents`), many specific hooks | **[verified]** |
| Draw on the HUD | `HudElementRegistry` + `VanillaHudElements`, callback receives `GuiGraphicsExtractor` and `DeltaTracker` (`HudRenderCallback` was removed) | **[verified]** |
| Key binds | `KeyMappingHelper.registerKeyMapping(new KeyMapping(...))`, read with `consumeClick()`; categories via `KeyMapping.Category` | **[verified]** |
| Chat received | `ClientReceiveMessageEvents` | **[check]** |
| Join/leave server | `ClientPlayConnectionEvents.JOIN` / `DISCONNECT` | **[check]** |
| Client commands | `ClientCommandRegistrationCallback` | **[check]** |
| Your own events | `net.fabricmc.fabric.api.event.Event` + `EventFactory` | **[verified]** base class |

Shape of an event registration (from the Fabric events page; this is the pattern, not a drop-in):

```java
AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
    // ...
    return InteractionResult.PASS;
});
```

Typical wiring for this mod **[check names against the docs]**:

```java
public class MinerClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> MacroManager.getInstance().onTick(mc));
        // key mapping, HUD element and level-render hooks go here, per the doc pages above
    }
}
```

## 6. Mixin notes for 26.2

- Same Mixin library and annotations as before; targets are Mojang names; there are no SRG names or reobfuscation steps. (The docs do not discuss refmaps; if the generator's build file has no mixin refmap settings, don't add any.)
- Accessor/invoker interfaces must be annotated `@Mixin`, live in an `accessor` sub-package, and use your mod ID as a method prefix: `@Accessor("<field>") FieldType yourmod$getField();`. Final fields need `@Mutable` on the setter. Cast through `Object` for final classes. **[verified]** [Accessors](https://docs.fabricmc.net/develop/mixins/accessors)
- Before writing a mixin, check whether a Fabric API event already covers it. Rewrite only what the API can't do (movement packet rotation, jump/strafe yaw, packet interception).
- For anything touching movement packets, rendering or entity models, assume the 1.8.9 injection point no longer exists; read the current method and choose a new one.

## 7. Rendering for debug overlays (path lines, block boxes, text in the world)

`RenderUtil` in `05_gui` draws with 1.8.9 immediate-mode OpenGL and cannot be translated. On 26.2 you need the Blaze3D-based pipeline exposed through `LevelRenderEvents` (the context carries a pose stack and buffer/submit objects per the porting page). I did not find a complete worked example for "draw a box/line in the world" in the docs pages I could fetch, so before writing `RenderUtil`:
1. Read the Fabric docs rendering section: <https://docs.fabricmc.net/develop/rendering/hud> is the HUD page; look at the sibling pages in the sidebar for world rendering.
2. Look at how vanilla renders block outlines and debug renderers in the decompiled 26.2 sources and copy that pattern.
3. Keep `RenderUtil`'s method names (`drawBox`, `drawLine`, `drawText`…) as the interface so the pathfinder and block-selection code don't change.

## 8. Config and GUI

OneConfig (1.8.9) won't run here. Create a plain `Config` class with the same static fields first so everything else compiles, then add a screen later using a Fabric config library (confirm it supports 26.2 before you depend on it) or a hand-written `Screen`. Anything in `MightyMinerConfig` that belongs to failsafes, Discord or sounds can be dropped.

## 9. Doc index

Official Fabric
- Porting to 26.1/26.2 (start here): <https://docs.fabricmc.net/develop/porting/index>
- Migrating mappings (unobfuscated, no Yarn): <https://docs.fabricmc.net/develop/porting/mappings/index>
- Fabric API porting changes (26.1.2): <https://docs.fabricmc.net/26.1.2/develop/porting/fabric-api>
- Setting up your environment: <https://docs.fabricmc.net/develop/getting-started/setting-up>
- Creating a project: <https://docs.fabricmc.net/develop/getting-started/creating-a-project>
- Template generator: <https://fabricmc.net/develop/template/>
- Events: <https://docs.fabricmc.net/develop/events>
- Key mappings: <https://docs.fabricmc.net/develop/key-mappings>
- HUD rendering: <https://docs.fabricmc.net/develop/rendering/hud>
- Accessors/invokers: <https://docs.fabricmc.net/develop/mixins/accessors>
- Release posts: [26.1](https://fabricmc.net/2026/03/14/261.html), [26.2](https://fabricmc.net/2026/06/15/262.html)
- Version/tool selector: <https://fabricmc.net/develop/>

Vanilla changes per version (what moved, before → after)
- NeoForge primers: [26.1](https://docs.neoforged.net/primer/docs/26.1/), [26.2](https://docs.neoforged.net/primer/docs/26.2/)

Browse decompiled sources
- <https://mcsrc.dev>

## 10. What I could not verify

- Exact Fabric API version string for your build (use the generator).
- Whether the 26.2 key-mapping and HUD snippets differ from the 26.1 doc text I read; the docs site shows 26.2 as current, but check the page header says 26.2.
- Fabric API event names marked **[check]** above.
- Every ⚠️ row in `MAPPING_1.8.9_to_26.2.md`.
- Anything about how Hypixel's data (scoreboard, lore, item IDs) looks to a 26.2 client. Log it in-game first.
