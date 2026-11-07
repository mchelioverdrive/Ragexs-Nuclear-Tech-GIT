package com.hbm.render.model;

import com.hbm.entity.mob.gemini.EntityOceanGardener;

/** Narrow upper resident on a tall leg base, all measured from the feet. */
public final class ModelOceanGardener extends ModelGardener {
    private static final double BASE = EntityOceanGardener.BODY_OFFSET;
    private static final double[][] BODY = {{BASE, 0.15}, {BASE + 0.6, 0.7}, {BASE + 1.4, 1.35},
            {BASE + 2.8, 1.6}, {BASE + 4.6, 1.3}, {BASE + 5.9, 0.12}};
    @Override protected double[][] rings() { return BODY; }
    @Override protected double depth() { return 0.85D; }
    @Override protected double eyeHeight() { return BASE + 4.7D; }
    @Override protected double eyeRadius() { return 1.1D; }
}
