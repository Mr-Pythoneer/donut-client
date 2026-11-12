# Auto Reconnect

When a multiplayer server drops you, Auto Reconnect puts a countdown on Minecraft's disconnect screen ("Reconnect in 5s") with a **Stop** button, and when the countdown ends it joins the same server again through the normal connect screen. It starts **disabled**.

It's in the click GUI under **Visual** (it's a utility; Right Shift → Auto Reconnect). Search finds it by "rejoin", "kicked", "disconnect" or its setting names.

## How it works

- Every time you connect to a server (server list, Direct Connect, a server transfer), the address is remembered. Joining a singleplayer world clears it, so a singleplayer world is never "reconnected".
- On the vanilla disconnect screen it adds, at the bottom:
  - a line saying where it will reconnect and which attempt this is ("attempt 2/5"),
  - **Reconnect in Ns**: click to reconnect now,
  - **Stop**: cancels the countdown on this screen. The button then reads **Reconnect** and still works by hand.
- When the countdown ends it calls vanilla `ConnectScreen.startConnecting` with the same address and server entry, exactly what the server list does. If that fails you land on a new disconnect screen and the next attempt is counted.
- Attempts reset when a join succeeds or when you connect somewhere yourself.
- **Leaving the disconnect screen** (Back to Server List, Esc, anything) stops retrying. Nothing runs in the background.
- Realms are skipped.

## Settings

| Setting | Default | What it does |
| --- | --- | --- |
| Delay | 5 s (1-60) | Wait before the first attempt. |
| Max attempts (0 = unlimited) | 5 (0-50) | After this many failed attempts it stops and shows "gave up after N attempts". 0 keeps trying until you leave the screen. |
| Wait longer after each failure | on | Doubles the wait after each failed attempt, capped at 60 seconds (5, 10, 20, 40, 60 s), so a restarting server has time to come back. Off: the same delay every time. |
| Don't retry bans and whitelists | on | See below. |

## Kicks it won't retry

Retrying a ban only produces more failed logins, so with **Don't retry bans and whitelists** on the screen shows "not retrying (reason)" instead of a countdown when the reason looks final:

- Vanilla reasons by translation key: `multiplayer.disconnect.banned` (and `.reason`, `.expiration`), `banned_ip.reason`/`.expiration`, `not_whitelisted`, `duplicate_login` (you logged in from another place), `outdated_client`, `outdated_server`, `incompatible`.
- Plugin reasons are usually plain text, so the text (lower case) is also checked for: "banned", "ban expires", "whitelist", "white-list", "white list", "blacklist", "logged in from another location", "outdated client", "outdated server", "incompatible".

Everything else is retried: timeouts, "Server closed", restarts, "Connection refused", "Kicked by an operator", anti-cheat kicks with other wording. This is a heuristic: a server with unusual ban wording will still be retried up to **Max attempts**. The rules live in `autoreconnect/ReconnectPolicy` and are unit-tested in `ReconnectPolicyTest`.

## Read this first

Some servers dislike rapid reconnects and may rate-limit or temp-ban clients that reconnect too often. Keep the delay and backoff reasonable on public servers.

## Implementation

- `autoreconnect/AutoReconnectController`: remembers the target, decorates `DisconnectedScreen` (Fabric `ScreenEvents.AFTER_INIT`, per-screen tick/render/remove events), counts attempts.
- `autoreconnect/ReconnectPolicy`: pure delay, attempt and kick-reason rules.
- `mixin/AutoReconnectConnectMixin`: records the address at the start of `ConnectScreen.startConnecting`.
- `mixin/DisconnectedScreenAccessor`: reads the kick reason and the screen to return to.

## Tests

- Unit: `ReconnectPolicyTest` (fixed delay, backoff doubling and cap, attempt limits and labels, final vs retryable reasons, settings defaults/clamps/JSON).
- Runtime: `AutoReconnectGameTest` (in `-PnewToolsATestsOnly=true`). Without a dedicated server (which would need the Minecraft EULA accepted in the test run directory), it simulates a multiplayer disconnect: the controller is given a closed local port as the remembered server and the vanilla `DisconnectedScreen` is opened. The countdown then really calls `ConnectScreen.startConnecting`, which fails with "Connection refused" and returns a new disconnect screen. It checks the countdown and Stop buttons, attempt 2/2, giving up after the limit, Stop through the screen's mouse handler, leaving the screen, a vanilla ban reason, a plugin-style whitelist reason, the module switched off, and that a singleplayer world clears the target. Screenshots `auto-reconnect-01` to `-04`.
- Not covered: a real kick from a running dedicated server followed by a successful rejoin. The join path is vanilla's own connect screen, so it behaves like the server list's Join button.
