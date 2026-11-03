package com.hbm.items.special;

import java.util.List;

import com.hbm.handler.disease.DiseaseEvents;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

/** The existing empty syringe collects only after a confirmed native player melee hit. */
public final class ItemSyringeEmpty extends Item {
	@Override
	public boolean hitEntity(ItemStack stack, EntityLivingBase target, EntityLivingBase user) {
		if(user instanceof EntityPlayer) DiseaseEvents.collectRabiesSample(stack, target, (EntityPlayer) user);
		return false;
	}

	@Override
	public void addInformation(ItemStack stack, EntityPlayer player, List lines, boolean advanced) {
		lines.add(StatCollector.translateToLocal("item.syringe_empty.desc"));
	}
}
