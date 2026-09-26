package com.hbm.items.armor;

import api.hbm.energymk2.EnergyUnits;
import java.util.List;

import com.hbm.handler.ArmorModHandler;

import api.hbm.energymk2.IBatteryItem;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

public class ArmorFSBPowered extends ArmorFSB implements IBatteryItem {

	public long maxPower = 1;
	public long chargeRate;
	public long consumption;
	public long drain;

	public ArmorFSBPowered(ArmorMaterial material, int slot, String texture, long maxPower, long chargeRate, long consumption, long drain) {
		super(material, slot, texture);
		this.maxPower = maxPower;
		this.chargeRate = chargeRate;
		this.consumption = consumption;
		this.drain = drain;
		this.setMaxDamage(1);
	}

	@SideOnly(Side.CLIENT)
	public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean ext) {
		list.add("Stored Energy: " + EnergyUnits.formatJoules(getStoredEnergyQuanta(stack)) + " / " + EnergyUnits.formatJoules(getEnergyCapacityQuanta(stack)));
		super.addInformation(stack, player, list, ext);
	}

	@Override
	public boolean isArmorEnabled(ItemStack stack) {
		return getStoredEnergyQuanta(stack) > 0;
	}

	@Override
	public void receiveEnergyQuanta(ItemStack stack, long i) {
		if(stack.getItem() instanceof ArmorFSBPowered) {
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
		if(stack.getItem() instanceof ArmorFSBPowered) {
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
		if(stack.getItem() instanceof ArmorFSBPowered) {
			if(stack.hasTagCompound()) {
				EnergyUnits.writeEnergyQuanta(stack.stackTagCompound, EnergyUnits.readEnergyQuanta(stack.stackTagCompound, "charge") - i);
			} else {
				stack.stackTagCompound = new NBTTagCompound();
				EnergyUnits.writeEnergyQuanta(stack.stackTagCompound, getEnergyCapacityQuanta(stack) - i);
			}

			if(EnergyUnits.readEnergyQuanta(stack.stackTagCompound, "charge") < 0)
				EnergyUnits.writeEnergyQuanta(stack.stackTagCompound, 0);
		}
	}

	@Override
	public long getStoredEnergyQuanta(ItemStack stack) {
		if(stack.getItem() instanceof ArmorFSBPowered) {
			if(stack.hasTagCompound()) {
				return Math.min(EnergyUnits.readEnergyQuanta(stack.stackTagCompound, "charge"), getEnergyCapacityQuanta(stack));
			} else {
				stack.stackTagCompound = new NBTTagCompound();
				EnergyUnits.writeEnergyQuanta(stack.stackTagCompound, getEnergyCapacityQuanta(stack));
				return EnergyUnits.readEnergyQuanta(stack.stackTagCompound, "charge");
			}
		}

		return 0;
	}

	@Override
	public boolean showDurabilityBar(ItemStack stack) {
		return getStoredEnergyQuanta(stack) < getEnergyCapacityQuanta(stack);
	}

	@Override
	public double getDurabilityForDisplay(ItemStack stack) {

		return 1 - (double) getStoredEnergyQuanta(stack) / (double) getEnergyCapacityQuanta(stack);
	}

	@Override
	public long getEnergyCapacityQuanta(ItemStack stack) {
		if(ArmorModHandler.hasMods(stack)) {
			ItemStack mod = ArmorModHandler.pryMod(stack, ArmorModHandler.battery);
			if(mod != null && mod.getItem() instanceof ItemModBattery) {
				return (long) (maxPower * ((ItemModBattery) mod.getItem()).mod);
			}
		}
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
	public void setDamage(ItemStack stack, int damage) {
		this.extractEnergyQuanta(stack, damage * consumption);
	}

	public void onArmorTick(World world, EntityPlayer player, ItemStack itemStack) {

		super.onArmorTick(world, player, itemStack);

		if(this.drain > 0 && ArmorFSB.hasFSBArmor(player) && !player.capabilities.isCreativeMode) {
			this.extractEnergyQuanta(itemStack, drain);
		}
	}
}
