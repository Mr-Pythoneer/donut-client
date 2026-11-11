# Quality-of-life features

Four modules and two click-GUI improvements. They only use data your client already has: your own player, camera, inventory and loaded world. None of them send packets, move your player or change the world. All four modules start disabled and support Favorites, the active-module list and an assignable shortcut (Right Shift → module → **Keybind**, or Minecraft Controls → Donut Client).

## Coordinates (Visual)

A small HUD showing:

- position (XYZ)
- facing direction and yaw
- chunk and position inside the chunk (correct for negative coordinates)
- the matching Nether/Overworld coordinates (8:1), or the dimension name elsewhere
- optionally, the biome
- while Freecam is on, the camera's own position and facing

Place it in the top-left (default) or bottom-right corner. It hides while F3, F1 or a menu is open and respects Donut's HUD switch.

## Waypoints (Visual)

Save named places and see them through walls as an outlined block, a vertical beam and a name/distance label.

**Adding waypoints:**

- **Manage waypoints** in the inspector, or the **Open waypoints** shortcut (unbound by default). The manager can add a waypoint at your position, at Storage ESP's nearest target, or at the group Base Finder's inspect panel is showing.
- **Add waypoint here** (button or shortcut). A local action-bar message confirms it.

**Managing them:**

- Rename, hide/show, recolor (8 presets) and delete them from the manager.
- Duplicate names are numbered ("Base", "Base 2", …).

**Storage:**

- Waypoints are stored per server (multiplayer address) or per singleplayer world (level name) and per dimension. They are saved only in your local Donut config, and the limit is 500 in total.
- Two singleplayer worlds with the same name share a list.

**Display:**

- **Show Nether / Overworld waypoints converted** also shows Overworld waypoints in the Nether at their 1/8 position, and the reverse.
- Beams, labels, distance in labels, opacity and label range (32–4096 blocks) are configurable.
- Labels are projected from the camera each frame. View bobbing isn't applied, so labels may sway slightly while you walk.

## Zoom (Visual)

Hold the zoom key (default **Z**, changeable in Controls as "Zoom (hold)") to magnify your view.

- **Zoom level:** 1.5–50×, default 4×.
- **Smooth transition:** eases in and out.
- **Scroll to adjust while held:** 25% per notch. The hotbar doesn't change while zooming. The scrolled level lasts for that hold only, unless **Remember scrolled level** is on.
- **Lower mouse sensitivity:** divides mouse sensitivity by the zoom while zoomed.

Zoom changes only this client's world field of view; the hand keeps its normal size. Turning while zoomed is ordinary vanilla mouse look, just at lower sensitivity. Zoom doesn't work while Freecam is on.

## Durability Alert (Visual)

Warns when your main-hand item, offhand item or armor is at or below a chosen percentage of durability (1–50%, default 10%).

- A centered card under the crosshair lists each item with its icon, percentage and uses left. The text turns red at 5% or less.
- A short chime plays once per item when it first crosses the threshold. The chime plays only on your client.
- Repairing or swapping the item resets its warning.
- Armor and offhand checks can be turned off.

## Settings-aware search (click GUI)

- Typing in **Search modules…** searches every category, whichever tab is open. Clearing the search returns to the tab's normal filter.
- Results match module names, keywords and the names of settings in each module's inspector. For example, "tracer" finds Amethyst Scanner, BlockESP and Storage ESP.
- Each result shows the setting that matched ("Setting: Tracers"), or its category and state.
- **Enter** opens the top result's settings.
- **Ctrl+F** focuses search and **Enabled only** still applies.
- The searchable setting names are kept in `ModuleSearchIndex`. A unit test fails if a label in the inspector code is missing from it.

## Settings profiles (Appearance → Profiles)

**Export / import settings** opens a manager for local profile files in `config/donut-profiles/`.

**Exporting:**

- **Export current settings** writes every Donut setting plus all Donut shortcuts to a timestamped JSON file.
- **Open folder** shows the files so you can copy them to another computer or share them.

**Importing:**

- Selecting a profile shows a summary: export date, version, shortcut count, favorites and key module values.
- **Import selected** asks for a second click (**Confirm import**).
- Before applying anything, it validates the file and backs up your current `donut-client.json` as `donut-client.json.before-import-<date>`.
- It then applies the settings and shortcuts.
- An import never turns modules on or off: each module keeps its current state.
- Malformed files, unknown formats and files over 8 MB are rejected without changing anything.

Profiles can include server-specific data you entered, such as waypoints, staff rosters and Sound Detector profiles. Check a profile before sharing it.

## Validation

- **Unit tests** (`./gradlew test`): coordinate formatting and conversion, zoom math, durability checks, waypoint keys/filtering/naming/conversion, label projection, profile round trips/rejection/backup, settings defaults/clamps, and the search index.
- **Runtime and GUI suites:** run `./gradlew runClientGameTest -PqolTestsOnly=true`.

See [TESTING.md](../TESTING.md) for what was run.
