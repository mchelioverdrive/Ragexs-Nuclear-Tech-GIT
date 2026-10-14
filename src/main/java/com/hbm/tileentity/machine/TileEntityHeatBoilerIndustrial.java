package com.hbm.tileentity.machine;

import java.io.IOException;

import com.google.gson.JsonObject;
import com.google.gson.stream.JsonWriter;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.lib.Library;
import com.hbm.main.MainRegistry;
import com.hbm.sound.AudioWrapper;
import com.hbm.tileentity.IConfigurableMachine;
import com.hbm.util.fauxpointtwelve.DirPos;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;

public class TileEntityHeatBoilerIndustrial extends TileEntityHeatBoilerBase implements net.minecraft.inventory.ISidedInventory {
	/* CONFIGURABLE */
	public static int maxHeat = 12_800_000;
	public static double diffusion = 0.1D;

	private AudioWrapper audio;
	private int audioTime;
	private AxisAlignedBB renderBounds;

	public TileEntityHeatBoilerIndustrial() {
		tanks = new FluidTank[3];
		tanks[0] = new FluidTank(Fluids.FRESH_WATER, 64_000).migrateFrom(Fluids.WATER);
		tanks[1] = new FluidTank(Fluids.HOTSTEAM, 64_000 * 100);
        tanks[2] = new FluidTank(Fluids.HEAVYOIL, 24000);
	}

    private final com.hbm.realsim.FuelCredit firedFuel = new com.hbm.realsim.FuelCredit();
    @Override public long getRatedThermalWatts() { return 12000000L; }
    @Override protected long getThermalCapacityQuanta() { return 128000000L; }
    @Override protected long getStartupQuanta() { return 10000000L; }
    @Override protected com.hbm.realsim.SteamLedger.State boilerOutlet() { return com.hbm.realsim.SteamLedger.State.HOT; }
    @Override protected long additionalHeat(long request) {
        if(tanks[0].getFill() == 0 && thermal.ownedMass() == 0 || tanks[1].getFill() >= tanks[1].getMaxFill()) return 0;
        com.hbm.dim.trait.CBT_Atmosphere air = com.hbm.dim.CelestialBody.getTrait(worldObj, com.hbm.dim.trait.CBT_Atmosphere.class);
        if(air == null || !air.hasFluid(Fluids.AIR, 0.19)) return 0;
        String name = tanks[2].getTankType().getName();
        boolean liquid = name.equals("HEAVYOIL") || name.equals("HEAVYOIL_VACUUM") || name.equals("HEATINGOIL") || name.equals("HEATINGOIL_VACUUM");
        long heat = liquid ? com.hbm.realsim.Combustion.generate(tanks[2], firedFuel, request, 850000) : firedFuel.release(request);
        for(int i = 0; i < solidFuel.length && heat < request; i++) {
            long chemical = com.hbm.realsim.Combustion.solidChemicalMicrojoules(solidFuel[i]);
            if(chemical <= 0 || solidFuel[i].stackSize <= 0 || firedFuel.hasCredit()) continue;
            firedFuel.reserve(com.hbm.realsim.Combustion.solidIdentity(solidFuel[i]), chemical, 1, 850000);
            solidFuel[i].stackSize--; if(solidFuel[i].stackSize == 0) solidFuel[i] = null;
            heat += firedFuel.release(request - heat);
            markDirty();
        }
        if(heat > 0 && worldObj.getTotalWorldTime() % 20 == 0) com.hbm.handler.pollution.PollutionHandler.incrementPollution(worldObj, xCoord, yCoord, zCoord, com.hbm.handler.pollution.PollutionHandler.PollutionType.SOOT, com.hbm.handler.pollution.PollutionHandler.SOOT_PER_SECOND * 3);
        return heat;
    }
    @Override public FluidTank[] getReceivingTanks() { return new FluidTank[] { tanks[0], tanks[2] }; }
    @Override public void readFromNBT(net.minecraft.nbt.NBTTagCompound nbt) {
        super.readFromNBT(nbt); tanks[2].readFromNBT(nbt, "realSimBoilerFuel"); firedFuel.read(nbt);
        java.util.Arrays.fill(solidFuel, null);
        net.minecraft.nbt.NBTTagList list = nbt.getTagList("realSimBoilerSolids", 10);
        for(int i = 0; i < list.tagCount(); i++) { net.minecraft.nbt.NBTTagCompound item = list.getCompoundTagAt(i); int slot = item.getByte("Slot"); if(slot >= 0 && slot < solidFuel.length) solidFuel[slot] = net.minecraft.item.ItemStack.loadItemStackFromNBT(item); }
    }
    @Override public void writeToNBT(net.minecraft.nbt.NBTTagCompound nbt) {
        super.writeToNBT(nbt); tanks[2].writeToNBT(nbt, "realSimBoilerFuel"); firedFuel.write(nbt);
        net.minecraft.nbt.NBTTagList list = new net.minecraft.nbt.NBTTagList();
        for(int i = 0; i < solidFuel.length; i++) if(solidFuel[i] != null) { net.minecraft.nbt.NBTTagCompound item = new net.minecraft.nbt.NBTTagCompound(); item.setByte("Slot", (byte) i); solidFuel[i].writeToNBT(item); list.appendTag(item); }
        nbt.setTag("realSimBoilerSolids", list);
    }
    @Override protected void writeExtraPacketData(net.minecraft.nbt.NBTTagCompound nbt) { tanks[2].writeToNBT(nbt, "realSimBoilerFuel"); }
    @Override public void networkUnpack(net.minecraft.nbt.NBTTagCompound nbt) { super.networkUnpack(nbt); tanks[2].readFromNBT(nbt, "realSimBoilerFuel"); }

    private final net.minecraft.item.ItemStack[] solidFuel = new net.minecraft.item.ItemStack[2];
    @Override public int getSizeInventory() { return solidFuel.length; }
    @Override public net.minecraft.item.ItemStack getStackInSlot(int slot) { return solidFuel[slot]; }
    @Override public net.minecraft.item.ItemStack decrStackSize(int slot, int amount) {
        net.minecraft.item.ItemStack stack = solidFuel[slot]; if(stack == null || amount <= 0) return null;
        net.minecraft.item.ItemStack removed = stack.splitStack(Math.min(amount, stack.stackSize)); if(stack.stackSize == 0) solidFuel[slot] = null; fuelChanged(); return removed;
    }
    @Override public net.minecraft.item.ItemStack getStackInSlotOnClosing(int slot) { net.minecraft.item.ItemStack stack = solidFuel[slot]; solidFuel[slot] = null; fuelChanged(); return stack; }
    @Override public void setInventorySlotContents(int slot, net.minecraft.item.ItemStack stack) { solidFuel[slot] = stack; if(stack != null) stack.stackSize = Math.min(stack.stackSize, getInventoryStackLimit()); fuelChanged(); }
    private void fuelChanged() { markDirty(); markMachineDirty(com.hbm.machine.MachineDirtyCause.INVENTORY); }
    @Override public String getInventoryName() { return "Industrial boiler fuel"; }
    @Override public boolean hasCustomInventoryName() { return false; }
    @Override public int getInventoryStackLimit() { return 64; }
    @Override public boolean isUseableByPlayer(net.minecraft.entity.player.EntityPlayer player) { return worldObj.getTileEntity(xCoord, yCoord, zCoord) == this && player.getDistanceSq(xCoord + 0.5, yCoord + 0.5, zCoord + 0.5) <= 64; }
    @Override public void openInventory() { }
    @Override public void closeInventory() { }
    @Override public boolean isItemValidForSlot(int slot, net.minecraft.item.ItemStack stack) { return com.hbm.realsim.Combustion.solidChemicalMicrojoules(stack) > 0; }
    @Override public int[] getAccessibleSlotsFromSide(int side) { return new int[] {0, 1}; }
    @Override public boolean canInsertItem(int slot, net.minecraft.item.ItemStack stack, int side) { return isItemValidForSlot(slot, stack); }
    @Override public boolean canExtractItem(int slot, net.minecraft.item.ItemStack stack, int side) { return true; }
    /** Hand feed uses the same inventory as hopper ports; existing dummy-block drops preserve unburned items. */
    public int insertFuel(net.minecraft.item.ItemStack stack) {
        if(!isItemValidForSlot(0, stack)) return 0;
        int before = stack.stackSize;
        for(int i = 0; i < solidFuel.length && stack.stackSize > 0; i++) {
            if(solidFuel[i] != null && (!solidFuel[i].isItemEqual(stack) || !net.minecraft.item.ItemStack.areItemStackTagsEqual(solidFuel[i], stack))) continue;
            int amount = Math.min(stack.stackSize, 64 - (solidFuel[i] == null ? 0 : solidFuel[i].stackSize));
            if(amount <= 0) continue;
            if(solidFuel[i] == null) { solidFuel[i] = stack.copy(); solidFuel[i].stackSize = amount; } else solidFuel[i].stackSize += amount;
            stack.stackSize -= amount;
        }
        if(before != stack.stackSize) fuelChanged(); return before - stack.stackSize;
    }

	@Override protected int getMaxBoilerHeat() { return maxHeat; }
	@Override protected double getBoilerDiffusion() { return diffusion; }

	@Override protected DirPos[] getBoilerConnections() {
		return new DirPos[] {
				new DirPos(xCoord + 2, yCoord, zCoord, Library.POS_X),
				new DirPos(xCoord - 2, yCoord, zCoord, Library.NEG_X),
				new DirPos(xCoord, yCoord, zCoord + 2, Library.POS_Z),
				new DirPos(xCoord, yCoord, zCoord - 2, Library.NEG_Z),
				new DirPos(xCoord, yCoord + 5, zCoord, Library.POS_Y),
		};
	}

	@Override protected boolean shouldExplodeOnBlockedOutput() { return false; }
	@Override protected void handleBlockedOutput() { }

	@Override public void updateEntity() {
		if(!worldObj.isRemote) return;
		if(this.isOn) audioTime = 20;
		if(audioTime > 0) {
			audioTime--;
			if(audio == null) {
				audio = createAudioLoop();
				audio.startSound();
			} else if(!audio.isPlaying()) {
				audio = rebootAudio(audio);
			}
			audio.updateVolume(getVolume(1F));
			audio.keepAlive();
		} else if(audio != null) {
			audio.stopSound();
			audio = null;
		}
	}

	@Override public AudioWrapper createAudioLoop() {
		return MainRegistry.proxy.getLoopedSound("hbm:block.boiler", xCoord, yCoord, zCoord, 0.125F, 10F, 1.0F, 20);
	}

	@Override public void onChunkUnload() {
		super.onChunkUnload();
		this.stopAudio();
	}

	@Override public void invalidate() {
		super.invalidate();
		this.stopAudio();
	}

	private void stopAudio() {
		if(audio != null) {
			audio.stopSound();
			audio = null;
		}
	}

	@Override public String getConfigName() { return "boilerIndustrial"; }

	@Override public void readIfPresent(JsonObject obj) {
		maxHeat = IConfigurableMachine.grab(obj, "I:maxHeat", maxHeat);
		diffusion = IConfigurableMachine.grab(obj, "D:diffusion", diffusion);
	}

	@Override public void writeConfig(JsonWriter writer) throws IOException {
		writer.name("I:maxHeat").value(maxHeat);
		writer.name("D:diffusion").value(diffusion);
	}

	@Override public AxisAlignedBB getRenderBoundingBox() {
		if(renderBounds == null) renderBounds = AxisAlignedBB.getBoundingBox(xCoord - 1, yCoord, zCoord - 1, xCoord + 2, yCoord + 5, zCoord + 2);
		return renderBounds;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public double getMaxRenderDistanceSquared() { return 65536.0D; }
}
