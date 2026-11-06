package com.hbm.entity.mob.witherstorm;

/** Story Mode anatomy within the existing encounter phases. */
public enum WitherStormStage {
    A, A_TRANSFORMED, B, C_SMALL, C_BIG, C_DAMAGED, D;

    public int sectionRadius() {
        switch(this) {
            case A: return 1;
            case A_TRANSFORMED: return 2;
            case B: return 3;
            case C_SMALL: return 4;
            case C_BIG: case C_DAMAGED: return 5;
            default: return 7;
        }
    }
    public int consumptionBudget() {
        switch(this) {
            case A: return 32;
            case A_TRANSFORMED: return 64;
            case B: return 256;
            case C_SMALL: return 1024;
            case C_BIG: case C_DAMAGED: return 2048;
            default: return 4096;
        }
    }
    public boolean hasTentacles() { return ordinal() >= B.ordinal(); }
    public boolean hasRaisedTentacle(int slot) { return this == B && (slot == 3 || slot == 6); }
    public boolean hasShallowHeads() { return this == B || this == C_SMALL || this == C_BIG || this == D; }
    public boolean hasPosedTentacles() { return this == B || this == C_SMALL || this == C_BIG || this == D; }
    public double eyeY() { return hasShallowHeads() ? WitherStormDestroyerPose.EYE_Y : WitherStormMath.EYE_Y; }
    public double eyeZ() { return hasShallowHeads() ? WitherStormDestroyerPose.EYE_Z : WitherStormMath.EYE_Z; }
    public float crownScale() { return this == C_SMALL ? 0.65F : 1F; }
}
