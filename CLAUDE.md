# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

A Minecraft NeoForge 1.21.1 mod ("Clockwork Block") — an addon for the **Create** mod that adds two kinetic blocks.

## Build & Run

```bash
# Build the mod JAR
./gradlew build

# Run the Minecraft client
./gradlew runClient

# Run the dedicated server
./gradlew runServer

# Generate IDE project files
./gradlew eclipse
```

The output JAR is at `build/libs/clockworkblock-1.21.1-<version>-<revision>.jar`.

## Dependencies

- **NeoForge** 21.1.219 (Gradle plugin `net.neoforged.moddev` 2.0.141)
- **Create** 6.0.11 — local `includeBuild("../Create")` in settings.gradle (modify Create source in parent directory)
- **Sable** 1.2.2 (physics framework for shield block sub-level pushing, optional compileOnly)
- Java 21

## Key Architectural Patterns

### Registry Pattern
Every registry class uses NeoForge's `DeferredRegister` with a static `register(IEventBus)` method called from the mod constructor. Order in constructor matters: blocks/items register first, then block entity types (they reference blocks), then data components, creative tabs, menu types.

```java
// ClockworkBlockMod constructor — registration order
ClockworkBlocks.register(modEventBus);           // Blocks + Items
ClockworkBlockEntityTypes.register(modEventBus);  // BlockEntityType (references blocks)
ClockworkDataComponents.register(modEventBus);    // DataComponentType
ClockworkCreativeModeTabs.register(modEventBus);  // Creative tab insertion
ClockworkMenuTypes.register(modEventBus);         // MenuType for ShieldBlock GUI
```

### NBT Persistence via DataComponents
Block data survives break/place via `DataComponentType` on the item stack. Two different flows exist:

- **ClockworkBlock** (energy/speed/power): Item's `getDrops()` and `getCloneItemStack()` call `applyStoredState()` to write DataComponents before the item is created. `setPlacedBy()` reads them back. `getDrops()` is what actually fires on block break.
- **ShieldBlock**: No DataComponents — uses standard BE `write()/read()` NBT for phi/flow/range and ShieldLinkBehaviour's own NBT for frequencies.

### ClockworkBlock (Kinetic Battery)

- **Input**: Back face. `calculateStressApplied()` = `power / speed` when charging (powered by a network, no redstone signal).
- **Output**: Front face. Generates stress capacity = `MAX_ENERGY / outputSpeed` via `calculateAddedStressCapacity()` when discharging (has redstone signal, stored energy > 0).
- **Output speed**: 4 discrete steps (16/32/64/128 RPM), cycled by shift-clicking on the block. The `commonSetup` registers the default as `BlockStressValues.GeneratedRpm`.
- **Power**: Scroll value box on top face, 1–256 (`× 256 SU/rpm`). Actual max power is capped by available input speed.
- **Stored energy**: `MAX_ENERGY = 65536 × 256 × 1200 = ~2B SU·tick`. Discharge cost = `ceil(stress)` per tick.
- **Redstone toggle**: `POWERED` state change triggers `updateGeneratedRotation()`. Charging/discharging are mutually exclusive via `shouldDischarge()` and `isChargingLoadActive()`.

### ShieldBlock (Frequency-Based Entity Pusher)

- **Input**: Back face. Stress load = `range³ × flow × 16 / speed` SU.
- **Cone scanning**: Configurable phi (45–270° cone angle) and range (1–32 blocks). Entities within the cone's AABB are filtered by dot product with the facing direction ≥ `cos(phi/2)`.
- **Push force**: `flow × (cos(distance/range + π/2) + 1)` — cosine-based falloff, strongest at `distance=0`. Entity push factor = 1/2 for most entities, 1/2 for ItemEntity (same factor currently).
- **Line-of-sight**: Only checked when shield is in the main world (not on a sub-level). Uses `ClipContext` raycast from entity to block center.
- **Frequency-Shared Scan State**: Shields with the same Frequency pair share a `SharedScanState` per level per tick via a `WeakHashMap<Level, Map<FrequencyKey, SharedScanState>>`. This prevents duplicate pushes from multiple shields covering the same area. The map is cleared each game tick (keyed by `tickStamp`).
- **GUI parameter sync**: Blue face opens `ShieldBlockMenu` (server-side container) → `ShieldBlockScreen` (client) with 3 sliders. Value changes are sent as button clicks (`(paramIndex << 8) | value`), decoded server-side to call `setPhi()/setFlow()/setRange()`.
- **NBT**: `phi` (45-270), `minRange`, `flow` (1-32), `range` (1-32) — stored in standard BE NBT. Backward compatibility: `"MaxRange"` → flow, defaults range to 8 if missing.
- **Renderer**: Renders frequency items in the two frequency slots on the red face. Hover outlines via `Outliner` on client tick (`ShieldBlockRenderer.tick()` → `ClockworkBlockClientEvents`).

### Sable Physics Integration (Sub-Level Pushing)

`ShieldBlockEntity` implements `BlockEntitySubLevelActor` for pushing in Create contraption sub-levels:

- **`sable$tick(ServerSubLevel)`**: Called each game tick when the shield is inside a sub-level. Transforms local facing to world-facing via `subLevel.logicalPose().orientation()`. Scans both entities and other sub-levels, adds to the shared frequency state.
- **`sable$physicsTick(ServerSubLevel, RigidBodyHandle, double)`**: Called each physics step. Two actions:
  1. **Sub-level push**: Applies `PROPULSION` force group to target sub-levels found by `scanAndPush()`. Force = `flow × 200 × cos-falloff`. Damping and acceleration limiting applied.
  2. **Ground recoil**: Raycasts in shield facing direction. If it hits a block within range, applies opposite force to own sub-level (recoil). Recoil force transformed to sub-level local coords.
- **`getSubLevelWorldFacing()`**: Transforms the local `FACING` direction to world space using `subLevel.logicalPose().orientation()`.
- **Force groups**: Uses `ForceGroups.PROPULSION` via `getOrCreateQueuedForceGroup()`.

### Contraption Support (Main World → Sub-Level)
`ShieldBlockMovementBehaviour` implements `MovementBehaviour` for shields mounted on Create contraptions. It reads phi/flow/range from `context.blockEntityData` NBT and pushes entities using the same cone/force logic as the block entity. Entity push factor differs: 1/128 for ItemEntity, 1/32 for others.

### Client Rendering

- **ClockworkBlock**: `KineticBlockEntityRenderer` extension, renders `SHAFT_HALF` on the active face (input or output depending on `POWERED` state). Skips if Flywheel visualization is active.
- **ShieldBlock**: Custom `BlockEntityRenderer` that renders frequency items in slots. Hover outlines (`ValueBox`) rendered via `Outliner` on each client tick.
- **Screen**: `ShieldBlockScreen` extends `AbstractContainerScreen` with 3 custom sliders (`ShieldSlider extends AbstractSliderButton`). Slider values are synced from server via `containerTick()` reading menu fields.

## Mod Files (neoforge.mods.toml)

Depends on neoforge (required), minecraft 1.21.1 (required), create ≥ 6.0.10 (required).
