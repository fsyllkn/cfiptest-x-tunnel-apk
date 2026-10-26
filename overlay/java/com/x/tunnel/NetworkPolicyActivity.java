package com.x.tunnel;

import android.app.Activity;
import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.Button;
import android.widget.ScrollView;

public class NetworkPolicyActivity extends Activity {
    private Preferences prefs;
    private RadioButton standard;
    private RadioButton aiStable;
    private RadioButton webrtcCompat;
    private RadioButton webrtcStrict;
    private TextView status;
    private EditText routesInput;
    private TextView routesError;
    private boolean loading = false;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = new Preferences(this);
        setTitle("网络策略");
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(buildUi());
        setContentView(scroll);
        load();
    }

    private LinearLayout buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(16));

        TextView title = new TextView(this);
        title.setText("X-Tunnel 网络策略");
        title.setTextSize(22);
        root.addView(title, full());

        TextView note = new TextView(this);
        note.setText("所有进入 VPN 的应用仍然只使用当前一个 X-Tunnel 出站。这里仅控制 DNS/IP版本/UDP/WebRTC 处理，不做节点分组或域名分流。");
        note.setAlpha(0.72f);
        LinearLayout.LayoutParams nlp = full();
        nlp.topMargin = dp(8);
        root.addView(note, nlp);

        TextView modeTitle = new TextView(this);
        modeTitle.setText("网络模式");
        modeTitle.setTextSize(18);
        LinearLayout.LayoutParams mtp = full();
        mtp.topMargin = dp(18);
        root.addView(modeTitle, mtp);

        RadioGroup modeGroup = new RadioGroup(this);
        modeGroup.setOrientation(RadioGroup.VERTICAL);
        standard = new RadioButton(this);
        standard.setText("标准模式");
        aiStable = new RadioButton(this);
        aiStable.setText("AI 稳定模式");
        modeGroup.addView(standard);
        modeGroup.addView(aiStable);
        root.addView(modeGroup, full());

        TextView aiDesc = new TextView(this);
        aiDesc.setText("AI 稳定模式：保持远程 DNS，运行时强制 IPv4 only，并阻止 QUIC/UDP 443；IPv6 仍由 VPN 捕获，不允许绕过系统 VPN。");
        aiDesc.setAlpha(0.68f);
        root.addView(aiDesc, full());

        TextView wrTitle = new TextView(this);
        wrTitle.setText("WebRTC");
        wrTitle.setTextSize(18);
        LinearLayout.LayoutParams wtp = full();
        wtp.topMargin = dp(18);
        root.addView(wrTitle, wtp);

        RadioGroup wrGroup = new RadioGroup(this);
        wrGroup.setOrientation(RadioGroup.VERTICAL);
        webrtcCompat = new RadioButton(this);
        webrtcCompat.setText("兼容模式（推荐）");
        webrtcStrict = new RadioButton(this);
        webrtcStrict.setText("严格保护");
        wrGroup.addView(webrtcCompat);
        wrGroup.addView(webrtcStrict);
        root.addView(wrGroup, full());

        TextView wrDesc = new TextView(this);
        wrDesc.setText("兼容模式阻断 UDP/443。严格保护在所有网络模式下只放行 DNS/UDP 53，拦截其余所有 UDP 目的端口（包含非标准 STUN/TURN），可能影响实时语音、视频或游戏。请断开并重新连接 VPN 使设置生效。");
        wrDesc.setAlpha(0.68f);
        root.addView(wrDesc, full());

        TextView routesTitle = new TextView(this);
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
        routesInput.setHint("10.0.0.104\n192.168.50.0/24");
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

        status = new TextView(this);
        status.setPadding(0, dp(18), 0, 0);
        root.addView(status, full());

        modeGroup.setOnCheckedChangeListener((g, id) -> save());
        wrGroup.setOnCheckedChangeListener((g, id) -> save());

        return root;
    }

    private void load() {
        loading = true;
        if (prefs.getNetworkMode() == Preferences.NETWORK_AI_STABLE) {
            aiStable.setChecked(true);
        } else {
            standard.setChecked(true);
        }

        if (prefs.getWebRtcMode() == Preferences.WEBRTC_STRICT) {
            webrtcStrict.setChecked(true);
        } else {
            webrtcCompat.setChecked(true);
        }
        routesInput.setText(prefs.getCustomRoutes());
        routesError.setText("");
        loading = false;
        updateStatus();
    }

    private void save() {
        if (loading) return;
        if (standard == null || aiStable == null || webrtcCompat == null || webrtcStrict == null) return;
        prefs.setNetworkMode(aiStable.isChecked()
                ? Preferences.NETWORK_AI_STABLE
                : Preferences.NETWORK_STANDARD);
        prefs.setWebRtcMode(webrtcStrict.isChecked()
                ? Preferences.WEBRTC_STRICT
                : Preferences.WEBRTC_COMPAT);
        updateStatus();
    }

    private void saveRoutes() {
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

    private void updateStatus() {
        String mode = prefs.getNetworkMode() == Preferences.NETWORK_AI_STABLE
                ? "AI 稳定"
                : "标准";
        String wr = prefs.getWebRtcMode() == Preferences.WEBRTC_STRICT
                ? "严格保护"
                : "兼容";
        String profileName = prefs.getProfileName(prefs.getCurrentProfileId());
        status.setText("当前节点：" + profileName + "\n当前："
                + mode
                + " / WebRTC "
                + wr
                + "\n运行时 IP 策略："
                + prefs.getRuntimeIpsPref()
                + "\n运行时 UDP 屏蔽端口："
                + prefs.getRuntimeUdpBlockPorts()
                + "\n远程 DNS：启用"
                + (prefs.getEnable() ? "\n注意：VPN 正在运行，策略将在重新连接后生效" : ""));
    }

    private LinearLayout.LayoutParams full() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int n) {
        return Math.round(n * getResources().getDisplayMetrics().density);
    }
}
