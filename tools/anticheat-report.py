#!/usr/bin/env python3
"""Anti-cheat probe report: what each anti-cheat recorded during each scenario of AntiCheatProbeGameTest.

The probe brackets every scenario with "[probe] begin/end <scenario>" in the server log. Each plugin keeps its
detections somewhere different, so this reads them all and files each one under the scenario whose time window
it falls in:
  - server console (logs/latest.log): Intave notifications and punishments, DonutAntiCheat, plugin errors
  - plugins/GrimAC/data/history.v1.db (every Grim flag, including those below its console alert threshold)
  - plugins/Spartan/logs/log*.yml
  - plugins/OmniAnticheat/anticheat.db (oac_violations), plugins/BSAntiCheat/anticheat.db (anticheat_logs)
  - plugins/alhAntiCheat/logs.yml (a running suspicion score, not timestamped: reported once at the end)
Next to that it shows the client's own record (probe-client.tsv): server setbacks, kicks and what the module did.

usage: tools/anticheat-report.py [server-dir]   (default: /Volumes/ VideoGames/DevTools/ac-testserver)
"""
import collections, csv, datetime as dt, pathlib, re, sqlite3, sys

DIR = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else "/Volumes/ VideoGames/DevTools/ac-testserver")
# latest.log style "[12:50:05] [Server thread/INFO]: ..." or console style "[12:50:05 INFO]: ..."
LINE = re.compile(r"^\[(?P<time>[\d:]+)(?:\] \[[^\]]*/| )(?P<level>\w+)\]: (?P<msg>.*)$")
COLOR = re.compile(r"§.|\x1b\[[\d;]*m")
# Console lines that are anti-cheat output, by plugin. Everything else in the console is fixture noise.
CONSOLE_PLUGINS = [("Intave", re.compile(r"^\[Intave\] Issued server command /(intave internals sendnotify|ban|kick)", re.I)),
                   ("GrimAC", re.compile(r"^(Grim|\[Grim)|\bGrimAC\b|failed \w+ \(x\d+\)", re.I)),
                   ("DonutAntiCheat", re.compile(r"Donut\s*SMP AC|\[DonutAC|DonutAntiCheat\]", re.I)),
                   ("Spartan", re.compile(r"^\[Spartan\]|Spartan Notification", re.I)),
                   ("OmniAnticheat", re.compile(r"^\[(Omni|OAC)", re.I)),
                   ("BSAntiCheat", re.compile(r"^\[BS(AntiCheat|AC)?\]", re.I)),
                   ("alhAntiCheat", re.compile(r"^\[alh", re.I)),
                   ("plugin error", re.compile(r"^Could not pass event|^Error occurred while|^Exception in", re.I))]


def windows_from_log(log):
    """[(scenario, start, end)] in local time, from the probe markers."""
    day = dt.datetime.fromtimestamp(log.stat().st_mtime).date()
    out, open_ = [], {}
    lines = log.read_text(errors="replace").splitlines()
    console = []
    for raw in lines:
        m = LINE.match(raw)
        if not m: continue
        t = dt.datetime.combine(day, dt.time.fromisoformat(m["time"]))
        msg = COLOR.sub("", m["msg"]).strip()
        b = re.search(r"\[probe\] begin (\S+)", msg)
        e = re.search(r"\[probe\] end (\S+)", msg)
        if b: open_[b.group(1)] = t
        elif e and e.group(1) in open_: out.append((e.group(1), open_.pop(e.group(1)), t))
        else: console.append((t, msg))
    for name, start in open_.items(): out.append((name, start, dt.datetime.max))
    # The log can hold several probe runs: keep only the latest (from its first scenario on).
    starts = [start for name, start, _ in out if name == "baseline-walk-and-mine"]
    if starts: out = [w for w in out if w[1] >= max(starts)]
    out.sort(key=lambda w: w[1])
    return out, console


def which(windows, t):
    for name, start, end in windows:
        if start <= t <= end + dt.timedelta(seconds=1): return name
    return None


def main():
    log = DIR / "logs" / "latest.log"
    windows, console = windows_from_log(log)
    hits = collections.defaultdict(list)  # scenario -> [(plugin, text)]

    for t, msg in console:
        for plugin, pattern in CONSOLE_PLUGINS:
            if pattern.search(msg):
                scenario = which(windows, t)
                if scenario: hits[scenario].append((plugin, msg))
                break

    for path in sorted((DIR / "plugins" / "Spartan" / "logs").glob("log*.yml")):
        text = path.read_text(errors="replace")
        for m in re.finditer(r"^(\d{4}/\d\d/\d\d \d\d:\d\d:\d\d):\d+[-+]\d\d:\d\d: '(.*?)'(?=\n\d{4}/|\Z)", text, re.S | re.M):
            t = dt.datetime.strptime(m.group(1), "%Y/%m/%d %H:%M:%S")
            body = " ".join(m.group(2).split())
            check = re.search(r"\(Check: ([^)]+)\)", body)
            detection = re.search(r"\(Detection: ([^)]+)\)", body)
            ore = re.search(r"\(Ore: ([^)]+)\)", body)
            if check: text_ = f"{check.group(1)} / {detection.group(1) if detection else '?'}"
            elif ore: text_ = f"ore-mined tracker: {ore.group(1)}"
            else: continue
            scenario = which(windows, t)
            if scenario: hits[scenario].append(("Spartan ore log" if ore and not check else "Spartan", text_))

    def rows(db, query):
        path = DIR / "plugins" / db
        if not path.exists(): return []
        try:
            with sqlite3.connect(f"file:{path}?mode=ro", uri=True) as con: return con.execute(query).fetchall()
        except sqlite3.Error as error:
            print(f"(could not read {db}: {error})", file=sys.stderr); return []

    # GrimAC keeps every flag (even below its console alert threshold) in its history database.
    for ts, check, vl in rows("GrimAC/data/history.v1.db", "select v.occurred_at, coalesce(c.display, v.check_id), v.vl "
                                "from grim_violations v left join grim_checks c on c.check_id = v.check_id"):
        scenario = which(windows, dt.datetime.fromtimestamp(ts / 1000))
        if scenario: hits[scenario].append(("GrimAC", str(check)))
    for ts, check, vl, info in rows("OmniAnticheat/anticheat.db", "select ts, check_name, vl, info from oac_violations"):
        scenario = which(windows, dt.datetime.fromtimestamp(ts / 1000))
        if scenario: hits[scenario].append(("OmniAnticheat", f"{check} (vl {vl:.1f})"))
    for ts, kind, value, desc in rows("BSAntiCheat/anticheat.db", "select ts, log_type, value, description from anticheat_logs"):
        try: t = dt.datetime.fromisoformat(str(ts)).replace(tzinfo=dt.timezone.utc).astimezone().replace(tzinfo=None)
        except ValueError:
            try: t = dt.datetime.fromtimestamp(float(ts) / 1000)
            except ValueError: continue
        scenario = which(windows, t)
        if scenario: hits[scenario].append(("BSAntiCheat", f"{kind}: {desc}"))

    client = {}
    tsv = DIR / "probe-client.tsv"
    if tsv.exists():
        for row in csv.DictReader(tsv.open(), delimiter="\t"): client[row["scenario"]] = row

    order = [name for name, _, _ in windows]
    print("| Scenario | Setbacks | Kicked | Detections (by plugin) | What the module did |")
    print("|---|---|---|---|---|")
    for scenario in order + [s for s in client if s not in order]:
        counts = collections.Counter(p for p, _ in hits.get(scenario, []))
        flags = ", ".join(f"{p} {n}" for p, n in counts.most_common()) or "none"
        row = client.get(scenario, {})
        print(f"| {scenario} | {row.get('setbacks', '?')} | {row.get('kicked') or '-'} | {flags} | {row.get('detail', '')} |")
    alh = DIR / "plugins" / "alhAntiCheat" / "logs.yml"
    if alh.exists():
        scores = re.findall(r"^\s+[0-9a-f-]{36}: (\S+)", alh.read_text(), re.M)
        print(f"\nalhAntiCheat (freecam) suspicion score at the end of the run: {', '.join(scores) or 'none'}")
    print()
    for scenario in order:
        found = hits.get(scenario, [])
        if not found: continue
        print(f"### {scenario}")
        for (plugin, text), n in collections.Counter(found).most_common(30): print(f"- {plugin}: {text[:200]} ({n}x)")
        print()


if __name__ == "__main__":
    main()
