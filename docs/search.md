# Search

Search highlights any blocks you choose through walls: each block ID in your list gets its own colour, with optional tracers and a count/nearest HUD. It starts **disabled** with an **empty list**.

Open **Right Shift → Visual → Search** (or search for `search`, `xray` or `find blocks`). Use the module's switch to enable it. Assign a shortcut in its inspector or under Minecraft's Controls (**Toggle Search**; unbound by default). It supports Favorites and the active-module array list.

## Choosing blocks

In the inspector's **Blocks** section:

- **Type a block ID** into the field, such as `diamond_ore`, `spawner` or `minecraft:ancient_debris` (the `minecraft:` namespace is added for you, and case doesn't matter). Suggestions from the block registry appear as you type; click one to add it, or press Enter / **Add block** to add what you typed. Unknown IDs, air and malformed text are refused with a reason (and a close match when there is one).
- **Add looked-at block** adds the block under your crosshair.
- Click an entry's **colour swatch** to change its colour; **Remove** drops it.

New entries get the next colour from a fixed palette. The list holds up to 64 blocks. IDs are kept even if the mod that adds a block is missing; they simply match nothing until it's back.

## Anti-xray servers

Search shows exactly what the server sent. Many servers run anti-xray, which **disguises hidden ores as stone** (so real ores aren't highlighted) or **sends fake ores** in places with none (so highlights can be wrong). Only blocks you could already see, such as ores exposed to air, are certain. Search never requests chunks or blocks from the server to get around this.

## Range: what the client can actually see

**Range** goes from 16 to 512 blocks (default 96), measured from the camera. A client only has the chunks the server sent, which is the smaller of your render distance and the **server's view distance**; the inspector shows the current loaded radius. Blocks beyond it are not on your client.

## Display

- **Max results** (16–4096, default 512): only the nearest N blocks are drawn. The HUD total still counts everything in range and says "nearest N" when the cap applies.
- **Opacity** (0–255): outline strength; the fill is a light tint. At 0 nothing is drawn.
- **Merge adjacent blocks** (default on): neighbouring blocks of the same type are joined into as few boxes as possible (a vein of ore becomes one or two boxes). The boxes cover exactly the matching blocks and never include empty space. Off draws one box per block.
- **Tracers**: lines from the crosshair to each box (at most 256).
- **Count / nearest HUD**: bottom-left panel with the in-range total, a count per block type, and the nearest match's coordinates and distance. It stacks with the other bottom-left panels and respects Donut's HUD toggle, F1 and open menus.
- **Rescan loaded chunks**: clears the index and scans every loaded chunk again.

## Performance and cache lifetime

Search works exactly like Storage ESP:

- Blocks are indexed per chunk. Each chunk section's block palette is checked first, and only sections that can contain one of your blocks are scanned.
- Chunks are queued when they arrive (nearest first on enable), when a single or multi-block update touches a searched block or a previously found one, and when a chunk unloads.
- Each client tick processes at most **8 chunks and 2 ms** of queued work.
- The visible list is rebuilt every 5 ticks, or immediately after a change. Range, max results and merging only rebuild the list; colours, opacity and tracers apply on the next frame. Changing the block list rescans the loaded chunks.
- Disabling the module, changing world or dimension, and disconnecting clear every cache.

## Validation

`./gradlew test` covers settings defaults, clamps, JSON round trips, unknown keys and list repair; block-ID normalisation, registry validation (unknown, air, malformed) and suggestion order; and box merging (rows, cubes, L-shapes and 200 random shapes checked for an exact partition). `./gradlew runClientGameTest -PnewToolsBTestsOnly=true` runs the runtime suite against `/setblock` fixtures behind a wall (empty list, counts, merged boxes, the rendered frame, tracers, single and multi-block updates, range, the nearest-N cap, list changes, disable and disconnect) and the GUI suite (typed IDs with suggestions and validation, Add looked-at block, remove, persistence, shortcut and array list), plus the module-keybind regression suite.

**Anti-xray (tested 2026-11-12):** on a server with Paper's anti-xray in engine mode 2, Search showed about 63,000 "diamonds" within 64 blocks, and none of the 40 nearest were real. Anti-xray sends fake ores in place of hidden blocks, and Search can only show what the server sends. Exposed ores, the ones touching air, are real. See [anticheat-results.md](anticheat-results.md).
