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

ui = "java/com/x/tunnel/NetworkPolicyActivity.java"
replace_once(
    ui,
    'import android.widget.TextView;',
    '''import android.widget.TextView;
import android.widget.EditText;
import android.widget.Button;
import android.widget.ScrollView;''',
)
replace_once(
    ui,
    '    private TextView status;',
    '''    private TextView status;
    private EditText routesInput;
    private TextView routesError;''',
)
replace_once(
    ui,
    '        setContentView(buildUi());',
    '''        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(buildUi());
        setContentView(scroll);''',
)
replace_once(
    ui,
    '        status = new TextView(this);',
    '''        TextView routesTitle = new TextView(this);
        routesTitle.setText("自定义内网路由（当前节点）");
        routesTitle.setTextSize(18);
        LinearLayout.LayoutParams rtp = full();
        rtp.topMargin = dp(18);
        root.addView(routesTitle, rtp);

        TextView routesHelp = new TextView(this);
        routesHelp.setText("每行一个 IPv4 或 CIDR，如 10.0.0.104 或 192.168.50.0/24。" +
                "单个 IP 自动转换为 /32，不要填写端口。只对进入 VPN 的应用生效，" +
                "由当前 X-Tunnel 节点的远端网络访问目标；切换节点后使用各自保存的规则。");
        routesHelp.setAlpha(0.72f);
        root.addView(routesHelp, full());

        routesInput = new EditText(this);
        routesInput.setSingleLine(false);
        routesInput.setMinLines(4);
        routesInput.setMaxLines(9);
        routesInput.setGravity(android.view.Gravity.TOP);
        routesInput.setHint("10.0.0.104\\n192.168.50.0/24");
        routesInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        root.addView(routesInput, full());

        Button saveRoutes = new Button(this);
        saveRoutes.setText("保存当前节点路由");
        saveRoutes.setOnClickListener(v -> saveRoutes());
        root.addView(saveRoutes, full());

        routesError = new TextView(this);
        routesError.setTextColor(0xFFD32F2F);
        root.addView(routesError, full());

        status = new TextView(this);''',
)
replace_once(
    ui,
    '''        loading = false;
        updateStatus();''',
    '''        routesInput.setText(prefs.getCustomRoutes());
        routesError.setText("");
        loading = false;
        updateStatus();''',
)
replace_once(
    ui,
    '''    private void updateStatus() {''',
    '''    private void saveRoutes() {
        try {
            prefs.setCustomRoutes(routesInput.getText().toString());
            routesInput.setText(prefs.getCustomRoutes());
            routesError.setText("");
            android.widget.Toast.makeText(this,
                    "已保存当前节点路由；VPN 重新连接后生效",
                    android.widget.Toast.LENGTH_LONG).show();
        } catch (IllegalArgumentException ex) {
            routesError.setText("保存失败：" + ex.getMessage());
        }
        updateStatus();
    }

    private void updateStatus() {''',
)
replace_once(
    ui,
    '''        status.setText("当前："''',
    '''        String profileName = prefs.getProfileName(prefs.getCurrentProfileId());
        status.setText("当前节点：" + profileName + "\\n当前："''',
)

print("patched custom routes into Preferences, TProxyService and NetworkPolicyActivity")
