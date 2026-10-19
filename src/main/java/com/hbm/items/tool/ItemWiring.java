package com.hbm.items.tool;

import java.util.List;

import com.hbm.main.MainRegistry;
import com.hbm.tileentity.network.TileEntityPylonBase;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

public class ItemWiring extends Item {

	private static final String SELECTION = "wireSelection";


	@Override
	public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side, float hitX, float hitY, float hitZ) {
		if(player.isSneaking()) return false;
		// Vanilla's interaction packet is handled by the owning server thread.
		if(world.isRemote) return true;
		TileEntityPylonBase second = TileEntityPylonBase.findPylon(world, x, y, z);
		if(second == null) {
			player.addChatMessage(new ChatComponentText("Wire error - No loaded pole or connector at this position"));
			return true;
		}
		if(!player.canPlayerEdit(x, y, z, side, stack)
				|| !player.canPlayerEdit(second.xCoord, second.yCoord, second.zCoord, side, stack)) return true;
		NBTTagCompound root = stack.getTagCompound();
		if(root == null) { root = new NBTTagCompound(); stack.setTagCompound(root); }
		if(!root.hasKey(SELECTION, 10)) {
			// Old x/y/z-only selections cannot safely identify a world or a pole lifetime.
			root.removeTag("x"); root.removeTag("y"); root.removeTag("z");
			NBTTagCompound selection = new NBTTagCompound();
			selection.setInteger("x", second.xCoord);
			selection.setInteger("y", second.yCoord);
			selection.setInteger("z", second.zCoord);
			selection.setInteger("dimension", world.provider.dimensionId);
			selection.setLong("idMost", second.getWireEndpointId().getMostSignificantBits());
			selection.setLong("idLeast", second.getWireEndpointId().getLeastSignificantBits());
			root.setTag(SELECTION, selection);
			second.markDirty();
			player.addChatMessage(new ChatComponentText("Wire start selected"));
		} else {
			NBTTagCompound selection = root.getCompoundTag(SELECTION);
			// Consume before graph work, even if an unexpected downstream failure escapes.
			root.removeTag(SELECTION);
			if(root.hasNoTags()) stack.setTagCompound(null);
			TileEntityPylonBase first = null;
			if(selection.hasKey("x", 3) && selection.hasKey("y", 3) && selection.hasKey("z", 3)
					&& selection.hasKey("dimension", 3) && selection.getInteger("dimension") == world.provider.dimensionId
					&& selection.hasKey("idMost", 4) && selection.hasKey("idLeast", 4)) {
				first = TileEntityPylonBase.loadedPylon(world, selection.getInteger("x"), selection.getInteger("y"), selection.getInteger("z"));
			}
			String message;
			if(first == null || first.getWireEndpointId().getMostSignificantBits() != selection.getLong("idMost")
					|| first.getWireEndpointId().getLeastSignificantBits() != selection.getLong("idLeast")) {
				message = "Wire error - Start pole is unloaded, replaced, missing or in another dimension; select it again";
			} else if(!player.canPlayerEdit(first.xCoord, first.yCoord, first.zCoord, side, stack)) {
				message = "Wire error - You cannot edit the start pole";
			} else {
				switch(TileEntityPylonBase.connect(first, second)) {
				case 0: message = "Wire connected"; break;
				case 1: message = "Wire error - Transmission poles require a substation to reach distribution cables"; break;
				case 2: message = "Wire error - Cannot connect a pole to itself"; break;
				case 3: message = "Wire error - Span exceeds the shorter endpoint's wire range"; break;
				case 5: message = "Wire error - These poles are already connected"; break;
				default: message = "Wire error - Both poles must be loaded and valid";
				}
			}
			player.addChatMessage(new ChatComponentText(message));
		}
		player.swingItem();
		return true;
	}

	@Override
	public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean extended) {
		NBTTagCompound root = stack.getTagCompound();
		if(root != null && root.hasKey(SELECTION, 10)) {
			NBTTagCompound selection = root.getCompoundTag(SELECTION);
			list.add("Wire start: " + selection.getInteger("x") + ", " + selection.getInteger("y") + ", " + selection.getInteger("z"));
			list.add("Dimension: " + selection.getInteger("dimension"));
		} else {
			list.add("Right-click poles to connect");
		}
	}

	@Override
	public void onUpdate(ItemStack stack, World world, Entity entity, int slot, boolean inhand) {
		NBTTagCompound root = stack.getTagCompound();
		if(world.isRemote && inhand && root != null && root.hasKey(SELECTION, 10)) {
			NBTTagCompound selection = root.getCompoundTag(SELECTION);
			if(selection.getInteger("dimension") != world.provider.dimensionId) return;
			Vec3 delta = Vec3.createVectorHelper(entity.posX - selection.getInteger("x"), entity.posY - selection.getInteger("y"), entity.posZ - selection.getInteger("z"));
			MainRegistry.proxy.displayTooltip(((int) delta.lengthVector()) + "m", MainRegistry.proxy.ID_CABLE);
		}
	}
}
