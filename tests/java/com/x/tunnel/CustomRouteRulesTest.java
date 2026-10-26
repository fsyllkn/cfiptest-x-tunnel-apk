package com.x.tunnel;

import android.net.VpnService;

public final class CustomRouteRulesTest {
    private static void assertEquals(String wanted, String actual) {
        if (!wanted.equals(actual))
            throw new AssertionError("wanted " + wanted + " but got " + actual);
    }

    private static void invalid(String value) {
        try {
            CustomRouteRules.normalize(value);
            throw new AssertionError("Accepted invalid route: " + value);
        } catch (IllegalArgumentException expected) {
            // invalid rules must prevent both save and VPN startup
        }
    }

    public static void main(String[] args) {
        String v = CustomRouteRules.normalize(
            "10.0.0.104, 192.168.50.50/24;\n10.0.0.104/32");
        assertEquals("10.0.0.104/32\n192.168.50.0/24", v);
        assertEquals("", CustomRouteRules.normalize(""));
        assertEquals("10.0.0.104/32", CustomRouteRules.normalize("010.000.000.104"));
        invalid("10.0.0.104:14432");
        invalid("10.0.0.999");
        invalid("10.0.0.1/33");
        invalid("10.0.0.1/0");
        invalid("10.0.0.1/");
        invalid("127.0.0.1");
        invalid("224.0.0.1");
        invalid("10.0.0");
        invalid("example.com");
        invalid("2001:db8::1");
        VpnService.Builder builder = new VpnService.Builder();
        CustomRouteRules.addToVpn(builder, v);
        if (builder.routes.size() != 2)
            throw new AssertionError("VPN did not receive all custom routes");
        assertEquals("10.0.0.104/32", builder.routes.get(0));
        assertEquals("192.168.50.0/24", builder.routes.get(1));
        System.out.println("CustomRouteRules tests passed");
    }
}
