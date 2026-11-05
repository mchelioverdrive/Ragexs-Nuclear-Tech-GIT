package com.hbm.entity.mob.witherstorm;

/** Allocation-free travelling bends. Root displacement is always zero. */
public final class WitherStormTentacleMotion {
    private WitherStormTentacleMotion() {}
    public static void idle(int slot, double t, double time, double weight, double[] out) {
        double phase = slot * 2.399963D;
        double wave = time * (0.055D + (slot % 4) * 0.006D) + phase - t * 3.8D;
        double amplitude = 2.2D * weight * t * t * (0.85D + 0.15D * Math.sin(phase));
        out[0] += amplitude * (Math.sin(wave) + 0.28D * Math.sin(wave * 1.63D - t * 2D));
        out[1] += amplitude * 0.55D * Math.cos(wave - 0.8D);
        out[2] += amplitude * 0.85D * Math.sin(wave + 1.2D);
    }
}
