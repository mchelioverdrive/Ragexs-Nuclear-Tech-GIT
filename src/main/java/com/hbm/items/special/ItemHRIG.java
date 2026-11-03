package com.hbm.items.special;

import java.util.List;

import com.hbm.config.VersatileConfig;
import com.hbm.handler.disease.Rabies;
import com.hbm.items.ModItems;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

/** Immediate injection for players and villagers, independent of drinking; no stored protection. */
public final class ItemHRIG extends ItemSyringe {
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
		if(target.worldObj.isRemote || stack.stackSize <= 0 || !(user instanceof EntityPlayer)) return false;
		EntityPlayer player = (EntityPlayer) user;
		if(!(target instanceof EntityPlayer || target instanceof EntityVillager)) {
			player.addChatMessage(new ChatComponentTranslation("hrig.players_only"));
			return false;
		}
		if(inject(target, player) && !player.capabilities.isCreativeMode) {
			stack.stackSize--;
			returnEmpty(player);
		}
		return false; // Native syringe convention: no additional item durability damage.
	}

	/** Called only by the pre-trading interaction event, which cancels vanilla item/trade dispatch. */
	public void treatVillager(ItemStack stack, EntityVillager target, EntityPlayer user) {
		if(target.worldObj.isRemote || stack.stackSize <= 0 || !inject(target, user)
				|| user.capabilities.isCreativeMode) return;
		if(--stack.stackSize <= 0)
			user.inventory.setInventorySlotContents(user.inventory.currentItem, new ItemStack(ModItems.syringe_empty));
		else returnEmpty(user);
		user.inventory.markDirty();
	}

	@Override
	public boolean itemInteractionForEntity(ItemStack stack, EntityPlayer user, EntityLivingBase target) {
		// Client acknowledgement for babies/sneaking/trading villagers; the server event owns treatment.
		return target instanceof EntityVillager;
	}

	private boolean inject(EntityLivingBase target, EntityPlayer user) {
		if(VersatileConfig.hasPotionSickness(target)) {
			user.addChatMessage(new ChatComponentTranslation("hrig.potion_sickness"));
			return false;
		}
		String result = Rabies.treat(target);
		if("hrig.uninfected".equals(result) && target instanceof EntityVillager) {
			user.addChatMessage(new ChatComponentTranslation("hrig.villager_uninfected"));
			return false;
		}
		user.addChatMessage(new ChatComponentTranslation(result));
		if(!"hrig.success".equals(result) && !"hrig.uninfected".equals(result)) return false;
		target.worldObj.playSoundAtEntity(target, "hbm:item.syringe", 1.0F, 1.0F);
		VersatileConfig.applyPotionSickness(target, 5);
		return true;
	}

	private void returnEmpty(EntityPlayer player) {
		ItemStack empty = new ItemStack(ModItems.syringe_empty);
		if(!player.inventory.addItemStackToInventory(empty)) player.dropPlayerItemWithRandomChoice(empty, false);
	}

	@Override
	public void addInformation(ItemStack stack, EntityPlayer player, List lines, boolean advanced) {
		for(int i = 1; i <= 2; i++) lines.add(StatCollector.translateToLocal("item.syringe_hrig.desc." + i));
	}
}
