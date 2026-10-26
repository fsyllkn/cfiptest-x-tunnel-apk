package com.x.tunnel;

import java.util.HashSet;
import java.util.Set;

public final class UdpPolicyTest {
    private static void verify(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static Set<Integer> toPorts(String ports) {
        Set<Integer> result = new HashSet<>();
        for (String token : ports.split(",")) {
            result.add(Integer.parseInt(token.trim()));
        }
        return result;
    }

    private static void assertStrict(boolean aiMode) {
        String value = UdpPolicy.forMode("53,1234", aiMode, true);
        Set<Integer> blocked = toPorts(value);
        verify(blocked.size() == 65534, "Strict must block 65534 UDP ports");
        verify(!blocked.contains(53), "DNS/UDP 53 must remain available");
        for (int port = 1; port <= 65535; port++) {
            verify(blocked.contains(port) == (port != 53),
                "Unexpected UDP strict rule at port " + port);
        }
        verify(value == UdpPolicy.forMode("", aiMode, true),
            "Strict list should be shared/cached, not recreated per VPN connect");
    }

    public static void main(String[] args) {
        assertStrict(false);
        assertStrict(true);
        Set<Integer> standard = toPorts(UdpPolicy.forMode("53,443,8443", false, false));
        verify(standard.size() == 3 && standard.contains(53) &&
            standard.contains(443) && standard.contains(8443),
            "Standard/compat should preserve valid user ports plus UDP/443");
        Set<Integer> ai = toPorts(UdpPolicy.forMode("", true, false));
        verify(ai.size() == 1 && ai.contains(443), "AI compat blocks QUIC UDP/443");
        verify(toPorts(UdpPolicy.forMode("", false, false)).contains(443),
            "Standard compat blocks QUIC UDP/443");
        System.out.println("UdpPolicy tests passed: strict=all UDP except DNS/53");
    }
}
