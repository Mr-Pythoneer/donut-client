# Auto Miner

Auto Miner looks for ores near you, walks to them and mines them the way you would: it turns your view toward the block, picks the best pickaxe in your hotbar, and holds the break at normal speed and normal reach. It starts **disabled** and never switches itself back on: it is cleared when Minecraft starts and whenever it stops (disconnect, death, dimension change, full inventory).

Turn it on and off with **Toggle Auto Miner** under Minecraft's **Controls** (unbound by default). A click-GUI page is planned; until then the settings live in `config/donut-client.json` under `autoMiner`.

## Read this first

Automated mining is a bot. **Many servers do not allow it**, and using it there can get you banned even though it only does what a player could do. Check the server's rules before turning it on. It is meant for singleplayer, your own server, or servers that explicitly allow it.

## What it guarantees

Auto Miner is client-side and vanilla-only:

- It mines with the same game-mode calls a held attack button makes (`startDestroyBlock` / `continueDestroyBlock`), only on the block your crosshair is actually on, at the reach the game gives you. Breaking takes as long as it would by hand.
- It walks with ordinary movement input (forward and jump), using the same pathfinder as AutoBuilder. It never teleports, flies, or moves you any other way.
- It turns your real view (yaw and pitch) at a capped speed you can watch. There are no hidden or packet-only rotations.
- It sends no custom packets, does not extend reach, and does nothing to hide from anti-cheat.
- It reads only chunks your client already has. It never requests chunks.

## How it works

1. **Search.** It sweeps the loaded chunks within **Range** of you, a few chunks per tick (at most 4 chunks or 1.5 ms per tick), and picks the nearest ore that qualifies.
2. **Walk.** It walks to a spot where the ore is within reach and in plain view. It never stands on top of the ore it is about to mine.
3. **Mine.** It selects the fastest hotbar tool that can actually harvest the ore, turns toward it and breaks it. When it is done it searches again.

A target is skipped, and remembered as skipped for one minute, when it can't be reached, it is next to lava or water, there is no suitable tool, or a tunnel to it would be unsafe. The HUD shows the last skipped ore and why.

### Exposed and buried ores

By default it only targets **exposed** ores: ones with at least one face touching air or a see-through block, so you could see them.

With **Include buried ores** on, it also targets ores enclosed in stone. When there is no walkable spot that can see one, it digs a **1×2 tunnel** toward it. Each step is one block sideways, going up or down a block at a time like a staircase, until the ore is in view. It never digs straight down under your feet, and it gives up on that ore after **Tunnel limit** blocks.

## Safety and pausing

These **pause** it. The HUD says why, and you have full control of your character while paused:

| Reason | Resumes when |
| --- | --- |
| A screen or menu is open | the menu closes |
| Freecam is on | Freecam is off |
| You press a movement key (forward, back, left, right, jump or sneak) | about **2 seconds** after you last pressed one |
| Health at or below **Pause at health** | you heal above it |
| Riding something, using an item, or in Adventure/Spectator mode | that ends |
| AutoBuilder is running | AutoBuilder stops |

These **skip** a target:

- The ore, the next block to break, or the floor you would stand on borders **lava or water** (including waterlogged blocks).
- A tunnel step that would leave a **drop over 3 blocks** or no floor, would break an unbreakable block or one with contents (such as a chest), or has sand or gravel above it.

These **stop** it, release everything it was holding (movement input, the block being broken), put your original hotbar slot back and switch the module off:

- You switch it off, disconnect, die, or change dimension or world.
- Your inventory is full.

Areas at the edge of loaded chunks count as unsafe.

## Settings

| Setting | Default | Range |
| --- | --- | --- |
| Ores to mine | Diamond, iron, gold, emerald, ancient debris | Coal, copper, iron, gold, redstone, lapis, diamond, emerald, nether quartz, nether gold, ancient debris. Deepslate variants count as the same ore. |
| Include buried ores | Off | |
| Range | 24 blocks | 8–64 |
| Pause at health | 6 HP (3 hearts) | 1–19 |
| Protect low-durability tools | On | |
| Tool reserve | 8 uses | 1–200. A protected tool with this many uses or fewer is never used. |
| Tunnel limit | 32 blocks | 4–128 |
| Turn speed | 18° per tick | 5–45 |
| Show HUD | On | |

## HUD

Two lines at the top centre: what it is doing (for example "Auto Miner: mining Diamond ore 4m" or "Auto Miner paused: menu open"), then the number mined this session and the last skipped target. After it stops itself, the reason shows for about five seconds. The HUD hides with Donut's HUD toggle, F1 and open menus.

## Limits

- It can only see what the server sent. Anti-xray servers may hide or fake ores.
- The mined count goes up when your client sees the block break. If the server then refuses the break (for example in a protected area), the count can be one too high.
- Tool choice compares the tools' base mining speed; Efficiency enchantments are not taken into account.
- It does not light caves, fight mobs, or eat. Low health pauses it, but staying alive is still up to you.
