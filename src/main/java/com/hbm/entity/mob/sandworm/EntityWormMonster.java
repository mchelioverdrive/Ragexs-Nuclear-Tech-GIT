package com.hbm.entity.mob.sandworm;

import java.util.UUID;
import com.hbm.world.sandworm.WormEncounter;
import com.hbm.world.sandworm.WormEncounters;
import net.minecraft.entity.Entity;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;

/** Encounter membership is explicit: no healing, shielding, or progress from unrelated mobs. */
public abstract class EntityWormMonster extends EntityMob {
    public UUID encounter;
    protected EntityWormMonster(World w) { super(w); func_110163_bv(); experienceValue = 100; }
    public void bind(UUID owner, UUID identity) { encounter = owner; if(identity != null) entityUniqueID = identity; }
    public boolean allied(Entity e) {
        if(!(e instanceof EntityWormMonster)) return false;
        UUID other = ((EntityWormMonster)e).encounter;
        return encounter == null ? other == null : encounter.equals(other);
    }
    public boolean canAffect(EntityPlayer p) {
        if(!p.isEntityAlive() || p.capabilities.isCreativeMode) return false;
        if(encounter == null) return true;
        WormEncounter record = WormEncounters.get(worldObj).find(encounter);
        return record != null && record.contains(p.posX, p.posZ);
    }
    protected EntityPlayer nearbyPlayer(double range) {
        EntityPlayer best = null; double distance = range * range;
        for(Object o : worldObj.playerEntities) {
            EntityPlayer p = (EntityPlayer)o; double d = getDistanceSqToEntity(p);
            if(d < distance && canAffect(p) && canEntityBeSeen(p)) { best = p; distance = d; }
        }
        return best;
    }
    @Override public void onUpdate() {
        if(!worldObj.isRemote) {
            WormEncounters data = WormEncounters.get(worldObj);
            if(!data.track(this)) { setDead(); return; }
            if(encounter != null) {
                WormEncounter e = data.find(encounter);
                boolean finished = e == null || e.retired() || (this instanceof EntityBrain && e.brainDead) || (this instanceof EntityHeart && e.heartDead);
                if(finished && getHealth() > 0) { setDead(); return; }
            }
        }
        super.onUpdate();
    }
    @Override public void onDeath(DamageSource source) {
        super.onDeath(source);
        if(!worldObj.isRemote && encounter != null) WormEncounters.get(worldObj).organDied(this);
    }
    @Override protected boolean canDespawn() { return false; }
    @Override protected boolean isAIEnabled() { return true; }
    @Override public void writeEntityToNBT(NBTTagCompound n) {
        super.writeEntityToNBT(n); if(encounter != null) n.setString("WormEncounter", encounter.toString());
    }
    @Override public void readEntityFromNBT(NBTTagCompound n) {
        super.readEntityFromNBT(n);
        encounter = n.hasKey("WormEncounter") ? UUID.fromString(n.getString("WormEncounter")) : null;
    }
}
