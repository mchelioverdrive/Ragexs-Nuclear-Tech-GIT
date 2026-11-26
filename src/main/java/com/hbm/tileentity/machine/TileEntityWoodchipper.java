package com.hbm.tileentity.machine;

import java.util.UUID;

import api.hbm.energymk2.EnergyUnits;
import com.hbm.blocks.machine.MachineWoodchipper;
import com.hbm.blocks.machine.BlockRestraintDevice.Kind;
import com.hbm.config.RestraintConfig;
import com.hbm.config.WoodchipperConfig;
import com.hbm.config.WoodchipperConfig.Values;
import com.hbm.handler.restraint.ExecutionDeviceEffects.Cue;
import com.hbm.handler.restraint.Restraints;
import com.hbm.inventory.container.ContainerWoodchipper;
import com.hbm.inventory.gui.GUIWoodchipper;
import com.hbm.inventory.recipes.WoodchipperRecipes;
import com.hbm.main.MainRegistry;
import com.hbm.sound.AudioWrapper;
import com.hbm.tileentity.IGUIProvider;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import net.minecraft.block.Block;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.passive.EntityHorse;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

public final class TileEntityWoodchipper extends TileEntityRestraintDevice implements IGUIProvider {
    public static final int INPUT = 0, OUTPUT = 1;
    private static final int[] INPUT_SLOTS = {INPUT}, OUTPUT_SLOTS = {OUTPUT};
    private static final int HIT_PERIOD = 20;
    private static final String WOOD_CHIPS = "blockcrack_" + Block.getIdFromBlock(Blocks.planks) + "_0";
    private int progress;
    private ItemStack progressInput;
    private UUID operator;
    private boolean attachedPower;
    private Values appliedSettings;

    private boolean spinning, cuttingWood, showParticles;
    private int displayedDuration = 20;
    private long displayedCapacity;
    private float previousAngle, angle;
    private AudioWrapper motor;

    public TileEntityWoodchipper() { super(2); }

    @Override public Kind kind() { return Kind.WOODCHIPPER; }
    @Override public float yaw() { return -(getBlockMetadata() & 3) * 90F; }
    @Override protected Vec3 occupantAnchor() { return local(0, 1, -0.0625); }
    @Override public String getName() { return "container.woodchipper"; }
    @Override public int getInventoryStackLimit() { return 64; }
    @Override public int[] getAccessibleSlotsFromSide(int side) { return side == 1 ? INPUT_SLOTS : OUTPUT_SLOTS; }
    @Override public boolean isItemValidForSlot(int slot, ItemStack stack) {
        return slot == INPUT && WoodchipperRecipes.getResult(stack) != null;
    }
    @Override public boolean canInsertItem(int slot, ItemStack stack, int side) { return side == 1 && isItemValidForSlot(slot, stack); }
    @Override public boolean canExtractItem(int slot, ItemStack stack, int side) { return side != 1 && slot == OUTPUT; }

    private static boolean entitiesEnabled(Values settings) {
        return settings.enabled && settings.entities && RestraintConfig.enabled && RestraintConfig.devices;
    }

    @Override protected boolean isOccupantCompatible(EntityLivingBase occupant) {
        Values settings = WoodchipperConfig.settings;
        return entitiesEnabled(settings) && Restraints.compatible(occupant, settings.bosses)
                && occupant.ridingEntity == null && occupant.riddenByEntity == null && !(occupant instanceof EntityHorse)
                && !(occupant instanceof EntityPig && ((EntityPig) occupant).getSaddled())
                && (!(occupant instanceof EntityPlayer) || settings.players);
    }

    @Override public boolean interact(EntityPlayer player) {
        if(worldObj.isRemote || !isUseableByPlayer(player) || Restraints.blocked(player)) return false;
        ItemStack held = player.getHeldItem();
        if(held != null && held.getItem() == Items.lead) {
            if(captive != null) {
                if(active) Restraints.message(player, "woodchipper.stop_first");
                else if(!release(false)) Restraints.message(player, "restraint.no_exit");
            } else if(!entitiesEnabled(WoodchipperConfig.settings)) Restraints.message(player, "restraint.disabled");
            else if(!tryCaptureEscorted(player)) Restraints.message(player, "restraint.no_captive");
            return true;
        }
        if(held != null || !player.isSneaking()) return false;
        if(active) setActive(false);
        else {
            EntityLivingBase captiveEntity = occupant();
            if(captiveEntity == null || !isOccupantCompatible(captiveEntity) || !mayHurt(captiveEntity, player)
                    || energyQuanta < WoodchipperConfig.settings.energyPerTick || hasStopSignal())
                Restraints.message(player, "woodchipper.not_ready");
            else if(confirmActivation(player)) {
                operator = player.getUniqueID();
                cycle = 0;
                setActive(true);
            }
        }
        return true;
    }

    private static boolean mayHurt(EntityLivingBase occupant, EntityPlayer player) {
        if(!(occupant instanceof EntityPlayer)) return true;
        EntityPlayer target = (EntityPlayer) occupant;
        return player != null && !target.capabilities.disableDamage && target.canAttackPlayer(player);
    }

    @Override protected void setActive(boolean value) {
        super.setActive(value);
        if(!value) operator = null;
    }

    @Override public void updateEntity() {
        if(worldObj.isRemote) {
            updateClient();
            return;
        }
        if(!attachedPower) {
            ForgeDirection side = MachineWoodchipper.powerSide(getBlockMetadata());
            trySubscribe(worldObj, xCoord + side.offsetX, yCoord, zCoord + side.offsetZ, side);
            attachedPower = true;
        }

        Values settings = WoodchipperConfig.settings;
        if(appliedSettings != settings) {
            appliedSettings = settings;
            showParticles = settings.particles;
            markNetworkDirty();
            markPowerNetDirty();
        }

        boolean worked = false, chipped = false;
        if(captive != null) {
            EntityLivingBase occupant = trackOccupant();
            if(!entitiesEnabled(settings) || occupant != null && !isOccupantCompatible(occupant)) release(true);
            else if(active) worked = processOccupant(occupant, settings);
        } else if(settings.enabled && slots[INPUT] != null && energyQuanta >= settings.energyPerTick) {
            chipped = processMaterial(settings);
            worked = chipped;
        }
        if(progress > 0 && !sameInput(slots[INPUT], progressInput)) clearProgress();
        updateMotor(worked, chipped);
        if((worldObj.getTotalWorldTime() & 3L) == 0L) networkPackNTIfDirty(64, spinning);
    }

    private boolean processOccupant(EntityLivingBase occupant, Values settings) {
        if(occupant == null || energyQuanta < settings.energyPerTick || hasStopSignal()
                || occupant instanceof EntityPlayer && !mayHurt(occupant, operator == null ? null : worldObj.func_152378_a(operator))) {
            setActive(false);
            return false;
        }
        setStoredEnergyQuanta(energyQuanta - settings.energyPerTick);
        if(++cycle == HIT_PERIOD) {
            cycle = 0;
            // An accepted death emits its own cue and releases the captive synchronously.
            if(settings.damage > 0F && occupant.attackEntityFrom(Restraints.BLADE, settings.damage) && settings.particles && owns(occupant))
                emitExecutionEffect(Cue.CHIPPER_HIT, occupant, outlet(0));
        }
        return active;
    }

    @Override public void bladeDeathEffect(EntityLivingBase occupant) {
        if(active && WoodchipperConfig.settings.particles) emitExecutionEffect(Cue.CHIPPER_DEATH, occupant, outlet(0));
    }

    private Vec3 outlet(double sideways) { return local(sideways, 0.34, 0.51); }

    private boolean processMaterial(Values settings) {
        ItemStack input = slots[INPUT];
        ItemStack output = slots[OUTPUT];
        if(output != null && output.stackSize >= output.getMaxStackSize()) return false;
        ItemStack result = WoodchipperRecipes.getResult(input);
        if(result == null || output != null && (!sameInput(output, result) || output.stackSize > output.getMaxStackSize() - result.stackSize)
                || hasStopSignal()) return false;
        if(!sameInput(input, progressInput)) {
            progress = 0;
            progressInput = input.copy();
            progressInput.stackSize = 1;
        }
        setStoredEnergyQuanta(energyQuanta - settings.energyPerTick);
        if(++progress >= settings.durationTicks) {
            decrStackSize(INPUT, 1);
            if(output == null) setInventorySlotContents(OUTPUT, result.copy());
            else output.stackSize += result.stackSize;
            clearProgress();
            playMachineSound("hbm:block.crateBreak", 0.45F);
        }
        return true;
    }

    private void clearProgress() {
        progress = 0;
        progressInput = null;
        markChanged();
        markNetworkDirty();
    }

    private static boolean sameInput(ItemStack a, ItemStack b) {
        return a != null && b != null && a.isItemEqual(b) && ItemStack.areItemStackTagsEqual(a, b);
    }

    private boolean hasStopSignal() {
        if(worldObj.checkChunksExist(xCoord - 2, yCoord, zCoord - 2, xCoord + 2, yCoord, zCoord + 2))
            return worldObj.isBlockIndirectlyGettingPowered(xCoord, yCoord, zCoord);
        for(ForgeDirection side : ForgeDirection.VALID_DIRECTIONS) {
            int x = xCoord + side.offsetX, y = yCoord + side.offsetY, z = zCoord + side.offsetZ;
            if(!worldObj.blockExists(x, y, z)) continue;
            Block block = worldObj.getBlock(x, y, z);
            if(!block.shouldCheckWeakPower(worldObj, x, y, z, side.ordinal())) {
                if(block.isProvidingWeakPower(worldObj, x, y, z, side.ordinal()) > 0) return true;
                continue;
            }
            for(ForgeDirection source : ForgeDirection.VALID_DIRECTIONS) {
                int sx = x + source.offsetX, sy = y + source.offsetY, sz = z + source.offsetZ;
                if(worldObj.blockExists(sx, sy, sz) && worldObj.isBlockProvidingPowerTo(sx, sy, sz, source.ordinal()) > 0) return true;
            }
        }
        return false;
    }

    private void updateMotor(boolean running, boolean material) {
        if(spinning == running && cuttingWood == material) return;
        if(spinning != running) playMachineSound(running ? "hbm:block.reactorStart" : "hbm:block.reactorStop", 0.55F);
        spinning = running;
        cuttingWood = material;
        markNetworkDirty();
        worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

    private void playMachineSound(String sound, float volume) {
        worldObj.playSoundEffect(xCoord + 0.5D, yCoord + 0.5D, zCoord + 0.5D, sound, getVolume(volume), 1F);
    }

    @SideOnly(Side.CLIENT)
    private void updateClient() {
        previousAngle = angle;
        if(!spinning) {
            stopMotor();
            return;
        }
        angle += 24F;
        if(angle >= 360F) { angle -= 360F; previousAngle -= 360F; }
        if(motor == null) {
            motor = MainRegistry.proxy.getLoopedSound("hbm:block.centrifugeOperate", xCoord + 0.5F, yCoord + 0.5F, zCoord + 0.5F,
                    getVolume(0.45F), 16F, 1F);
            motor.startSound();
        }
        motor.updateVolume(getVolume(0.45F));
        if(cuttingWood && showParticles && worldObj.getTotalWorldTime() % 4L == 0L) {
            Vec3 outlet = outlet((worldObj.rand.nextFloat() - 0.5F) * 0.25F);
            ForgeDirection front = MachineWoodchipper.powerSide(getBlockMetadata()).getOpposite();
            worldObj.spawnParticle(WOOD_CHIPS, outlet.xCoord, outlet.yCoord, outlet.zCoord, front.offsetX * 0.14, 0.06, front.offsetZ * 0.14);
        }
    }

    @SideOnly(Side.CLIENT)
    public float rotorAngle(float partial) { return spinning ? previousAngle + (angle - previousAngle) * partial : angle; }

    private void stopMotor() {
        if(motor != null) { motor.stopSound(); motor = null; }
    }
    @Override public void invalidate() { stopMotor(); super.invalidate(); }
    @Override public void onChunkUnload() {
        stopMotor();
        attachedPower = false;
        active = spinning = cuttingWood = false;
        operator = null;
        cycle = 0;
        super.onChunkUnload();
    }

    @Override public boolean canConnect(ForgeDirection side) { return side == MachineWoodchipper.powerSide(getBlockMetadata()); }
    @Override public long getEnergyCapacityQuanta() { return WoodchipperConfig.settings.energyPerTick * 100L; }
    @Override public long getMaxInputQuantaPerTick() {
        Values settings = WoodchipperConfig.settings;
        return settings.enabled ? settings.energyPerTick * 2L : 0L;
    }
    @Override public void setStoredEnergyQuanta(long value) {
        value = Math.max(0L, value);
        if(energyQuanta == value) return;
        energyQuanta = value;
        if(worldObj != null) markChanged();
        markNetworkDirty();
        markPowerNetDirty();
    }

    @Override public void serialize(ByteBuf buffer) {
        super.serialize(buffer);
        buffer.writeInt(progress);
        buffer.writeInt(WoodchipperConfig.settings.durationTicks);
        buffer.writeLong(getEnergyCapacityQuanta());
        buffer.writeBoolean(spinning);
        buffer.writeBoolean(cuttingWood);
        buffer.writeBoolean(showParticles);
        buffer.writeInt(0); // Reserved former hit-sequence field. Occupant effects use event packets.
    }

    @Override public void deserialize(ByteBuf buffer) {
        super.deserialize(buffer);
        progress = buffer.readInt();
        displayedDuration = buffer.readInt();
        displayedCapacity = buffer.readLong();
        spinning = buffer.readBoolean();
        cuttingWood = buffer.readBoolean();
        showParticles = buffer.readBoolean();
        buffer.readInt(); // Reserved former hit-sequence field.
        if(!spinning) stopMotor();
    }

    @Override public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setInteger("progress", progress);
        if(progressInput != null) tag.setTag("progressInput", progressInput.writeToNBT(new NBTTagCompound()));
    }
    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        active = false;
        operator = null;
        cycle = 0;
        progress = Math.max(0, Math.min(1199, tag.getInteger("progress")));
        progressInput = ItemStack.loadItemStackFromNBT(tag.getCompoundTag("progressInput"));
        if(!sameInput(slots[INPUT], progressInput)) { progress = 0; progressInput = null; }
    }

    public int displayedProgress() { return progress; }
    public int displayedDuration() { return displayedDuration; }
    public long displayedCapacity() { return displayedCapacity; }
    public boolean cuttingWood() { return cuttingWood; }

    @Override public AxisAlignedBB getRenderBoundingBox() {
        return AxisAlignedBB.getBoundingBox(xCoord, yCoord, zCoord, xCoord + 1, yCoord + 1, zCoord + 1);
    }
    @Override public Container provideContainer(int id, EntityPlayer player, World world, int x, int y, int z) {
        return new ContainerWoodchipper(player.inventory, this);
    }
    @Override @SideOnly(Side.CLIENT)
    public Object provideGUI(int id, EntityPlayer player, World world, int x, int y, int z) {
        return new GUIWoodchipper(player.inventory, this);
    }
}
