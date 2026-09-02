# Create PAPI

A working PAPI (Precision Approach Path Indicator) array for Minecraft **1.21.1** / **NeoForge**.

Units in a row show red or white depending on the **viewer's own** angle relative to a configurable
glideslope, exactly as the real thing does: too low is all red, too high is all white, and two people
looking from different heights at the same instant see different colours.

Build it **four** units wide for a full PAPI, or **two** for an **APAPI** — the abbreviated version
installed on shorter runways.

---

## Building an array

1. Place **four** PAPI Lights in a straight row, all facing the same way — or **two** for an APAPI.
   The block aims in the direction you place it from, so stand where the approach is and place.
   The row runs **perpendicular** to that aim direction, like a real installation.
2. A run of any other length — one, three, five, or a row with mismatched facings — stays dark.
3. **Right-click any unit** to open the configuration screen. Every unit opens the same settings;
   the screen reads and writes through to the array's leader.

On the configured path a PAPI reads two white nearest the runway and two red beyond; an APAPI reads
one white and one red.

Craft one from a Create Electron Tube above a Redstone Dust. The item lives in Create's
creative tab.

## Configuration

Per array, not global — every array you build has its own settings, stored on its leader and
mirrored to the other three.

| Setting | Default | Meaning |
| --- | --- | --- |
| Glideslope | `3°` | The nominal approach path angle. |
| Spread per unit | `0.3333°` PAPI / `0.5°` APAPI | Angular step between adjacent units. |

The defaults are the real-world figures from ICAO Annex 14 and FAA AC 150/5340-30:

- **PAPI** — **20 arcminutes** between adjacent units, putting a 3° array at 2.5° / 2.8333° /
  3.1667° / 3.5°, a 1° total spread.
- **APAPI** — units **15 arcminutes** either side of the path, so 2.75° / 3.25° for a 3° approach.

Unit 0 is set shallowest and sits nearest the runway; the highest index is steepest and sits
furthest away.

An array you have never configured always shows the correct textbook angles for whatever size it
currently is, so converting a PAPI to an APAPI re-defaults the spread. Once you apply settings by
hand they stand, whatever the array is later rebuilt as; the **Defaults** button fills in the
standard values for the current size.

---

## How it works

### Colour is never networked

There is no server-side "colour" state and nothing about colour is ever sent over the wire. Each
client computes, from its own camera position, the elevation angle to each unit's bulb and compares
it against that unit's cut-off angle. This is not a shortcut — it is the correct analogue of the
real optics, where a split lens shows red below the cut-off and white above it to whoever happens to
be looking.

The server owns only the array geometry (which blocks are in a run, and each one's index) and the
configuration (glideslope, spread). Both are ordinary server-authoritative block entity state.

Units go dark when the viewer is behind the array. Real PAPI is directional; you see nothing from
the back.

### "Soft" multiblock

No controller block, no controller item — all four units are the same block.

On placement, neighbour update, and removal, a block walks the horizontal axis perpendicular to its
own facing, collecting contiguous PAPI blocks that share that facing. A run of four (PAPI) or two
(APAPI) activates and every member learns its index; any other length leaves the whole run dark.
Because the scan walks the whole run and writes to all of it, a change anywhere keeps the entire
array consistent.

| Run length | 1 | 2 | 3 | 4 | 5+ |
| --- | --- | --- | --- | --- | --- |
| Result | dark | APAPI | dark | PAPI | dark |

Ordering is derived from geometry alone, so all four agree without negotiating: index 0 is the unit
at the `facing.getCounterClockWise()` end, which is the pilot's right as they look back down the
approach. That is what makes the standard **RED RED WHITE WHITE** (or **RED WHITE**) reading come out
the right way round. The index-0 unit is also the leader that owns the configuration.

Cut-off angles are spaced evenly and symmetrically about the centre angle —
`glideslope + (index - (units - 1) / 2) * spread` — so the same formula covers both sizes.

Configuration survives an array being broken and rebuilt: on each rescan the settings are taken from
the previous leader if it is still present, then from any unit that was already part of a working
array, and only then from the defaults.

### Rendering

Each unit is a **single-bulb** housing. Create's `nixie_tube` model is a `neoforge:composite` of two
independent cuboid pairs — `connector1`/`tube1` and `connector2`/`tube2` — and both pairs sample the
same UV regions of `create:block/nixie_tube`, because that texture only ever held one bulb's worth of
pixels. So our model declares one such pair, centred in the block, and points at Create's texture:
one bulb of geometry, referenced at runtime, nothing copied.

The glowing element is drawn by our block entity renderer, which calls Create's public
`NixieTubeRenderer.drawTube(...)` and passes it the colour this client just computed.

That method takes the colour as a parameter, so no mixin, no reflection, and no subclass of Create's
block entity is involved — the integration is one static call. Nothing depends on Create's own
colour sync, dye, or text-set mechanics.

Everything goes through standard vertex consumers and the normal `BlockEntityRenderer` /
`BakedModel` pipeline, so Sodium needs no special-casing.

**Create is a required dependency**, not an optional one: its assets are not redistributable, so the
texture is referenced at runtime rather than copied into this jar. Without Create installed the block
would render untextured.

---

## Development

The mod id is `papilights` and the Java package is `com.papilights`, both predating the "Create PAPI"
name. They are deliberately left alone: the id is the namespace for every block, item, model and
loot table, so changing it would break existing worlds.

Dependencies come straight out of the sibling `mods/` folder rather than from a maven. Since 1.20.x
NeoForge ships production jars against official Mojang mappings, so a distributed mod jar is usable
as a compile-time dependency directly — and pinning the exact jars you run removes any chance of a
version skew.

```bash
./gradlew build
```

`runClient` and `runServer` sync `mods/*.jar` into their game directories first, so the dev runs boot
with the full target mod list.

```bash
./gradlew runClient
```

Sodium is excluded from the server run: it installs a ModLauncher service that probes the LWJGL
version before FML applies side filtering, which hard-crashes any dedicated server at bootstrap. It
is a client-only renderer, so this only affects where the jar is placed.

```bash
./gradlew runServer
```

### Layout

| Path | |
| --- | --- |
| `block/PapiArray` | Run detection, unit ordering, leader election, config propagation |
| `block/PapiLightBlock` | Placement, neighbour hooks, right-click |
| `block/PapiLightBlockEntity` | Index / leader / config state, persistence and sync |
| `client/PapiOptics` | Per-viewer red-vs-white decision |
| `client/PapiLightRenderer` | Bulb rendering via Create's `drawTube` |
| `client/PapiConfigScreen` | Configuration GUI |
| `menu/PapiConfigMenu` | Slot-less menu backing the screen |
| `network/SetPapiConfigPayload` | Client to server config write, validated and clamped |
| `compat/PapiJeiPlugin` | Optional JEI information page |
