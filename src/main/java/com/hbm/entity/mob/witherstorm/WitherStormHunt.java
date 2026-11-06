package com.hbm.entity.mob.witherstorm;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.entity.EntityLivingBase;

/** Complete local census for tentacle combat, independent of player pursuit and idle flight. */
final class WitherStormHunt {
    static final double RADIUS = 96D;
    final List<EntityLivingBase> living = new ArrayList<>();
    private long nextRefresh;

    @SuppressWarnings("unchecked")
    void refresh(EntityWitherStorm boss) {
        long now = boss.worldObj.getTotalWorldTime();
        if(now < nextRefresh) return;
        nextRefresh = now + 20;
        living.clear();
        List<EntityLivingBase> found = boss.worldObj.getEntitiesWithinAABB(EntityLivingBase.class,
            boss.boundingBox.expand(RADIUS, RADIUS, RADIUS));
        for(EntityLivingBase entity : found) {
            if(!boss.validTarget(entity)) continue;
            if(!WitherStormMath.edible(entity, boss.worldObj)) continue;
            living.add(entity);
        }
    }
}
