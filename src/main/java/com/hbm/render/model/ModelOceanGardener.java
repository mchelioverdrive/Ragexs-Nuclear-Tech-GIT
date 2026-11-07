package com.hbm.render.model;

/** Narrow upper resident, unlike the broad underground carapace. Hanging geometry lives in OceanGardenerPose. */
public final class ModelOceanGardener extends ModelGardener {
    private static final double[][] BODY = {{0.0, 0.15}, {0.6, 0.7}, {1.4, 1.35}, {2.8, 1.6}, {4.6, 1.3}, {5.9, 0.12}};
    @Override protected double[][] rings() { return BODY; }
    @Override protected double depth() { return 0.85D; }
    @Override protected double eyeHeight() { return 4.7D; }
    @Override protected double eyeRadius() { return 1.1D; }
}
