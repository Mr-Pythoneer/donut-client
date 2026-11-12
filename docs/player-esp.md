# Player ESP

Player ESP outlines other players through walls, with optional tracers and a nametag above each one showing their name, health, held item, armour and distance. Mobs, animals and dropped items can be added with their own switches. It starts **disabled** and, by default, shows players only.

Open **Right Shift → Visual → Player ESP** (or search for `player`, `nametag`, `tracers` or `friends`). Use the module's switch to enable it. Assign a shortcut in its inspector or under Minecraft's Controls (**Toggle Player ESP**; unbound by default). It supports Favorites and the active-module array list.

## What it can and can't see

Player ESP only reads entities your client has already received. The server decides which players it sends you (its entity tracking range, usually well under the render distance), so a player who isn't loaded on your client can't be shown. Nothing is requested from the server and no packets are sent.

It never shows:

- **you**, including your own body while Freecam is active;
- the **camera entity** (whoever you are spectating);
- any entity the **camera is inside**, which would otherwise cover the whole screen.

Armour stands, minecarts, boats and other non-mob entities are never shown.

## Targets

| Switch | Default | Entities |
| --- | --- | --- |
| Players | on | Other players |
| Hostile mobs | off | Monsters (zombies, husks, skeletons, creepers...) |
| Animals / passive mobs | off | Other mobs: animals, villagers, golems... |
| Dropped items | off | Item entities on the ground, named with their count |

## Boxes and colours

- **Through-wall boxes**: an outline around each target's bounding box at its rendered (interpolated) position, with a faint fill. Drawn without depth testing, so walls never hide them.
- **Tracers**: lines from the crosshair to each target.
- **Color players by distance** (default on): red when close, through yellow, to green at the edge of the range. Turn it off to use one **Player color**.
- **Team colors**: use a player's scoreboard team colour when they have one.
- **Friend color**: friends always use this colour.
- Mobs, animals and items each have their own colour (shown when that target type is switched on).

## Nametags

Tags are drawn on screen with the same projection as waypoint labels (including view bobbing and hurt tilt), so they stay on the target while you move. They sit just above Minecraft's own name tag and use the shared HUD panel size (**HUD → Panel size**).

- **Health**: read from the health the server already syncs for each entity, with absorption as `+N`. **Health in hearts** shows `7.5❤`; turn it off for `15 HP`. The colour goes green, amber, red as health drops. Some servers send fake health for other players; the tag shows what was received.
- **Held item** and **Armor icons**: the main-hand item and the four armour slots, as received.
- **Distance**: metres from the camera.
- **Respect Name Hider** (default on): while Name Hider is on, tags say **Player** (or **Friend**) instead of real names, and the friends list in the inspector shows numbered entries. Minecraft's own name tags above players are not changed.

## Range and limits

- **Range** (8–512 blocks, default 128), measured from the camera.
- **Max shown** (1–256, default 64): only the nearest targets are drawn. The inspector shows how many matched before the cap.
- **Opacity** (0–255, default 220): box and tracer strength. Tags stay readable at low opacity.

## Friends

Type a username (1–16 letters, digits or underscores) into the **Friends** field and press Enter or **Add friend**. Names match without case. **Remove** deletes an entry. Friends are saved in the Donut config (and in exported profiles), up to 200 names.

## Performance

Targets are chosen once per client tick from the entities the client is already rendering, sorted by distance and capped. Boxes and tags are drawn each frame from that list. Disabling the module or disconnecting clears it.

## Validation

`./gradlew test` covers settings defaults, clamps, JSON round trips, unknown keys and friend validation; target classification and filters; the self/camera exclusions; distance, team, friend and fixed colours; Name Hider names; health and nametag formatting. `./gradlew runClientGameTest -PnewToolsBTestsOnly=true` runs the runtime and GUI suites with a server fake player behind a wall (box drawn, tag text with synced health and equipment, never yourself even in Freecam, nothing when the camera is inside the target, Name Hider, friends, mob/animal/item filters, nearest-N, range, disable and disconnect), plus the module-keybind regression suite.
