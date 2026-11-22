package com.pvpclient.util;

/** Exponentially smoothed value; call update() exactly once per frame. */
public final class Smooth {
    private double value, target;

    public Smooth(double initial) { value = target = initial; }

    public void to(double t) { target = t; }
    public void snap(double v) { value = target = v; }
    public double get() { return value; }

    public double update() {
        if (!Anim.enabled) {
            value = target;
        } else {
            value += (target - value) * (1.0 - Math.exp(-Anim.dt * Anim.speed));
            if (Math.abs(target - value) < 0.0005) value = target;
        }
        return value;
    }
}
