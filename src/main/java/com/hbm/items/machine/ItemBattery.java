package com.hbm.items.machine;

import api.hbm.energymk2.EnergyUnits;
import java.util.List;

import com.hbm.items.ModItems;
import com.hbm.util.BobMathUtil;

import api.hbm.energymk2.IBatteryItem;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

public class ItemBattery extends Item implements IBatteryItem {

	protected long maxCharge;
	protected long chargeRate;
	protected long dischargeRate;

	public ItemBattery(long dura, long chargeRate, long dischargeRate) {
		this.maxCharge = dura;
		this.chargeRate = chargeRate;
		this.dischargeRate = dischargeRate;
	}

	@Override
	public void addInformation(ItemStack itemstack, EntityPlayer player, List list, boolean bool) {
		long charge = maxCharge;
		if(itemstack.hasTagCompound())
			charge = getStoredEnergyQuanta(itemstack);

		if(itemstack.getItem() != ModItems.fusion_core && itemstack.getItem() != ModItems.energy_core) {
			list.add("Stored Energy: " + EnergyUnits.formatJoules(charge) + " / " + EnergyUnits.formatJoules(maxCharge));
		} else {
			String charge1 = BobMathUtil.getShortNumber((charge * 100) / this.maxCharge);
			list.add("Charge: " + charge1 + "%");
			list.add("(" + EnergyUnits.formatJoules(charge) + " / " + EnergyUnits.formatJoules(maxCharge) + ")");
		}
		list.add("Maximum Input: " + EnergyUnits.formatQuantaPerTickAsWatts(chargeRate));
		list.add("Maximum Output: " + EnergyUnits.formatQuantaPerTickAsWatts(dischargeRate));
	}

	@Override
	public EnumRarity getRarity(ItemStack p_77613_1_) {

		if(this == ModItems.battery_schrabidium) {
			return EnumRarity.rare;
		}

		if(this == ModItems.fusion_core || this == ModItems.energy_core) {
			return EnumRarity.uncommon;
		}

		return EnumRarity.common;
	}

	public void receiveEnergyQuanta(ItemStack stack, long i) {
		if(stack.getItem() instanceof ItemBattery) {
			if(stack.hasTagCompound()) {
				EnergyUnits.writeEnergyQuanta(stack.stackTagCompound, EnergyUnits.readEnergyQuanta(stack.stackTagCompound, "charge") + i);
			} else {
				stack.stackTagCompound = new NBTTagCompound();
				EnergyUnits.writeEnergyQuanta(stack.stackTagCompound, i);
			}
		}
	}

	public void setStoredEnergyQuanta(ItemStack stack, long i) {
		if(stack.getItem() instanceof ItemBattery) {
			if(stack.hasTagCompound()) {
				EnergyUnits.writeEnergyQuanta(stack.stackTagCompound, i);
			} else {
				stack.stackTagCompound = new NBTTagCompound();
				EnergyUnits.writeEnergyQuanta(stack.stackTagCompound, i);
			}
		}
	}

	public void extractEnergyQuanta(ItemStack stack, long i) {
		if(stack.getItem() instanceof ItemBattery) {
			if(stack.hasTagCompound()) {
				EnergyUnits.writeEnergyQuanta(stack.stackTagCompound, EnergyUnits.readEnergyQuanta(stack.stackTagCompound, "charge") - i);
			} else {
				stack.stackTagCompound = new NBTTagCompound();
				EnergyUnits.writeEnergyQuanta(stack.stackTagCompound, this.maxCharge - i);
			}
		}
	}

	public long getStoredEnergyQuanta(ItemStack stack) {
		if(stack.getItem() instanceof ItemBattery) {
			if(stack.hasTagCompound()) {
				return EnergyUnits.readEnergyQuanta(stack.stackTagCompound, "charge");
			} else {
				stack.stackTagCompound = new NBTTagCompound();
				EnergyUnits.writeEnergyQuanta(stack.stackTagCompound, ((ItemBattery) stack.getItem()).maxCharge);
				return EnergyUnits.readEnergyQuanta(stack.stackTagCompound, "charge");
			}
		}

		return 0;
	}

	@Override
	public long getEnergyCapacityQuanta(ItemStack stack) {
		return maxCharge;
	}

	@Override
	public long getMaxInputQuantaPerTick() {
		return chargeRate;
	}

	@Override
	public long getMaxOutputQuantaPerTick() {
		return dischargeRate;
	}

	public static ItemStack getEmptyBattery(Item item) {

		if(item instanceof ItemBattery) {
			ItemStack stack = new ItemStack(item);
			stack.stackTagCompound = new NBTTagCompound();
			EnergyUnits.writeEnergyQuanta(stack.stackTagCompound, 0);
			return stack.copy();
		}

		return null;
	}

	public static ItemStack getFullBattery(Item item) {

		if(item instanceof ItemBattery) {
			ItemStack stack = new ItemStack(item);
			stack.stackTagCompound = new NBTTagCompound();
			EnergyUnits.writeEnergyQuanta(stack.stackTagCompound, ((ItemBattery) item).getEnergyCapacityQuanta(stack));
			return stack.copy();
		}

		return new ItemStack(item);
	}

	public boolean showDurabilityBar(ItemStack stack) {
		return true;
	}

	public double getDurabilityForDisplay(ItemStack stack) {
		return 1D - (double) getStoredEnergyQuanta(stack) / (double) getEnergyCapacityQuanta(stack);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void getSubItems(Item item, CreativeTabs tab, List list) {

		if(this.chargeRate > 0) {
			list.add(getEmptyBattery(item));
		}
		
		if(this.dischargeRate > 0) {
			list.add(getFullBattery(item));
		}
	}
}
