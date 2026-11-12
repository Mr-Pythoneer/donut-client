# Stash Finder

Stash Finder logs chunks that hold a lot of storage the first time you see them: coordinates, how many of each storage type, dimension, server and time. You get an action-bar note when one is found, a list to manage them, one-click waypoints, and CSV/JSON export. It starts **disabled**.

Open **Right Shift → Visual → Stash Finder** (or search for `stash`). Assign a shortcut in its inspector or under Minecraft's Controls (**Toggle Stash Finder**; unbound by default). **Open stash list** is a separate, unbound control (**Open stash list**) and a button in the inspector. It supports Favorites and the module list.

## What counts

Stash Finder uses Storage ESP's block classification (`StorageKind`) and its chunk scan (`StorageEspScanner.scanStorage`), but runs its own small queue, so it works **whether or not Storage ESP is on**. It scans:

- every loaded chunk when you join or enable it (nearest first),
- each chunk the server sends,
- a chunk again 10 ticks after a storage block is placed in it (so a row of chests being built is counted once it settles).

At most 4 chunks and 1 ms per tick. Sections whose palette has no storage block are skipped, so chunks without storage cost almost nothing.

Each storage **block** counts once: a double chest is two chests. Storage vehicles (chest minecarts, chest boats) are not counted: they move, so they don't mark a place.

A chunk is logged when its **weighted** count reaches **Minimum storage** (default 8). Default weights: chests, trapped chests, barrels, shulker boxes and ender chests count **1** each; hoppers, droppers / dispensers, furnaces, brewing stands and other storage count **0** (they're listed in the entry but don't trigger it). Each weight can be set from 0 to 5, e.g. shulker boxes 3.

A chunk is logged **once** per server and dimension. If you see it again with different storage (still at or above the threshold), its counts are refreshed without a second notice. A logged stash is never removed because storage disappeared; delete it in the list. After changing the threshold or weights, **Rescan loaded chunks** applies them to what's around you.

## Each entry

- Server key (the same per-server split as waypoints: the address, or the singleplayer save folder) and dimension
- Chunk X / Z and the storage block nearest the middle of the cluster (where waypoints and "Copy coords" point)
- Count per kind, total blocks and the weighted score
- When it was first found (and last refreshed)

## The stash list

**Open stash list** shows this server's stashes, newest first, in every dimension. Select one, then:

- **Add waypoint**: a waypoint named "Stash *score*" at the entry's position, through the Waypoints module (you must be in the stash's dimension; the waypoint limit applies).
- **Copy coords**: copies `x y z` to the clipboard.
- **Delete**: click twice to confirm.
- **Export**: writes this server's stashes to `config/donut-stashes/export-<server>-<date>.csv` and `.json`. The CSV has one row per stash with `server, dimension, chunk_x, chunk_z, x, y, z, score, total, found_at` (ISO-8601 UTC) and a column per storage kind.

## Persistence

The log is saved to `config/donut-stashes/stashes.json` (written atomically, at most every 5 seconds, and on disconnect and shutdown), separate from the main Donut config so it can grow without slowing config saves. It keeps at most 5000 stashes across all servers; the oldest go first.

## Notices

| Setting | Default |
| --- | --- |
| Action-bar notice ("Stash found: 10 storage at x, y, z") | On |
| Toast (top right; covers the module list for a few seconds) | Off |

Both are local only. Stash Finder never sends chat messages, packets or anything else to the server, and only reads blocks the server already sent you (so anti-xray or a short view distance limits what it can see, exactly as for Storage ESP).

## Tests

- Unit: `StashLogTest` (weighted threshold, logged once then refreshed, per server and dimension, newest first, bounded log, CSV/JSON export round trip with awkward server names, duplicate entries dropped on load); `NewToolsSettingsTest` (defaults, weight clamping, vehicles never weighted).
- Game test `StashFinderGameTest` (`-PnewToolsCTestsOnly=true`), with Storage ESP off: 10 chests and a hopper placed with `/setblock` in one chunk become exactly one entry (10 chests, 1 hopper, score 10, total 11); 5 barrels in the next chunk are not logged; in the list screen, Add waypoint creates "Stash 10" at the entry, Copy coords fills the clipboard, Export writes CSV and JSON that read back, the log file is persisted, and Delete removes the entry.
