package com.hbm.handler;

import java.util.HashMap;
import java.util.Map;

import cpw.mods.fml.common.eventhandler.Event.Result;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.FillBucketEvent;

public class BucketHandler {

	public static BucketHandler INSTANCE = new BucketHandler();
	public Map<Block, Item> buckets = new HashMap<Block, Item>();

	private BucketHandler() {
	}

	@SubscribeEvent
	public void onBucketFill(FillBucketEvent event) {

		if(event.current == null || event.current.getItem() != net.minecraft.init.Items.bucket || event.target == null) return;
        ItemStack result;
        Block target = event.world.getBlock(event.target.blockX, event.target.blockY, event.target.blockZ);
        if(target.getMaterial() == net.minecraft.block.material.Material.water && event.world.getBlockMetadata(event.target.blockX, event.target.blockY, event.target.blockZ) == 0) {
            if(!event.world.isRemote) event.world.setBlockToAir(event.target.blockX, event.target.blockY, event.target.blockZ);
            result = waterBucket();
        } else result = fillCustomBucket(event.world, event.target);

		if (result == null)
			return;

		event.result = result;
		event.setResult(Result.ALLOW);
	}

	private ItemStack fillCustomBucket(World world, MovingObjectPosition pos) {

		Block block = world.getBlock(pos.blockX, pos.blockY, pos.blockZ);

		Item bucket = buckets.get(block);
		if (bucket != null && world.getBlockMetadata(pos.blockX, pos.blockY, pos.blockZ) == 0) {
			world.setBlockToAir(pos.blockX, pos.blockY, pos.blockZ);
			return new ItemStack(bucket);
		} else
			return null;

	}
    public static ItemStack stamp(ItemStack stack, int amount) {
        if(!stack.hasTagCompound()) stack.setTagCompound(new net.minecraft.nbt.NBTTagCompound());
        stack.getTagCompound().setInteger(com.hbm.inventory.FluidContainerRegistry.AMOUNT_KEY, amount); return stack;
    }
    public static ItemStack waterBucket() { return stamp(new ItemStack(net.minecraft.init.Items.water_bucket), 10); }

    @SubscribeEvent
    public void onBottleFill(net.minecraftforge.event.entity.player.PlayerInteractEvent event) {
        net.minecraft.entity.player.EntityPlayer player=event.entityPlayer; ItemStack held=player.getCurrentEquippedItem();
        if(held == null || held.getItem() != net.minecraft.init.Items.glass_bottle || event.action == net.minecraftforge.event.entity.player.PlayerInteractEvent.Action.LEFT_CLICK_BLOCK) return;
        World world=player.worldObj;
        net.minecraft.util.Vec3 start=net.minecraft.util.Vec3.createVectorHelper(player.posX,player.posY+1.62D-player.yOffset,player.posZ),look=player.getLook(1F);
        MovingObjectPosition hit=world.rayTraceBlocks(start,start.addVector(look.xCoord*5,look.yCoord*5,look.zCoord*5),true);
        if(hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK || world.getBlock(hit.blockX,hit.blockY,hit.blockZ).getMaterial() != net.minecraft.block.material.Material.water) return;
        if(!world.canMineBlock(player,hit.blockX,hit.blockY,hit.blockZ) || !player.canPlayerEdit(hit.blockX,hit.blockY,hit.blockZ,hit.sideHit,held)) return;
        event.setCanceled(true);
        if(!world.isRemote) giveFilled(player,held,stamp(new ItemStack(net.minecraft.init.Items.potionitem,1,0),1));
    }
    @SubscribeEvent
    public void onMilk(net.minecraftforge.event.entity.player.EntityInteractEvent event) {
        if(event.target.getClass() != net.minecraft.entity.passive.EntityCow.class && event.target.getClass() != net.minecraft.entity.passive.EntityMooshroom.class) return;
        ItemStack held=event.entityPlayer.getCurrentEquippedItem();
        if(held == null || held.getItem() != net.minecraft.init.Items.bucket) return;
        event.setCanceled(true);
        if(!event.entityPlayer.worldObj.isRemote) giveFilled(event.entityPlayer,held,stamp(new ItemStack(net.minecraft.init.Items.milk_bucket),10));
    }
    private static void giveFilled(net.minecraft.entity.player.EntityPlayer player,ItemStack held,ItemStack filled) {
        if(--held.stackSize <= 0) player.inventory.setInventorySlotContents(player.inventory.currentItem,filled);
        else if(!player.inventory.addItemStackToInventory(filled)) player.dropPlayerItemWithRandomChoice(filled,false);
        player.inventory.markDirty(); player.inventoryContainer.detectAndSendChanges();
    }
    /** Vanilla automation must stamp newly collected water too. Other dispenser behavior stays with its registered owner. */
    public static void installDispenserWaterFill() {
        final net.minecraft.dispenser.IBehaviorDispenseItem original=(net.minecraft.dispenser.IBehaviorDispenseItem)net.minecraft.block.BlockDispenser.dispenseBehaviorRegistry.getObject(net.minecraft.init.Items.bucket);
        final net.minecraft.dispenser.BehaviorDefaultDispenseItem water=new net.minecraft.dispenser.BehaviorDefaultDispenseItem() {
            @Override protected ItemStack dispenseStack(net.minecraft.dispenser.IBlockSource source,ItemStack empty) {
                net.minecraftforge.common.util.ForgeDirection d=net.minecraftforge.common.util.ForgeDirection.getOrientation(source.getBlockMetadata()&7);
                source.getWorld().setBlockToAir(source.getXInt()+d.offsetX,source.getYInt()+d.offsetY,source.getZInt()+d.offsetZ);
                ItemStack filled=waterBucket(); if(--empty.stackSize <= 0) return filled;
                net.minecraft.tileentity.TileEntityDispenser dispenser=(net.minecraft.tileentity.TileEntityDispenser)source.getBlockTileEntity();
                if(dispenser.func_146019_a(filled) < 0) new net.minecraft.dispenser.BehaviorDefaultDispenseItem().dispense(source,filled);
                return empty;
            }
        };
        net.minecraft.block.BlockDispenser.dispenseBehaviorRegistry.putObject(net.minecraft.init.Items.bucket,new net.minecraft.dispenser.IBehaviorDispenseItem() {
            @Override public ItemStack dispense(net.minecraft.dispenser.IBlockSource source,ItemStack stack) {
                net.minecraftforge.common.util.ForgeDirection d=net.minecraftforge.common.util.ForgeDirection.getOrientation(source.getBlockMetadata()&7);
                int x=source.getXInt()+d.offsetX,y=source.getYInt()+d.offsetY,z=source.getZInt()+d.offsetZ;
                return source.getWorld().getBlock(x,y,z).getMaterial() == net.minecraft.block.material.Material.water && source.getWorld().getBlockMetadata(x,y,z) == 0
                        ? water.dispense(source,stack) : original.dispense(source,stack);
            }
        });
    }

}
