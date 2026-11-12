# Freelook

Freelook lets you look around your player without turning it. Hold **Left Alt** and move the mouse: the camera orbits your player in third person while your player keeps facing, walking and aiming the same way. Let go and you are back in your previous view. It starts **disabled**.

It's in the click GUI under **Visual** (Right Shift → Freelook). The module switch turns the feature on; after that the Freelook key does the looking. Search finds it by "orbit", "third person", "look around" or by its setting names.

## What it changes, and what it doesn't

- Only the rendered camera moves. Mouse turns go to Freelook's own camera angles instead of your player's, so your player's yaw, pitch, movement direction, crosshair target and every packet stay exactly as they were. Nothing is sent to the server.
- Vanilla still places the camera: the third-person code (front-view flip, wall collision) runs as usual with Freelook's angles, so the camera never goes through walls.
- The perspective is switched with the normal perspective option (like F5) and switched back when you let go. If you press F5 yourself while Freelook is on, your choice is kept.

## Settings

| Setting | Default | What it does |
| --- | --- | --- |
| Toggle instead of hold | off | Off: Freelook lasts while the key is held. On: each press turns it on or off. |
| Perspective | Back | **Back**: third person behind you. **Front**: third person facing you. **Keep current**: stay in whatever view you are in, first person included. |
| Invert pitch | off | Flips the up/down direction of the camera only. |
| Camera distance | 4 blocks (1-12) | How far the camera sits from you while Freelook is on. Vanilla is 4. Walls still pull the camera in. Your size (scale attribute) still applies. |

Keys (Minecraft **Controls**, Donut Client category):

- **Freelook (hold)**: Left Alt by default. In toggle mode it is a press.
- **Toggle Freelook module**: unbound; switches the module on or off. Also in the inspector's Keybind box.

## Interactions

- **Freecam wins.** Freelook is inactive while Freecam is on, and turning Freecam on ends Freelook.
- **Menus.** Opening any screen releases the key, so hold mode stops and resumes when you close the menu and still hold the key. In toggle mode, presses in menus are ignored.
- **Zoom** still works while freelooking; its lower sensitivity applies to the camera too.
- Disconnecting or closing the game ends Freelook and restores your perspective.

## Implementation

- `freelook/FreelookController`: state, key handling, perspective switch and restore.
- `freelook/FreelookMath`: pure angle math (vanilla's 0.15 degrees per mouse unit, pitch clamp, yaw wrap, distance scale). Unit-tested in `FreelookTest`.
- `mixin/FreelookCameraMixin`: redirects `Camera.setup`'s `getViewYRot`/`getViewXRot` calls for your player while active, and scales the distance passed to `getMaxZoom`.
- `mixin/MouseHandlerMixin`: routes mouse turns to Freelook instead of `LocalPlayer.turn` while active.

## Tests

`FreelookGameTest` (in `-PnewToolsATestsOnly=true`): holding the key switches to third person; after a mouse move the player's yaw and pitch are bit-for-bit unchanged while the camera yaw differs and follows Freelook; W walks along the player's own heading; a longer distance moves the camera out; release restores first person; toggle mode with Keep current; Freecam overrides it; a menu pauses it. Screenshots `freelook-00` to `freelook-03`.
