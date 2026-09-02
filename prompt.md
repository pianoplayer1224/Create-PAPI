# PAPI Lights — NeoForge Mod Project Brief

You're building a Minecraft mod for me. Read this whole brief before writing code, and ask me if anything below is ambiguous rather than guessing.

## Target environment
- Minecraft **1.21.1**, mod loader **NeoForge**
- Must remain compatible at runtime with: **Sodium**, **Lithium**, **Create**, **Create Aeronautics** (requires **Sable**), **JEI**
- Only **Create** (and transitively Sable/Aeronautics, if we go that route) need to be compile-time dependencies. Sodium/Lithium are rendering/performance mods we never call into directly — compatibility with them just means: use standard NeoForge/vanilla rendering APIs (vertex consumers, standard `BlockEntityRenderer`/`BakedModel` pipeline), no legacy immediate-mode GL, no reflection hacks into the vanilla renderer internals.

## What the mod does
Adds a **PAPI (Precision Approach Path Indicator)** light array: 4 blocks placed in a straight line. Each block shows red or white depending on the *viewer's* angle relative to a configured glideslope (default **3°**), exactly like real-world PAPI lights — someone flying too low sees more red, too high sees more white, on the correct path sees a 2-red/2-white split.

## Architecture decisions (already made — build to these, don't relitigate them)

1. **Colour is calculated per-player, client-side, every render pass (or throttled to every few ticks — see Performance below).** No server-authoritative "colour" state. The server only stores/syncs config: the glideslope angle and the array's orientation/facing. Each client independently computes, from its own local player's eye position, whether it's above or below the glideslope line to each active PAPI unit, and renders that unit red or white accordingly. This mirrors how real PAPI works optically (a lens splits light so different viewers at different angles see different colours) — it's not a simplification, it's the correct analogue.

2. **Multiblock behaviour is "soft" — no dedicated controller item.** All 4 placed blocks are the same block type. On placement and on neighbour update, a block scans along its own facing axis for contiguous PAPI blocks. If it finds itself in a run of **exactly 4** (no more, no fewer — real PAPI is always a 4-unit array), it activates and determines its own index (0–3) within the line. Runs of the wrong length stay inactive/dark.
   - Shared configuration (the glideslope angle) is owned by one deterministic block in the run — e.g. whichever has the lowest coordinate along the line's axis. The other 3 just read from it.
   - **Right-clicking any of the 4 blocks opens the same config screen**, reading/writing through to the leader, so the player never needs to find "the" special block.
   - Each unit's actual red/white threshold is the configured centre angle **plus a small per-index offset**, so the array shows the classic graduated real-PAPI look (all-red well below, 2-red/2-white on path, all-white well above) rather than every block switching at the exact same instant. Use a sensible default spread (something on the order of a few tenths of a degree across the 4 units — I'm giving you an approximate figure, not an authoritative one; make the spread a config value so it can be tuned, and flag it to me if you find a more precise real-world figure worth defaulting to).

3. **Visuals: attempt to reuse Create's real Nixie Tubes (two per block, matching "both bulbs" from the original spec), but treat them purely as a rendering asset — not as the source of truth for colour.**
   - **Do this investigation first, before writing the block:** pull in Create as a dependency, and inspect its actual 1.21.1 source (public repo: `github.com/Creators-of-Create/Create` — check out the 1.21.1/NeoForge branch, or read the dependency's sources jar if Gradle resolves one) for `NixieTubeBlock` / `NixieTubeBlockEntity` (exact class names may differ — find them) and however Create renders the glowing bulbs.
   - **Decision point:** if the renderer's colour-selection is cleanly overridable (e.g. a method you can override in a subclass, or a field read at render time you can substitute), extend/wrap the real block entity and renderer, keeping the model/texture but substituting your own client-computed colour at render time.
   - **Fallback:** if Create's rendering is too tightly coupled to its own networked state to override cleanly, build a **custom block that visually matches the Nixie Tube bulbs** (reuse the model/texture assets if their license/access allows, otherwise recreate a close equivalent) with your own independent `BlockEntityRenderer`. Tell me which path you ended up on and why.
   - Either way: don't rely on Create's own colour-sync, dye, or text-set mechanics for PAPI logic — those stay Create's own thing if the block happens to still support them incidentally, but our logic is independent.

## Configuration
- Right-click any of the 4 blocks → GUI screen (standard NeoForge menu/screen, not a chat command) showing:
  - Current glideslope angle, editable, defaulting to **3°**
  - (Optional, nice-to-have) the per-index angular spread, if you want it exposed rather than hardcoded
- Config is per-array (stored on the leader block entity, synced to the other 3), not a global mod config — each PAPI array the player builds can have its own glideslope.

## Performance / compatibility notes
- Recomputing an angle-to-player check every single frame for every active PAPI block is cheap trigonometry, but be sensible: throttle to something like every 1–2 ticks rather than truly every frame if profiling suggests it matters, especially with multiple arrays placed. Don't prematurely over-engineer this — get it working first, then check whether throttling is actually needed.
- Use standard vertex-consumer based rendering so Sodium's chunk/BER pipeline doesn't need any special-casing.
- No client-only assumptions that break dedicated servers — the mod is client+server; only the colour computation is client-only, everything else (placement, multiblock detection, config storage/sync) is normal server-authoritative state.

## JEI
- Not core to functionality. If low-effort, add a simple JEI info page explaining how to build/configure the array. Don't block on this — treat it as a nice-to-have pass at the end.

## Suggested build order
1. **Investigate Create's Nixie Tube source** and settle the reuse-vs-custom decision from point 3 above. Report back what you found.
2. Scaffold the NeoForge 1.21.1 MDK project (mod id/package — use a sensible placeholder and I'll rename if I want something specific), get `Create`/`Sable`/`Create Aeronautics`/`JEI` resolving as dependencies via `build.gradle` (verify the correct maven repos — don't guess a URL, look it up).
3. Implement the single PAPI block, its placement/neighbour-scan multiblock detection, and the leader/config-sync logic. Get it working with a placeholder appearance (even a plain coloured block) before touching Nixie Tube integration — validate the multiblock and glideslope math first.
4. Implement the config GUI screen.
5. Wire up the actual rendering per the decision from step 1.
6. Test in a `runClient` environment with the full mod list (Create, Sable, Aeronautics, JEI, Sodium, Lithium) to confirm no conflicts.
7. Optional JEI page.

## When to check in with me
- If Create's Nixie Tube renderer turns out to require deep reflection/mixins to override — tell me before going down that path, since it's fragile across Create updates.
- If the maven repos for Create/Aeronautics/Sable/JEI aren't straightforward to resolve.
- Any point where "real PAPI behaviour" is ambiguous for a voxel-block context and you're making a judgment call.
