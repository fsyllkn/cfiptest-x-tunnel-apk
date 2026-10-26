package com.x.tunnel;

/**
 * Translate the UI's WebRTC policy to the original X-Tunnel UDP port-list API.
 *
 * The bundled, pinned libgojni accepts individual comma-delimited UDP ports,
 * NOT a wildcard or a numeric range. Strict therefore enumerates every valid
 * destination UDP port except 53 (DNS), and caches the resulting string.
 */
public final class UdpPolicy {
    private UdpPolicy() {}

    private static final class StrictPorts {
        static final String VALUE = makeStrictPorts();

        private static String makeStrictPorts() {
            StringBuilder out = new StringBuilder(390000);
            for (int port = 1; port <= 65535; ++port) {
                if (port == 53) continue;
                if (out.length() > 0) out.append(',');
                out.append(port);
            }
            return out.toString();
        }
    }

    public static String forMode(String userPorts, boolean aiStable, boolean strict) {
        if (strict) return StrictPorts.VALUE;
        // Standard + compatible mode must still block QUIC/UDP 443.
        java.util.LinkedHashSet<Integer> blocked = new java.util.LinkedHashSet<>();
        if (userPorts != null) {
            for (String token : userPorts.split(",")) {
                token = token.trim();
                if (token.isEmpty()) continue;
                try {
                    int port = Integer.parseInt(token);
                    if (port >= 1 && port <= 65535) blocked.add(port);
                } catch (NumberFormatException ignored) {
                    // The native core ignores invalid entries, so preserve that policy.
                }
            }
        }
        blocked.add(443);
        StringBuilder out = new StringBuilder();
        for (int port : blocked) {
            if (out.length() > 0) out.append(',');
            out.append(port);
        }
        return out.toString();
    }
}
