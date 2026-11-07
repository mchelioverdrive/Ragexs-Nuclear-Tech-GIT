package com.hbm.entity.mob.gemini;

import com.hbm.world.gemini.GardenerNests;
import com.hbm.world.gemini.GeminiSpawning;
import com.hbm.world.gemini.OceanGardenerSpawning;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.IEntityLivingData;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

/** The origin is the floating upper body; the skeleton hangs 26 blocks below it. */
public final class EntityOceanGardener extends EntityGardener {
    public EntityOceanGardener(World world) { super(world); setSize(3.6F, 6F); experienceValue = 120; }
    @Override public boolean isOcean() { return true; }
    @Override public float getEyeHeight() { return 4.7F; }
    @Override protected double attackReach() { return 31D; }
    @Override protected double territoryRadius() { return 40D; }
    @Override protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(520D);
        getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(0.055D);
        getEntityAttribute(SharedMonsterAttributes.followRange).setBaseValue(36D);
    }
    @Override public boolean canBreatheUnderwater() { return true; }
    @Override protected boolean environmentActive() {
        return (GeminiSpawning.manualAdmission(this) || OceanGardenerSpawning.active(worldObj))
                && water(posX, posY + 5.8D, posZ) && water(posX, posY, posZ);
    }
    @Override protected boolean suitablePrey(Entity target) {
        boolean boat = isVehicle(target);
        if(!super.suitablePrey(target)) return false;
        return water(target.posX, target.boundingBox.minY, target.posZ)
                || boat && water(target.posX, target.boundingBox.minY - 1D, target.posZ);
    }
    private boolean water(double x, double y, double z) {
        int bx = MathHelper.floor_double(x), by = MathHelper.floor_double(y), bz = MathHelper.floor_double(z);
        return by > 1 && by < 255 && worldObj.blockExists(bx, by, bz)
                && worldObj.getBlock(bx, by, bz).getMaterial() == Material.water;
    }
    @Override protected boolean canTravel(double x, double y, double z) {
        AxisAlignedBB next = boundingBox.getOffsetBoundingBox(x, y, z);
        if(!GardenerNests.loaded(worldObj, visualBounds().addCoord(x, y, z).expand(1, 0, 1))
                || Math.abs(next.minY - nestY) > 4D || !worldObj.getCollidingBoundingBoxes(this, next).isEmpty()) return false;
        for(int corner = 0; corner < 8; corner++)
            if(!water((corner & 1) == 0 ? next.minX : next.maxX, (corner & 2) == 0 ? next.minY : next.maxY,
                    (corner & 4) == 0 ? next.minZ : next.maxZ)) return false;
        // Probe the hanging skeleton as well as the body; never drag it through the seabed or a wall.
        for(int arm = 0; arm < GardenerPose.ARMS; arm++) for(int joint = 0; joint < 9; joint++) {
            Vec3 a = OceanGardenerPose.point(this, arm, joint / 2, 1);
            if((joint & 1) != 0) {
                Vec3 b = OceanGardenerPose.point(this, arm, joint / 2 + 1, 1);
                a = Vec3.createVectorHelper((a.xCoord + b.xCoord) * 0.5D, (a.yCoord + b.yCoord) * 0.5D, (a.zCoord + b.zCoord) * 0.5D);
            }
            if(!water(a.xCoord + x, a.yCoord + y, a.zCoord + z)) return false;
        }
        return true;
    }
    @Override public IEntityLivingData onSpawnWithEgg(IEntityLivingData data) {
        IEntityLivingData result = super.onSpawnWithEgg(data);
        int x = MathHelper.floor_double(posX), z = MathHelper.floor_double(posZ);
        int floor = OceanGardenerSpawning.seabed(worldObj, x, z);
        if(floor >= 0) anchor(x + 0.5D, floor + 26D, z + 0.5D);
        return result;
    }
}
