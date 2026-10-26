#!/usr/bin/env python3
"""Apply per-profile IPv4/CIDR routing on top of the verified APK UI reconstruction.

Run after patch_reference_ui.py, before adding overlay files to the Gradle project.
Fail closed when upstream source markers change, rather than emitting a stale APK.
"""
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_custom_routes.py <upstream-repository-root>")

app = Path(sys.argv[1]).resolve() / "x-tunnel-android-gui-src/src/main"


def replace_once(filename, old, new):
    path = app / filename
    data = path.read_text(encoding="utf-8")
    count = data.count(old)
    if count != 1:
        raise RuntimeError(f"{filename}: expected one marker but found {count}: {old[:100]!r}")
    path.write_text(data.replace(old, new, 1), encoding="utf-8")


prefs = "java/com/x/tunnel/Preferences.java"
replace_once(
    prefs,
    '        public static final String WEBRTC_MODE = "WebRtcMode";',
    '        public static final String WEBRTC_MODE = "WebRtcMode";\n'
    '        public static final String CUSTOM_ROUTES = "CustomRoutes";',
)
replace_once(
    prefs,
    '            editor.putBoolean(DISABLE_ECH + "_" + toId, prefs.getBoolean(DISABLE_ECH + "_" + fromId, false));',
    '            editor.putBoolean(DISABLE_ECH + "_" + toId, prefs.getBoolean(DISABLE_ECH + "_" + fromId, false));\n'
    '            editor.putString(CUSTOM_ROUTES + "_" + toId, prefs.getString(CUSTOM_ROUTES + "_" + fromId, ""));',
)
replace_once(
    prefs,
    'String[] keys = {WSS_ADDR, ECH_DNS, ECH_DOMAIN, PREF_IP, IPS_PREF, UDP_BLOCK_PORTS, INSECURE, TOKEN, WS_CONN, DISABLE_ECH};',
    'String[] keys = {WSS_ADDR, ECH_DNS, ECH_DOMAIN, PREF_IP, IPS_PREF, UDP_BLOCK_PORTS, INSECURE, TOKEN, WS_CONN, DISABLE_ECH, CUSTOM_ROUTES};',
)
replace_once(
    prefs,
    '        public String getRuntimeIpsPref() {',
    '''        /** Per-profile destination routes, included in all SharedPreferences backups. */
        public String getCustomRoutes() {
                return prefs.getString(getKey(CUSTOM_ROUTES), "");
        }

        public void setCustomRoutes(String routes) {
                String validated = CustomRouteRules.normalize(routes);
                prefs.edit().putString(getKey(CUSTOM_ROUTES), validated).commit();
        }

        public String getRuntimeIpsPref() {''',
)

svc = "java/com/x/tunnel/TProxyService.java"
replace_once(
    svc,
    '\t\tPreferences prefs = new Preferences(this);',
    '''\t\tPreferences prefs = new Preferences(this);
        // Block VPN startup if restored/imported routes contain invalid entries.
        final String customRoutes;
        try {
            customRoutes = CustomRouteRules.normalize(prefs.getCustomRoutes());
        } catch (IllegalArgumentException invalidRoute) {
            prefs.setEnable(false);
            new Handler(Looper.getMainLooper()).post(() ->
                Toast.makeText(getApplicationContext(),
                    "自定义路由错误：" + invalidRoute.getMessage(), Toast.LENGTH_LONG).show());
            stopSelf();
            return;
        }''',
)
replace_once(
    svc,
    '\t\tbuilder.addRoute("0.0.0.0", 0);',
    '''\t\tbuilder.addRoute("0.0.0.0", 0);
        // Explicit destination rules are additive: they must use the X-Tunnel VPN,
        // not an excluded/direct route. Must execute before builder.establish().
        CustomRouteRules.addToVpn(builder, customRoutes);''',
)
replace_once(svc, "                                prefs.getUdpBlockPorts(),",
             "                                prefs.getRuntimeUdpBlockPorts(),")
replace_once(svc, "                                prefs.getIpsPref(),",
             "                                prefs.getRuntimeIpsPref(),")


print("patched per-profile custom routes into Preferences and TProxyService")
