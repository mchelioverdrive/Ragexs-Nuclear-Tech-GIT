package com.hbm.tileentity.machine;

import java.util.List;
import java.util.UUID;
import api.hbm.energymk2.EnergyUnits;
import api.hbm.energymk2.IEnergyReceiverMK2;
import com.hbm.blocks.machine.BlockRestraintDevice;
import com.hbm.blocks.machine.BlockRestraintDevice.Kind;
import com.hbm.config.RestraintConfig;
import com.hbm.handler.restraint.RestraintState;
import com.hbm.handler.restraint.Restraints;
import com.hbm.items.ModItems;
import com.hbm.tileentity.TileEntityMachineBase;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraftforge.common.util.ForgeDirection;

/** Shared occupancy protocol. The entity UUID and per-placement token must agree on both ends. */
public class TileEntityRestraintDevice extends TileEntityMachineBase implements IEnergyReceiverMK2 {
    public UUID token = UUID.randomUUID(), captive;
    public boolean active, locked;
    public int temperature = 20, burnTicks, cycle;
    public long energyQuanta;
    public float captiveHeight = 1.8F;
    private int missingTicks, cachedEntity = -1;
    private UUID armedBy;
    private long armedUntil;
    private boolean armedSneakReleased;
    public float hatch, previousHatch, straps, previousStraps;
    private int clientCycle;
    private long clientCycleAt;
    private boolean clientInitialized;
    private boolean collisionCellsChecked;
    private com.hbm.sound.AudioWrapper dischargeAudio;

    public TileEntityRestraintDevice() { super(1); }
    public Kind kind() {
        return getBlockType() instanceof BlockRestraintDevice ? ((BlockRestraintDevice) getBlockType()).kind : Kind.GUILLOTINE;
    }
    @Override public String getName() { return "container.restraint_device"; }
    @Override public int getInventoryStackLimit() { return kind() == Kind.HANGING ? 1 : 16; }
    @Override public boolean isItemValidForSlot(int slot, ItemStack stack) {
        return slot == 0 && stack != null && (kind() == Kind.HANGING ? stack.getItem() == ModItems.noose : kind() == Kind.TABLE ? stack.getItem() == ModItems.injection_terminal
                : kind() == Kind.BULL && TileEntityFurnace.getItemBurnTime(stack) > 0 && !stack.getItem().hasContainerItem(stack));
    }
    @Override public int[] getAccessibleSlotsFromSide(int side) { return kind() == Kind.BULL || kind() == Kind.TABLE ? new int[] {0} : new int[0]; }
    @Override public boolean canExtractItem(int slot, ItemStack stack, int side) { return !active && (kind() != Kind.HANGING || captive == null); }

    public boolean hasNoose() { return kind() == Kind.HANGING && isItemValidForSlot(0, slots[0]); }

    public boolean owns(EntityLivingBase e) {
        RestraintState s = RestraintState.get(e);
        return e.getUniqueID().equals(captive) && s.occupied() && token.equals(s.deviceToken)
                && s.dimension == worldObj.provider.dimensionId && s.x == xCoord && s.y == yCoord && s.z == zCoord;
    }
    public EntityLivingBase occupant() {
        if(captive == null) return null;
        if(cachedEntity >= 0 && worldObj.getEntityByID(cachedEntity) instanceof EntityLivingBase) {
            EntityLivingBase e = (EntityLivingBase) worldObj.getEntityByID(cachedEntity);
            if(owns(e) && e.isEntityAlive()) return e;
        }
        if(worldObj.getTotalWorldTime() % 20 != 0) return null;
        List<EntityLivingBase> local = worldObj.getEntitiesWithinAABB(EntityLivingBase.class,
                AxisAlignedBB.getBoundingBox(xCoord - 4, yCoord - 2, zCoord - 4, xCoord + 5, yCoord + 5, zCoord + 5));
        for(EntityLivingBase e : local) if(owns(e) && e.isEntityAlive()) { cachedEntity = e.getEntityId(); return e; }
        return null;
    }

    public boolean capture(EntityLivingBase e) {
        if(worldObj.isRemote || !RestraintConfig.enabled || !RestraintConfig.devices || captive != null || active || locked
                || !Restraints.compatible(e) || e.worldObj != worldObj || e.ridingEntity != null || e.riddenByEntity != null
                || e.getDistanceSq(xCoord + 0.5, yCoord + 0.5, zCoord + 0.5) > 36) return false;
        RestraintState s = RestraintState.get(e);
        if(s.occupied() || !(s.cuffs || s.jacket || s.asleep > 0) || kind() == Kind.HANGING && !hasNoose()
                || kind() == Kind.BULL && (temperature > RestraintConfig.bullSafeTemperature || e.height > 2.1F)) return false;
        captive = e.getUniqueID(); cachedEntity = e.getEntityId(); missingTicks = 0;
        captiveHeight = e.height;
        s.device = true; s.deviceToken = token; s.dimension = worldObj.provider.dimensionId;
        s.x = xCoord; s.y = yCoord; s.z = zCoord; s.escort = null;
        armedBy = null;
        position(e); Restraints.changed(e); sync();
        sound(kind() == Kind.HANGING ? "step.cloth" : "hbm:door.lever", 0.7F);
        return true;
    }

    public float yaw() {
        return BlockRestraintDevice.modelYaw(getBlockMetadata());
    }
    public Vec3 local(double x, double y, double z) {
        double angle = Math.toRadians(yaw());
        return Vec3.createVectorHelper(xCoord + 0.5 + x * Math.cos(angle) + z * Math.sin(angle), yCoord + y,
                zCoord + 0.5 - x * Math.sin(angle) + z * Math.cos(angle));
    }
    public void position(EntityLivingBase e) {
        if(owns(e)) cachedEntity = e.getEntityId();
        Vec3 p = local(0, kind() == Kind.TABLE ? 0.92 : kind() == Kind.BULL ? 0.65 : kind() == Kind.CHAIR ? 0.02 : 0.25, 0);
        if(kind() == Kind.HANGING) p = hangingPosition(active ? cycle : 0);
        e.setPosition(p.xCoord, p.yCoord, p.zCoord);
        e.rotationYaw = e.rotationYawHead = e.renderYawOffset = -yaw();
        e.rotationPitch = 0;
        e.motionX = e.motionY = e.motionZ = 0; e.fallDistance = 0;
        RestraintState s = RestraintState.get(e);
        s.anchorX = p.xCoord; s.anchorY = p.yCoord; s.anchorZ = p.zCoord; s.anchored = true;
    }

    public static float hangingDrop(float time) { return Math.min(1F, Math.max(0F, time / 12F)) * 0.55F; }
    public static float hangingSway(float time) { return (float) Math.sin(time * 0.18F) * Math.min(1F, time / 12F) * 0.06F; }
    public Vec3 hangingPosition(float time) { return local(hangingSway(time), 1 - hangingDrop(time), 0); }

    public boolean release(boolean forced) {
        if(worldObj.isRemote || !forced && (active || locked || temperature > RestraintConfig.bullSafeTemperature)) return false;
        EntityLivingBase e = occupant();
        if(e == null && cachedEntity >= 0 && worldObj.getEntityByID(cachedEntity) instanceof EntityLivingBase) {
            EntityLivingBase candidate = (EntityLivingBase) worldObj.getEntityByID(cachedEntity);
            if(owns(candidate)) e = candidate;
        }
        if(e != null) {
            Vec3 exit = exit(e);
            if(exit == null && !forced) return false;
            if(exit != null) e.setPosition(exit.xCoord, exit.yCoord, exit.zCoord);
            Restraints.clearDevice(e);
        }
        forget(); return true;
    }
    private Vec3 exit(EntityLivingBase e) {
        for(int radius = 2; radius <= 4; ++radius) for(int height = 0; height <= 2; ++height) for(int side = 0; side < 4; ++side) {
            double angle = side * Math.PI / 2;
            Vec3 p = local(Math.sin(angle) * radius, height, Math.cos(angle) * radius);
            int bx = MathHelper.floor_double(p.xCoord), by = MathHelper.floor_double(p.yCoord), bz = MathHelper.floor_double(p.zCoord);
            if(!worldObj.blockExists(bx, by, bz) || !worldObj.isSideSolid(bx, by - 1, bz, ForgeDirection.UP)) continue;
            AxisAlignedBB box = e.boundingBox.getOffsetBoundingBox(p.xCoord - e.posX, p.yCoord - e.posY, p.zCoord - e.posZ);
            if(worldObj.getCollidingBoundingBoxes(e, box).isEmpty() && !worldObj.isAnyLiquid(box)) return p;
        }
        return null;
    }
    public void forget() {
        captive = null; cachedEntity = -1; missingTicks = 0; armedBy = null;
        if(kind() != Kind.GUILLOTINE) setActive(false);
        sync();
    }
    public void destroy() { release(true); setActive(false); }

    public boolean interact(EntityPlayer player) {
        if(worldObj.isRemote || !isUseableByPlayer(player) || Restraints.blocked(player)) return false;
        if(!RestraintConfig.enabled || !RestraintConfig.devices) { Restraints.message(player, "restraint.disabled"); return true; }
        ItemStack held = player.getHeldItem();
        if(held != null && isItemValidForSlot(0, held) && !active) {
            if(slots[0] == null || slots[0].isItemEqual(held) && ItemStack.areItemStackTagsEqual(slots[0], held) && slots[0].stackSize < getInventoryStackLimit()) {
                if(slots[0] == null) { slots[0] = held.copy(); slots[0].stackSize = 1; } else ++slots[0].stackSize;
                Restraints.consume(player, held); sync();
                if(kind() == Kind.HANGING) sound("step.cloth", 0.6F);
                return true;
            }
        }
        if((held == null && !player.isSneaking() || held != null && held.getItem() == net.minecraft.init.Items.lead) && captive == null) {
            List<EntityLivingBase> nearby = worldObj.getEntitiesWithinAABB(EntityLivingBase.class,
                    AxisAlignedBB.getBoundingBox(xCoord - 5, yCoord - 3, zCoord - 5, xCoord + 6, yCoord + 4, zCoord + 6));
            EntityLivingBase selected = null;
            double distance = Double.MAX_VALUE;
            for(EntityLivingBase e : nearby) {
                RestraintState s = RestraintState.get(e);
                if(player.getUniqueID().equals(s.escort) && e.getDistanceSqToEntity(player) < distance) {
                    selected = e; distance = e.getDistanceSqToEntity(player);
                }
            }
            if(selected != null) {
                if(!capture(selected)) Restraints.message(player, "restraint.no_captive");
                return true;
            }
            if(held != null) { Restraints.message(player, "restraint.no_captive"); return true; }
        }
        if(held != null) { Restraints.message(player, "restraint.controls"); return true; }
        if(player.isSneaking()) {
            if(kind() == Kind.BULL) {
                if(active) { setActive(false); sync(); Restraints.message(player, "restraint.cooling"); }
                else if(temperature > RestraintConfig.bullSafeTemperature) Restraints.message(player, "restraint.hot", temperature);
                else if(captive != null && !locked) { locked = true; sound("hbm:block.closeDoor", 0.7F); sync(); }
                else if(locked) activate(player);
                else status(player);
            } else if(active && kind() == Kind.CHAIR) { setActive(false); sync(); }
            else activate(player);
            return true;
        }
        armedBy = null;
        if(kind() == Kind.BULL && locked && !active && temperature <= RestraintConfig.bullSafeTemperature) {
            locked = false; sound("hbm:block.openDoor", 0.7F); sync();
        } else if(captive != null && !active && !locked) {
            if(!release(false)) Restraints.message(player, "restraint.no_exit");
        } else if(captive == null && slots[0] != null && !active) {
            ItemStack output = slots[0]; slots[0] = null; Restraints.give(player, output); sync();
        } else status(player);
        return true;
    }

    private void activate(EntityPlayer player) {
        EntityLivingBase e = occupant();
        if(active || e == null || kind() == Kind.BULL && !locked || kind() == Kind.CHAIR && energyQuanta < RestraintConfig.chairQuantaPerTick * 20L
                || kind() == Kind.HANGING && !hasNoose()
                || kind() == Kind.TABLE && !isItemValidForSlot(0, slots[0]) || kind() == Kind.TABLE && RestraintState.get(e).terminal > 0
                || kind() == Kind.BULL && burnTicks <= 0 && !isItemValidForSlot(0, slots[0])) {
            Restraints.message(player, "restraint.not_ready"); return;
        }
        long now = worldObj.getTotalWorldTime();
        if(!player.getUniqueID().equals(armedBy) || now > armedUntil) {
            armedBy = player.getUniqueID(); armedUntil = now + 60; armedSneakReleased = false;
            Restraints.message(player, "restraint.confirm"); return;
        }
        if(!armedSneakReleased) { Restraints.message(player, "restraint.confirm"); return; }
        armedBy = null; cycle = 0;
        if(kind() == Kind.TABLE) {
            decrStackSize(0, 1);
            // Commit the effect at the same time as item consumption so unloading cannot duplicate a dose.
            RestraintState s = RestraintState.get(e);
            s.terminal = RestraintConfig.injectionTicks; s.terminalElapsed = 0; Restraints.changed(e);
        }
        setActive(true); sync();
        sound(kind() == Kind.HANGING ? "random.door_open" : kind() == Kind.GUILLOTINE ? "hbm:block.pressOperate" : kind() == Kind.TABLE ? "random.fizz" : "hbm:door.lever", 0.8F);
    }

    private void status(EntityPlayer player) {
        Restraints.message(player, "restraint.status", captive == null ? "-" : captive.toString().substring(0, 8), temperature, energyQuanta / 2L);
        Restraints.message(player, "restraint.controls");
    }
    private void sound(String name, float volume) { worldObj.playSoundEffect(xCoord + 0.5, yCoord + 1, zCoord + 0.5, name, volume, 1F); }
    private void sync() { markDirty(); markNetworkDirty(); if(worldObj != null && !worldObj.isRemote) networkPackNT(64); }
    private void setActive(boolean value) {
        if(active == value) return;
        active = value; markDirty(); markNetworkDirty();
        worldObj.func_147451_t(xCoord, yCoord, zCoord);
    }

    @Override public void updateEntity() {
        if(worldObj.isRemote) {
            if(kind() == Kind.CHAIR && active) {
                if(dischargeAudio == null) {
                    dischargeAudio = com.hbm.main.MainRegistry.proxy.getLoopedSound("hbm:block.electricHum", xCoord, yCoord, zCoord, 0.6F, 12F, 1F);
                    dischargeAudio.startSound();
                }
            } else stopAudio();
            previousHatch = hatch; previousStraps = straps;
            hatch += ((locked ? 0F : 1F) - hatch) * 0.2F;
            straps += ((captive == null ? 0F : 1F) - straps) * 0.25F;
            if(active && worldObj.getTotalWorldTime() % 4 == 0) {
                if(kind() == Kind.BULL) {
                    worldObj.spawnParticle("smoke", xCoord + 0.5, yCoord + 2.7, zCoord + 0.5, 0, 0.03, 0);
                    worldObj.spawnParticle("flame", xCoord + 0.5, yCoord + 0.15, zCoord + 0.5, 0, 0.01, 0);
                } else if(kind() == Kind.CHAIR) {
                    worldObj.spawnParticle("reddust", xCoord + 0.5 + worldObj.rand.nextGaussian() * 0.25, yCoord + 1.5, zCoord + 0.5, 0.1, 0.3, 1);
                }
            }
            return;
        }
        if(!collisionCellsChecked && worldObj.getTotalWorldTime() % 20 == 0)
            collisionCellsChecked = ((BlockRestraintDevice) getBlockType()).ensureCollisionCells(worldObj, xCoord, yCoord, zCoord);
        if(armedBy != null) {
            EntityPlayer operator = worldObj.func_152378_a(armedBy);
            if(worldObj.getTotalWorldTime() > armedUntil || operator == null || !isUseableByPlayer(operator)) armedBy = null;
            else if(!operator.isSneaking()) armedSneakReleased = true;
        }
        if(kind() == Kind.CHAIR && worldObj.getTotalWorldTime() % 20 == 0)
            for(ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) trySubscribe(worldObj, xCoord + dir.offsetX, yCoord + dir.offsetY, zCoord + dir.offsetZ, dir);
        EntityLivingBase e = occupant();
        if(captive != null && e == null) {
            if(++missingTicks > 200 && worldObj.checkChunksExist(xCoord - 4, yCoord, zCoord - 4, xCoord + 4, yCoord + 4, zCoord + 4)) forget();
        } else missingTicks = 0;
        if(e != null) position(e);
        if(!RestraintConfig.enabled || !RestraintConfig.devices) { release(true); locked = false; setActive(false); return; }
        if(active && (captive == null || e != null)) {
            ++cycle;
            if(kind() == Kind.GUILLOTINE) {
                if(cycle == 5 && e != null) { e.attackEntityFrom(Restraints.BLADE, RestraintConfig.bladeDamage); sound("random.anvil_land", 0.8F); }
                if(cycle >= 45) setActive(false);
            } else if(kind() == Kind.CHAIR) {
                if(e == null || energyQuanta < RestraintConfig.chairQuantaPerTick) setActive(false);
                else {
                    setStoredEnergyQuanta(energyQuanta - RestraintConfig.chairQuantaPerTick);
                    if(cycle % 20 == 0) { e.attackEntityFrom(Restraints.ELECTRIC, RestraintConfig.electricDamage); sound("hbm:weapon.sparkShoot", 0.35F); }
                }
            } else if(kind() == Kind.TABLE) { if(cycle >= 40 || e == null) setActive(false); }
            else if(kind() == Kind.HANGING) {
                if(cycle == 12) sound("step.cloth", 0.8F);
                if(cycle >= 80 && e != null) {
                    e.attackEntityFrom(Restraints.HANGING, Math.max(10000F, e.getMaxHealth() * 2F));
                    // Respect damage cancellation/immunity, returning a surviving captive to the idle platform.
                    setActive(false); sync();
                }
            }
        }
        if(kind() == Kind.BULL) {
            if(active && locked && e != null) {
                if(burnTicks <= 0 && isItemValidForSlot(0, slots[0])) {
                    burnTicks = TileEntityFurnace.getItemBurnTime(slots[0]); decrStackSize(0, 1); markDirty();
                }
                if(burnTicks > 0) { --burnTicks; if(worldObj.getTotalWorldTime() % 10 == 0) temperature = Math.min(RestraintConfig.bullMaxTemperature, temperature + 5); }
                else setActive(false);
            }
            if(!active && temperature > 20 && worldObj.getTotalWorldTime() % 10 == 0) --temperature;
            if(e != null && temperature > 80 && worldObj.getTotalWorldTime() % 20 == 0) {
                e.attackEntityFrom(DamageSource.inFire, RestraintConfig.bullDamage * (temperature - 20) / 100F);
                if(active) sound("fire.fire", 0.6F);
            }
        }
        if(kind() == Kind.HANGING && e != null && e.isEntityAlive() && owns(e)) position(e);
        if(active || temperature > 20 || burnTicks > 0) markDirty();
        networkPackNTIfDirty(64);
    }

    public float animationCycle(float partial) {
        if(!active) return 0;
        return Math.min(kind() == Kind.HANGING ? 80 : kind() == Kind.GUILLOTINE ? 45 : 100000,
                clientCycle + Math.max(0L, worldObj.getTotalWorldTime() - clientCycleAt) + partial);
    }
    private void stopAudio() { if(dischargeAudio != null) { dischargeAudio.stopSound(); dischargeAudio = null; } }
    @Override public void onChunkUnload() { stopAudio(); super.onChunkUnload(); }
    @Override public void invalidate() { stopAudio(); super.invalidate(); }
    @Override public void serialize(ByteBuf b) {
        super.serialize(b); b.writeBoolean(active); b.writeBoolean(locked); b.writeInt(temperature); b.writeInt(cycle); b.writeLong(energyQuanta);
        b.writeBoolean(captive != null);
        if(captive != null) { b.writeLong(captive.getMostSignificantBits()); b.writeLong(captive.getLeastSignificantBits()); }
        if(kind() == Kind.HANGING) { b.writeBoolean(hasNoose()); b.writeFloat(captiveHeight); }
    }
    @Override public void deserialize(ByteBuf b) {
        super.deserialize(b); boolean wasActive = active;
        active = b.readBoolean(); locked = b.readBoolean(); temperature = b.readInt(); cycle = b.readInt(); energyQuanta = b.readLong();
        captive = b.readBoolean() ? new UUID(b.readLong(), b.readLong()) : null;
        if(kind() == Kind.HANGING) {
            boolean noose = b.readBoolean(); captiveHeight = b.readFloat();
            slots[0] = noose ? new ItemStack(ModItems.noose) : null;
        }
        if(!clientInitialized) {
            previousHatch = hatch = locked ? 0F : 1F;
            previousStraps = straps = captive == null ? 0F : 1F;
            clientInitialized = true;
        }
        clientCycle = cycle; clientCycleAt = worldObj == null ? 0 : worldObj.getTotalWorldTime();
        if(worldObj != null && wasActive != active) worldObj.func_147451_t(xCoord, yCoord, zCoord);
    }
    @Override public void writeToNBT(NBTTagCompound n) {
        super.writeToNBT(n);
        n.setString("restraintToken", token.toString()); if(captive != null) n.setString("captive", captive.toString());
        n.setBoolean("active", active); n.setBoolean("locked", locked);
        n.setInteger("temperature", temperature); n.setInteger("burnTicks", burnTicks); n.setInteger("cycle", cycle);
        n.setFloat("captiveHeight", captiveHeight);
        EnergyUnits.writeEnergyQuanta(n, energyQuanta);
    }
    @Override public void readFromNBT(NBTTagCompound n) {
        super.readFromNBT(n);
        UUID saved = RestraintState.uuid(n.getString("restraintToken")); if(saved != null) token = saved;
        captive = RestraintState.uuid(n.getString("captive"));
        active = n.getBoolean("active"); locked = n.getBoolean("locked");
        temperature = Math.max(20, Math.min(RestraintConfig.bullMaxTemperature, n.getInteger("temperature")));
        burnTicks = Math.max(0, n.getInteger("burnTicks")); cycle = Math.max(0, n.getInteger("cycle"));
        captiveHeight = n.hasKey("captiveHeight") ? MathHelper.clamp_float(n.getFloat("captiveHeight"), 1.3F, 2.5F) : 1.8F;
        energyQuanta = Math.max(0, EnergyUnits.readEnergyQuanta(n, "power")); cachedEntity = -1;
    }
    @Override public boolean canConnect(ForgeDirection dir) { return kind() == Kind.CHAIR && dir != ForgeDirection.UNKNOWN; }
    @Override public long getStoredEnergyQuanta() { return energyQuanta; }
    @Override public long getEnergyCapacityQuanta() { return kind() == Kind.CHAIR ? RestraintConfig.chairQuantaPerTick * 100L : 0; }
    @Override public long getMaxInputQuantaPerTick() { return kind() == Kind.CHAIR ? RestraintConfig.chairQuantaPerTick * 2L : 0; }
    @Override public void setStoredEnergyQuanta(long value) { energyQuanta = Math.max(0, value); markDirty(); markNetworkDirty(); markPowerNetDirty(); }
    @Override public AxisAlignedBB getRenderBoundingBox() { return AxisAlignedBB.getBoundingBox(xCoord - 3, yCoord - 1, zCoord - 3, xCoord + 4, yCoord + 5, zCoord + 4); }
}
