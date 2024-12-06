package com.hbm.items.food;

import com.hbm.items.ModItems;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumAction;
import net.minecraft.item.Item;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.world.World;

public class Itemgalaxygas extends ItemFood {
	//god that was painful
	public Itemgalaxygas(int hunger) {
		super(hunger, false);
		this.setAlwaysEdible();
	}

	@Override
	public int getMaxItemUseDuration(ItemStack stack) {
		return 40;
	}

	@Override
	public EnumAction getItemUseAction(ItemStack stack) {
		return EnumAction.drink;
	}
	@Override
	public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
		player.setItemInUse(stack, this.getMaxItemUseDuration(stack));
		return stack;
	}

	//@Override
	//@SideOnly(Side.CLIENT)
	//public boolean hasEffect(ItemStack p_77636_1_)
	//{
	//	return p_77636_1_.getItemDamage() == 2;
	//}

	//@Override
	//protected void onFoodEaten(ItemStack stack, World world, EntityPlayer player) {
	//	double slowdownChance = 0.5; // 50% chance
	//	double confusionChance = 0.3; // 30% chance
	//	double blindnessChance = 0.2; // 20% chance
	//	double weaknessChance = 0.4; // 40% chance
//
	//	// Apply effects based on random chance
	//	if (Math.random() < slowdownChance) {
	//		player.removePotionEffect(Potion.moveSlowdown.id);
	//		player.addPotionEffect(new PotionEffect(Potion.moveSlowdown.id, 20 * 20, 1));
	//	}
	//	if (Math.random() < confusionChance) {
	//		player.removePotionEffect(Potion.confusion.id);
	//		player.addPotionEffect(new PotionEffect(Potion.confusion.id, 20 * 20, 2));
	//	}
	//	if (Math.random() < blindnessChance) {
	//		player.removePotionEffect(Potion.blindness.id);
	//		player.addPotionEffect(new PotionEffect(Potion.blindness.id, 40 * 20, 2));
	//	}
	//	if (Math.random() < weaknessChance) {
	//		player.removePotionEffect(Potion.weakness.id);
	//		player.addPotionEffect(new PotionEffect(Potion.weakness.id, 10 * 20, 2));
	//	}
	//}

	@Override
	protected void onFoodEaten(ItemStack stack, World world, EntityPlayer player) {
		if(!world.isRemote) {
			double slowdownChance = 0.5; // 50% chance
			double confusionChance = 0.3; // 30% chance
			double blindnessChance = 0.2; // 20% chance
			double weaknessChance = 0.4; // 40% chance

			// Apply effects based on random chance
			if (Math.random() < slowdownChance) {
				player.removePotionEffect(Potion.moveSlowdown.id);
				player.addPotionEffect(new PotionEffect(Potion.moveSlowdown.id, 20 * 20, 1));
			}
			if (Math.random() < confusionChance) {
				player.removePotionEffect(Potion.confusion.id);
				player.addPotionEffect(new PotionEffect(Potion.confusion.id, 20 * 20, 2));
			}
			if (Math.random() < blindnessChance) {
				player.removePotionEffect(Potion.blindness.id);
				player.addPotionEffect(new PotionEffect(Potion.blindness.id, 40 * 20, 2));
			}
			if (Math.random() < weaknessChance) {
				player.removePotionEffect(Potion.weakness.id);
				player.addPotionEffect(new PotionEffect(Potion.weakness.id, 10 * 20, 2));
			}
			//player.addPotionEffect(new PotionEffect(Potion.moveSpeed.id, 200));

		}

	}




	//@Override
	//public ItemStack onEaten(ItemStack stack, World world, EntityPlayer player) {
//
//
	//	// Reduce the stack size if the player is not in Creative mode
	//	if (!player.capabilities.isCreativeMode) {
	//		--stack.stackSize;
	//	}
//
	//	return stack;
	//}

	@Override
	public void onUsingTick(ItemStack stack, EntityPlayer player, int count) {

		if(count % 5 == 0 && count >= 10) {
			player.playSound("hbm:player.gulp", 1F, 1F);
		}

		if(count == 1) {
			this.onEaten(stack, player.worldObj, player);
			player.clearItemInUse();
			player.itemInUseCount = 10;
			player.playSound("hbm:player.groan", 1F, 1F);
			return;
		}

		if(count <= 24 && count % 4 == 0) {
			player.itemInUseCount--;
		}
	}
}
