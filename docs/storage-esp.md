# Storage ESP

Storage ESP outlines storage blocks and storage vehicles through walls, with per-type toggles and colors, optional tracers, and a count/nearest-distance HUD. It starts **disabled**.

Open **Right Shift → Visual → Storage ESP** (or search for `chest`, `barrel`, `shulker` or `storage`). Use the module's switch to enable it. Assign a shortcut in its inspector or under Minecraft's Controls (**Toggle Storage ESP**; unbound by default). It supports Favorites and the active-module array list.

## Targets

Each kind has its own switch and color:

| Kind | Blocks / entities |
| --- | --- |
| Chests | Chest, all copper chest stages |
| Trapped chests | Trapped chest |
| Barrels | Barrel |
| Shulker boxes | All 17 shulker boxes |
| Ender chests | Ender chest |
| Hoppers | Hopper |
| Droppers / dispensers | Dropper, dispenser |
| Furnaces / smokers / blast furnaces | All three |
| Brewing stands | Brewing stand |
| Crafters / decorated pots / chiseled bookshelves | Other blocks that hold items |
| Chest / hopper minecarts and chest boats | Container vehicles the client has received |

**Double chests** are drawn as one box covering both halves and counted once; the HUD shows how many chests are doubles. A half counts as part of a pair only when both halves' received block states point at each other, so a trapped chest is never merged with a normal chest. When one half is broken, the other becomes a single chest on the next update.

Storage ESP uses its own finer classification. Base Finder's container, ender-storage, hopper and machine categories and its behavior are unchanged.

## Range: what the client can actually see

**Range** goes from 16 to 512 blocks (default 128) and is measured from the camera. The real limit is lower: a client only has the chunks the server sent, which is the smaller of your render distance and the **server's view distance**. The inspector shows the current loaded radius. Storage beyond it is not on your client, and Storage ESP cannot see it. It does not request chunks, send packets or try to work around that limit. A range larger than the loaded radius simply includes everything that is loaded.

Storage ESP shows exactly what the server sent. Servers using anti-xray or similar obfuscation may hide or disguise blocks, and those will not be highlighted.

## Display

- **Nearest targets shown** (16–2048, default 256): only the nearest N targets are drawn. The HUD total still counts everything in range and says "nearest N" when the limit applies.
- **Opacity** (0–255): outline strength. Fill is a light tint of the same color. At 0 nothing is drawn.
- **Tracers**: lines from the crosshair to each shown target.
- **Count / nearest HUD**: bottom-left panel with the in-range total, per-kind counts (with doubles), and the nearest target's coordinates and distance. It stacks above the BlockESP HUD when both are visible, and respects Donut's HUD toggle, F1 and open menus.
- **Rescan loaded chunks**: clears the index and scans every loaded chunk again.

## Performance and cache lifetime

- Storage ESP indexes storage per chunk. Each chunk section's block palette is checked first, and only sections that can contain storage are scanned.
- Chunks are queued when they arrive (nearest first on enable), when a block update touches storage, and when a chunk unloads.
- Each client tick processes at most **8 chunks and 2 ms** of queued work.
- The visible target list is rebuilt every 5 ticks, or immediately after a change. Range, kind toggles and the target limit only rebuild the list; they never rescan chunks. Colors, opacity and tracers apply on the next frame.
- Storage vehicles are read from entities the client is already rendering, and their boxes follow the entity live.
- Disabling the module, changing world or dimension, and disconnecting clear every cache.

## Validation

Run `./gradlew test` for kind classification of every state of every listed block, rejection of non-storage blocks, double-chest pairing in all four facings (including mismatched, missing, trapped and copper partners), nearest-N ordering, and settings defaults, clamps, persistence and repair. Run `./gradlew runClientGameTest -PstorageEspTestsOnly=true` for the runtime and GUI suites, which also run the module-keybind regression suite. See [TESTING.md](../TESTING.md) for what each run covered.
