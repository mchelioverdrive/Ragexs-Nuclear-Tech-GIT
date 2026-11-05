package com.hbm.entity.mob.witherstorm;

/** Story Mode anatomy within the existing encounter phases. */
public enum WitherStormStage {
    A, A_TRANSFORMED, B, C_SMALL, C_BIG, C_DAMAGED, D;

    public boolean hasTentacles() { return ordinal() >= B.ordinal(); }
    public boolean hasRaisedTentacle(int slot) { return this == B && (slot == 3 || slot == 6); }
    public float crownScale() { return this == C_SMALL ? 0.65F : 1F; }
}
