package com.hbm.entity.mob.witherstorm;

/** Story Mode anatomy within the existing encounter phases. */
public enum WitherStormStage {
    A, A_TRANSFORMED, B, C_SMALL, C_BIG, C_DAMAGED, D;

    public boolean hasTentacles() { return ordinal() >= B.ordinal(); }
    public boolean hasRaisedTentacle(int slot) { return this == B && (slot == 3 || slot == 6); }
    public boolean hasShallowHeads() { return this == B || this == C_SMALL || this == C_BIG; }
    public boolean hasPosedTentacles() { return this == B || this == C_SMALL || this == C_BIG; }
    public double eyeY() { return hasShallowHeads() ? WitherStormDestroyerPose.EYE_Y : WitherStormMath.EYE_Y; }
    public double eyeZ() { return hasShallowHeads() ? WitherStormDestroyerPose.EYE_Z : WitherStormMath.EYE_Z; }
    public float crownScale() { return this == C_SMALL ? 0.65F : 1F; }
}
