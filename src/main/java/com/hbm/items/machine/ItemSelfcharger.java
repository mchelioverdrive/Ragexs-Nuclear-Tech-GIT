package com.hbm.items.machine;

import java.util.List;

import com.hbm.util.BobMathUtil;

import api.hbm.energymk2.IBatteryItem;
import api.hbm.energymk2.EnergyUnits;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

public class ItemSelfcharger extends Item implements IBatteryItem {
	
	long charge;
	
	public ItemSelfcharger(long charge) {
		this.charge = charge;
	}

	@Override
	public void addInformation(ItemStack itemstack, EntityPlayer player, List list, boolean bool) {
		list.add(EnumChatFormatting.YELLOW + "Output: " + EnergyUnits.formatQuantaPerTickAsWatts(charge));
	}

	@Override
	public void receiveEnergyQuanta(ItemStack stack, long i) { }

	@Override
	public void setStoredEnergyQuanta(ItemStack stack, long i) { }

	@Override
	public void extractEnergyQuanta(ItemStack stack, long i) { }

	@Override
	public long getStoredEnergyQuanta(ItemStack stack) {
		return charge;
	}

	@Override
	public long getEnergyCapacityQuanta(ItemStack stack) {
		return charge;
	}

	@Override
	public long getMaxInputQuantaPerTick() {
		return 0;
	}

	@Override
	public long getMaxOutputQuantaPerTick() {
		return charge;
	}

}
