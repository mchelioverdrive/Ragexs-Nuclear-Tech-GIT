package com.hbm.entity.mob.gemini;

import com.hbm.world.gemini.GardenerNests;
import com.hbm.world.gemini.GeminiSpawning;
import com.hbm.world.gemini.OceanGardenerSpawning;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.IEntityLivingData;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

/** Feet own the entity origin; the narrow body stands on a 26-block articulated base. */
public final class EntityOceanGardener extends EntityGardener {
    public static final double BODY_OFFSET = 26D;
    private boolean legacyOrigin;
    public EntityOceanGardener(World world) { super(world); setSize(3.6F, 32F); experienceValue = 120; }
    @Override public boolean isOcean() { return true; }
    @Override public double bodyOffset() { return BODY_OFFSET; }
    @Override public float getEyeHeight() { return (float) BODY_OFFSET + 4.7F; }
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
        return GeminiSpawning.manualAdmission(this) || OceanGardenerSpawning.active(worldObj);
    }
    @Override protected boolean inTerritory(Entity target) {
        double dx = target.posX - nestX, dz = target.posZ - nestZ;
        return dx * dx + dz * dz <= territoryRadius() * territoryRadius()
                && Math.abs(target.posY - nestY - BODY_OFFSET) < 32D;
    }
    private boolean water(double x, double y, double z) {
        int bx = MathHelper.floor_double(x), by = MathHelper.floor_double(y), bz = MathHelper.floor_double(z);
        return by > 1 && by < 255 && worldObj.blockExists(bx, by, bz)
                && worldObj.getBlock(bx, by, bz).getMaterial() == Material.water;
    }
    private boolean swimming() { return water(posX, boundingBox.minY + 0.5D, posZ); }
    @Override protected double travelVertical(double goalY, double speed) {
        return swimming() ? super.travelVertical(goalY, speed) : 0;
    }
    @Override protected void moveGardener(double x, double y, double z) {
        // The shared steering bypasses ordinary entity physics, so explicitly settle/step on land.
        super.moveGardener(x, swimming() ? y : -0.15D, z);
    }
    @Override protected boolean canTravel(double x, double y, double z) {
        AxisAlignedBB next = boundingBox.getOffsetBoundingBox(x, y, z);
        if(!GardenerNests.loaded(worldObj, visualBounds().addCoord(x, y, z).expand(1, 0, 1))
                || next.minY < 1 || next.maxY > 255 || Math.abs(next.minY - nestY) > 4D) return false;
        double step = 0;
        if(!worldObj.getCollidingBoundingBoxes(this, next).isEmpty()) {
            if(swimming()) return false;
            step = 1D;
            next = next.getOffsetBoundingBox(0, step, 0);
            if(!worldObj.getCollidingBoundingBoxes(this, next).isEmpty()) return false;
        }
        // Water supports swimming; solid ground supports walking. Do not walk into ordinary drops.
        for(int corner = 0; corner < 4; corner++) {
            int bx = MathHelper.floor_double((corner % 2 == 0 ? next.minX : next.maxX) + (corner % 2 == 0 ? 0.5D : -0.5D));
            int bz = MathHelper.floor_double((corner < 2 ? next.minZ : next.maxZ) + (corner < 2 ? 0.5D : -0.5D));
            int by = MathHelper.floor_double(next.minY);
            if(!water(bx, next.minY + 0.5D, bz)
                    && !worldObj.getBlock(bx, by - 1, bz).getMaterial().blocksMovement()
                    && !worldObj.getBlock(bx, by - 2, bz).getMaterial().blocksMovement()) return false;
        }
        // Continuous solid-terrain probes cover every segment; air no longer blocks the skeleton.
        for(int arm = 0; arm < GardenerPose.ARMS; arm++) for(int joint = 0; joint < GardenerPose.SEGMENTS; joint++) {
            Vec3 a = OceanGardenerPose.point(this, arm, joint, 1).addVector(x, y + step, z);
            Vec3 b = OceanGardenerPose.point(this, arm, joint + 1, 1).addVector(x, y + step, z);
            if(worldObj.func_147447_a(a, b, false, true, false) != null) return false;
        }
        return true;
    }
    @Override public void writeEntityToNBT(NBTTagCompound nbt) {
        super.writeEntityToNBT(nbt);
        nbt.setBoolean("OceanFeetOrigin", !legacyOrigin);
    }
    @Override public void readEntityFromNBT(NBTTagCompound nbt) {
        super.readEntityFromNBT(nbt);
        legacyOrigin = !nbt.getBoolean("OceanFeetOrigin");
    }
    @Override public void onUpdate() {
        // A chunk's entities can deserialize before its terrain is available through the provider.
        if(!worldObj.isRemote && legacyOrigin) {
            double feetY = posY - BODY_OFFSET;
            // Old manual eggs placed the upper body on the ground. Keep those residents above it.
            int x = MathHelper.floor_double(posX), z = MathHelper.floor_double(posZ);
            if(worldObj.blockExists(x, MathHelper.floor_double(posY), z)) {
                for(int y = MathHelper.floor_double(posY) - 1; y >= Math.max(0, MathHelper.floor_double(feetY)); y--)
                    if(worldObj.getBlock(x, y, z).getMaterial().blocksMovement()) { feetY = Math.max(feetY, y + 1D); break; }
            }
            nestY -= posY - feetY;
            setPosition(posX, feetY, posZ);
            prevPosY = lastTickPosY = feetY;
            legacyOrigin = false;
        }
        super.onUpdate();
    }
    @Override public IEntityLivingData onSpawnWithEgg(IEntityLivingData data) {
        IEntityLivingData result = super.onSpawnWithEgg(data);
        int x = MathHelper.floor_double(posX), z = MathHelper.floor_double(posZ);
        int floor = OceanGardenerSpawning.seabed(worldObj, x, z);
        if(!GeminiSpawning.isManual(this) && floor >= 0) anchor(x + 0.5D, floor, z + 0.5D);
        return result;
    }
}
