package com.hbm.tileentity.machine;

import api.hbm.energymk2.EnergyUnits;
import java.util.List;

import com.hbm.entity.mob.EntityFBI;
import com.hbm.entity.mob.EntityFBIDrone;
import com.hbm.inventory.container.ContainerRadiobox;
import com.hbm.lib.ModDamageSource;
import com.hbm.machine.MachineDirtyCause;
import com.hbm.machine.MachineExecutionStrategy;
import com.hbm.tileentity.IGUIProvider;
import com.hbm.tileentity.TileEntityLoadedBase;

import api.hbm.energymk2.IEnergyReceiverMK2;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.entity.Entity;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

public class TileEntityRadiobox extends TileEntityLoadedBase implements IEnergyReceiverMK2, IGUIProvider {
	private static final int TASK_EFFECT = 1;
	private boolean runtimeEnergyMutation;
	
	long energyQuanta;
	public static long maxPower = 500000;
	public boolean infinite = false;
	
	@Override
	public int getMachineExecutionStrategies() {
		return MachineExecutionStrategy.EVENT_DRIVEN | MachineExecutionStrategy.SCHEDULED | MachineExecutionStrategy.COARSE_20;
	}

	@Override
	public void onMachineRuntimeDirty(int causes) {
		if(worldObj != null && !worldObj.isRemote && this.getBlockMetadata() > 5 && (energyQuanta >= 25000 || infinite))
			this.scheduleMachineTransition(worldObj.getTotalWorldTime() + 1L, TASK_EFFECT, 0);
	}

	@Override
	public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(taskType != TASK_EFFECT || taskSlot != 0 || worldObj == null || worldObj.isRemote) return;
		if(this.getBlockMetadata() > 5 && (energyQuanta >= 25000 || infinite)) {
			
			if(!infinite) {
				runtimeEnergyMutation = true;
				this.setStoredEnergyQuanta(this.energyQuanta - 25000);
				runtimeEnergyMutation = false;
				this.markDirty();
			}
			
			int range = 15;
			
			List<IMob> entities = worldObj.getEntitiesWithinAABB(IMob.class, AxisAlignedBB.getBoundingBox(xCoord - range, yCoord - range, zCoord - range, xCoord + range, yCoord + range, zCoord + range));
			
			for(IMob entity : entities) {
				
				if(entity instanceof EntityFBI || entity instanceof EntityFBIDrone) continue;
				
				((Entity)entity).attackEntityFrom(ModDamageSource.enervation, 20.0F);
			}
		}
		this.onMachineRuntimeDirty(0);
	}

	@Override
	public void onMachineCoarsePoll(int cadence) {
		if(cadence != 20 || worldObj == null || worldObj.isRemote) return;
		this.updateConnections();
		this.markMachineDirty(MachineDirtyCause.CONFIGURATION | MachineDirtyCause.REDSTONE);
	}
	
	private void updateConnections() {
		
		for(ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS)
			this.trySubscribe(worldObj, xCoord + dir.offsetX, yCoord + dir.offsetY, zCoord + dir.offsetZ, dir);
	}
	
	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		
		energyQuanta = EnergyUnits.readEnergyQuanta(nbt, "power");
		infinite = nbt.getBoolean("infinite");
	}
	
	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		
		EnergyUnits.writeEnergyQuanta(nbt, energyQuanta);
		nbt.setBoolean("infinite", infinite);
	}

	@Override
	public void setStoredEnergyQuanta(long i) {
		if(this.energyQuanta == i) return;
		this.energyQuanta = i;
		this.markPowerNetDirty();
		if(!runtimeEnergyMutation) this.markMachineEnergyDirty();
	}

	@Override
	public long getStoredEnergyQuanta() {
		return energyQuanta;
	}

	@Override
	public long getEnergyCapacityQuanta() {
		return maxPower;
	}
	
	@Override
	@SideOnly(Side.CLIENT)
	public double getMaxRenderDistanceSquared() {
		return 65536.0D;
	}

	@Override
	public Container provideContainer(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new ContainerRadiobox(player.inventory, this);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public Object provideGUI(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return null;
	}
}
