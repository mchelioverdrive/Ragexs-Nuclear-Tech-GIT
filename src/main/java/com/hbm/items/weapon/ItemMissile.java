package com.hbm.items.weapon;

import com.hbm.inventory.fluid.FluidVolume;

import java.util.List;

import com.hbm.items.ItemCustomLore;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

public class ItemMissile extends ItemCustomLore {
	
	public final MissileFormFactor formFactor;
	public final MissileTier tier;
	public final MissileFuel fuel;
	public final MissileDefinition definition;
	public int fuelCap;
	public boolean launchable = true;
	
	public ItemMissile(MissileFormFactor form, MissileTier tier) {
		this(form, tier, form.defaultFuel);
	}
	
	public ItemMissile(MissileFormFactor form, MissileTier tier, MissileFuel fuel) {
		this(form, tier, fuel, null);
	}

	public ItemMissile(MissileDefinition definition) {
		this(definition.form, definition.generation.tier, definition.fuel, definition);
		setFuelCap(definition.fuelCapacity);
	}

	private ItemMissile(MissileFormFactor form, MissileTier tier, MissileFuel fuel, MissileDefinition definition) {
		this.formFactor = form;
		this.tier = tier;
		this.fuel = fuel;
		this.definition = definition;
		this.setFuelCap(this.fuel.defaultCap);
	}
	
	public ItemMissile notLaunchable() {
		this.launchable = false;
		return this;
	}
	
	public ItemMissile setFuelCap(int fuelCap) {
		this.fuelCap = fuelCap;
		return this;
	}

	@Override
	public String getUnlocalizedName(ItemStack stack) {
		if(definition == null) return super.getUnlocalizedName(stack);
		return definition.translationKey() + (launchable ? "" : ".damaged");
	}
	
	@Override
	public void addInformation(ItemStack itemstack, EntityPlayer player, List list, boolean bool) {
		if(definition != null) {
			list.add(StatCollector.translateToLocalFormatted("missile.tooltip.identity",
					StatCollector.translateToLocal(definition.country.translationKey()),
					StatCollector.translateToLocal(definition.classification.translationKey())));
			list.add(StatCollector.translateToLocal(definition.generation.translationKey()));
			list.add(StatCollector.translateToLocalFormatted("missile.tooltip.payload",
					StatCollector.translateToLocal(definition.payload.translationKey())));
			list.add(StatCollector.translateToLocal(definition.translationKey() + ".purpose"));
		}
		list.add(EnumChatFormatting.ITALIC + this.tier.display);
		
		if(!this.launchable) {
			list.add(EnumChatFormatting.RED + "Not launchable!");
		} else {
			list.add("Fuel: " + this.fuel.display);
			if(this.fuelCap > 0) list.add("Fuel capacity: " + FluidVolume.format(this.fuelCap));
			if(definition == null) super.addInformation(itemstack, player, list, bool);
		}
	}
	
	public enum MissileFormFactor {
		ABM(MissileFuel.SOLID),
		MICRO(MissileFuel.SOLID),
		V2(MissileFuel.ETHANOL_PEROXIDE),
		STRONG(MissileFuel.KEROSENE_PEROXIDE),
		HUGE(MissileFuel.KEROSENE_LOXY),
		ATLAS(MissileFuel.JETFUEL_LOXY),
		OTHER(MissileFuel.KEROSENE_PEROXIDE);
		
		protected MissileFuel defaultFuel;
		
		private MissileFormFactor(MissileFuel defaultFuel) {
			this.defaultFuel = defaultFuel;
		}
	}
	
	public enum MissileTier {
		TIER0("Tier 0"),
		TIER1("Tier 1"),
		TIER2("Tier 2"),
		TIER3("Tier 3"),
		TIER4("Tier 4");
		
		public String display;
		
		private MissileTier(String display) {
			this.display = display;
		}
	}
	
	public enum MissileFuel {
		SOLID(EnumChatFormatting.GOLD + "Solid Rocket Fuel", 250),
		ETHANOL_PEROXIDE(EnumChatFormatting.AQUA + "Ethanol / Hydrogen Peroxide", 4_000),
		KEROSENE_PEROXIDE(EnumChatFormatting.BLUE + "Kerosene / Hydrogen Peroxide", 8_000),
		KEROSENE_LOXY(EnumChatFormatting.LIGHT_PURPLE + "Kerosene / Liquid Oxygen", 12_000),
		JETFUEL_LOXY(EnumChatFormatting.RED + "Jet Fuel / Liquid Oxygen", 16_000);
		
		public String display;
		public int defaultCap;
		
		private MissileFuel(String display, int defaultCap) {
			this.display = display;
			this.defaultCap = defaultCap;
		}
	}
}
