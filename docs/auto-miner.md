# Auto Miner

Auto Miner looks for ores near you, walks to them and mines them the way you would: it turns your view toward the block, picks the best pickaxe in your hotbar, and holds the break at normal speed and normal reach. It starts **disabled** and never switches itself back on: it is cleared when Minecraft starts and whenever it stops (disconnect, death, dimension change, full inventory).

It's in the click GUI under **Blatant** (Right Shift → Auto Miner). The module switch turns it on, and its inspector holds the ore list and every setting below, plus a keybind (unbound by default; also under Minecraft's **Controls** as **Toggle Auto Miner**). Search finds it by ore names and setting names, e.g. "tunnel" or "ancient".

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
2. **Walk.** It walks to a spot where the ore is within reach and in plain view. It never stands on top of the ore it is about to mine. It follows the path in straight lines (looking a few blocks ahead instead of steering through every block centre) and keeps walking through small corrections.
3. **Mine.** It selects a hotbar tool that can actually harvest the ore (see **Tool choice** below), turns toward it and breaks it.
4. **Pick up.** With **Pick up drops** on, it walks over the items that ore dropped (stepping into the hole if needed) for up to five seconds, then searches again. Items that were already lying around are left alone, and unreachable drops are skipped.

**Turning.** The view turns a little on every rendered frame, easing out as it lines up, never faster than **Turn speed**. Turning only on game ticks (20 times a second) looks like stuttering at high frame rates, because Minecraft doesn't smooth your own view between ticks. It is still your real view, exactly as a mouse would move it.

### Stone and deepslate forms

**Stone variants** and **Deepslate variants** choose which form of each selected ore to mine (Nether ores count as the normal form). Turn off stone variants to mine only deepslate emerald, deepslate diamond and so on.

**Silk Touch for deepslate ores** (on by default) and **Silk Touch for stone ores** (off) make it use a Silk Touch pickaxe from your hotbar for that form, so the ore block itself drops. Without one it falls back to your other pickaxes. When Silk Touch isn't wanted it prefers a pickaxe without it.

### Tool choice

Only tools that can harvest the block count, and with **Protect low-durability tools** on, tools at or below the **Tool reserve** are left alone. From the rest it picks, in this order:

1. **Silk Touch**, following the two Silk Touch settings above.
2. **Fortune**, with **Prefer Fortune** on (the default): for ores whose drop Fortune multiplies (coal, copper, iron, gold, redstone, lapis, diamond, emerald, Nether quartz and Nether gold), the pickaxe with the highest Fortune level. This applies when Silk Touch isn't wanted, and also when it is wanted but you have no usable Silk Touch pickaxe.
3. **Speed**: the fastest tool, counting **Efficiency** the way the game does (it adds level squared + 1, only on blocks the tool is made for, so Efficiency V on a diamond pickaxe mines stone at 34 instead of 8).
4. On a tie it keeps the slot you're holding.

Ancient debris always drops itself, so it has no Silk Touch or Fortune preference: the fastest pickaxe wins. Tunnel blocks (stone, gravel and so on) also just use the fastest tool.

**Hotbar only.** It only switches between your nine hotbar slots, like pressing 1-9. It never moves items from the rest of your inventory into the hotbar, because the game only lets you do that with an inventory screen open. Keep the pickaxes you want it to use in the hotbar.

A target is skipped, and remembered as skipped for one minute, when it can't be reached, it is next to lava or water, there is no suitable tool, or a tunnel to it would be unsafe. The HUD shows the last skipped ore and why.

### Exposed and buried ores

By default it only targets **exposed** ores: ones with at least one face touching air or a see-through block, so you could see them.

With **Include buried ores** on, it also targets ores enclosed in stone. When there is no walkable spot that can see one, it digs a **1×2 tunnel** toward it. Each step is one block sideways, going up or down a block at a time like a staircase, until the ore is in view. It never digs straight down under your feet, and it gives up on that ore after **Tunnel limit** blocks.

### Gravel and sand

Gravel, sand, red sand and concrete powder fall when the block under them is broken. When some are stacked over (or sit in) a tunnel step, it breaks the block under them and clears whatever falls in:

- After breaking a block with gravel or sand on top, it stops and waits. It doesn't break anything or move into the step while a falling block is above the step (or above you), or while a loose block sits right over an open cell of the step. Then it waits a few more quiet ticks (6) before mining what landed.
- It mines each block that lands in the step, then waits again, until the stack is gone. Up to **12 fallen blocks** per ore are cleared. After that it gives up on that ore. These don't count toward the **Tunnel limit**.
- If blocks keep falling for 4 seconds without settling (for example worldgen gravel floating over a cave), it gives up on that ore.
- It never stands or stops under a falling block, and won't pick a standing spot with gravel or sand right above your head.

It skips the ore instead when:

- any block in the falling stack touches lava or water;
- more than 12 are stacked over the step;
- an anvil, dragon egg, pointed dripstone or suspicious sand/gravel sits on the stack (these aren't cleared);
- the stack would fall into the column you're standing in (for example over the headroom it clears before stepping up).

All the other tunnel rules still apply, and they are checked again every tick.

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
- A tunnel step that would leave a **drop over 3 blocks** or no floor, or would break an unbreakable block or one with contents (such as a chest).
- Gravel or sand over a tunnel step that touches lava or water, is more than 12 high, has an anvil or dripstone on top, or would fall on you (see **Gravel and sand**).

These **stop** it, release everything it was holding (movement input, the block being broken), put your original hotbar slot back and switch the module off:

- You switch it off, disconnect, die, or change dimension or world.
- Your inventory is full.

Areas at the edge of loaded chunks count as unsafe.

## Settings

| Setting | Default | Range |
| --- | --- | --- |
| Ores to mine | Diamond, iron, gold, emerald, ancient debris | Coal, copper, iron, gold, redstone, lapis, diamond, emerald, nether quartz, nether gold, ancient debris. Deepslate variants count as the same ore. |
| Stone variants / Deepslate variants | On / On | Which form of each ore to mine |
| Silk Touch for deepslate ores / stone ores | On / Off | Uses a Silk Touch pickaxe from the hotbar when there is one |
| Prefer Fortune | On | Uses the hotbar pickaxe with the most Fortune when Silk Touch isn't used. Not for ancient debris |
| Pick up drops | On | Up to 5 seconds per ore |
| Include buried ores | Off | |
| Range | 24 blocks | 8–64 |
| Pause at health | 6 HP (3 hearts) | 1–19 |
| Protect low-durability tools | On | |
| Tool reserve | 8 uses | 1–200. A protected tool with this many uses or fewer is never used. |
| Tunnel limit | 32 blocks | 4–128 |
| Turn speed | 18° per tick | 5–45 |
| Show HUD | On | |

## HUD

A small panel in the top-left stack, in the same style as Donut's other panels: what it is doing (for example "Mining Diamond ore 4m" or "Paused: menu open"), then the number mined this session and the last skipped target. After it stops itself, the reason shows for about five seconds. The HUD hides with Donut's HUD toggle, F1 and open menus.

## Limits

- It can only see what the server sent. Anti-xray servers may hide or fake ores.
- The mined count goes up when your client sees the block break. If the server then refuses the break (for example in a protected area), the count can be one too high.
- Tool choice only looks at the hotbar, and only at Silk Touch, Fortune and Efficiency. Haste, Mining Fatigue and being underwater slow every tool equally, so they don't change the choice.
- While it waits for gravel or sand to settle, the HUD says "waiting for falling blocks".
- It does not light caves, fight mobs, or eat. Low health pauses it, but staying alive is still up to you.
