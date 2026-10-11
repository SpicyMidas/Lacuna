#!/usr/bin/env bash
# Lacuna dedicated-server smoke test (spec section 4, gate 6).
#
# Boots a dedicated server on a fresh world, runs the scripted console session in
# tools/smoke_phase1.txt (selftest, forceloads, /place structure, /locate ...), saves, stops,
# restarts on the same world, runs tools/smoke_phase2.txt and verifies that state persisted.
# Both server logs must be free of ERROR / Exception / Failed to / Missing / Unknown registry /
# Couldn't load lines, except lines matching a regex in tools/log_allowlist.txt.
set -euo pipefail

cd "$(dirname "$0")/.."
RUN_DIR="run"
WORLD="lacuna_smoke"
LOG="$RUN_DIR/logs/latest.log"
OUT_DIR="tools/.smoke"
mkdir -p "$RUN_DIR" "$OUT_DIR"

echo "eula=true" > "$RUN_DIR/eula.txt"
cat > "$RUN_DIR/server.properties" <<PROPS
level-name=$WORLD
online-mode=false
spawn-protection=0
max-tick-time=-1
view-distance=6
simulation-distance=6
enable-command-block=true
level-seed=lacuna-smoke
PROPS
rm -rf "${RUN_DIR:?}/$WORLD"
cp tools/smoke_phase1.txt "$RUN_DIR/lacuna_smoke_phase1.txt"
cp tools/smoke_phase2.txt "$RUN_DIR/lacuna_smoke_phase2.txt"

check_log() {
    local phase="$1"
    cp "$LOG" "$OUT_DIR/$phase.log"
    local pattern='ERROR|Exception|Failed to|Missing|Unknown registry|Couldn'"'"'t load'
    local hits
    hits=$(grep -E "$pattern" "$LOG" || true)
    if [[ -n "$hits" && -f tools/log_allowlist.txt ]]; then
        while IFS= read -r allow; do
            [[ -z "$allow" || "$allow" == \#* ]] && continue
            hits=$(printf '%s\n' "$hits" | grep -Ev -- "$allow" || true)
        done < tools/log_allowlist.txt
    fi
    if [[ -n "$hits" ]]; then
        echo "SMOKE FAIL ($phase): forbidden log lines:"
        printf '%s\n' "$hits" | head -50
        exit 1
    fi
    if ! grep -q "\[SMOKE\] $phase complete" "$LOG"; then
        echo "SMOKE FAIL ($phase): scripted session did not complete"
        exit 1
    fi
}

echo "== smoke phase 1 (fresh world)"
./gradlew --no-configuration-cache runServer -PlacunaSmoke=phase1 --console=plain
check_log phase1
grep -q "Lacuna selftest PASS" "$LOG" || { echo "SMOKE FAIL: /lacuna selftest did not pass"; exit 1; }

echo "== smoke phase 2 (restart on the same world)"
./gradlew --no-configuration-cache runServer -PlacunaSmoke=phase2 --console=plain
check_log phase2
grep -q "\[SMOKE\] persistence check PASS" "$LOG" || { echo "SMOKE FAIL: persistence check"; exit 1; }

echo "SMOKE PASS"
