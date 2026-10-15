package com.hbm.items.machine;


import java.util.List;

import com.hbm.inventory.FluidStack;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.items.ModItems;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.IIcon;
import net.minecraft.util.StatCollector;

public class ItemFluidIcon extends Item {

	IIcon overlayIcon;

	public ItemFluidIcon() {
		this.setHasSubtypes(true);
		this.setMaxDamage(0);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void getSubItems(Item item, CreativeTabs tabs, List list) {
		FluidType[] order = Fluids.getInNiceOrder();
		for(int i = 1; i < order.length; ++i) {
			list.add(new ItemStack(item, 1, order[i].getID()));
		}
	}

	@Override
	public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean bool) {
		FluidType type = Fluids.fromID(stack.getItemDamage());
		if(getQuantity(stack) > 0) list.add(quantityText(stack));
		if(getPressure(stack) > 0) list.add("Network pressure class: " + getPressure(stack) + " PU (not physical bar)");
		type.addInfo(list);
		if(!stack.hasTagCompound() || !stack.getTagCompound().hasKey("physicalMass"))
			com.hbm.inventory.fluid.FluidInfo.appendStoredEnergy(list, type, getQuantity(stack));
	}

	public static String quantityText(ItemStack stack) {
		FluidType type = Fluids.fromID(stack.getItemDamage());
		if(stack.hasTagCompound() && stack.getTagCompound().hasKey("physicalMass")) {
			java.math.BigDecimal quantity = java.math.BigDecimal.valueOf(stack.getTagCompound().getLong("physicalMass"))
					.divide(java.math.BigDecimal.valueOf(com.hbm.realsim.PhysicalRegistry.get(type.getName()).massUnits), 6, java.math.RoundingMode.DOWN).stripTrailingZeros();
			return quantity.toPlainString() + " " + com.hbm.inventory.fluid.FluidInfo.unit(type) + " per batch (fractional output accumulates)";
		}
		return com.hbm.inventory.fluid.FluidInfo.amount(type, getQuantity(stack));
	}

	public static ItemStack addQuantity(ItemStack stack, int i) {
		if(!stack.hasTagCompound()) stack.stackTagCompound = new NBTTagCompound();
		stack.getTagCompound().setInteger("fill", i);
		return stack;
	}

	public static ItemStack addPressure(ItemStack stack, int i) {
		if(!stack.hasTagCompound()) stack.stackTagCompound = new NBTTagCompound();
		stack.getTagCompound().setInteger("pressure", i);
		return stack;
	}

	public static ItemStack make(FluidStack stack) {
		return make(stack.type, stack.fill, stack.pressure);
	}

	public static ItemStack makePhysical(FluidStack fluid, long mass) {
		if(mass <= 0) return null;
		ItemStack stack = make(fluid);
		stack.getTagCompound().setLong("physicalMass", mass);
		return stack;
	}

	public static ItemStack make(FluidType fluid, int i) {
		return make(fluid, i, 0);
	}

	public static ItemStack make(FluidType fluid, int i, int pressure) {
		return addPressure(addQuantity(new ItemStack(ModItems.fluid_icon, 1, fluid.ordinal()), i), pressure);
	}

	public static int getQuantity(ItemStack stack) {
		if(!stack.hasTagCompound()) return 0;
		return stack.getTagCompound().getInteger("fill");
	}

	public static int getPressure(ItemStack stack) {
		if(!stack.hasTagCompound()) return 0;
		return stack.getTagCompound().getInteger("pressure");
	}

	@Override
	public String getItemStackDisplayName(ItemStack stack) {
		FluidType type = Fluids.fromID(stack.getItemDamage());
		String s = (StatCollector.translateToLocal(type.getConditionalName())).trim();
		if(stack.hasTagCompound() && stack.getTagCompound().hasKey("pressure")
				&& (getPressure(stack) > 0 || type.getPhysicalState().phase == com.hbm.realsim.PhysicalState.Phase.GAS
				&& com.hbm.realsim.SteamLedger.state(type.getName()) == null)) {
			return getPressure(stack) == 0 ? StatCollector.translateToLocalFormatted("item.fluid_icon.uncompressed", s)
					: StatCollector.translateToLocalFormatted("item.fluid_icon.compressed", s, getPressure(stack));
		}

		if(s != null) {
			return s;
		}

		return "Unknown";
	}

	@Override
	@SideOnly(Side.CLIENT)
	public int getColorFromItemStack(ItemStack stack, int p_82790_2_) {
		int j = Fluids.fromID(stack.getItemDamage()).getColor();

		if(j < 0) {
			j = 16777215;
		}

		return j;
	}

}
