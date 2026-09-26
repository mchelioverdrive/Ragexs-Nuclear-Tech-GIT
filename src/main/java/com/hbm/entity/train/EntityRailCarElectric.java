package com.hbm.entity.train;

import com.hbm.items.ModItems;

import api.hbm.energymk2.IBatteryItem;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

public abstract class EntityRailCarElectric extends EntityRailCarRidable {

	public EntityRailCarElectric(World world) {
		super(world);
	}

	public abstract int getEnergyCapacityQuanta();
	public abstract int getPowerConsumption();
	
	public boolean hasChargeSlot() { return false; }
	public int getChargeSlot() { return 0; }
	
	@Override protected void entityInit() {
		super.entityInit();
		this.dataWatcher.addObject(3, new Integer(0));
	}
	
	@Override public boolean canAccelerate() {
		return true;
		//return this.getStoredEnergyQuanta() >= this.getPowerConsumption();
	}
	
	@Override public void consumeFuel() {
		//this.setStoredEnergyQuanta(this.getStoredEnergyQuanta() - this.getPowerConsumption());
	}
	
	public void setStoredEnergyQuanta(int power) {
		this.dataWatcher.updateObject(3, power);
	}
	
	public int getStoredEnergyQuanta() {
		return this.dataWatcher.getWatchableObjectInt(3);
	}
	
	@Override
	public void onUpdate() {
		super.onUpdate();
		
		if(!worldObj.isRemote) {
			
			if(this.hasChargeSlot()) {
				ItemStack stack = this.getStackInSlot(this.getChargeSlot());
				
				if(stack != null && stack.getItem() instanceof IBatteryItem) {
					IBatteryItem battery = (IBatteryItem) stack.getItem();
					int powerNeeded = this.getEnergyCapacityQuanta() - this.getStoredEnergyQuanta();
					long powerProvided = Math.min(battery.getMaxOutputQuantaPerTick(), battery.getStoredEnergyQuanta(stack));
					int powerTransfered = (int) Math.min(powerNeeded, powerProvided);
					
					if(powerTransfered > 0) {
						battery.extractEnergyQuanta(stack, powerTransfered);
						this.setStoredEnergyQuanta(this.getStoredEnergyQuanta() + powerTransfered);
					}
				} else if(stack != null) {
					if(stack.getItem() == ModItems.battery_creative || stack.getItem() == ModItems.fusion_core_infinite) {
						this.setStoredEnergyQuanta(this.getEnergyCapacityQuanta());
					}
				}
			}
		}
	}
}
