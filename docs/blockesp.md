# BlockESP

Unfortunately, this module is currently patched on the server.

BlockESP predicts the original locations of every Overworld ore in normal vanilla Minecraft Java **1.21.11** terrain: **coal, copper, iron, gold, redstone, lapis, diamond and emerald**, each in its stone and deepslate form. It rates each prediction with a confidence based on what your client has actually received.

Open **Right Shift → Visual → BlockESP** (or search for `BlockESP`, `deepslate` or an ore name). Use the module's switch to enable it. The world seed starts at **6608149111735331168**. Choose **Edit world seed** to enter another signed 64-bit numeric seed; Apply validates the value, while Cancel preserves the previous seed. Seeds are saved as strings so JSON cannot round large values.

## Targets and variants

Each ore has its own switch, and a color row appears under it while it is selected. **Emerald** and **Coal** start selected; the six newer ores start off. **Stone variants** starts off and **Deepslate variants** starts on. These defaults scan exactly the original two targets, deepslate emerald and deepslate coal. Settings saved before the full ore set load unchanged: the old `emerald`, `coal`, `emeraldColor` and `coalColor` fields still control those ores.

Stone ores sit mostly above Y=0 and deepslate ores below Y=8. Turning off every ore or both variant filters clears predictions and shows a message. **Prediction radius** controls the square of nearby chunks around the player, from 1 to 8 chunks in each horizontal direction. All heights are checked. A radius of 4 covers 81 chunks. Large initial scans take longer. Travel starts predictions for the new area, nearest chunks first. Selecting a new ore or variant rescans cached chunks for the extra targets, nearest first, while their existing markers stay visible; deselecting only filters.

## Confidence

Every marker gets a state and a percentage, computed only from blocks your client already received:

| State | When | Confidence |
| --- | --- | --- |
| **Confirmed** | The chunk is loaded and the block there is that ore. | 100% |
| **Gone** (hidden) | Air, cave air or a fluid was received there: mined, or never there. | hidden |
| **Likely** | Another solid block that touches no air. Anti-Xray may show stone, deepslate, planks or random ores there. | 95% × seed match |
| **Unverified** | The chunk has not been received yet; the ore may also have been mined. | 80% × seed match |
| **Contradicted** | The position touches air but shows another block. Anti-Xray can't disguise exposed blocks, so this mismatch is real. | 0% |

The **seed match score** checks predictions the client can see as **exposed**: a face touches received air. Anti-Xray servers cannot hide those, so they are an unbiased test. The score is the share of exposed predictions that really are that ore, for example **Seed match: 97% (63 of 65 visible checked)**. Mined (air) positions are not counted. With fewer than 16 checks the HUD says **not enough data**; enabling common ores (coal, iron, copper in stone) and exploring caves gives it more checks. At 85% or more the seed and version match. Between 50% and 85% it reports a partial match (older chunks, edits or another version). Below 50% the HUD warns that the seed or version probably doesn't match this world.

For the Likely and Unverified percentages the score is Laplace-smoothed, `(matches + 1) / (checks + 2)`, so with no evidence it is a neutral 50%. Only predictions with the currently selected ores and variants count. The rules are pure functions in `blockesp/OreConfidence.java` with unit tests.

**Minimum confidence to show** (0-100%, default 0%) hides markers below the chosen percentage. At 0% every prediction that is not known to be gone stays visible, as before. **Fade by confidence** (default on) keeps confirmed markers at full opacity and fades uncertain ones, down to a quarter of the chosen opacity at 0%. Turn it off for uniform markers.

## Display

World markers show an outline and a light fill through terrain. **Opacity** adjusts the markers; **Tracers** adds lines from the camera to the nearest 128 markers. **Coordinates / distance HUD** shows progress, a row per selected ore with its count, nearest distance and that marker's confidence, and the seed match line. With up to four ores each row also shows the nearest coordinates. With more, rows use two columns and a single **Nearest** line shows the closest ore's coordinates. The HUD respects Minecraft's F1 and Donut's global HUD toggle. The module supports Favorites, the active-module list, and a shortcut assignable in its inspector or Minecraft Controls.

## Caps

- The client keeps at most **512** sparse ore-result chunks, packed as 9 bytes per prediction.
- Each chunk keeps at most **1,024** predictions. Over that, rarer ores are kept first (emerald, diamond, gold, lapis, redstone, iron, copper, then coal). A chunk with every ore and both variants usually holds a few hundred.
- At most **2,048** markers are drawn, nearest first. HUD counts still cover the whole scan area.
- Tracers go to the nearest **128** markers.
- Confidence is re-checked in batches of about 8,192 predictions per tick, so a full radius-8 scan refreshes in about a second.

## What predictions can and can't tell you

All markers are **predictions**. A seed reconstructs original generation, not later player changes. A received air block hides an obsolete marker, while a solid mismatch does not: Anti-Xray can disguise an existing ore as stone or deepslate. The HUD's counts cover the selected scan area, not the entire world.

Match the version that originally generated the chunks. A server running 1.21.11 may still contain older chunks. Custom world generators, terrain presets, generation datapacks, modified block tags, or separate feature seeds are outside this module's scope. The seed match score is how you notice these: it falls when predictions stop matching what you can see. Even with the right seed and version, features that cross chunk borders are placed in the order chunks happened to generate, so a few ores next to overlapping features (lush cave pools, geodes) can differ. In the test world, 199 of 204 real diamonds were predicted, with no extra predictions. Changing the seed, leaving the Overworld, disconnecting, or disabling clears the displayed results. **Rescan predictions** clears cached predictions and retries a failed scan; it cannot reconstruct a server's mining history.

Confidence uses only blocks the server already sent you. BlockESP never requests chunks or blocks from the server and sends nothing extra.

## Generation and resource use

The module loads the vanilla normal world preset and uses Minecraft's actual chunk scheduler through FULL status. FULL status includes the neighboring feature writes that can cross chunk borders; simulating only ore placement or stopping at FEATURES can miss those writes. It scans generated block states for the selected exact block IDs and skips section palettes without any selected target.

The generation world belongs to one background worker. It has no players or listening sockets and never runs gameplay ticks. Its files are confined to a unique temporary directory under the active instance's `.cache/donut-blockesp/`, which is removed when that generation session closes. It does not access the user's saved worlds or request extra chunks from the connected server. The active world's registry tags are not replaced when loading prediction resources; matching vanilla block tags are required.

One chunk request is in flight at a time. The generation session is recycled after 128 requested chunks to bound its working data, and vanilla request-ticket expiry releases old chunks between batches. Seed/world changes invalidate pending work so an old result cannot appear in a new world. Cancellation lets the current vanilla generation operation finish, then releases the scratch world on its owner thread.

## Validation

Run `./gradlew test` for numeric seed, persistence (including settings saved before the full ore set), exact-block targets and variants, confidence states, the seed match score and verdicts, and negative-coordinate scheduling checks. Run `./gradlew runClientGameTest -PblockEspTestsOnly=true` for the fresh vanilla comparison (the original two targets and newly enabled diamonds), Confirmed/Likely/gone transitions, the seed match score for the right and a wrong seed, exact variant/height fixtures, native seed validation, preferences, rendering, shortcut, and lifecycle checks. The focused flag also runs the existing module-keybind regression suite. Test classes are excluded from the release JAR.
