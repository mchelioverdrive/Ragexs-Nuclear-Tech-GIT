package com.hbm.tileentity.machine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.hbm.blocks.ModBlocks;
import com.hbm.inventory.material.MaterialShapes;
import com.hbm.inventory.material.Mats.MaterialStack;
import com.hbm.machine.MachineExecutionStrategy;

import api.hbm.block.ICrucibleAcceptor;
import net.minecraft.block.Block;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

public class TileEntityFoundryChannel extends TileEntityFoundryBase {
	private static final int TASK_FLOW = 1;
	
	public int nextUpdate;
	public int lastFlow = 0;
	
	@Override
	public void updateEntity() {
		if(worldObj.isRemote) super.updateEntity();
	}

	@Override
	public int getMachineExecutionStrategies() {
		return MachineExecutionStrategy.EVENT_DRIVEN | MachineExecutionStrategy.SCHEDULED | MachineExecutionStrategy.COARSE_20;
	}

	@Override
	public void onMachineRuntimeDirty(int causes) {
		super.onMachineRuntimeDirty(causes);
		if(worldObj != null && !worldObj.isRemote && amount > 0 && type != null)
			this.scheduleMachineTransition(worldObj.getTotalWorldTime() + Math.max(nextUpdate, 1), TASK_FLOW, 0);
	}

	@Override
	public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(taskType != TASK_FLOW || taskSlot != 0 || worldObj == null || worldObj.isRemote) return;
		this.nextUpdate = 0;
		com.hbm.inventory.material.NTMMaterial oldType = this.type;
		int oldAmount = this.amount;
		runFlowStep();
		this.markMaterialMutation(oldType, oldAmount);
		if(amount > 0 && type != null)
			this.scheduleMachineTransition(worldObj.getTotalWorldTime() + Math.max(nextUpdate, 1), TASK_FLOW, 0);
	}

	private void runFlowStep() {
		
		if(!worldObj.isRemote) {
			
			if(this.type == null && this.amount != 0) {
				this.amount = 0;
			}
			
			if(nextUpdate <= 0 && this.amount > 0 && this.type != null) {
				
				boolean hasOp = false;
				nextUpdate = 5;
				
				List<Integer> ints = new ArrayList() {{ add(2); add(3); add(4); add(5); }};
				Collections.shuffle(ints);
				if(lastFlow > 0) {
					ints.remove((Integer) this.lastFlow);
					ints.add(this.lastFlow);
				}
				
				for(Integer i : ints) {
					ForgeDirection dir = ForgeDirection.getOrientation(i);
					Block b = worldObj.getBlock(xCoord + dir.offsetX, yCoord, zCoord + dir.offsetZ);
					
					if(b instanceof ICrucibleAcceptor && b != ModBlocks.foundry_channel) {
						ICrucibleAcceptor acc = (ICrucibleAcceptor) b;
						
						if(acc.canAcceptPartialFlow(worldObj, xCoord + dir.offsetX, yCoord, zCoord + dir.offsetZ, dir.getOpposite(), new MaterialStack(this.type, this.amount))) {
							MaterialStack left = acc.flow(worldObj, xCoord + dir.offsetX, yCoord, zCoord + dir.offsetZ, dir.getOpposite(), new MaterialStack(this.type, this.amount));
							if(left == null) {
								this.type = null;
								this.amount = 0;
							} else {
								this.amount = left.amount;
							}
							hasOp = true;
							break;
						}
					}
				}
				
				if(!hasOp) {
					for(Integer i : ints) {
						ForgeDirection dir = ForgeDirection.getOrientation(i);
						TileEntity b = worldObj.getTileEntity(xCoord + dir.offsetX, yCoord, zCoord + dir.offsetZ);
						
						if(b instanceof TileEntityFoundryChannel) {
							TileEntityFoundryChannel acc = (TileEntityFoundryChannel) b;
							com.hbm.inventory.material.NTMMaterial oldType = acc.type;
							int oldAmount = acc.amount;
							
							if(acc.type == null || acc.type == this.type || acc.amount == 0) {
								acc.type = this.type;
								
								acc.lastFlow = dir.getOpposite().ordinal();
								
								if(worldObj.rand.nextInt(5) == 0 || this.amount == 1) { //force swap operations with single quanta to keep them moving
									//1:4 chance that the fill states are simply swapped
									//this promotes faster spreading and prevents spread limits
									int buf = this.amount;
									this.amount = acc.amount;
									acc.amount = buf;
									
								} else {
									//otherwise, equalize the neighbors
									int diff = this.amount - acc.amount;
									
									if(diff > 0) {
										diff /= 2;
										this.amount -= diff;
										acc.amount += diff;
									}
								}
							acc.markMaterialMutation(oldType, oldAmount);
							}
						}
					}
				}
			}
			
			if(this.amount == 0) {
				this.lastFlow = 0;
				this.nextUpdate = 0;
			}
		}
		
	}

	@Override
	public int getCapacity() {
		return MaterialShapes.INGOT.q(2);
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		this.lastFlow = nbt.getByte("flow");
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		nbt.setByte("flow", (byte) this.lastFlow);
	}
}
