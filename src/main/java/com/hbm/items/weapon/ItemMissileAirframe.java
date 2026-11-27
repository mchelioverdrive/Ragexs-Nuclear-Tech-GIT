package com.hbm.items.weapon;

import java.util.List;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

public class ItemMissileAirframe extends Item {

	public ItemMissileAirframe() {
		setHasSubtypes(true);
	}

	public static MissileDefinition getDefinition(ItemStack stack) {
		return stack != null && stack.getItem() instanceof ItemMissileAirframe
				? MissileDefinition.byId(stack.getItemDamage()) : null;
	}

	@Override
	public String getItemStackDisplayName(ItemStack stack) {
		MissileDefinition missile = getDefinition(stack);
		return missile == null ? super.getItemStackDisplayName(stack)
				: StatCollector.translateToLocalFormatted("missile.airframe.name",
						StatCollector.translateToLocal(missile.translationKey() + ".name"));
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void getSubItems(Item item, CreativeTabs tab, List list) {
		for(MissileDefinition missile : MissileDefinition.values()) list.add(missile.airframeStack());
	}

	@Override
	public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
		MissileDefinition missile = getDefinition(stack);
		if(missile != null) {
			list.add(StatCollector.translateToLocal(missile.classification.translationKey()));
			list.add(StatCollector.translateToLocal("missile.airframe.assembly"));
		}
	}
}
