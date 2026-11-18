# Seed Structure Finder

Type a world seed and find its structures — villages, strongholds, end cities and ships, fortresses, bastions, monuments, mansions, ancient cities, trial chambers, temples, outposts, shipwrecks, ocean ruins, buried treasure, ruined portals, trail ruins and nether fossils — with exact coordinates.

It's in **Visual** as **Seed Finder**. Click the tile (or bind a key) to open it.

## Accurate, not a guess

The finder runs the game's **own** structure placement and biome logic — the same code behind `/locate` — against a headless copy of the seed's world generator. So a result isn't "a village might be near here": it's where the game would actually put one, biome check included. (BlockESP's ore predictor uses the same headless generator.)

It is **client-side only**. It never contacts the server and sends nothing; it reads the seed you type and your own game's data. It works from single-player and from a multiplayer world alike.

## Using it

1. **Seed** — type the world's seed. A number is used as-is; any other text is hashed the way Minecraft does on the world-creation screen. In single-player the box is pre-filled with the current world's seed.
2. **Mode:**
   - **Nearest of each** — the single closest structure of every type, measured from where you're standing. Fast. This is the default.
   - **Area scan** — every structure of each type within a radius you choose (500–3000 blocks). More to look through, and slower.
3. **Find** — results fill in after a few seconds (the first search loads the seed's generator). **Stop** cancels.
4. Each row shows the type, coordinates `(x, z)`, dimension, and distance from where you searched.
5. **Add waypoint** saves the selected result; **Add all** saves the whole list (closest first, up to 64); **Copy coords** copies `x y z`.

Pick which types to search, switch mode, and set the radius in the module's settings panel in the Donut menu.

## Limits

- **Structure types only.** It finds structures, not biomes, ores (use BlockESP) or spawners.
- **The version must match.** It uses the running game's 1.21.11 generation. A seed from a different version, a modified generator, or a datapack that changes structures can differ.
- **Waypoints use your current dimension.** A waypoint you add is tagged with the dimension you're in, so an End City waypoint added from the Overworld won't show its beam until you're in the End. The coordinates are always correct.
- **Area scan can return a lot.** Common structures (ruined portals, ocean ruins) are dense; a wide radius can list hundreds. Narrow the radius or hide types you don't care about.
