package com.hbm.blocks.generic;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.hbm.blocks.ModBlocks;
import com.hbm.extprop.HbmLivingProps;
import com.hbm.extprop.HbmLivingProps.ContaminationEffect;
import com.hbm.handler.radiation.ChunkRadiationManager;
import com.hbm.items.ModItems;
import com.hbm.potion.HbmPotion;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

public class BlockFallout extends Block {


	public BlockFallout(Material mat) {
		super(mat);
		this.setBlockBounds(0.0F, 0.0F, 0.0F, 1.0F, 0.125F, 1.0F);
		if(this==ModBlocks.salted_fallout)
		{
			this.setTickRandomly(true);
		}
	}

	@Override
	public void setBlockBoundsBasedOnState(IBlockAccess world, int x, int y, int z) {
		int metadata = world.getBlockMetadata(x, y, z);
		float height = (1 + metadata) / 8.0F;
		this.setBlockBounds(0.0F, 0.0F, 0.0F, 1.0F, height, 1.0F);
	}

	@Override
	public int onBlockPlaced(World world, int x, int y, int z, int side, float hitX, float hitY, float hitZ, int metadata) {
		// Check if there's already a fallout block
		if (world.getBlock(x, y, z) == this) {
			int currentMetadata = world.getBlockMetadata(x, y, z);
			return Math.min(currentMetadata + 1, 7); // Increment metadata if already exists
		}
		return 0; // Place as a new block
	}



	public boolean isOpaqueCube() {
		return false;
	}

	public boolean renderAsNormalBlock() {
		return false;
	}

	@Override
	public boolean isNormalCube() {
		return false; // Avoid treating as a full cube
	}

	@Override
	public void onEntityCollidedWithBlock(World world, int x, int y, int z, Entity entity) {
		int metadata = world.getBlockMetadata(x, y, z);
		if (metadata < 7) {
			//idk what this is but its just wrong
			//entity.motionY = 0; // Prevent sinking if not full
			//entity.onGround = true;
		}
	}

	public Item getItemDropped(int p_149650_1_, Random p_149650_2_, int p_149650_3_) {
		return ModItems.fallout;
	}

	@Override
	public Item getItem(World world, int x, int y, int z) {
		return ModItems.fallout;
	}

	@Override
	public int damageDropped(int metadata) {
		return 0; // Ensure the item doesn't inherit the block's metadata
	}

	@Override
	public AxisAlignedBB getCollisionBoundingBoxFromPool(World world, int x, int y, int z) {
		int metadata = world.getBlockMetadata(x, y, z);
		float height = (1 + metadata) / 8.0F;
		if (metadata == 7) {
			return AxisAlignedBB.getBoundingBox(x, y, z, x + 1, y + 1, z + 1); // Full block
		}
		return AxisAlignedBB.getBoundingBox(x, y, z, x + 1, y + height - 0.001F, z + 1); // Slightly lower for smooth collision
	}

	@Override
	public void getSubBlocks(Item item, CreativeTabs tab, List list) {
		list.add(new ItemStack(item, 1, 0)); // Add only the base layer to the creative tab
	}

	@Override
	public String getLocalizedName() {
		return "Fallout Layer"; // Ensure the name doesn’t reflect metadata
	}

	@Override
	public int getDamageValue(World world, int x, int y, int z) {
		return 0; // Ensure the held item doesn’t reflect the block’s metadata
	}

	@Override
	public boolean isLadder(IBlockAccess world, int x, int y, int z, EntityLivingBase entity) {
		return false; // Fallout layers should not act like ladders
	}

	//@Override
	//public int getRenderBlockPass() {
	//	return 1; // Render in the translucent layer
	//}

	@Override
	public int quantityDropped(int metadata, int fortune, Random random) {
		return metadata + 1;
	}

	public boolean canPlaceBlockAt(World world, int x, int y, int z) {
		Block block = world.getBlock(x, y - 1, z);

		if (block == Blocks.ice || block == Blocks.packed_ice) return false;
		if (block.isLeaves(world, x, y - 1, z) && !block.isAir(world, x, y - 1, z)) return true;
		//if (block == this && (world.getBlockMetadata(x, y - 1, z) & 7) == 7) return true;

		if (block == this) {
			return world.getBlockMetadata(x, y - 1, z) < 7; // Allow stacking if metadata < 7
		}

		return (block.isOpaqueCube() || block.isLeaves(world, x, y - 1, z) ) && block.getMaterial().blocksMovement();
	}

	@Override
	public void onEntityWalking(World world, int x, int y, int z, Entity entity) {

		if(!world.isRemote && entity instanceof EntityLivingBase) {
			if(entity instanceof EntityPlayer && ((EntityPlayer)entity).capabilities.isCreativeMode) return;
			PotionEffect effect = new PotionEffect(HbmPotion.radiation.id, 10 * 60 * 20, 0);
			effect.setCurativeItems(new ArrayList());
			((EntityLivingBase) entity).addPotionEffect(effect);
		}
	}

	public void onBlockClicked(World world, int x, int y, int z, EntityPlayer player) {

		if(!world.isRemote) {
			HbmLivingProps.addCont(player, new ContaminationEffect(1F, 200, false));
		}
	}

	@Override
	public void onNeighborBlockChange(World world, int x, int y, int z, Block b) {
		this.func_150155_m(world, x, y, z);
		if (!this.canPlaceBlockAt(world, x, y, z)) {
			world.setBlockToAir(x, y, z);
		} else {
			int metadata = world.getBlockMetadata(x, y, z);
			if (metadata <= -1) {
				world.setBlockToAir(x, y, z);
			}
		}
	}

	private boolean func_150155_m(World world, int x, int y, int z) {
		if(!this.canPlaceBlockAt(world, x, y, z)) {
			world.setBlockToAir(x, y, z);
			return false;
		} else {
			return true;
		}
	}

	public void onBlockAdded(World world, int x, int y, int z) {
		super.onBlockAdded(world, x, y, z);
	}

	public boolean isReplaceable(IBlockAccess world, int x, int y, int z) {
		return true;
	}

	@Override
	public void updateTick(World world, int x, int y, int z, Random rand) {
		int metadata = world.getBlockMetadata(x, y, z);
		if(this==ModBlocks.salted_fallout)
		{
			ChunkRadiationManager.proxy.incrementRad(world, x, y, z, 50 * (metadata + 1));
		}
	}


}
