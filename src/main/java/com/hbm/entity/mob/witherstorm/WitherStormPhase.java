package com.hbm.entity.mob.witherstorm;

import com.hbm.config.WitherStormConfig;

public enum WitherStormPhase {
    COMMAND_BLOCK, DESTROYER, DEVOURER, THUNDERSTORM;

    public static WitherStormPhase forGrowth(int mass) {
        // Evolution changes state on the same entity: mass and identity never reset.
        if(mass > WitherStormConfig.thunder) return THUNDERSTORM;
        if(mass > WitherStormConfig.devourer) return DEVOURER;
        if(mass >= WitherStormConfig.transform) return DESTROYER;
        return COMMAND_BLOCK;
    }
}
