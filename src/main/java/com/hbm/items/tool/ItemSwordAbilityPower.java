package com.hbm.items.tool;

import api.hbm.energymk2.EnergyUnits;
import java.util.List;


import api.hbm.energymk2.IBatteryItem;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

public class ItemSwordAbilityPower extends ItemSwordAbility implements IBatteryItem {

	public long maxPower = 1;
	public long chargeRate;
	public long consumption;

	public ItemSwordAbilityPower(float damage, double movement, ToolMaterial material, long maxPower, long chargeRate, long consumption) {
		super(damage, movement, material);
		this.maxPower = maxPower;
		this.chargeRate = chargeRate;
		this.consumption = consumption;
		this.setMaxDamage(1);
	}

	@Override
    public void receiveEnergyQuanta(ItemStack stack, long i) {
    	if(stack.getItem() instanceof ItemSwordAbilityPower) {
    		if(stack.hasTagCompound()) {
    			EnergyUnits.writeEnergyQuanta(stack.stackTagCompound, EnergyUnits.readEnergyQuanta(stack.stackTagCompound, "charge") + i);
    		} else {
    			stack.stackTagCompound = new NBTTagCompound();
    			EnergyUnits.writeEnergyQuanta(stack.stackTagCompound, i);
    		}
    	}
    }

	@Override
    public void setStoredEnergyQuanta(ItemStack stack, long i) {
    	if(stack.getItem() instanceof ItemSwordAbilityPower) {
    		if(stack.hasTagCompound()) {
    			EnergyUnits.writeEnergyQuanta(stack.stackTagCompound, i);
    		} else {
    			stack.stackTagCompound = new NBTTagCompound();
    			EnergyUnits.writeEnergyQuanta(stack.stackTagCompound, i);
    		}
    	}
    }

	@Override
    public void extractEnergyQuanta(ItemStack stack, long i) {
    	if(stack.getItem() instanceof ItemSwordAbilityPower) {
    		if(stack.hasTagCompound()) {
    			EnergyUnits.writeEnergyQuanta(stack.stackTagCompound, EnergyUnits.readEnergyQuanta(stack.stackTagCompound, "charge") - i);
    		} else {
    			stack.stackTagCompound = new NBTTagCompound();
    			EnergyUnits.writeEnergyQuanta(stack.stackTagCompound, this.maxPower - i);
    		}
    		
    		if(EnergyUnits.readEnergyQuanta(stack.stackTagCompound, "charge") < 0)
    			EnergyUnits.writeEnergyQuanta(stack.stackTagCompound, 0);
    	}
    }

	@Override
    public long getStoredEnergyQuanta(ItemStack stack) {
    	if(stack.getItem() instanceof ItemSwordAbilityPower) {
    		if(stack.hasTagCompound()) {
    			return EnergyUnits.readEnergyQuanta(stack.stackTagCompound, "charge");
    		} else {
    			stack.stackTagCompound = new NBTTagCompound();
    			EnergyUnits.writeEnergyQuanta(stack.stackTagCompound, ((ItemSwordAbilityPower)stack.getItem()).maxPower);
    			return EnergyUnits.readEnergyQuanta(stack.stackTagCompound, "charge");
    		}
    	}
    	
    	return 0;
    }
    
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean ext) {
    	
		list.add("Stored Energy: " + EnergyUnits.formatJoules(getStoredEnergyQuanta(stack)) + " / " + EnergyUnits.formatJoules(maxPower));
    	
    	super.addInformation(stack, player, list, ext);
    }

	@Override
    public boolean showDurabilityBar(ItemStack stack) {
    	
        return getStoredEnergyQuanta(stack) < maxPower;
    }

	@Override
    public double getDurabilityForDisplay(ItemStack stack) {
    	
        return 1 - (double)getStoredEnergyQuanta(stack) / (double)maxPower;
    }

	@Override
    protected boolean canOperate(ItemStack stack) {
    	
    	return getStoredEnergyQuanta(stack) >= this.consumption;
    }

	@Override
    public long getEnergyCapacityQuanta(ItemStack stack) {
    	return maxPower;
    }

	@Override
    public long getMaxInputQuantaPerTick() {
    	return chargeRate;
    }

	@Override
	public long getMaxOutputQuantaPerTick() {
		return 0;
	}

	@Override
    public void setDamage(ItemStack stack, int damage)
    {
        this.extractEnergyQuanta(stack, damage * consumption);
    }

	@Override
    public boolean isDamageable() {
    	return true;
    }
}
