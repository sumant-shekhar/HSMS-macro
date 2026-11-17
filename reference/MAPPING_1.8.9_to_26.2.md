# Name mapping: Forge 1.8.9 (MCP/SRG) → Minecraft 26.2 (Mojang names, Fabric)

Scope: only the classes, methods and events that the files in this bundle actually use (135 distinct Minecraft/Forge/LWJGL/OneConfig imports were counted across the bundle).

## Read this first — how reliable is this table?

Minecraft **26.x ships unobfuscated with Mojang's own names**; Yarn is no longer used for 26.x, and mods do not need a mappings step ([Fabric porting guide](https://docs.fabricmc.net/develop/porting/index), [mappings page](https://docs.fabricmc.net/develop/porting/mappings/index)). So the "new" column is simply the class and method names you see in the decompiled game.

Every row has a confidence mark:

| Mark | Meaning |
|---|---|
| ✅ | The new name appears in documentation I fetched on 2026-11-17 (Fabric docs, Fabric API porting page, NeoForge 26.1 primer). |
| ⚠️ | The new name is the Mojang name as I know it from 1.21.x. Mojang names are stable across versions, but I could **not** re-check it against 26.2 from here. Confirm in your IDE (Ctrl+click / search for the class) before relying on it. |
| ❌ | No equivalent. The feature was removed or redesigned and the code must be rewritten. |

**This is not a machine-readable mapping file.** I had no access to the mapping files themselves in this session (no network from the shell). Treat this as a translation guide. To check any name, search the decompiled 26.2 sources in your IDE, or use <https://mcsrc.dev> (linked from Fabric's 26.1 release post), and use the NeoForge primers listed at the end for "what changed" per version.

Two more cautions:
- 1.8.9 → 26.2 is ten years of change. Renames are the easy part. Many classes below were **split, merged or redesigned**, and I note that where I know it.
- A NeoForge primer URL for 26.3 also appeared in search results. Your target is 26.2; check whether 26.3 has shipped before you pin versions.

---

## 1. Naming conventions (1.8.9 MCP → Mojang)

| 1.8.9 term | Mojang term | Conf. |
|---|---|---|
| World | Level | ✅ (`ClientLevel`) |
| EntityPlayerSP | LocalPlayer | ⚠️ |
| EntityPlayer | Player | ⚠️ |
| EntityLivingBase | LivingEntity | ⚠️ |
| Entity*, Tile entity | Entity, BlockEntity | ⚠️ |
| IBlockState | BlockState | ⚠️ |
| BlockPos | BlockPos (same) | ⚠️ |
| Vec3 | Vec3 (same) | ⚠️ |
| AxisAlignedBB | AABB | ⚠️ |
| MathHelper | Mth | ⚠️ |
| EnumFacing | Direction | ⚠️ |
| Packet classes `S3CPacketUpdateScore` | `Clientbound…Packet` / `Serverbound…Packet` | ⚠️ |
| `IChatComponent` / `ChatComponentText` | `Component` | ⚠️ |
| `EnumChatFormatting` | `ChatFormatting` | ⚠️ |
| Metadata / damage values on blocks and items | Block-state **properties** and item **data components** | ✅ (components) |

---

## 2. Core client access

| 1.8.9 | 26.2 | Conf. | Note |
|---|---|---|---|
| `Minecraft.getMinecraft()` | `Minecraft.getInstance()` | ✅ | |
| `mc.thePlayer` | `minecraft.player` (type `LocalPlayer`) | ⚠️ | |
| `mc.theWorld` | `minecraft.level` (type `ClientLevel`) | ⚠️ (type ✅) | |
| `mc.playerController` (`PlayerControllerMP`) | `minecraft.gameMode` (`MultiPlayerGameMode`) | ✅ class (`MultiPlayerGameMode#handleInventoryMouseClick` appears in the primer) | |
| `mc.gameSettings` (`GameSettings`) | `minecraft.options` (`Options`) | ✅ class (`Options` in primer) | |
| `mc.currentScreen` | `minecraft.gui.screen()` | ✅ | In 26.2, screen handling moved onto `Gui`: `Minecraft.getInstance().screen` → `Minecraft.getInstance().gui.screen()`, `setScreen` likewise (NeoForge 26.2 primer, per my fetch summary). |
| `mc.fontRendererObj` (`FontRenderer`) | `minecraft.font` (`Font`) | ✅ class | |
| `mc.getNetHandler()` (`NetHandlerPlayClient`) | `player.connection` (`ClientPacketListener`) | ✅ class | |
| `mc.timer.renderPartialTicks` (via accessor) | `DeltaTracker` (e.g. `getGameTimeDeltaPartialTick(false)`) | ⚠️ | HUD callbacks hand you a `DeltaTracker` ✅ |
| `Minecraft.isGamePaused`, `inGameHasFocus` | `minecraft.isPaused()`, `minecraft.isWindowActive()` | ⚠️ | |
| `Display.isActive()` | `minecraft.isWindowActive()` | ⚠️ | LWJGL 2 `Display` is gone; GLFW via `minecraft.getWindow()` |
| `Display.setTitle(...)` | `minecraft.getWindow().setTitle(...)` | ⚠️ | |
| `mc.getSession().getUsername()` | `minecraft.getUser().getName()` | ⚠️ | |
| `mc.mouseHelper` (`MouseHelper`) | `minecraft.mouseHandler` (`MouseHandler`) | ⚠️ | Your `MouseUngrab` replaces the helper object; on 26.2 you must hook `MouseHandler` (e.g. `grabMouse`/`releaseMouse`, cursor mode) instead. |
| `mc.gameSettings.gammaSetting = 1000` | `options.gamma().set(...)` (an `OptionInstance`) | ⚠️ | Values are clamped differently now. |

## 3. Player and entity members

| 1.8.9 | 26.2 | Conf. |
|---|---|---|
| `entity.rotationYaw` / `rotationPitch` | `getYRot()` / `getXRot()` (+ `setYRot`/`setXRot`) | ⚠️ |
| `entity.prevRotationYaw` | `yRotO` | ⚠️ |
| `entity.posX/posY/posZ` | `getX()/getY()/getZ()`, `position()` | ⚠️ |
| `entity.motionX/Y/Z` | `getDeltaMovement()` (`Vec3`) | ⚠️ |
| `entity.onGround` | `onGround()` | ⚠️ |
| `entity.getPositionEyes(1f)` | `getEyePosition()` | ⚠️ |
| `entity.getEntityBoundingBox()` | `getBoundingBox()` | ⚠️ |
| `entity.getDistanceSqToEntity` | `distanceToSqr(Entity)` | ⚠️ |
| `entity.isInWater()` | `isInWater()` | ⚠️ |
| `entity.getName()` / `getCustomNameTag()` | `getName()` (a `Component`) / `getCustomName()` | ⚠️ |
| `EntityLivingBase.getAIMoveSpeed()` | `getSpeed()` (attribute-based) | ⚠️ |
| `EntityLivingBase.getActivePotionEffect(Potion.jump)` | `getEffect(MobEffects.JUMP_BOOST)` (`Holder`-based) | ⚠️ |
| `EntityOtherPlayerMP` | `RemotePlayer` | ⚠️ |
| `EntityArmorStand` | `ArmorStand` | ⚠️ |
| `EntityItem` | `ItemEntity` | ⚠️ |
| `entity instanceof EntityLiving` | `Mob` | ⚠️ |
| `EntityPlayerSP.sendChatMessage("/warp x")` | `player.connection.sendCommand("warp x")` (**no leading slash**) / `sendChat("text")` | ⚠️ |
| `thePlayer.addChatMessage(new ChatComponentText(s))` | `player.sendSystemMessage(Component)` | ✅ (`Player#displayClientMessage` was split into `sendSystemMessage` and `sendOverlayMessage` in 26.1) |
| `EntityPlayerSP.dropOneItem` | drop is driven from input handling (`Minecraft`/`LocalPlayer.drop`) | ⚠️ — re-find the hook |
| `EntityPlayerSP.onUpdateWalkingPlayer()` (movement packet builder) | `LocalPlayer.sendPosition()` is where I'd start looking | ⚠️ **unverified** — name and structure have changed several times |
| `lastReportedYaw/Pitch` (via accessor) | `yRotLast` / `xRotLast` fields on `LocalPlayer` | ⚠️ **unverified** |

## 4. World, blocks, collision

| 1.8.9 | 26.2 | Conf. | Note |
|---|---|---|---|
| `world.getBlockState(pos)` | `level.getBlockState(pos)` | ⚠️ | |
| `state.getBlock()` | `state.getBlock()` | ⚠️ | |
| `Blocks.stone` etc. (`net.minecraft.init.Blocks`) | `Blocks.STONE` (`net.minecraft.world.level.block.Blocks`) | ⚠️ | UPPER_CASE |
| `block.isAir(...)`, `BlockAir` | `state.isAir()` | ⚠️ | |
| `block.getCollisionBoundingBox(world,pos,state)` | `state.getCollisionShape(level,pos)` → `VoxelShape` (`.max(Direction.Axis.Y)`, `.isEmpty()`) | ⚠️ | Used by the pathfinder height checks (module 02). |
| `block.isPassable(world,pos)` | ❌ no direct equivalent; use "collision shape is empty" and fluid/state checks | ⚠️ | |
| `block.isNormalCube()`, `isFullCube()` | `state.isCollisionShapeFullBlock(level,pos)` / `isSolidRender()` | ⚠️ | Pick per case. |
| `state.getValue(BlockSlab.HALF)`, `BlockStairs.FACING` | `SlabBlock.TYPE` / `StairBlock.FACING`, `BlockStateProperties.HALF` | ⚠️ | Property names are in `BlockStateProperties`. |
| `BlockLiquid.LEVEL` | `LiquidBlock.LEVEL` / `FluidState` | ⚠️ | Fluids are separate `FluidState`s now. |
| `BlockSnow.LAYERS` | `SnowLayerBlock.LAYERS` | ⚠️ | |
| Wool/stained glass/clay/prismarine **with metadata** | One block per colour (e.g. `Blocks.GRAY_WOOL`) | ⚠️ | **Breaks `MineableBlock` state IDs completely.** |
| `Block.getStateId(state)` / `Block.getStateById` | `Block.getId(state)` / `Block.stateById(id)` (global state-ID registry) | ⚠️ | IDs are **not** the 1.8.9 numbers. |
| `world.rayTraceBlocks(from, to, ...)` | `level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity))` | ⚠️ | |
| `MovingObjectPosition` | `HitResult` → `BlockHitResult` / `EntityHitResult` | ⚠️ (`EntityHitResult` ✅ in primer) | |
| `mc.objectMouseOver` | `minecraft.hitResult` | ⚠️ | `GameRenderer#pick` → `Minecraft#pick` (private) in 26.1 ✅ |
| `world.getChunkFromBlockCoords`, `Chunk` | `level.getChunk(x,z)` / `LevelChunk` | ⚠️ | |
| `ChunkProviderClient.chunkListing` (accessor mixin) | ❌ no equivalent; use `ClientChunkCache` / `level.getChunkSource()` and snapshot on the main thread | ⚠️ | Module 02: see notes. |
| `WorldClient` | `ClientLevel` | ✅ | |
| `IBlockAccess` | `BlockGetter` / `LevelReader` | ⚠️ | |
| `BiomeGenBase`, `WorldType`, `WorldSettings` | Biome via registry; world type/settings reworked | ❌ | Only used for mixin signatures. |

## 5. Items, inventory, containers, GUI logic

| 1.8.9 | 26.2 | Conf. | Note |
|---|---|---|---|
| `ItemStack` | `ItemStack` | ✅ | now `TypedInstance<Item>`; `getItemHolder` → `typeHolder` |
| `stack.getDisplayName()` | `stack.getHoverName()` / `DataComponents.CUSTOM_NAME`, `DataComponents.ITEM_NAME` | ✅ (`ITEM_NAME` in Fabric docs) | |
| Lore via `getTagCompound().getCompoundTag("display").getTagList("Lore")` (`NBTTagList`) | `stack.get(DataComponents.LORE)` (`ItemLore`) | ⚠️ | **No NBT `display` tag any more.** |
| SkyBlock `ExtraAttributes.id` (custom NBT) | `DataComponents.CUSTOM_DATA` (`CustomData`) | ⚠️ | Hypixel's data may arrive in a different shape on a modern client; **log it first**. |
| `Container`, `ContainerChest` | `AbstractContainerMenu`, `ChestMenu` | ⚠️ | |
| `GuiContainer`, `GuiChest` | `AbstractContainerScreen`, `ContainerScreen` | ⚠️ | |
| `IInventory` / `lowerChestInventory` | `Container` / `ChestMenu.getContainer()` | ⚠️ | |
| `Slot`, `slot.getStack()` | `Slot`, `slot.getItem()` | ⚠️ | |
| `playerController.windowClick(id, slot, button, mode, player)` | `gameMode.handleInventoryMouseClick(containerId, slotId, button, ContainerInput, player)` | ✅ | `ClickType` → **`ContainerInput`** in 26.1 |
| `GuiContainer.handleMouseClick` (mixin) | `AbstractContainerScreen#slotClicked(slot, id, button, ContainerInput)` | ✅ | |
| `InventoryPlayer.changeCurrentItem` (mixin, blocks scroll) | `Inventory.setSelectedSlot` / scroll handled in `MouseHandler` | ⚠️ | |
| `InventoryPlayer.currentItem` | `inventory.getSelectedSlot()` / `setSelectedSlot(int)` | ⚠️ | |
| `GuiScreen.renderBackground`, `drawScreen` | `Screen#extractBackground`, `extractRenderState` | ✅ | 26.1 renamed render → **extract** |
| `ScaledResolution` | `minecraft.getWindow()` (`getGuiScaledWidth/Height`) | ⚠️ | |

## 6. Scoreboard, tab list, chat, packets (module 03 and 06)

| 1.8.9 | 26.2 | Conf. |
|---|---|---|
| `Scoreboard`, `ScoreObjective`, `Score`, `ScorePlayerTeam` | `Scoreboard`, `Objective`, score entries (`ScoreHolder`-based), `PlayerTeam` | ⚠️ — scoreboard API was reworked in 1.20.3+; re-read it, don't port by name |
| `scoreboard.getObjectiveInDisplaySlot(1)` (sidebar) | `scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR)` | ⚠️ |
| `ScorePlayerTeam.formatPlayerName(team, name)` | `PlayerTeam.formatNameForTeam(team, Component)` | ⚠️ |
| `GuiPlayerTabOverlay` | `PlayerTabOverlay` | ⚠️ |
| `NetworkPlayerInfo` | `PlayerInfo` (`getTabListDisplayName()`) | ⚠️ |
| `NetHandlerPlayClient` | `ClientPacketListener` | ✅ |
| `S3CPacketUpdateScore` | `ClientboundSetScorePacket` | ⚠️ |
| `S3DPacketDisplayScoreboard` | `ClientboundSetDisplayObjectivePacket` | ⚠️ |
| `S3EPacketTeams` | `ClientboundSetPlayerTeamPacket` | ⚠️ |
| `S38PacketPlayerListItem` (tab list) | `ClientboundPlayerInfoUpdatePacket` | ⚠️ |
| `S47PacketPlayerListHeaderFooter` | `ClientboundTabListPacket` | ⚠️ |
| `S2APacketParticles` | `ClientboundLevelParticlesPacket` | ⚠️ |
| `S03PacketTimeUpdate` | `ClientboundSetTimePacket` | ✅ |
| `S08PacketPlayerPosLook` | `ClientboundPlayerPositionPacket` | ⚠️ |
| `NetworkManager.channelRead0` (mixin hook for all packets) | `Connection.channelRead0` (netty) | ⚠️ |
| `ChatComponentText`, `StringUtils.stripControlCodes` | `Component`, `ChatFormatting.stripFormatting(String)` | ⚠️ |
| `ClientChatReceivedEvent` | Fabric API `ClientReceiveMessageEvents` | ⚠️ (name from my knowledge of Fabric API; not in the docs I fetched) |
| `EnumParticleTypes.CRIT` etc. | `ParticleTypes.CRIT` (`SimpleParticleType`) | ⚠️ |

**Text is different on a modern client.** 1.8.9 code strips `§x` colour codes from strings. Modern clients carry `Component` trees. Call `component.getString()` to get plain text first, then run your regexes, and log real Hypixel lines before trusting any parser.

## 7. Input

| 1.8.9 | 26.2 | Conf. |
|---|---|---|
| `KeyBinding` | `KeyMapping` | ✅ |
| `new KeyBinding(name, keyCode, category)` + `ClientRegistry.registerKeyBinding` | `KeyMappingHelper.registerKeyMapping(new KeyMapping(translationKey, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, category))` with a `KeyMapping.Category` created via `KeyMapping.Category.register(Identifier)` | ✅ |
| `keyBinding.isPressed()` | `keyMapping.consumeClick()` | ✅ |
| `keyBinding.isKeyDown()` | `keyMapping.isDown()` | ⚠️ |
| `KeyBinding.setKeyBindState(code, bool)` / `onTick(code)` | `keyMapping.setDown(bool)` and `KeyMapping.click(InputConstants.Key)` | ⚠️ |
| `gameSettings.keyBindForward/Back/Left/Right/Jump/Sneak/Sprint/Attack/UseItem` | `options.keyUp / keyDown / keyLeft / keyRight / keyJump / keyShift / keySprint / keyAttack / keyUse` | ⚠️ |
| Hard-coded LWJGL 2 key codes in `KeyBindUtil` (`17,30,31,32` = W,A,S,D) | GLFW codes (`87,65,83,68`); better: compare `KeyMapping` objects, never numbers | ⚠️ — **this switch will silently misbehave if copied** |
| `Keyboard.*`, `Mouse.*` (LWJGL 2) | GLFW via `InputConstants` and `minecraft.getWindow()` | ⚠️ |
| `Minecraft.clickMouse()/rightClickMouse()/middleClickMouse()` via accessor | `Minecraft.startAttack()` / `startUseItem()` / `pickBlock()` (private; use accessor/invoker mixins) | ⚠️ **unverified** |

## 8. Rendering and HUD (module 05)

| 1.8.9 | 26.2 | Conf. |
|---|---|---|
| `GL11`, `GlStateManager`, `Tessellator`, `WorldRenderer`, `DefaultVertexFormats` | ❌ removed. Raw OpenGL must be replaced by the Blaze3D rendering API; `Tesselator` and `MultiBufferSource` are gone | ✅ ([Fabric porting guide](https://docs.fabricmc.net/develop/porting/index)) |
| `RenderGameOverlayEvent` | `HudElementRegistry` (e.g. `attachElementBefore(VanillaHudElements.CHAT, id, (graphics, deltaTracker) -> …)`) | ✅ |
| `GuiGraphics` | `GuiGraphicsExtractor` (drawing methods lose the `render`/`draw` prefix, e.g. `renderItem` → `item`) | ✅ |
| `fontRenderer.drawString(...)` | `graphics.text(font, text, x, y, color)` style call on the extractor | ⚠️ — check the method name in your IDE; I only confirmed `fill` in the docs |
| `RenderWorldLastEvent` | `LevelRenderEvents` (many specific events; no single "last") | ✅ (renamed from `WorldRenderEvents`) |
| `RenderManager.viewerPosX/Y/Z` | camera position from the render context / `minecraft.gameRenderer.getMainCamera().position()` | ⚠️ |
| `ModelBiped.setRotationAngles` (mixin) | ❌ entity models now run through **render states** (`HumanoidRenderState`, `HumanoidModel.setupAnim`) | ✅ state classes appear in primer; exact hook ⚠️ |
| `Entity.renderYawOffset` | `yBodyRot` | ⚠️ |
| OneConfig `TextHud` | your own HUD element (above) | ✅ (replacement) |
| OneConfig `OneColor` | plain ARGB `int` | ⚠️ |

## 9. Forge events and mod infrastructure → Fabric

| Forge 1.8.9 | Fabric 26.2 | Conf. |
|---|---|---|
| `@Mod` + `FMLPreInitializationEvent` / `Init` / `PostInit` | `ClientModInitializer.onInitializeClient()` declared in `fabric.mod.json` (`"entrypoints": {"client": [...]}`) | ⚠️ (standard Fabric layout; generator output is authoritative) |
| `MinecraftForge.EVENT_BUS.register(obj)` / `@SubscribeEvent` | register callbacks directly on Fabric events (no annotation scanning) | ✅ pattern (`Event#register`) |
| `MinecraftForge.EVENT_BUS.post(new MyEvent())` (in mixins) | your own `Event<T>` made with `EventFactory`, or a plain static listener list | ⚠️ |
| `TickEvent.ClientTickEvent` (`Phase.START/END`) | `ClientTickEvents.START_CLIENT_TICK` / `END_CLIENT_TICK` | ✅ `END_CLIENT_TICK`, ⚠️ START |
| `WorldEvent.Load/Unload` | `ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE`, `ClientPlayConnectionEvents.JOIN/DISCONNECT` | ✅ / ⚠️ |
| `InputEvent.KeyInputEvent` | poll `KeyMapping#consumeClick()` in the tick event | ✅ |
| `RenderWorldLastEvent` | `LevelRenderEvents.*` | ✅ |
| `RenderGameOverlayEvent` | `HudElementRegistry` | ✅ |
| `ClientChatReceivedEvent` | `ClientReceiveMessageEvents` | ⚠️ |
| `PlayerEvent` (logout) | `ClientPlayConnectionEvents.DISCONNECT` | ⚠️ |
| `@Cancelable` events | Fabric events return a result (`InteractionResult`, `boolean`) instead; design your own | ✅ pattern (`AttackBlockCallback` returns `InteractionResult`) |
| `@SideOnly(Side.CLIENT)` | client-only code lives in the client source set / `@Environment(EnvType.CLIENT)` | ⚠️ |
| `Display`/LWJGL 2 | GLFW (bundled) | ⚠️ |
| OneConfig `Config`, `@Switch/@Slider/...` | a Fabric config library (check that it supports 26.2) or your own screen | — |
| OneConfig `CommandManager` + `@Command` | Fabric API client commands (`ClientCommandRegistrationCallback`) | ⚠️ |
| OneConfig `Multithreading`, `Notifications` | `java.util.concurrent` executor; vanilla `SystemToast` | ⚠️ |
| Lombok | works the same; keep it if you like it | — |
| Kotlin (pathfinder) | Fabric language adapter for Kotlin, or rewrite to Java | ⚠️ |
| fastutil `Long2ObjectOpenHashMap` | Minecraft already bundles fastutil | ⚠️ |

## 10. Mixins: every mixin in the bundle and what to do with it

Mixin works the same way on 26.x, but there is no SRG/obfuscation step; targets use Mojang names. Accessor/invoker interfaces must be annotated `@Mixin`, and method names should carry your mod ID prefix, e.g. `example_mod$getFieldName()` ([Fabric accessor docs](https://docs.fabricmc.net/develop/mixins/accessors)).

| 1.8.9 mixin | What it does | 26.2 approach | Risk |
|---|---|---|---|
| `MixinChunkProviderClient` + `IChunkProviderClient` | Exposes chunk list | Probably unnecessary; read via `ClientChunkCache`/`level.getChunk` on the main thread | Medium |
| `MixinChunk` | Fires `BlockChangeEvent` | Fabric has no direct "block changed" client event I could verify; use a `ClientboundBlockUpdatePacket`/section update hook | Medium |
| `MixinNetHandlerPlayClient` | Tab list, scoreboard, footer, particles → events | Mixin on `ClientPacketListener` handlers for those packets | Medium |
| `MixinNetworkManager` | All packets → `PacketEvent` + scoreboard line cache | Mixin on `Connection` packet read; or use `ClientPlayNetworking` listeners | Medium |
| `MixinEntityPlayerSP` | Rewrites yaw/pitch used in the movement packet; cancels item drop while macro runs | Mixin on `LocalPlayer` where the move packet is built | **High** (structure changed) |
| `MixinEntityLivingBase` | Overrides the yaw used for jump boost and for strafing so movement direction ≠ camera | Mixin on `LivingEntity`/`Entity` movement-input code | **High** |
| `MixinModelBiped` | Shows server-side head rotation on the player model | Entity render-state pipeline; probably a different mixin target | **High** |
| `MixinMinecraft` | Suppresses one key check in `runTick` and forces focus when macro runs | `Minecraft` tick/focus code; find the new locations | High |
| `MinecraftAccessor`, `EntityPlayerSPAccessor` | Timer, click methods, last-reported yaw/pitch | Accessors/invokers on `Minecraft`, `LocalPlayer` | Medium |
| `MixinBlockStainedGlassPane` | Forces full-block bounds on panes | Block shape is now data-driven (`VoxelShape` via `getShape`/`getCollisionShape`); a different approach is needed | Medium |
| `MixinGuiContainer`, `MixinInventoryPlayer` | Cancel slot clicks and scroll while macro runs | Mixin on `AbstractContainerScreen#slotClicked` and on hotbar-scroll code | Medium |

---

## 11. Where to look when a name isn't here

1. Open the class in your IDE's decompiled 26.2 sources and read the Javadoc/methods. Mojang names mean the class you see is the class that runs.
2. <https://mcsrc.dev> — browser decompiler for Minecraft versions (linked from Fabric's 26.1 post).
3. NeoForge primers list what changed between versions with before → after names. They are written for NeoForge but cover vanilla changes: [26.1](https://docs.neoforged.net/primer/docs/26.1/), [26.2](https://docs.neoforged.net/primer/docs/26.2/). Older primers (1.21.x) cover the years between 1.8.9's mappings and 26.1.
4. Fabric API changes: [Fabric API porting for 26.1](https://docs.fabricmc.net/26.1.2/develop/porting/fabric-api).

## 12. Behaviour differences that matter for this code (not just names)

- **Block metadata is gone.** Every `getMetaFromState`/state-ID check must become a block or property check (applies to `MineableBlock`, `getBlockStrength`, pathfinder helpers).
- **Item data is in components, not NBT.** Lore, names and Hypixel's custom data need rewriting (`InventoryUtil`, `AutoGetStats`, every feature that matches item names).
- **Chat/scoreboard text is `Component`s.** Don't rely on `§` codes; see section 6.
- **Movement physics changed** (sneaking, swimming pose, sprint rules, step height). The pathfinder's cost constants are 1.8.9-specific.
- **Hitboxes and combat timing changed.** Anything that relies on 1.8 click rate or hitbox size (mob killing, aim points) needs re-tuning.
- **Click handling moved.** Attack/use/pick are separate paths now; there is no `leftClickCounter` to poke.
- **Screens/HUD use the "extract" render model** (26.1+), not immediate draw calls.

Sources: [Fabric: Porting to 26.1/26.2](https://docs.fabricmc.net/develop/porting/index), [Fabric: Migrating mappings](https://docs.fabricmc.net/develop/porting/mappings/index), [Fabric API porting (26.1.2)](https://docs.fabricmc.net/26.1.2/develop/porting/fabric-api), [Fabric: Key mappings](https://docs.fabricmc.net/develop/key-mappings), [Fabric: HUD rendering](https://docs.fabricmc.net/develop/rendering/hud), [Fabric: Accessors](https://docs.fabricmc.net/develop/mixins/accessors), [NeoForge primer 26.1](https://docs.neoforged.net/primer/docs/26.1/), [NeoForge primer 26.2](https://docs.neoforged.net/primer/docs/26.2/).
