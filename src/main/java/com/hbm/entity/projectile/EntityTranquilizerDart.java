package com.hbm.entity.projectile;

import com.hbm.handler.restraint.Restraints;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;

/** Uses RNT's existing throwable collision, tracking and persistence lifecycle. No pickup/dose duplication. */
public class EntityTranquilizerDart extends EntityThrowableNT {
    public EntityTranquilizerDart(World world) { super(world); }
    public EntityTranquilizerDart(World world, EntityLivingBase thrower) { super(world, thrower); }
    @Override protected float throwForce() { return 2F; }
    @Override public void onUpdate() { super.onUpdate(); if(!worldObj.isRemote && ticksExisted > 200) setDead(); }
    @Override protected void onImpact(MovingObjectPosition hit) {
        if(worldObj.isRemote) return;
        if(hit.entityHit instanceof EntityLivingBase && (getThrower() == null || !Restraints.blocked(getThrower())))
            Restraints.sedate((EntityLivingBase) hit.entityHit);
        worldObj.playSoundAtEntity(this, "random.click", 0.3F, 1.3F);
        setDead();
    }
}
