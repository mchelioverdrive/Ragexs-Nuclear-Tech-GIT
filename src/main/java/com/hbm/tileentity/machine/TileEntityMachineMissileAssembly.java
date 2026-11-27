package com.hbm.tileentity.machine;

import com.hbm.inventory.container.ContainerMachineMissileAssembly;
import com.hbm.inventory.gui.GUIMachineMissileAssembly;
import com.hbm.items.weapon.ItemMissile;
import com.hbm.items.weapon.ItemMissileAirframe;
import com.hbm.items.weapon.MissileDefinition;
import com.hbm.tileentity.IGUIProvider;
import com.hbm.tileentity.TileEntityLoadedBase;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;

public class TileEntityMachineMissileAssembly extends TileEntityLoadedBase implements ISidedInventory, IGUIProvider {
	private ItemStack[] slots;
	private int previewId;

	private static final int[] access = new int[] { 0 };

	private String customName;

	public TileEntityMachineMissileAssembly() {
		slots = new ItemStack[6];
	}

	@Override
	public int getSizeInventory() {
		return slots.length;
	}

	@Override
	public ItemStack getStackInSlot(int i) {
		return slots[i];
	}

	@Override
	public ItemStack getStackInSlotOnClosing(int i) {
		if (slots[i] != null) {
			ItemStack itemStack = slots[i];
			slots[i] = null;
			inventoryChanged();
			return itemStack;
		} else {
			return null;
		}
	}

	@Override
	public void setInventorySlotContents(int i, ItemStack itemStack) {
		slots[i] = itemStack;
		if (itemStack != null && itemStack.stackSize > getInventoryStackLimit()) {
			itemStack.stackSize = getInventoryStackLimit();
		}
		inventoryChanged();
	}

	@Override
	public String getInventoryName() {
		return this.hasCustomInventoryName() ? this.customName : "container.missileAssembly";
	}

	@Override
	public boolean hasCustomInventoryName() {
		return this.customName != null && this.customName.length() > 0;
	}

	public void setCustomName(String name) {
		this.customName = name;
	}

	@Override
	public int getInventoryStackLimit() {
		return 1;
	}

	@Override
	public boolean isUseableByPlayer(EntityPlayer player) {
		if (worldObj.getTileEntity(xCoord, yCoord, zCoord) != this) {
			return false;
		} else {
			return player.getDistanceSq(xCoord + 0.5D, yCoord + 0.5D, zCoord + 0.5D) <= 64;
		}
	}

	// You scrubs aren't needed for anything (right now)
	@Override
	public void openInventory() {
	}

	@Override
	public void closeInventory() {
	}

	@Override
	public boolean isItemValidForSlot(int i, ItemStack stack) {
		if(stack == null) return false;
		switch(i) {
		case 0:
			for(MissileDefinition.Generation generation : MissileDefinition.Generation.values()) {
				if(generation.targetingCircuit() == stack.getItem()) return true;
			}
			return false;
		case 1:
			for(MissileDefinition missile : MissileDefinition.values()) {
				if(missile.warhead() == stack.getItem()) return true;
			}
			return false;
		case 2: return ItemMissileAirframe.getDefinition(stack) != null;
		default: return false;
		}
	}

	@Override
	public ItemStack decrStackSize(int i, int j) {
		if (slots[i] != null) {
			if (slots[i].stackSize <= j) {
			ItemStack itemStack = slots[i];
			slots[i] = null;
			inventoryChanged();
			return itemStack;
			}
			ItemStack itemStack1 = slots[i].splitStack(j);
			if (slots[i].stackSize == 0) {
				slots[i] = null;
			}
			inventoryChanged();

			return itemStack1;
		} else {
			return null;
		}
	}
	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		NBTTagList list = nbt.getTagList("items", 10);

		slots = new ItemStack[getSizeInventory()];

		for (int i = 0; i < list.tagCount(); i++) {
			NBTTagCompound nbt1 = list.getCompoundTagAt(i);
			byte b0 = nbt1.getByte("slot");
			if (b0 >= 0 && b0 < slots.length) {
				slots[b0] = ItemStack.loadItemStackFromNBT(nbt1);
			}
		}
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		
		NBTTagList list = new NBTTagList();

		for (int i = 0; i < slots.length; i++) {
			if (slots[i] != null) {
				NBTTagCompound nbt1 = new NBTTagCompound();
				nbt1.setByte("slot", (byte) i);
				slots[i].writeToNBT(nbt1);
				list.appendTag(nbt1);
			}
		}
		nbt.setTag("items", list);
	}

	@Override
	public int[] getAccessibleSlotsFromSide(int p_94128_1_) {
		return access;
	}

	@Override
	public boolean canInsertItem(int i, ItemStack itemStack, int j) {
		return this.isItemValidForSlot(i, itemStack);
	}

	@Override
	public boolean canExtractItem(int i, ItemStack itemStack, int j) {
		return false;
	}
	
	@Override
	public boolean canUpdate() { return false; }

	public MissileDefinition getAirframe() {
		return ItemMissileAirframe.getDefinition(slots[2]);
	}

	public boolean hasMatchingCircuit(MissileDefinition missile) {
		return missile != null && slots[0] != null && slots[0].getItem() == missile.targetingCircuit();
	}

	public boolean hasMatchingWarhead(MissileDefinition missile) {
		return missile != null && slots[1] != null && slots[1].getItem() == missile.warhead();
	}

	public boolean canBuild() {
		MissileDefinition missile = getAirframe();
		return slots[5] == null && hasMatchingCircuit(missile) && hasMatchingWarhead(missile);
	}

	public void construct() {
		if(worldObj == null || worldObj.isRemote || !canBuild()) return;

		slots[5] = getAirframe().missileStack();
		slots[0] = null;
		slots[1] = null;
		slots[2] = null;
		inventoryChanged();
		worldObj.playSoundEffect(xCoord, yCoord, zCoord, "hbm:block.missileAssembly2", 1F, 1F);
	}

	private void inventoryChanged() {
		markDirty();
		if(worldObj != null && !worldObj.isRemote) worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
	}

	public MissileDefinition getPreviewMissile() {
		MissileDefinition missile = getAirframe();
		if(missile == null && slots[5] != null && slots[5].getItem() instanceof ItemMissile) {
			missile = ((ItemMissile) slots[5].getItem()).definition;
		}
		return missile;
	}

	@Override
	public Packet getDescriptionPacket() {
		MissileDefinition missile = getPreviewMissile();
		NBTTagCompound data = new NBTTagCompound();
		data.setInteger("airframe", missile == null ? 0 : missile.id);
		return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 0, data);
	}

	@Override
	public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity packet) {
		previewId = packet.func_148857_g().getInteger("airframe");
	}

	public MissileDefinition getWorldPreview() {
		return MissileDefinition.byId(previewId);
	}

	@Override
	public AxisAlignedBB getRenderBoundingBox() {
		return TileEntity.INFINITE_EXTENT_AABB;
	}
	
	@Override
	@SideOnly(Side.CLIENT)
	public double getMaxRenderDistanceSquared()
	{
		return 65536.0D;
	}

	@Override
	public Container provideContainer(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new ContainerMachineMissileAssembly(player.inventory, this);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public Object provideGUI(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new GUIMachineMissileAssembly(player.inventory, this);
	}
}
