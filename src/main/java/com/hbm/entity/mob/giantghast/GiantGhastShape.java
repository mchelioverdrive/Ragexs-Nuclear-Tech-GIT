package com.hbm.entity.mob.giantghast;

import net.minecraft.util.AxisAlignedBB;

/** Box dimensions in the boss's local coordinates, with the central face toward +Z. */
public enum GiantGhastShape {
    HEAD(-5.5, 6, -5.5, 5.5, 16.25, 5.5),
    LEFT(-8.5, 6.75, -3.75, -5.5, 15.75, 4.5),
    RIGHT(5.5, 6.75, -3.75, 8.5, 15.75, 4.5),
    CORE(-1.41, 16.25, -1.41, 1.41, 16.38, 1.41),
    ROOF_FRONT(-5.5, 16.25, 1.55, 5.5, 17, 5.5),
    ROOF_BACK(-5.5, 16.25, -5.5, 5.5, 17, -1.55),
    ROOF_LEFT(-5.5, 16.25, -1.55, -1.55, 17, 1.55),
    ROOF_RIGHT(1.55, 16.25, -1.55, 5.5, 17, 1.55);

    public final double minX, minY, minZ, maxX, maxY, maxZ;
    final AxisAlignedBB local;

    GiantGhastShape(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
        local = AxisAlignedBB.getBoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
