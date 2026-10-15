package com.hbm.inventory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.hbm.blocks.ModBlocks;
import com.hbm.config.GeneralConfig;
import com.hbm.dim.SolarSystem;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.Fluids.CD_Canister;
import com.hbm.inventory.fluid.Fluids.CD_Gastank;
import com.hbm.items.ModItems;
import com.hbm.util.Compat;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

public class FluidContainerRegistry {
	public static final String AMOUNT_KEY = "realSimContainerAmount";
	private static final HashMap<net.minecraft.item.Item, List<FluidContainer>> itemMap = new HashMap<>();

	public static int normalizedCapacity(net.minecraft.item.Item item, FluidType type, int legacy) {
        if(item == Items.water_bucket || item == Items.milk_bucket) return 10;
        if(item == Items.potionitem && type == com.hbm.inventory.fluid.Fluids.WATER) return 1;
		if(type.getPhysicalState().basis == com.hbm.realsim.PhysicalState.Basis.STANDARD_GAS_LITER && item == ModItems.canister_full) return 500;
		if(type.getPhysicalState().basis != com.hbm.realsim.PhysicalState.Basis.LIQUID_LITER) return legacy;
		if(item == ModItems.canister_full || item == ModItems.fluid_tank_full || item == ModItems.fluid_tank_lead_full
				|| item == ModItems.disperser_canister || item == ModItems.gas_full) return 20;
		if(item == ModItems.fluid_barrel_full) return 200;
		return legacy;
	}

	/** Compare identity and existing tags while accepting both old and newly stamped quantities. */
	public static boolean matchesFilled(ItemStack template, ItemStack stack) {
		if(template == null || stack == null || !template.isItemEqual(stack)) return false;
		ItemStack a = template.copy(), b = stack.copy();
		for(ItemStack value : new ItemStack[] {a, b}) if(value.hasTagCompound()) {
			value.getTagCompound().removeTag(AMOUNT_KEY);
			if(value.getTagCompound().hasNoTags()) value.setTagCompound(null);
		}
		return ItemStack.areItemStackTagsEqual(a, b);
	}

	public static FluidContainer filledDescriptor(ItemStack stack) {
		List<FluidContainer> candidates = itemMap.get(stack.getItem());
		if(candidates != null) for(FluidContainer container : candidates)
			if(matchesFilled(container.fullContainer, stack)) return container;
		return null;
	}

	public static List<FluidContainer> itemDescriptors(ItemStack stack) { return itemMap.get(stack.getItem()); }

	/** For creative/new reward output only. Existing untagged inventory remains legacy stock. */
	public static ItemStack newFilledStack(ItemStack stack) {
		FluidContainer container = filledDescriptor(stack);
		if(container != null && container.content != container.legacyContent) {
			if(!stack.hasTagCompound()) stack.setTagCompound(new net.minecraft.nbt.NBTTagCompound());
			stack.getTagCompound().setInteger(AMOUNT_KEY, container.content);
		}
		return stack;
	}

	//TODO: continue incorporating hashmaps into this
	public static List<FluidContainer> allContainers = new ArrayList<FluidContainer>();
	private static HashMap<FluidType, List<FluidContainer>> containerMap = new HashMap<FluidType, List<FluidContainer>>();

	public static void register() {
		FluidContainerRegistry.registerContainer(new FluidContainer(new ItemStack(Items.water_bucket), new ItemStack(Items.bucket), Fluids.WATER, 1000));
		FluidContainerRegistry.registerContainer(new FluidContainer(new ItemStack(Items.potionitem), new ItemStack(Items.glass_bottle), Fluids.WATER, 250));
		FluidContainerRegistry.registerContainer(new FluidContainer(new ItemStack(Items.lava_bucket), new ItemStack(Items.bucket), Fluids.LAVA, 1000));
		//FluidContainerRegistry.registerContainer(new FluidContainer(new ItemStack(ModItems.bucket_mud), new ItemStack(Items.bucket), Fluids.WATZ, 1000));
		FluidContainerRegistry.registerContainer(new FluidContainer(new ItemStack(ModItems.bucket_schrabidic_acid), new ItemStack(Items.bucket), Fluids.SCHRABIDIC, 1000));
		FluidContainerRegistry.registerContainer(new FluidContainer(new ItemStack(ModItems.bucket_sulfuric_acid), new ItemStack(Items.bucket), Fluids.SULFURIC_ACID, 1000));
		FluidContainerRegistry.registerContainer(new FluidContainer(new ItemStack(ModItems.bucket_mercury), new ItemStack(Items.bucket), Fluids.MERCURY, 1000));
		FluidContainerRegistry.registerContainer(new FluidContainer(new ItemStack(ModItems.bucket_bromine), new ItemStack(Items.bucket), Fluids.BROMINE, 1000));

		FluidContainerRegistry.registerContainer(new FluidContainer(new ItemStack(ModBlocks.red_barrel), new ItemStack(ModItems.tank_steel), Fluids.DIESEL, 10000));
		FluidContainerRegistry.registerContainer(new FluidContainer(new ItemStack(ModBlocks.pink_barrel), new ItemStack(ModItems.tank_steel), Fluids.KEROSENE, 10000));
		FluidContainerRegistry.registerContainer(new FluidContainer(new ItemStack(ModBlocks.lox_barrel), new ItemStack(ModItems.tank_steel), Fluids.OXYGEN, 10000));

		FluidContainerRegistry.registerContainer(new FluidContainer(new ItemStack(ModBlocks.ore_gneiss_gas), null, Fluids.PETROLEUM, GeneralConfig.enable528 ? 50 : 250));

		for(int i = 0; i < SolarSystem.Body.values().length; i++) {
			FluidContainerRegistry.registerContainer(new FluidContainer(new ItemStack(ModBlocks.ore_oil, 1, i), null, Fluids.OIL, 250));
			FluidContainerRegistry.registerContainer(new FluidContainer(new ItemStack(ModBlocks.ore_gas, 1, i), null, Fluids.GAS, 100));
			FluidContainerRegistry.registerContainer(new FluidContainer(new ItemStack(ModBlocks.ore_shale, 1, i), null, Fluids.GAS, GeneralConfig.enable528 ? 50 : 250));
		}

		FluidContainerRegistry.registerContainer(new FluidContainer(new ItemStack(ModItems.cell_deuterium), new ItemStack(ModItems.cell_empty), Fluids.DEUTERIUM, 1000));
		FluidContainerRegistry.registerContainer(new FluidContainer(new ItemStack(ModItems.cell_tritium), new ItemStack(ModItems.cell_empty), Fluids.TRITIUM, 1000));
		FluidContainerRegistry.registerContainer(new FluidContainer(new ItemStack(ModItems.cell_uf6), new ItemStack(ModItems.cell_empty), Fluids.UF6, 1000));
		FluidContainerRegistry.registerContainer(new FluidContainer(new ItemStack(ModItems.cell_puf6), new ItemStack(ModItems.cell_empty), Fluids.PUF6, 1000));
		FluidContainerRegistry.registerContainer(new FluidContainer(new ItemStack(ModItems.cell_antimatter), new ItemStack(ModItems.cell_empty), Fluids.AMAT, 1000));
		FluidContainerRegistry.registerContainer(new FluidContainer(new ItemStack(ModItems.cell_anti_schrabidium), new ItemStack(ModItems.cell_empty), Fluids.ASCHRAB, 1000));
		FluidContainerRegistry.registerContainer(new FluidContainer(new ItemStack(ModItems.cell_sas3), new ItemStack(ModItems.cell_empty), Fluids.SAS3, 1000));
		FluidContainerRegistry.registerContainer(new FluidContainer(new ItemStack(ModItems.bottle_mercury), new ItemStack(Items.glass_bottle), Fluids.MERCURY, 1000));
		FluidContainerRegistry.registerContainer(new FluidContainer(new ItemStack(ModItems.ingot_mercury), null, Fluids.MERCURY, 125));

		FluidContainerRegistry.registerContainer(new FluidContainer(new ItemStack(ModItems.rod_zirnox_tritium), new ItemStack(ModItems.rod_zirnox_empty), Fluids.TRITIUM, 2000));

		FluidContainerRegistry.registerContainer(new FluidContainer(new ItemStack(ModItems.particle_hydrogen), new ItemStack(ModItems.particle_empty), Fluids.HYDROGEN, 1000));
		FluidContainerRegistry.registerContainer(new FluidContainer(new ItemStack(ModItems.particle_amat), new ItemStack(ModItems.particle_empty), Fluids.AMAT, 1000));
		FluidContainerRegistry.registerContainer(new FluidContainer(new ItemStack(ModItems.particle_aschrab), new ItemStack(ModItems.particle_empty), Fluids.ASCHRAB, 1000));

		FluidContainerRegistry.registerContainer(new FluidContainer(new ItemStack(ModItems.iv_blood), new ItemStack(ModItems.iv_empty), Fluids.BLOOD, 100));
		//FluidContainerRegistry.registerContainer(new FluidContainer(new ItemStack(ModItems.iv_xp), new ItemStack(ModItems.iv_xp_empty), Fluids.XPJUICE, 100));
		FluidContainerRegistry.registerContainer(new FluidContainer(new ItemStack(ModItems.iv_blood), new ItemStack(ModItems.iv_empty), Fluids.BLOOD, 100));
		//FluidContainerRegistry.registerContainer(new FluidContainer(new ItemStack(Items.experience_bottle), new ItemStack(Items.glass_bottle), Fluids.XPJUICE, 100));

		FluidContainerRegistry.registerContainer(new FluidContainer(new ItemStack(ModItems.glass_smilk), new ItemStack(ModItems.glass_empty), Fluids.SMILK, 100));
		FluidContainerRegistry.registerContainer(new FluidContainer(new ItemStack(ModItems.can_mug), new ItemStack(ModItems.can_empty), Fluids.MUG, 100));
		FluidContainerRegistry.registerContainer(new FluidContainer(new ItemStack(Items.milk_bucket), new ItemStack(Items.bucket), Fluids.MILK, 1000));
		FluidContainerRegistry.registerContainer(new FluidContainer(new ItemStack(ModItems.coffee), new ItemStack(ModItems.cmug_empty), Fluids.COFFEE, 100));
		FluidContainerRegistry.registerContainer(new FluidContainer(new ItemStack(ModItems.teacup), new ItemStack(ModItems.teacup_empty), Fluids.TEA, 100));
		FluidContainerRegistry.registerContainer(new FluidContainer(new ItemStack(ModItems.bottle_honey), new ItemStack(Items.glass_bottle), Fluids.HONEY, 100));

		FluidContainerRegistry.registerContainer(new FluidContainer(new ItemStack(ModBlocks.dry_ice), null, Fluids.CARBONDIOXIDE, 1000)); //literal block of carbon dioxide
		//will be useful to heat up atmospheres, trust me.

		FluidType[] fluids = Fluids.getAll();
		for(int i = 1; i < fluids.length; i++) {

			FluidType type = fluids[i];
			int id = type.getID();

			if(type.getContainer(CD_Canister.class) != null) FluidContainerRegistry.registerContainer(new FluidContainer(new ItemStack(ModItems.canister_full, 1, id), new ItemStack(ModItems.canister_empty), type, 1000));
			if(type.getContainer(CD_Gastank.class) != null) FluidContainerRegistry.registerContainer(new FluidContainer(new ItemStack(ModItems.gas_full, 1, id), new ItemStack(ModItems.gas_empty), type, 1000));

			if(type.hasNoContainer()) continue;

			if(type.isDispersable()){
				FluidContainerRegistry.registerContainer(new FluidContainer(new ItemStack(ModItems.disperser_canister, 1 , i), new ItemStack(ModItems.disperser_canister_empty), Fluids.fromID(i), 2000));
				//FluidContainerRegistry.registerContainer(new FluidContainer(new ItemStack(ModItems.glyphid_gland, 1 , i), new ItemStack(ModItems.glyphid_gland_empty), Fluids.fromID(i), 4000));
			}

			FluidContainerRegistry.registerContainer(new FluidContainer(new ItemStack(ModItems.fluid_tank_lead_full, 1, id), new ItemStack(ModItems.fluid_tank_lead_empty), type, 1000));

			if(type.needsLeadContainer()) continue;

			FluidContainerRegistry.registerContainer(new FluidContainer(new ItemStack(ModItems.fluid_tank_full, 1, id), new ItemStack(ModItems.fluid_tank_empty), type, 1000));
			FluidContainerRegistry.registerContainer(new FluidContainer(new ItemStack(ModItems.fluid_barrel_full, 1, id), new ItemStack(ModItems.fluid_barrel_empty), type, 16000));
		}

		Compat.registerCompatFluidContainers();
	}

	public static void registerContainer(FluidContainer con) {
		allContainers.add(con);
		for(ItemStack stack : new ItemStack[] {con.fullContainer, con.emptyContainer}) if(stack != null) {
			List<FluidContainer> candidates = itemMap.get(stack.getItem());
			if(candidates == null) { candidates = new ArrayList<>(); itemMap.put(stack.getItem(), candidates); }
			candidates.add(con);
		}
		OreDictionary.registerOre(con.type.getDict(con.content), con.fullContainer);

		if(!containerMap.containsKey(con.type))
			containerMap.put(con.type, new ArrayList<FluidContainer>());

		List<FluidContainer> items = containerMap.get(con.type);
		items.add(con);
	}

	public static List<FluidContainer> getContainers(FluidType type) {
		return containerMap.get(type);
	}

	public static FluidContainer getContainer(FluidType type, ItemStack stack) {
		if(stack == null)
			return null;

		ItemStack sta = stack.copy();
		sta.stackSize = 1;

		if (!containerMap.containsKey(type))
			return null;

		for (FluidContainer container : getContainers(type)) {
			if (ItemStack.areItemStacksEqual(container.emptyContainer, sta) && ItemStack.areItemStackTagsEqual(container.emptyContainer, sta)) {
				return container;
			}
		}

		return null;
	}

	public static int getFluidContent(ItemStack stack, FluidType type) {

		if(stack == null)
			return 0;

		ItemStack sta = stack.copy();
		sta.stackSize = 1;

		if (!containerMap.containsKey(type))
			return 0;

		for(FluidContainer container : containerMap.get(type)) {
			if(matchesFilled(container.fullContainer, sta))
				return container.amount(stack);
		}

		return 0;
	}

	public static FluidType getFluidType(ItemStack stack) {

		if(stack == null)
			return Fluids.NONE;

		ItemStack sta = stack.copy();
		sta.stackSize = 1;

		for(FluidContainer container : allContainers) {
			if(matchesFilled(container.fullContainer, sta))
				return container.type;
		}

		return Fluids.NONE;
	}

	public static ItemStack getFullContainer(ItemStack stack, FluidType type) {
		if(stack == null)
			return null;

		ItemStack sta = stack.copy();
		sta.stackSize = 1;

		if (!containerMap.containsKey(type))
			return null;

		for(FluidContainer container : containerMap.get(type)) {
			if(ItemStack.areItemStacksEqual(container.emptyContainer, sta) &&  ItemStack.areItemStackTagsEqual(container.emptyContainer, sta))
				return container.fullContainer.copy();
		}

		return null;
	}

	public static ItemStack getEmptyContainer(ItemStack stack) {
		if(stack == null)
			return null;

		ItemStack sta = stack.copy();
		sta.stackSize = 1;

		for(FluidContainer container : allContainers) {
			if(matchesFilled(container.fullContainer, sta))
				return container.emptyContainer == null ? null : container.emptyContainer.copy();
		}

		return null;
	}

}
