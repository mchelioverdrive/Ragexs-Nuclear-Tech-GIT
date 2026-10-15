package com.hbm.handler;

import java.util.HashMap;

import com.hbm.inventory.RecipesCommon.ComparableStack;
import com.hbm.items.ModItems;

import cpw.mods.fml.common.IFuelHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntityFurnace;

public class FuelHandler implements IFuelHandler {

	@Override
	public int getBurnTime(ItemStack fuel) {

		if(fuel == null) return 0;
		int physical = com.hbm.realsim.Combustion.legacySolidTicks(fuel, 1600);
		if(physical > 0) return physical;

		int single = 200;

		if(fuel.getItem().equals(ModItems.solid_fuel))						return single * 16;
		if(fuel.getItem().equals(ModItems.solid_fuel_presto))				return single * 40;
		if(fuel.getItem().equals(ModItems.solid_fuel_presto_triplet))		return single * 200;
		//why the fuck are presto logs made from tatb and ...... BOOOOOOB YOU FUCK Ok it is fixed

		//bullshit
		//if(fuel.getItem().equals(ModItems.solid_fuel_bf))					return single * 160;
		//if(fuel.getItem().equals(ModItems.solid_fuel_presto_bf))			return single * 400;
		//if(fuel.getItem().equals(ModItems.solid_fuel_presto_triplet_bf))	return single * 2000;
		//uhhhhh???
		if(fuel.getItem().equals(ModItems.rocket_fuel))						return single * 32;

		if(fuel.getItem() == ModItems.coal_infernal) return 4800;
		if(fuel.getItem() == ModItems.crystal_coal) return 6400;

		return 0;
	}

	private static HashMap<ComparableStack, Integer> burnCache = new HashMap();

	public static int getBurnTimeFromCache(ItemStack stack) {
		if(stack == null) return 0;
		int physical = com.hbm.realsim.Combustion.legacySolidTicks(stack, 1600);
		if(physical > 0) return physical;

		ComparableStack comp = new ComparableStack(stack).makeSingular();

		if(burnCache.containsKey(comp)) {
			return burnCache.get(comp);
		}

		int burnTime = TileEntityFurnace.getItemBurnTime(stack);
		burnCache.put(comp, burnTime);

		return burnTime;
	}
}
