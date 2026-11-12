# Anti-cheat test results (2026-11-12)

How Donut Client fared on a local test server running several anti-cheats at once. Every module that talks to the server was run as a normal survival player.

## Setup

- **Server:** Purpur 1.21.11 build 2568 (a Paper fork), offline mode, on this computer only. Paper's anti-xray was on in engine mode 2, which hides every buried ore behind fake ones.
- **Anti-cheats, all at default settings except where noted:**
  - GrimAC 2.3.75
  - Intave 14.9.7 (free base)
  - Spartan 4.0.9.0 (limited free version)
  - OmniAnticheat 1.0.2
  - BS-AntiCheat 1.1.2
  - DonutAntiCheat 1.0.0
  - alhAntiCheat 1.0.1 (a freecam detector)
  - PacketEvents 2.14.0, which BS-AntiCheat and Omni need for their packet checks
- **Couldn't be used:** ParagonAC fails to start on this server. NoChance's download didn't match its published checksum.
- **The player** was never made an operator, because many anti-cheats skip checks for operators.
- **Fixtures:** every scenario reset the player and the area (fills, teleports, items) through the server console.
- **How it was run:**
  1. `tools/anticheat-server.sh setup|start`
  2. `./gradlew runClientGameTest -PantiCheatProbeOnly=true`
  3. `tools/anticheat-report.py`, which reads each anti-cheat's own record: Grim's database, Spartan's log, Omni's and BS's databases, and the console for Intave and Omni.
- **Passes:** three in all. The table is from the last clean pass. Pass 1 adds the Intave ban for Fly.

## Results

| What ran | Caught by | Outcome |
|---|---|---|
| **Baseline:** walking, jumping and mining by hand, no modules | Spartan (gravity-simulation), Omni (one teleport-confirm flag) | These happen with **no modules on**, so they're background noise from those two plugins. Grim and Intave were silent. |
| **19 visual and HUD modules** on while walking, Freelook held | Nothing beyond the baseline noise | ESPs, Search, Coordinates, Waypoints, Zoom, Fullbright, HUD widgets and the rest only read what the server already sends. |
| **Freecam,** flying the camera for 5 s | Nothing; alh's freecam score stayed **0** | The body doesn't move, and no packets change. |
| **Staff Detector** for 20 s | Nothing | Its `/msg` tab-complete requests weren't flagged. |
| **Auto Eat** | GrimAC **Post** (x2) | Grim saw a packet sent out of the normal order within a tick (the slot switch or eating). Below Grim's punish level. |
| **Auto Miner,** 8 exposed ores | **Spartan:** impossible-actions/breaking (x4) and exploits/**baritone** (x1); Spartan's ore log recorded every ore | Mined all 6 ores it targets. The other 2 were redstone and lapis, which are off by default. Spartan noticed blocks being broken *through* another block, and recognised the bot-like walking pattern. Grim, Intave and Omni were silent. |
| **AutoBuilder,** a 5x2 cobblestone wall | **GrimAC AimModulo360** (x5), **Omni** packet flood (31 movement packets over its cap) | Built all 10 blocks. Grim saw head turns of 360° or more; Omni saw too many movement packets at once. |
| **Search** for diamonds, underground | Nothing | **Useless under anti-xray:** it showed about 63,000 "diamonds" within 64 blocks, and 0 of the 40 nearest were real. They're Paper's fake ores. |
| **BlockESP** seed prediction, underground | Nothing | **Unaffected by anti-xray:** all 40 of the 40 nearest predicted diamonds were real. Mining them would still show up in ore-ratio logs like Spartan's. |
| **NewChunks** | Nothing | **Doesn't work on this server:** a never-visited area produced 0 "new" marks (82 to 97 "old"). Paper/Purpur don't send the signal it looks for. |
| **Fly** | **Omni** kick in about 2 s (BadPackets); **GrimAC** Simulation and BadPacketsI; **Intave** "invalid packets" then **ban** within 4 s (pass 1); Spartan | **Caught by every serious anti-cheat almost immediately.** Don't use it on a protected server. |
| **Auto Reconnect** after a console `/kick` | Nothing | **Worked on a real server:** the countdown ran, and it rejoined by itself. |

**Pass 4** (after the AutoBuilder step fix and the multiplayer guard; the probe allowlists only its own test server):
- **AutoBuilder** built the full 5x3 wall (15 of 15), with 3 temporary steps. Grim flagged AimModulo360 (x6) again; Omni didn't flag it this time.
- **BlockESP:** 39 of 40 predicted diamonds were real.
- **Everything else** matched pass 3. Spartan's baseline noise didn't appear this time, so it varies from run to run.

**Never flagged anything in any pass:** DonutAntiCheat and BS-AntiCheat, even during Fly; and alhAntiCheat, whose freecam score stayed 0.

## What it means

- **Safe to use** on servers like these: the read-only tools, meaning every visual/HUD module, Freecam, Freelook, Staff Detector, Waypoints and Auto Reconnect. They send nothing unusual, and no anti-cheat reacted to them.
- **Will get you flagged:** the automation, meaning Auto Miner, AutoBuilder and Auto Eat. Grim or Spartan record them, though at these short durations nothing punished the player. Longer sessions would add up.
- **Will get you kicked or banned within seconds:** Fly.
- **Anti-xray servers:** Search shows mostly fakes. Storage ESP and Base Finder probably do too for *buried* chests, since anti-xray hides chests as well, but that wasn't tested. BlockESP's seed prediction is unaffected, because it doesn't use what the server sends.
- **NewChunks:** can't be trusted on Paper-based servers.

These results are what the plugins reported. Donut Client's rules stay as they are: client-side only and no anti-cheat evasion, so nothing was changed to get past these checks.
