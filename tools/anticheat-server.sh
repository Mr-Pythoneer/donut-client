#!/bin/sh
# Local anti-cheat test server (Purpur 1.21.11) for AntiCheatProbeGameTest. Offline mode, bound to 127.0.0.1 only.
#   tools/anticheat-server.sh setup   download Purpur 1.21.11 and the anti-cheat plugins (hashes checked), write configs
#   tools/anticheat-server.sh start   run the server in the background; commands appended to console.in reach its console
#   tools/anticheat-server.sh stop    stop it cleanly
# Running the server means accepting Minecraft's EULA (https://aka.ms/MinecraftEULA); `setup` writes eula=true,
# so only run it if you accept it. Needs Java 21+ (JAVA_HOME).
set -eu
DIR=${DONUT_AC_DIR:-"/Volumes/ VideoGames/DevTools/ac-testserver"}
PORT=${DONUT_AC_PORT:-25599}
SEED=6608149111735331168   # BlockEspSettings.DEFAULT_SEED, so the seed predictor can be checked against real blocks
PLUGINS="packetevents grimac intave spartan-anticheat bs-anticheat omnianticheat donutanticheat alh-anticheat"
# nochance-anticheat is left out: the file Modrinth serves does not match its published hash (checked 2026-11-12).
# paragonac is left out: it fails to enable on Purpur 1.21.11 (missing net.kyori.adventure.nbt).
JAVA=${JAVA_HOME:+$JAVA_HOME/bin/}java

fetch() { # url sha-algorithm hash destination
    if [ -f "$4" ] && [ "$(shasum -a "$2" "$4" | cut -d' ' -f1)" = "$3" ]; then return; fi
    rm -f "$4.part"; i=0
    until curl -fsSL -C - -o "$4.part" "$1"; do i=$((i+1)); [ $i -lt 30 ] || exit 1; sleep 2; done  # resumes cut-off transfers
    [ "$(shasum -a "$2" "$4.part" | cut -d' ' -f1)" = "$3" ] || { echo "hash mismatch for $4" >&2; rm -f "$4.part"; exit 1; }
    mv "$4.part" "$4"
}

case "${1:-}" in
setup)
    mkdir -p "$DIR/plugins" "$DIR/config"
    cd "$DIR"
    # Purpur (a Paper fork with Paper's anti-xray); Paper's own CDN sends empty bodies on some networks.
    build=$(curl -fsS https://api.purpurmc.org/v2/purpur/1.21.11 | python3 -c "import json,sys; print(json.load(sys.stdin)['builds']['latest'])")
    md5=$(curl -fsS "https://api.purpurmc.org/v2/purpur/1.21.11/$build" | python3 -c "import json,sys; print(json.load(sys.stdin)['md5'])")
    if [ ! -f server.jar ] || [ "$(md5 -q server.jar)" != "$md5" ]; then
        curl -fsSL -A "donut-client-anticheat-probe/1.0" -o server.jar.part "https://api.purpurmc.org/v2/purpur/1.21.11/$build/download"
        [ "$(md5 -q server.jar.part)" = "$md5" ] || { echo "hash mismatch for server.jar" >&2; exit 1; }
        mv server.jar.part server.jar
    fi
    echo "purpur: 1.21.11 build $build"
    for slug in $PLUGINS; do
        line=$(curl -fsS "https://api.modrinth.com/v2/project/$slug/version?game_versions=%5B%221.21.11%22%5D&loaders=%5B%22paper%22,%22spigot%22,%22bukkit%22%5D" | python3 -c "
import json,sys
v=json.load(sys.stdin)[0]; f=next((f for f in v['files'] if f['primary']), v['files'][0])
print(f['url'], f['hashes']['sha512'], v['version_number'])")
        set -- $line
        fetch "$1" 512 "$2" "plugins/$slug.jar"
        echo "$slug: $3"
    done
    echo "eula=true" > eula.txt
    cat > server.properties <<EOF
online-mode=false
server-ip=127.0.0.1
server-port=$PORT
level-seed=$SEED
level-name=probe-world
allow-flight=true
spawn-protection=0
view-distance=8
simulation-distance=6
max-players=2
difficulty=peaceful
motd=Donut anti-cheat probe
enable-command-block=false
EOF
    # Paper's anti-xray (engine mode 2 hides every buried ore behind fake ones); Paper merges the rest of its defaults.
    cat > config/paper-world-defaults.yml <<'EOF'
anticheat:
  anti-xray:
    enabled: true
    engine-mode: 2
    max-block-height: 64
    update-radius: 2
    lava-obscures: false
    use-permission: false
    hidden-blocks: [copper_ore, deepslate_copper_ore, raw_copper_block, gold_ore, deepslate_gold_ore, iron_ore, deepslate_iron_ore, raw_iron_block, coal_ore, deepslate_coal_ore, lapis_ore, deepslate_lapis_ore, mossy_cobblestone, obsidian, chest, diamond_ore, deepslate_diamond_ore, redstone_ore, deepslate_redstone_ore, clay, emerald_ore, deepslate_emerald_ore, ender_chest]
    replacement-blocks: [stone, oak_planks, deepslate]
EOF
    echo "ready in $DIR"
    ;;
start)
    cd "$DIR"
    if pgrep -f "bin/java -Xms1G -Xmx3G -jar server.jar" > /dev/null; then echo "already running" >&2; exit 1; fi
    pkill -f "tail -n0 -F console.in" || true
    : > console.in
    nohup sh -c "tail -n0 -F console.in | \"$JAVA\" -Xms1G -Xmx3G -jar server.jar --nogui" > server.out 2>&1 &
    echo "starting (pid $!); waiting for 'Done'"
    for i in $(seq 1 180); do grep -q 'Done (' server.out 2>/dev/null && { echo "up on 127.0.0.1:$PORT"; exit 0; }; sleep 1; done
    echo "server did not report Done in 180 s; see $DIR/server.out" >&2; exit 1
    ;;
stop)
    cd "$DIR"; echo stop >> console.in
    # Match the java process itself: the sh/tail wrapper outlives it until tail is killed. Shutdown can take ~60 s.
    for i in $(seq 1 150); do pgrep -f "bin/java -Xms1G -Xmx3G -jar server.jar" > /dev/null || { pkill -f "tail -n0 -F console.in" || true; echo stopped; exit 0; }; sleep 1; done
    echo "still running after 150 s" >&2; exit 1
    ;;
*) echo "usage: $0 setup|start|stop" >&2; exit 2 ;;
esac
