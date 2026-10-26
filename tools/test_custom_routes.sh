#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
javac -encoding UTF-8 -d "$OUT" \
  "$ROOT/tests/java/android/net/VpnService.java" \
  "$ROOT/overlay/java/com/x/tunnel/CustomRouteRules.java" \
  "$ROOT/tests/java/com/x/tunnel/CustomRouteRulesTest.java" \
  "$ROOT/overlay/java/com/x/tunnel/UdpPolicy.java" \
  "$ROOT/tests/java/com/x/tunnel/UdpPolicyTest.java"
java -cp "$OUT" com.x.tunnel.CustomRouteRulesTest
java -cp "$OUT" com.x.tunnel.UdpPolicyTest
python3 -m py_compile "$ROOT/tools/patch_reference_ui.py" \
  "$ROOT/tools/patch_custom_routes.py" "$ROOT/tools/patch_stable_tun.py"
