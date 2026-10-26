#!/usr/bin/env python3
"""Restore the private IPv4 TUN interface used by the tested privacy baseline.

The exact numeric value from the final 2026-10-12 APK is not present in this
repository; this uses a documented /32 RFC1918 address instead of the earlier
198.18.0.1 benchmarking/FakeIP-looking TUN address. Leave the mapped remote DNS
at 198.18.0.2 because it belongs to the HEV mapdns flow and is not TUN's IP.
"""
from pathlib import Path
import sys

TUN_IPV4 = "10.254.253.1"

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_stable_tun.py <upstream-repository-root>")
path = (Path(sys.argv[1]).resolve() /
        "x-tunnel-android-gui-src/src/main/java/com/x/tunnel/Preferences.java")
original = path.read_text(encoding="utf-8")
old = '''	public String getTunnelIpv4Address() {
		return "198.18.0.1";
	}'''
new = '''	public String getTunnelIpv4Address() {
		// Private /32 TUN address, distinct from HEV's virtual mapdns endpoint.
		return "10.254.253.1";
	}'''
if original.count(old) != 1:
    raise RuntimeError("upstream TUN address marker mismatch; refuse silent fallback")
path.write_text(original.replace(old, new, 1), encoding="utf-8")
print("Private IPv4 TUN: " + TUN_IPV4 + " (original reference address not available)")
