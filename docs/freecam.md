# Freecam

Freecam detaches the camera from your player so you can look around freely. It is **camera-only**: your player never moves, and no position, rotation, ability, gamemode or movement packet is ever written for it. Freecam changes only the virtual camera's position, yaw and pitch on your client. Attack, use, pick, drop, swap and hotbar keys are drained while it is on, so nothing replays when you leave.

Open **Right Shift → Visual → Freecam** (or press **F6**, the default shortcut). Freecam always starts off when you join a world.

## Controls

| Input | Action |
| --- | --- |
| Forward / Left / Back / Right | Move the camera along the view direction |
| Jump / Sneak | Up / down |
| Sprint | Multiply speed by **Sprint boost** |
| Mouse | Look around (paused while **Look at target** is on) |
| Scroll wheel | Change camera speed by 15% per notch (when **Scroll wheel adjusts speed** is on). The hotbar slot never changes while Freecam is on. |

## Settings

- **Camera speed:** 0.1–10 blocks per tick (the cap was 5 before). Scroll changes are saved when you disable Freecam.
- **Sprint boost:** 1–10×.
- **Smoothing:** 0–95%, default **0%**. At 0% movement is instant, exactly as before. Higher values ease acceleration and stopping. The camera coasts briefly after you release the keys.
- **Speed / status readout:** a small box above the hotbar. It shows the speed for about 1.5 s after a change or after enabling. While Follow or Look at target is on, it also shows their status. It respects Donut's HUD toggle, F1 and open menus.
- **Resume last camera position:** off (default) means re-enabling starts at your eyes. On means it continues from the last camera pose, as long as you are in the same world and dimension. Disconnecting forgets the remembered pose. Disabling Freecam always returns your view to your player, because the player itself never moved.
- **Follow nearest player:** keeps the camera at its current offset from the nearest other player your client has received, within **Follow range** (8–128 blocks). Movement keys nudge the offset. If the player leaves range or despawns, the camera stays put and picks the next nearest player. It uses only player entities the server already sent to your client.
- **Look at target:** the camera keeps facing the chosen block while you move. Choose **Set target** to enter whole-block X/Y/Z values, or fill them from the current camera position. Invalid or cancelled input keeps the previous target.

## Lifecycle

Freecam turns off automatically when you die, respawn, change dimension or world, or disconnect. Your normal movement input is restored. Opening a menu (chat, inventory, the click GUI) pauses camera movement and mouse look without turning Freecam off.

Your own player model renders while the camera is away from it. It is hidden only while the camera is inside your body, to avoid a view filled by your own head.

## Limits

The server sends chunks and entities based on your **real player's** position, not the camera's. Freecam can only show what is already loaded around you: flying beyond your render/view distance shows unloaded or empty terrain. Freecam does not request extra chunks and cannot see entities the server has not sent.

AutoBuilder pauses while Freecam is on, and Freecam asks you to release a bow or trident before enabling, because releasing it would be a gameplay action.

## Validation

Run `./gradlew test` for settings persistence/clamps, coordinate parsing, and the smoothing, scroll, look-angle and follow math. Run `./gradlew runClientGameTest -PfreecamTestsOnly=true` for the runtime suite, which also runs the module-keybind regression suite. See [TESTING.md](../TESTING.md) for what each run covered.
