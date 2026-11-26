package com.hbm.world.squirrelstapler;

import com.hbm.config.SquirrelConfig;
import com.hbm.entity.mob.squirrel.EntitySquirrel;
import net.minecraft.block.Block;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

public final class StaplerSquirrels {
    private StaplerSquirrels() {}

    public static final class Large extends EntitySquirrel {
        public Large(World world) { super(world); }
        @Override protected float bodyScale() { return 1.6F; }
        @Override protected double maximumHealth(SquirrelConfig.Values settings) { return settings.health + 2D; }
        @Override public boolean isLargeHuntingSquirrel() { return true; }
        @Override public boolean canUseTrees() { return false; }
    }

    public abstract static class Apparition extends EntitySquirrel {
        private EntityPlayer witness;
        private int endAt, pursuitAt, nextHit;
        private boolean seen;
        private double retreatX, retreatZ;

        protected Apparition(World world) {
            super(world);
            experienceValue = 0;
        }

        void begin(EntityPlayer player, int lifetime, int chaseDelay) {
            witness = player;
            endAt = lifetime;
            pursuitAt = chaseDelay;
            retreatX = posX + (posX - player.posX) * 0.45D;
            retreatZ = posZ + (posZ - player.posZ) * 0.45D;
        }

        @Override protected void updateAITasks() {
            if(witness == null || !witness.isEntityAlive() || witness.worldObj != worldObj || ticksExisted >= endAt
                    || getDistanceSqToEntity(witness) > 72D * 72D) {
                setDead();
                return;
            }
            if(ticksExisted % 10 != 0) return;
            boolean loaded = canSeeWitness();
            boolean clear = loaded && witness.canEntityBeSeen(this);
            double distance = getDistanceSqToEntity(witness);
            getLookHelper().setLookPositionWithEntity(witness, 35F, 25F);
            if(this instanceof Ghost && seen && !clear && (ticksExisted < pursuitAt || distance > 10D * 10D)) {
                setDead();
                return;
            }
            seen |= clear;
            if(this instanceof Silhouette || this instanceof Ghost && ticksExisted < pursuitAt) {
                if(seen && canNavigate(retreatX, retreatZ)) {
                    int x = MathHelper.floor_double(retreatX), z = MathHelper.floor_double(retreatZ);
                    int y = StaplerForest.ground(worldObj, x, z);
                    if(y > 0) getNavigator().tryMoveToXYZ(retreatX, y, retreatZ, 0.9D);
                }
                return;
            }
            boolean stalking = this instanceof Bear && (ticksExisted < pursuitAt || (ticksExisted - pursuitAt) % 160 >= 90);
            if(stalking && distance < 13D * 13D) {
                if(!getNavigator().noPath() || !clear) return;
                if(ticksExisted % 30 == 0 && seekCover(distance)) return;
                if(canNavigate(retreatX, retreatZ)) {
                    int y = StaplerForest.ground(worldObj, MathHelper.floor_double(retreatX), MathHelper.floor_double(retreatZ));
                    if(y > 0) getNavigator().tryMoveToXYZ(retreatX, y, retreatZ, 0.85D);
                }
                return;
            }
            if(ticksExisted == pursuitAt && this instanceof Bear) playSound("hbm:stapler.growl", 1.6F, 0.8F);
            if(loaded) {
                double step = Math.min(1D, 6D / Math.max(1D, Math.sqrt(distance)));
                double x = posX + (witness.posX - posX) * step, z = posZ + (witness.posZ - posZ) * step;
                if(canNavigate(x, z)) {
                    int y = StaplerForest.ground(worldObj, MathHelper.floor_double(x), MathHelper.floor_double(z));
                    if(y > 0) getNavigator().tryMoveToXYZ(x, y, z, stalking ? 0.85D : 1.65D);
                }
            }
            if(!stalking && clear && distance < (this instanceof Bear ? 8D : 2D) && ticksExisted >= nextHit) {
                nextHit = ticksExisted + 30;
                witness.attackEntityFrom(DamageSource.causeMobDamage(this), this instanceof Bear ? 6F : 2F);
            }
        }

        private boolean seekCover(double distanceSquared) {
            double distance = Math.sqrt(distanceSquared);
            if(distance < 0.5D) return false;
            double awayX = (posX - witness.posX) / distance, awayZ = (posZ - witness.posZ) / distance;
            Vec3 eye = Vec3.createVectorHelper(witness.posX, witness.posY + witness.getEyeHeight(), witness.posZ);
            for(int side = -1; side <= 1; side += 2) for(int offset = 2; offset <= 5; offset += 3) {
                double x = posX + awayX * 2D + awayZ * side * offset;
                double z = posZ + awayZ * 2D - awayX * side * offset;
                if(!canNavigate(x, z)) continue;
                int y = StaplerForest.ground(worldObj, MathHelper.floor_double(x), MathHelper.floor_double(z));
                if(y < 0) continue;
                MovingObjectPosition cover = worldObj.rayTraceBlocks(eye, Vec3.createVectorHelper(x, y + height * 0.8D, z));
                if(cover == null) continue;
                Block block = worldObj.getBlock(cover.blockX, cover.blockY, cover.blockZ);
                if((block.isLeaves(worldObj, cover.blockX, cover.blockY, cover.blockZ)
                        || block.isWood(worldObj, cover.blockX, cover.blockY, cover.blockZ))
                        && getNavigator().tryMoveToXYZ(x, y, z, 0.95D)) return true;
            }
            return false;
        }

        private boolean canSeeWitness() {
            int ax = MathHelper.floor_double(posX), az = MathHelper.floor_double(posZ);
            int bx = MathHelper.floor_double(witness.posX), bz = MathHelper.floor_double(witness.posZ);
            return worldObj.checkChunksExist(Math.min(ax, bx), 0, Math.min(az, bz), Math.max(ax, bx), 0, Math.max(az, bz));
        }

        private boolean canNavigate(double x, double z) {
            int ax = MathHelper.floor_double(posX), az = MathHelper.floor_double(posZ);
            int bx = MathHelper.floor_double(x), bz = MathHelper.floor_double(z);
            int radius = 17;
            return worldObj.blockExists(bx, 64, bz)
                    && worldObj.checkChunksExist(ax - radius, 0, az - radius, ax + radius, 0, az + radius);
        }

        @Override protected String getLivingSound() { return null; }
        @Override protected void dropFewItems(boolean player, int looting) {}
        @Override protected void dropEquipment(boolean player, int looting) {}
        @Override public boolean writeToNBTOptional(NBTTagCompound tag) { return false; }
        @Override public boolean writeMountToNBT(NBTTagCompound tag) { return false; }
        @Override public boolean allowLeashing() { return false; }
        @Override public boolean isCreatureType(EnumCreatureType type, boolean forSpawnCount) { return false; }
    }

    public static final class Ghost extends Apparition {
        public Ghost(World world) { super(world); }
    }

    public static final class Bear extends Apparition {
        public Bear(World world) { super(world); }
        @Override protected float bodyScale() { return 2.8F; }
        @Override protected double maximumHealth(SquirrelConfig.Values settings) { return 40D; }
    }

    public static final class Silhouette extends Apparition {
        public Silhouette(World world) { super(world); }
        @Override protected void entityInit() {
            super.entityInit();
            dataWatcher.addObject(23, (byte)0);
        }
        void setShadow() { dataWatcher.updateObject(23, (byte)1); }
        public boolean isShadow() { return dataWatcher.getWatchableObjectByte(23) != 0; }
        @Override protected float bodyScale() { return 1.8F; }
        @Override public boolean attackEntityFrom(DamageSource source, float amount) { return false; }
    }
}
