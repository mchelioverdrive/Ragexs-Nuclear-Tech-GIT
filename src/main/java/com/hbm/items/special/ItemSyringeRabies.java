package com.hbm.items.special;

import java.util.List;

import com.hbm.handler.disease.DiseaseManager;
import com.hbm.handler.disease.Rabies;
import com.hbm.items.ModItems;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

/** Uses native syringe interactions; establishment and the original timeline belong to DiseaseManager. */
public final class ItemSyringeRabies extends ItemSyringe {

	@Override
	public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
		if(world.isRemote || stack.stackSize <= 0 || !inject(player, player)) return stack;
		if(player.capabilities.isCreativeMode) return stack;
		if(--stack.stackSize <= 0) return new ItemStack(ModItems.syringe_empty);
		returnEmpty(player);
		return stack;
	}

	@Override
	public boolean hitEntity(ItemStack stack, EntityLivingBase target, EntityLivingBase user) {
		// Native hit-injection runs after attack/PvP/protection checks, as with the other syringes.
		if(target.worldObj.isRemote || stack.stackSize <= 0 || !(user instanceof EntityPlayer)) return false;
		EntityPlayer player = (EntityPlayer) user;
		if(inject(target, player) && !player.capabilities.isCreativeMode) {
			stack.stackSize--;
			returnEmpty(player);
		}
		return false;
	}

	private boolean inject(EntityLivingBase target, EntityPlayer user) {
		if(!DiseaseManager.infect(target, DiseaseManager.pathogen(Rabies.ID),
				"syringe:" + user.getUniqueID(), user.getCommandSenderName())) return false;
		target.worldObj.playSoundAtEntity(target, "hbm:item.syringe", 1.0F, 1.0F);
		return true;
	}

	private void returnEmpty(EntityPlayer player) {
		ItemStack empty = new ItemStack(ModItems.syringe_empty);
		if(!player.inventory.addItemStackToInventory(empty)) player.dropPlayerItemWithRandomChoice(empty, false);
	}

	@Override
	public void addInformation(ItemStack stack, EntityPlayer player, List lines, boolean advanced) {
		lines.add(StatCollector.translateToLocal("item.syringe_rabies.desc"));
	}
}
