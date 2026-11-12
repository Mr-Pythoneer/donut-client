# NewChunks

NewChunks marks chunks that the server appears to be **generating for the first time** while you watch (red), and optionally chunks that **already existed** (green), with a flat coloured overlay at a height you choose. It starts **disabled**.

Open **Right Shift → Visual → NewChunks** (or search for `new chunks`). Assign a shortcut in its inspector or under Minecraft's Controls (**Toggle NewChunks**; unbound by default). It supports Favorites and the module list.

## How it decides (and why it's only a guess)

NewChunks uses only packets the client already receives. It never requests chunks or sends anything.

When a server generates a chunk, liquids from lakes, springs and aquifers are placed as still **source** blocks with fluid ticks still pending. Those ticks run after the chunk exists, so the client then receives block updates for **flowing** liquid spreading from a source. A chunk that existed before has its liquids settled: its flowing water and lava are already part of the chunk data when it arrives. So:

- a chunk that **arrives with flowing liquid already in its data** is marked **old**;
- a **flowing-liquid block update next to a source** (beside it or below it), in a chunk not already marked old, marks it **new**.

This is the method other clients describe publicly as "NewChunks" (liquid flow detection); Donut's implementation is its own. The decision is a small pure state machine (`NewChunksTracker`) fed from the block-update, section-update and chunk packets after vanilla applied them.

Marks are **sticky for the session**: a new chunk stays new when you come back to it (its settled water would otherwise make it look old), and an old chunk ignores later flow. Everything is cleared when you disable the module, change dimension or world, or disconnect. Only chunks received while the module is on are classified.

### False positives and gaps, honestly

- **Chunks without liquid are never classified.** Plains, deserts and most of the Nether's interior stay unmarked.
- **Anyone's water counts.** A bucket of water (or a broken ice block, a sponge, a door opening onto water) in an old chunk whose data had no flowing liquid yet marks it **new**. The game test uses exactly this to drive the packet path.
- **Chunk batching makes "old" over-report in fresh terrain.** Since 1.20.2 the server sends chunks in batches at a rate the client negotiates. A freshly generated chunk that waits in the send queue longer than its first fluid ticks (water ticks every 5 game ticks) arrives already flowing and is marked **old**. In a local run flying into ungenerated terrain (fixed seed), NewChunks marked 11 chunks new and 41 old, although all of them were new. **Treat red as a reasonable hint and green as weak evidence.** That's why "Show old chunks" is off by default.
- **Edges.** Water flowing from a new chunk into a neighbour that has no flowing liquid of its own marks the neighbour new too.
- **Pre-generated worlds** (generated ahead of time, e.g. with a pre-generation plugin) have their fluids settled and look old everywhere.
- **Simulation distance.** Fluid ticks only run in chunks the server is ticking. Fresh chunks beyond the simulation distance stay unmarked until you get close enough for them to tick.
- **Version-specific "old chunk" signals** (for example terrain from before 1.18 lacking deepslate or copper) were **not** implemented: on servers they are easily confused with custom generation, resets and trimmed worlds, so they were not reliable enough to show.

## Display

| Setting | Default |
| --- | --- |
| Show new chunks / New chunk color | On, red `#FF5A4E` |
| Show old chunks / Old chunk color | Off, green `#58C98B` |
| Range | 16 chunks (2–64) around the camera |
| Overlay height (Y) | 0 (−64–320) |
| Opacity | 70 (0–255); 0 draws nothing |
| Outlines | On |
| Through walls | On (the overlay is visible through terrain) |

The overlay is a thin flat square per chunk, slightly inset so neighbouring chunks keep a visible seam. Set the height to the level you travel at (for example sea level, 62) so it reads like a map under you. **Clear marks** forgets this session's marks; the inspector shows how many chunks are new and old.

## Tests

- Unit: `NewChunksTrackerTest` replays synthetic event streams: fresh chunks become new from flow beside a source; chunks arriving with settled flow are old; flow far from a source, placed sources and liquid-free chunks are ignored; marks are sticky until cleared; memory is bounded. `NewToolsSettingsTest` covers defaults and clamping.
- Game test `NewChunksGameTest` (`-PnewToolsCTestsOnly=true`):
  1. Flat test world (deterministic): no liquid, nothing marked; pouring water marks that chunk new through the real packet path and the renderer receives its box; after leaving until the client forgets the chunk, clearing the session and coming back, the chunk arrives with settled water and is marked old.
  2. Real terrain (fixed seed, spectator above it): flying into ungenerated terrain marks at least one chunk new. Only "some" is asserted, because real terrain never guarantees liquid in a given chunk, and the old/new split is affected by chunk batching (above).
