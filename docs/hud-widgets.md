# HUD Widgets

HUD Widgets puts small, compact readouts on your screen: FPS, ping, clicks per second, key strokes, armour durability, potion effects, a compass, a clock, the server address and memory use. It is one module with a switch per widget. It starts **disabled**; when you enable it, only **FPS**, **Ping**, **Armour status** and **Potion effects** are on.

Open **Right Shift → Visual → HUD Widgets** (or search for `fps`, `ping`, `cps`, `keystrokes`, `armour`, `potion`...). Assign a shortcut in its inspector or under Minecraft's Controls (**Toggle HUD Widgets**; unbound by default). It supports Favorites and the module list.

## Widgets

| Widget | Shows | Default |
| --- | --- | --- |
| FPS | Frames per second | On, top right |
| Ping | Your own latency from the tab list (`37 ms`, coloured green / amber / red at 100 and 250 ms). In singleplayer it says `singleplayer`, because there is no network round trip; before the server has sent your tab-list entry it shows `-` | On, top right |
| Clicks per second | Left and right clicks in the last second, e.g. `5 \| 1`. Only your own presses, counted from the mouse handler while no menu is open | Off, top right |
| Memory usage | Used / maximum Java heap in MB, optionally with a percentage | Off, top right |
| Clock | Your computer's time (24-hour or 12-hour), optionally also the in-game day and time (`Day 12  06:30`) | Off, top right |
| Server address | The address you joined, or `Singleplayer` | Off, top right |
| Compass | Cardinal direction and bearing, north = 0° (`NE 45°`) | Off, top right |
| Potion effects | Effect icon, name with level (`Speed II`), time left (`1:30`, `∞` for infinite). Beneficial effects first, then the one running out soonest; under 10 seconds the time turns amber | On, top right |
| Armour status | Helmet to boots: item icon, durability left / max and percent, amber at 25% and red at 10% | On, bottom right |
| Key strokes | Key caps for your bound forward / left / back / right keys, Space, LMB and RMB; a cap lights up while held | Off, bottom left |

TPS is not shown: the client can only estimate it, and the estimate is unreliable.

## Corners and panels

Each switched-on widget has a **Corner** button that cycles top left → top right → bottom left → bottom right. Text widgets (FPS, ping, CPS, memory, clock, server, compass) that share a corner are merged into one compact panel; potion effects, armour and key strokes are their own panels.

All panels use Donut's shared panel style, so in each corner they **stack** with the other Donut panels instead of overlapping:

- **Top left**: below Coordinates (and AutoBuilder / Staff Detector / Auto Miner panels).
- **Top right**: below the module list *and* below vanilla's effect icons. This anchor is new; the module list reports its height every frame.
- **Bottom left**: above Storage ESP and BlockESP.
- **Bottom right**: above Coordinates when it is set to bottom right, and above the hotbar when the window is narrow.

The defaults keep the top-right stats and potions under the module list, armour bottom right and key strokes bottom left, so nothing collides with Coordinates or Storage ESP.

## When they're hidden

Like every Donut panel, the widgets are hidden with **F1**, while **F3** is open, while any menu or screen is open, and when Donut's **HUD** switch is off. Their size follows **HUD → Panel size**, and they scale with the window rather than Minecraft's GUI scale.

## Settings

| Setting | Default |
| --- | --- |
| One switch per widget (above) | FPS, Ping, Armour, Potions on |
| Corner per widget | see table |
| 24-hour clock | On |
| In-game day and time | Off |
| Memory percentage | On |

## Privacy

Everything is read locally: your FPS, your own tab-list entry, your own mouse buttons and key bindings, your own armour and effects, the system clock and the JVM's memory. Nothing is sent to the server. The CPS counter does not record other players or anything outside the game window.

## Tests

- Unit: `NewToolsSettingsTest` (defaults, clamping, JSON), `HudWidgetTextTest` (FPS, ping, CPS sliding window with a fake clock, potion time and level, compass, clocks, memory).
- Game test `HudWidgetsGameTest` (`./gradlew runClientGametest -PnewToolsCTestsOnly=true`): default set draws; `/effect give` shows the potion panel; every widget on at GUI scale 2 and 4 and at a larger panel size, checking that no two Donut panels overlap and that top-right panels start below the module list; three real left clicks and two right clicks counted through the mouse handler (and a click in a menu not counted); W lights its key cap; F1, menus and disabling hide everything; moving widgets to other corners.
