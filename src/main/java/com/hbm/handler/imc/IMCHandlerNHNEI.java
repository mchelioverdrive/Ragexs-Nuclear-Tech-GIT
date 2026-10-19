package com.hbm.handler.imc;

import codechicken.nei.recipe.TemplateRecipeHandler;
import com.hbm.lib.RefStrings;
import com.hbm.main.NEIRegistry;

import cpw.mods.fml.common.event.FMLInterModComms;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

public class IMCHandlerNHNEI {

	public static void IMCSender() {

		for(TemplateRecipeHandler handler : NEIRegistry.listAllHandlers()) {

			Class<? extends TemplateRecipeHandler> handlerClass = handler.getClass();

			ItemStack[] machines = handler instanceof ICompatNHNEI ? ((ICompatNHNEI) handler).getMachinesForRecipe() : null;
			if(machines != null && machines.length > 0) {
				String blockName = net.minecraft.item.Item.itemRegistry.getNameForObject(machines[0].getItem());
				String hClass = handlerClass.getName();
				sendHandler(hClass, ((ICompatNHNEI) handler).getRecipeID(), blockName, ((ICompatNHNEI) handler).getNEIMaxRecipesPerPage(), ((ICompatNHNEI) handler).getNEIHandlerHeight());
				for(ItemStack stack : machines) {
					sendCatalyst(hClass, net.minecraft.item.Item.itemRegistry.getNameForObject(stack.getItem()));
				}
			}
		}
	}

	private static void sendHandler(String aName, String handlerID, String aBlock, int maxRecipesPerPage, int height) {
		NBTTagCompound aNBT = new NBTTagCompound();
		aNBT.setString("handler", aName);
		aNBT.setString("handlerID", handlerID);
		aNBT.setString("modName", RefStrings.NAME);
		aNBT.setString("modId", RefStrings.MODID);
		aNBT.setBoolean("modRequired", true);
		aNBT.setString("itemName", aBlock);
		aNBT.setInteger("handlerHeight", height);
		aNBT.setInteger("handlerWidth", 166);
		aNBT.setInteger("maxRecipesPerPage", maxRecipesPerPage);
		aNBT.setInteger("yShift", 6);
		FMLInterModComms.sendMessage("NotEnoughItems", "registerHandlerInfo", aNBT);
	}

	private static void sendCatalyst(String aName, String aStack, int aPriority) {
		NBTTagCompound aNBT = new NBTTagCompound();
		aNBT.setString("handlerID", aName);
		aNBT.setString("catalystHandlerID", aName);
		aNBT.setString("itemName", aStack);
		aNBT.setInteger("priority", aPriority);
		FMLInterModComms.sendMessage("NotEnoughItems", "registerCatalystInfo", aNBT);
	}

	private static void sendCatalyst(String aName, String aStack) {
		sendCatalyst(aName, aStack, 0);
	}
}
