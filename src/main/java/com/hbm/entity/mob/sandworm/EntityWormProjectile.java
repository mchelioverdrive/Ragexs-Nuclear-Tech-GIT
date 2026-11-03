package com.hbm.entity.mob.sandworm;

import java.util.UUID;
import com.hbm.config.SandwormConfig;
import com.hbm.world.sandworm.WormContent;
import com.hbm.world.sandworm.WormEncounter;
import com.hbm.world.sandworm.WormEncounters;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityThrowable;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;

/** Both attacks use vanilla swept projectile collisions and saved encounter ownership. */
public final class EntityWormProjectile extends EntityThrowable {
    private UUID encounter;
    private int age;
    public EntityWormProjectile(World w) { super(w); setSize(0.4F, 0.4F); }
    private EntityWormProjectile(EntityWormMonster shooter, boolean blood) {
        super(shooter.worldObj, shooter); encounter = shooter.encounter;
        dataWatcher.updateObject(20, (byte)(blood ? 1 : 0));
        setPosition(shooter.posX, shooter.posY + (blood ? 3 : 1), shooter.posZ);
    }
    @Override protected void entityInit() { super.entityInit(); dataWatcher.addObject(20, (byte)0); }
    public boolean blood() { return dataWatcher.getWatchableObjectByte(20) != 0; }
    public UUID encounter() { return encounter; }
    public static void energy(EntityBrain brain, double angle) {
        EntityWormProjectile p = new EntityWormProjectile(brain, false);
        p.motionX = Math.sin(angle) * 0.05; p.motionZ = Math.cos(angle) * 0.05; p.motionY = 0;
        p.setPosition(p.posX + Math.sin(angle) * 2.5, p.posY, p.posZ + Math.cos(angle) * 2.5);
        brain.worldObj.spawnEntityInWorld(p);
    }
    public static void blood(EntityHeart heart) {
        EntityWormProjectile p = new EntityWormProjectile(heart, true);
        double angle = heart.getRNG().nextDouble() * Math.PI * 2;
        p.motionX = Math.sin(angle) * 0.35; p.motionZ = Math.cos(angle) * 0.35; p.motionY = 0.3;
        heart.worldObj.spawnEntityInWorld(p);
    }
    @Override protected float getGravityVelocity() { return blood() ? 0.03F : 0; }
    @Override public void onUpdate() {
        if(!worldObj.isRemote) {
            WormEncounter e = encounter == null ? null : WormEncounters.get(worldObj).find(encounter);
            if(++age > (blood() ? 100 : 40) || (encounter != null && (e == null || e.retired()))) { setDead(); return; }
            if(!worldObj.getChunkProvider().chunkExists(MathHelper.floor_double(posX + motionX) >> 4, MathHelper.floor_double(posZ + motionZ) >> 4)) { setDead(); return; }
            if(!blood()) {
                double speed = Math.hypot(motionX, motionZ);
                if(speed > 0) { motionX += motionX / speed * 0.05; motionZ += motionZ / speed * 0.05; }
            }
        }
        super.onUpdate();
    }
    @Override protected void onImpact(MovingObjectPosition hit) {
        if(worldObj.isRemote) return;
        if(hit.entityHit != null) {
            // Reference blood blobs pass through entities and defend through the temporary blood pool on landing.
            if(blood()) return;
            if(!(hit.entityHit instanceof EntityPlayer)) return;
            EntityPlayer p = (EntityPlayer)hit.entityHit;
            if(p.capabilities.isCreativeMode) return;
            p.attackEntityFrom(DamageSource.causeIndirectMagicDamage(this, getThrower()), SandwormConfig.brainDamage);
        } else if(blood() && worldObj.getGameRules().getGameRuleBooleanValue("mobGriefing")) {
            int x = hit.blockX, y = hit.blockY + 1, z = hit.blockZ;
            WormEncounter e = encounter == null ? null : WormEncounters.get(worldObj).find(encounter);
            if((e == null || e.contains(x, z)) && y > 0 && y < 255 && worldObj.isAirBlock(x, y, z)) worldObj.setBlock(x, y, z, WormContent.bloodSplash);
        }
        setDead();
    }
    @Override public void writeEntityToNBT(NBTTagCompound n) { super.writeEntityToNBT(n); if(encounter != null) n.setString("Encounter", encounter.toString()); n.setBoolean("Blood", blood()); n.setInteger("Age", age); }
    @Override public void readEntityFromNBT(NBTTagCompound n) { super.readEntityFromNBT(n); encounter = n.hasKey("Encounter") ? UUID.fromString(n.getString("Encounter")) : null; dataWatcher.updateObject(20, (byte)(n.getBoolean("Blood") ? 1 : 0)); age = n.getInteger("Age"); }
}
