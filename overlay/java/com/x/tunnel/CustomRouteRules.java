package com.x.tunnel;

import android.net.VpnService;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Per-profile IPv4 routes that must remain inside the X-Tunnel VPN.
 * This is a routing policy, not a destination-specific direct-connect exception.
 * Note: the server still needs to be able to reach the requested private address.
 */
public final class CustomRouteRules {
    private CustomRouteRules() {}

    public static final class Route {
        public final String address;
        public final int prefix;
        private Route(String address, int prefix) {
            this.address = address;
            this.prefix = prefix;
        }
        @Override public String toString() { return address + "/" + prefix; }
    }

    public static List<Route> parse(String value) {
        ArrayList<Route> routes = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        if (value == null || value.trim().isEmpty()) return routes;
        String[] tokens = value.split("[,;\r\n]+", -1);
        for (String token : tokens) {
            String rule = token.trim();
            if (rule.isEmpty()) continue;
            if (rule.indexOf(':') >= 0) {
                throw new IllegalArgumentException("不支持端口或 IPv6：" + rule + "；只填写 IPv4/CIDR");
            }
            String[] pair = rule.split("/", -1);
            if (pair.length > 2 || pair.length == 0) {
                throw new IllegalArgumentException("路由格式错误：" + rule);
            }
            int prefix = 32;
            if (pair.length == 2) {
                try {
                    if (!pair[1].matches("[0-9]{1,2}")) throw new NumberFormatException();
                    prefix = Integer.parseInt(pair[1]);
                } catch (NumberFormatException ex) {
                    throw new IllegalArgumentException("无效掩码：" + rule);
                }
                if (prefix < 1 || prefix > 32) {
                    throw new IllegalArgumentException("掩码必须为 1~32：" + rule);
                }
            }
            String[] octets = pair[0].split("\\.", -1);
            if (octets.length != 4) throw new IllegalArgumentException("无效 IPv4：" + rule);
            long number = 0L;
            for (String octet : octets) {
                if (!octet.matches("[0-9]{1,3}")) {
                    throw new IllegalArgumentException("无效 IPv4：" + rule);
                }
                int n = Integer.parseInt(octet);
                if (n > 255) throw new IllegalArgumentException("IPv4 超出范围：" + rule);
                number = (number << 8) | n;
            }
            int first = (int) (number >>> 24);
            if (first == 0 || first == 127 || first >= 224) {
                throw new IllegalArgumentException("不允许环回、组播或保留地址：" + rule);
            }
            long mask = (0xffffffffL << (32 - prefix)) & 0xffffffffL;
            long network = number & mask;
            String canonical = ((network >>> 24) & 255) + "." +
                ((network >>> 16) & 255) + "." +
                ((network >>> 8) & 255) + "." +
                (network & 255);
            Route route = new Route(canonical, prefix);
            if (seen.add(route.toString())) routes.add(route);
            if (routes.size() > 128) {
                throw new IllegalArgumentException("最多允许 128 条路由");
            }
        }
        return routes;
    }

    /** Normalize single addresses to /32, normalize CIDR networks, remove duplicates. */
    public static String normalize(String value) {
        StringBuilder result = new StringBuilder();
        for (Route route : parse(value)) {
            if (result.length() > 0) result.append('\n');
            result.append(route);
        }
        return result.toString();
    }

    /** Called before VpnService.Builder.establish(); never adds exclusion routes. */
    public static void addToVpn(VpnService.Builder builder, String normalized) {
        for (Route route : parse(normalized)) {
            builder.addRoute(route.address, route.prefix);
        }
    }
}
