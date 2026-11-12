# Auto Eat

Auto Eat eats food from your hotbar when you get hungry, then switches back to the item you were holding. It starts **disabled**.

It's in the click GUI under **Blatant** (Right Shift → Auto Eat). Search finds it by "food", "hunger", "saturation" or any food name in its list.

## How it eats

1. It checks every tick whether to eat: hunger (0-20) at or below **Eat at or below hunger**, or, with **Eat when health is low** on, health at or below **Low health at or below** while hunger is below full (so natural regeneration can work). It never eats at full hunger.
2. It picks the most filling allowed food **in your hotbar only** (nutrition plus saturation; ties go to the slot already selected, then the leftmost). It never moves items between your inventory and hotbar: the vanilla client can't do that without an open inventory.
3. It selects that slot and uses the item exactly like a right click in the air (`MultiPlayerGameMode.useItem`), then holds the use key until vanilla finishes eating. Eating takes the normal time.
4. When you are still below the threshold it eats again right away. Then it switches back to your previous slot, unless you picked another slot yourself meanwhile.

If the server doesn't start the eat (for example a cooldown), it switches back and waits two seconds before trying again.

## When it waits

- A menu is open (an eat in progress is cancelled; nothing is consumed).
- You are in Creative or Spectator.
- You are already using another item (a bow, a shield, your own eating).
- Freecam is on, or AutoBuilder is running.
- No allowed food is in the hotbar (the inspector shows "no allowed food in the hotbar").
- If you change the hotbar slot during an eat, the eat is cancelled and your choice is kept.

## Auto Miner

Auto Miner holds your attack, movement and hotbar slot while it works, so the two take turns:

- **Hunger 6 or lower (critical):** Auto Eat asks Auto Miner to pause right away, even mid-target.
- **Otherwise:** Auto Eat waits until Auto Miner is between targets (searching for the next ore or picking up drops), then asks it to pause.

Auto Miner pauses with the reason "Auto Eat is eating", hands back all input, and resumes on its own when eating ends. While Auto Eat waits, its HUD line says "waiting for Auto Miner to finish its target".

## Never-eat list

Each food below has a toggle under **Never eat**. On means Auto Eat never eats it. Any other food is allowed.

| Food | Default |
| --- | --- |
| Rotten flesh | never eat |
| Spider eye | never eat |
| Poisonous potato | never eat |
| Pufferfish | never eat |
| Chorus fruit (teleports you) | never eat |
| Suspicious stew (random effect) | never eat |
| Golden apple | never eat |
| Enchanted golden apple | never eat |
| Raw chicken | allowed |

Golden apples are on the list by default so they aren't spent on ordinary hunger; turn them off if you want Auto Eat to use them. Raw chicken has a toggle because of its hunger chance but is allowed by default.

## Settings

| Setting | Default | What it does |
| --- | --- | --- |
| Eat at or below hunger | 14 (1-19) | Hunger level that triggers eating. |
| Eat when health is low | on | Also eat when hurt and not full. |
| Low health at or below | 10 HP (1-19) | Health (half-hearts) for the setting above. |
| Never eat | see above | Per-food blacklist. |
| Show HUD while eating | on | A small "Auto Eat / Eating Bread" panel above the hotbar, also shown while waiting for Auto Miner. |

## Read this first

Automatic eating is automation. Some servers don't allow it; check the rules. It only uses ordinary client input (slot change, item use, held use key), sends nothing custom and does nothing to hide from anti-cheat.

## Implementation

- `autoeat/AutoEatController`: per-tick state (idle, waiting for the miner, eating), slot select and restore, use key hold/release.
- `autoeat/FoodChoice`: pure rules (when to eat, best hotbar food, critical hunger). Unit-tested in `FoodChoiceTest`.
- `config/AutoEatSettings`: thresholds and the never-eat map keyed by item id.
- `AutoMinerController.pauseReason` checks `AutoEatController.holdsMiner()`.

## Tests

- Unit: `FoodChoiceTest` (thresholds, low-health rule, best food, blacklist, hotbar-only slot filter, ties, settings defaults/clamps/JSON repair).
- Runtime: `AutoEatGameTest` (in `-PnewToolsATestsOnly=true`), survival world with hunger set server-side: nothing while disabled; enabling eats the hotbar bread with real item use while rotten flesh is skipped, the slot returns to the sword and the use key is released; only blacklisted food means nothing is eaten; bread in the main inventory is never moved or eaten; Auto Miner between targets pauses with "Auto Eat is eating" and resumes; low health at hunger 19 eats beef; Creative never eats. Screenshots `auto-eat-01`, `auto-eat-02`.
- Not covered at runtime: the critical-hunger interrupt while Auto Miner is in the middle of mining a target (the rule is unit-tested; the pause mechanism is the same one the game test exercises).
