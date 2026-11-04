package com.hbm.entity.mob.giantenderman;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.hbm.entity.mob.giantenderman.GiantEndermanTerrain.Cell;

import cpw.mods.fml.common.registry.IEntityAdditionalSpawnData;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

/** A single tracked entity carries a formation, regardless of the number of blocks it contains. */
public final class EntityEndermanTerrain extends Entity implements IEntityAdditionalSpawnData {
    public static final int MAX_CELLS = 64;
    public List<Cell> cells = Collections.emptyList();
    private EntityGiantEnderman owner;
    private int flight;
    private double radius = 0.5;

    public EntityEndermanTerrain(World world) {
        super(world);
        noClip = true;
        ignoreFrustumCheck = true;
    }

    EntityEndermanTerrain(EntityGiantEnderman owner, List<Cell> cells, Vec3 start, boolean held) {
        this(owner.worldObj);
        this.owner = owner;
        this.cells = cells;
        updateRadius();
        setPosition(start.xCoord, start.yCoord, start.zCoord);
        dataWatcher.updateObject(20, held ? owner.getEntityId() : -1);
    }

    @Override protected void entityInit() { dataWatcher.addObject(20, -1); }

    public EntityGiantEnderman carrier() {
        int id = dataWatcher.getWatchableObjectInt(20);
        if (id < 0) return null;
        Entity entity = worldObj.getEntityByID(id);
        return entity instanceof EntityGiantEnderman ? (EntityGiantEnderman)entity : null;
    }

    void launch(Vec3 goal, double spread) {
        dataWatcher.updateObject(20, -1);
        double dx = goal.xCoord - posX, dz = goal.zCoord - posZ;
        double distance = Math.hypot(dx, dz);
        double time = Math.max(12, Math.min(36, distance / 0.95));
        motionX = (dx + dz * spread) / time;
        motionY = (goal.yCoord - posY) / time + 0.0225 * (time - 1);
        motionZ = (dz - dx * spread) / time;
        velocityChanged = true;
    }

    @Override public void onUpdate() {
        super.onUpdate();
        if (dataWatcher.getWatchableObjectInt(20) >= 0) {
            EntityGiantEnderman carrier = carrier();
            if (carrier != null) {
                Vec3 hand = carrier.handPoint();
                setPosition(hand.xCoord, hand.yCoord, hand.zCoord);
            }
            if (!worldObj.isRemote && (carrier == null || !carrier.isEntityAlive() || carrier.heldTerrain() != this)) setDead();
            return;
        }
        if (worldObj.isRemote) return;
        if (++flight > 80 || owner == null || !owner.isEntityAlive() || !owner.addedToChunk || owner.worldObj != worldObj) {
            setDead();
            return;
        }
        Vec3 start = Vec3.createVectorHelper(posX, posY, posZ);
        Vec3 end = start.addVector(motionX, motionY, motionZ);
        AxisAlignedBB swept = AxisAlignedBB.getBoundingBox(posX-radius, posY-radius, posZ-radius, posX+radius, posY+radius, posZ+radius)
            .addCoord(motionX, motionY, motionZ);
        if (end.yCoord < 0 || end.yCoord >= worldObj.getActualHeight() || !GiantEndermanTerrain.loaded(worldObj, swept.expand(2, 0, 2))) {
            setDead();
            return;
        }

        Vec3 contact = null;
        EntityLivingBase victim = null;
        double closest = start.squareDistanceTo(end) + 0.001;
        for (Object shape : worldObj.func_147461_a(swept)) {
            Vec3 hit = contact((AxisAlignedBB)shape, start, end);
            if (hit != null && start.squareDistanceTo(hit) < closest) {
                contact = hit;
                closest = start.squareDistanceTo(hit);
            }
        }
        for (Object found : worldObj.getEntitiesWithinAABBExcludingEntity(this, swept.expand(1, 1, 1))) {
            Entity entity = (Entity)found;
            EntityLivingBase living = EntityGiantEnderman.livingOwner(entity);
            if (!owner.canHarm(living) || entity == living && EntityGiantEnderman.usesPartHitboxes(living)) continue;
            Vec3 hit = contact(entity.boundingBox, start, end);
            if (hit != null && start.squareDistanceTo(hit) < closest) {
                contact = hit;
                closest = start.squareDistanceTo(hit);
                victim = living;
            }
        }
        if (contact != null) {
            owner.terrainImpact(contact, victim, Math.min(4, 1 + cells.size() / 10));
            setDead();
        } else {
            setPosition(end.xCoord, end.yCoord, end.zCoord);
            motionY -= 0.045;
        }
    }

    private Vec3 contact(AxisAlignedBB box, Vec3 start, Vec3 end) {
        AxisAlignedBB expanded = box.expand(radius, radius, radius);
        if (expanded.isVecInside(start)) return start;
        MovingObjectPosition hit = expanded.calculateIntercept(start, end);
        return hit == null ? null : hit.hitVec;
    }

    private void updateRadius() {
        double extent = 0;
        for (Cell cell : cells) extent = Math.max(extent, Math.max(Math.abs(cell.x), Math.max(Math.abs(cell.y), Math.abs(cell.z))));
        radius = extent + 0.5;
        setSize((float)(radius * 2), (float)(radius * 2));
    }

    @Override public void writeSpawnData(ByteBuf buffer) {
        buffer.writeByte(cells.size());
        for (Cell cell : cells) {
            buffer.writeByte(cell.x).writeByte(cell.y).writeByte(cell.z).writeShort(cell.id).writeByte(cell.meta);
        }
    }

    @Override public void readSpawnData(ByteBuf buffer) {
        int count = Math.min(MAX_CELLS, buffer.readUnsignedByte());
        List<Cell> received = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            received.add(new Cell(buffer.readByte(), buffer.readByte(), buffer.readByte(), buffer.readUnsignedShort(), buffer.readUnsignedByte()));
        }
        cells = received;
        updateRadius();
    }

    @Override public boolean isInRangeToRenderDist(double distance) { return distance < 192 * 192; }
    @Override public boolean writeToNBTOptional(NBTTagCompound tag) { return false; }
    @Override protected void writeEntityToNBT(NBTTagCompound tag) { }
    @Override protected void readEntityFromNBT(NBTTagCompound tag) { setDead(); }
}
