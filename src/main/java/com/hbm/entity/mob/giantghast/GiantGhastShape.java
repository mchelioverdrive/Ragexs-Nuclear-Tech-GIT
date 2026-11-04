package com.hbm.entity.mob.giantghast;

import net.minecraft.util.AxisAlignedBB;

/** Box dimensions in the boss's local coordinates, with the central face toward +Z. */
public enum GiantGhastShape {
    HEAD(-5, 4, -5.5, 5, 13, 5.5),
    LEFT(-7.5, 5, -3.5, -4.75, 11.5, 4.5),
    RIGHT(4.75, 5, -3.5, 7.5, 11.5, 4.5),
    CORE(-1.25, 13, -1.25, 1.25, 13.65, 1.25);

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
