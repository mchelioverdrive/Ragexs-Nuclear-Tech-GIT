package com.hbm.tileentity.machine;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.hbm.tileentity.INBTPacketReceiver;
import com.hbm.tileentity.TileEntityLoadedBase;
import com.hbm.machine.MachineExecutionStrategy;

import api.hbm.energymk2.IBatteryItem;
import api.hbm.energymk2.IEnergyReceiverMK2;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.common.util.ForgeDirection;

public class TileEntityCharger extends TileEntityLoadedBase implements IEnergyReceiverMK2, INBTPacketReceiver {
	private static final int TASK_CHARGE = 1;
	
	private List<EntityPlayer> players = new ArrayList();
	private long charge = 0;
	private int lastOp = 0;
	
	boolean particles = false;
	
	public int usingTicks;
	public int lastUsingTicks;
	public static final int delay = 20;
	private static final AxisAlignedBB UNIT_BOX = AxisAlignedBB.getBoundingBox(-0.5, 0, -0.5, 0.5, 0, 0.5);

	@Override
	public int getMachineExecutionStrategies() {
		return MachineExecutionStrategy.EVENT_DRIVEN | MachineExecutionStrategy.SCHEDULED | MachineExecutionStrategy.COARSE_5 | MachineExecutionStrategy.COARSE_20;
	}

	@Override
	public void onMachineRuntimeDirty(int causes) {
		if(worldObj == null || worldObj.isRemote) return;
		if(lastOp > 0 || usingTicks > 0 || !findPlayers().isEmpty())
			this.scheduleMachineTransition(worldObj.getTotalWorldTime() + 1L, TASK_CHARGE, 0);
	}

	@Override
	public void onMachineCoarsePoll(int cadence) {
		if(worldObj == null || worldObj.isRemote) return;
		if(cadence == 20) {
			ForgeDirection dir = ForgeDirection.getOrientation(this.getBlockMetadata()).getOpposite();
			this.trySubscribe(worldObj, xCoord + dir.offsetX, yCoord, zCoord + dir.offsetZ, dir);
		} else if(cadence == 5) this.onMachineRuntimeDirty(0);
	}

	private List<EntityPlayer> findPlayers() {
		return worldObj.getEntitiesWithinAABB(EntityPlayer.class,
			AxisAlignedBB.getBoundingBox(xCoord, yCoord, zCoord, xCoord + 1, yCoord, zCoord + 1));
	}

	@Override
	public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(taskType != TASK_CHARGE || taskSlot != 0 || worldObj == null || worldObj.isRemote) return;
		this.runChargeStep();
		if(lastOp > 0 || usingTicks > 0 || charge > 0)
			this.scheduleMachineTransition(worldObj.getTotalWorldTime() + 1L, TASK_CHARGE, 0);
	}

	@Override
	public void updateEntity() {
		if(!worldObj.isRemote) return;
		ForgeDirection dir = ForgeDirection.getOrientation(this.getBlockMetadata()).getOpposite();
		this.advanceUsingTicks();
		this.spawnParticles(dir);
	}

	private void runChargeStep() {
		ForgeDirection dir = ForgeDirection.getOrientation(this.getBlockMetadata()).getOpposite();
		long previousCharge = this.charge;
		boolean previouslyReady = this.usingTicks >= delay;
		players = findPlayers();
			
			charge = 0;
			
			for(EntityPlayer player : players) {
				
				for(int i = 0; i < 5; i++) {
					
					ItemStack stack = player.getEquipmentInSlot(i);
					
					if(stack != null && stack.getItem() instanceof IBatteryItem) {
						IBatteryItem battery = (IBatteryItem) stack.getItem();
						charge += Math.min(battery.getEnergyCapacityQuanta(stack) - battery.getStoredEnergyQuanta(stack), battery.getMaxInputQuantaPerTick());
					}
				}
			}
			
			particles = lastOp > 0;
			
			if(particles) {
				
				lastOp--;
				
				if(worldObj.getTotalWorldTime() % 20 == 0)
					worldObj.playSoundEffect(xCoord + 0.5, yCoord + 0.5, zCoord + 0.5, "random.fizz", 0.2F, 0.5F);
			}
			
			NBTTagCompound data = new NBTTagCompound();
			data.setLong("c", charge);
			data.setBoolean("p", particles);
			INBTPacketReceiver.networkPack(this, data, 50);
		this.advanceUsingTicks();
		if(this.charge != previousCharge || previouslyReady != (this.usingTicks >= delay)) this.markPowerNetDirty();
		this.spawnParticles(dir);
	}

	private void advanceUsingTicks() {
		lastUsingTicks = usingTicks;
		
		if((charge > 0 || particles) && usingTicks < delay) {
			usingTicks++;
			if(usingTicks == 2)
				worldObj.playSoundEffect(xCoord + 0.5, yCoord + 0.5, zCoord + 0.5, "tile.piston.out", 0.5F, 0.5F);
		}
		if((charge <= 0 && !particles) && usingTicks > 0) {
			usingTicks--;
			if(usingTicks == 4)
				worldObj.playSoundEffect(xCoord + 0.5, yCoord + 0.5, zCoord + 0.5, "tile.piston.in", 0.5F, 0.5F);
		}
	}

	private void spawnParticles(ForgeDirection dir) {
		if(particles) {
			Random rand = worldObj.rand;
			worldObj.spawnParticle("magicCrit",
					xCoord + 0.5 + rand.nextDouble() * 0.0625 + dir.offsetX * 0.75,
					yCoord + 0.1,
					zCoord + 0.5 + rand.nextDouble() * 0.0625 + dir.offsetZ * 0.75,
					-dir.offsetX + rand.nextGaussian() * 0.1,
					0,
					-dir.offsetZ + rand.nextGaussian() * 0.1);
		}
	}

	@Override
	public void networkUnpack(NBTTagCompound nbt) {
		this.charge = nbt.getLong("c");
		this.particles = nbt.getBoolean("p");
	}

	@Override
	public long getStoredEnergyQuanta() {
		return 0;
	}

	@Override
	public long getEnergyCapacityQuanta() {
		return charge;
	}

	@Override
	public void setStoredEnergyQuanta(long power) { }
	
	@Override
	public long receiveEnergyQuanta(long power) {
		long offered = power;
		
		if(this.usingTicks < delay || power == 0)
			return power;
		
		for(EntityPlayer player : players) {
			
			for(int i = 0; i < 5; i++) {
				
				ItemStack stack = player.getEquipmentInSlot(i);
				
				if(stack != null && stack.getItem() instanceof IBatteryItem) {
					IBatteryItem battery = (IBatteryItem) stack.getItem();
					
					long toCharge = Math.min(battery.getEnergyCapacityQuanta(stack) - battery.getStoredEnergyQuanta(stack), battery.getMaxInputQuantaPerTick());
					toCharge = Math.min(toCharge, power / 5);
					battery.receiveEnergyQuanta(stack, toCharge);
					power -= toCharge;
					
					lastOp = 4;
				}
			}
		}
		
		if(power != offered) {
			this.markPowerNetDirty();
			this.markMachineEnergyDirty();
		}
		return power;
	}
}
