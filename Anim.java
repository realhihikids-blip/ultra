package com.pvpclient.util;

/** Shared frame timing for all animations. Ticked once per frame from the HUD callback. */
public final class Anim {
    public static double dt = 0.016;
    public static boolean enabled = true;
    public static double speed = 14;
    private static long last = 0;

    private Anim() {}

    public static void tick() {
        long now = System.nanoTime();
        if (last == 0) last = now;
        dt = Math.min(0.1, (now - last) / 1_000_000_000.0);
        last = now;
    }
}
