package com.hbm.tileentity.machine;

import com.hbm.blocks.ModBlocks;
import com.hbm.blocks.machine.ReactorResearch;
import com.hbm.handler.CompatHandler;
import com.hbm.inventory.container.ContainerMachineReactorBreeding;
import com.hbm.inventory.gui.GUIMachineReactorBreeding;
import com.hbm.inventory.recipes.BreederRecipes;
import com.hbm.inventory.recipes.BreederRecipes.BreederRecipe;
import com.hbm.machine.MachineDirtyCause;
import com.hbm.machine.MachineExecutionStrategy;
import com.hbm.tileentity.IGUIProvider;
import com.hbm.tileentity.TileEntityMachineBase;
import com.hbm.util.CompatEnergyControl;

import api.hbm.tile.IInfoProviderEC;
import cpw.mods.fml.common.Optional;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.SimpleComponent;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

@Optional.InterfaceList({@Optional.Interface(iface = "li.cil.oc.api.network.SimpleComponent", modid = "OpenComputers")})
public class TileEntityMachineReactorBreeding extends TileEntityMachineBase implements SimpleComponent, IGUIProvider, IInfoProviderEC, CompatHandler.OCComponent {
	private static final int TASK_BREED = 0;

	public int flux;
	public float progress;

	private static final int[] slots_io = new int[] { 0, 1 };

	public TileEntityMachineReactorBreeding() {
		super(2);
	}

	@Override
	public String getName() {
		return "container.reactorBreeding";
	}

	@Override
	public int getMachineExecutionStrategies() {
		return MachineExecutionStrategy.EVENT_DRIVEN | MachineExecutionStrategy.SCHEDULED | MachineExecutionStrategy.COARSE_5 | MachineExecutionStrategy.COARSE_20;
	}

	@Override
	public void updateEntity() { }

	@Override
	public void onMachineRuntimeDirty(int causes) {
		if(worldObj == null || worldObj.isRemote) return;
		if((causes & (MachineDirtyCause.LIFECYCLE | MachineDirtyCause.INVENTORY | MachineDirtyCause.TOPOLOGY)) != 0) getInteractions();
		if(canProcess() && flux > 0) scheduleMachineTransition(worldObj.getTotalWorldTime() + 1L, TASK_BREED, 0);
		else cancelMachineTransition(TASK_BREED, 0);
	}

	@Override
	public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(taskType != TASK_BREED || taskSlot != 0 || worldObj == null || worldObj.isRemote) return;
		getInteractions();
		BreederRecipe out = BreederRecipes.getOutput(slots[0], this.flux);
		if(canProcess() && out != null) {
			progress += 0.0025F * ((float) this.flux / (float) out.flux);
			if(progress >= 1.0F) {
				progress = 0F;
				processItem();
				markDirty();
			}
		} else {
			progress = 0.0F;
		}
		sendBreederPacket();
		if(!isInvalid() && canProcess() && flux > 0) scheduleMachineTransition(worldObj.getTotalWorldTime() + 1L, TASK_BREED, 0);
	}

	@Override
	public void onMachineCoarsePoll(int cadence) {
		if(worldObj == null || worldObj.isRemote) return;
		if(cadence == 5) {
			int oldFlux = flux;
			getInteractions();
			if(flux != oldFlux) markMachineDirty(MachineDirtyCause.ENVIRONMENT);
			else if(canProcess() && flux > 0) scheduleMachineTransition(worldObj.getTotalWorldTime() + 1L, TASK_BREED, 0);
		} else if(cadence == 20 && canProcess() && flux <= 0) {
			sendBreederPacket();
		}
	}

	private void sendBreederPacket() {
		NBTTagCompound data = new NBTTagCompound();
		data.setInteger("flux", flux);
		data.setFloat("progress", progress);
		networkPack(data, 20);
	}

	public void networkUnpack(NBTTagCompound data) {
		super.networkUnpack(data);

		flux = data.getInteger("flux");
		progress = data.getFloat("progress");
	}

	public void getInteractions() {

		for(byte d = 2; d < 6; d++) {
			ForgeDirection dir = ForgeDirection.getOrientation(d);
			Block b = worldObj.getBlock(xCoord + dir.offsetX, yCoord, zCoord + dir.offsetZ);

			if(b == ModBlocks.reactor_research) {

				int[] pos = ((ReactorResearch) ModBlocks.reactor_research).findCore(worldObj, xCoord + dir.offsetX, yCoord, zCoord + dir.offsetZ);

				if(pos != null) {
					TileEntity tile = worldObj.getTileEntity(pos[0], pos[1], pos[2]);

					if(tile instanceof TileEntityReactorResearch) {
						TileEntityReactorResearch reactor = (TileEntityReactorResearch) tile;
						this.flux += reactor.totalFlux;
					}
				}
			}
		}
	}

	public boolean canProcess() {

		if(slots[0] == null)
			return false;

		BreederRecipe recipe =
			BreederRecipes.getOutput(slots[0], this.flux);

		if(recipe == null)
			return false;

		if(slots[1] == null)
			return true;

		if(!slots[1].isItemEqual(recipe.output))
			return false;

		return slots[1].stackSize < slots[1].getMaxStackSize();
	}

	private void processItem() {

		BreederRecipe rec =
			BreederRecipes.getOutput(slots[0], this.flux);

		if(rec == null)
			return;

		ItemStack itemStack = rec.output;

		if(slots[1] == null) {

			slots[1] = itemStack.copy();

		} else if(slots[1].isItemEqual(itemStack)) {

			slots[1].stackSize += itemStack.stackSize;
		}

		slots[0].stackSize--;

		if(slots[0].stackSize <= 0) {
			slots[0] = null;
		}
	}



	@Override
	public int[] getAccessibleSlotsFromSide(int side) {
		return slots_io;
	}

	@Override
	public boolean isItemValidForSlot(int i, ItemStack itemStack) {
		return i == 0;
	}

	@Override
	public boolean canExtractItem(int i, ItemStack itemStack, int j) {
		return i == 1;
	}

	public int getProgressScaled(int i) {
		return (int) (this.progress * i);
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);

		flux = nbt.getInteger("flux");
		progress = nbt.getFloat("progress");
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);

		nbt.setInteger("flux", flux);
		nbt.setFloat("progress", progress);
	}

	AxisAlignedBB bb = null;

	@Override
	public AxisAlignedBB getRenderBoundingBox() {

		if(bb == null) {
			bb = AxisAlignedBB.getBoundingBox(
					xCoord,
					yCoord,
					zCoord,
					xCoord + 1,
					yCoord + 3,
					zCoord + 1
					);
		}

		return bb;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public double getMaxRenderDistanceSquared() {
		return 65536.0D;
	}

	// do some opencomputer stuff
	@Override
	@Optional.Method(modid = "OpenComputers")
	public String getComponentName() {
		return "breeding_reactor";
	}

	@Callback
	@Optional.Method(modid = "OpenComputers")
	public Object[] getFlux(Context context, Arguments args) {
		return new Object[] {flux};
	}

	@Callback
	@Optional.Method(modid = "OpenComputers")
	public Object[] getProgress(Context context, Arguments args) {
		return new Object[] {progress};
	}

	@Callback
	@Optional.Method(modid = "OpenComputers")
	public Object[] getInfo(Context context, Arguments args) {
		return new Object[] {flux, progress};
	}

	@Override
	public Container provideContainer(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new ContainerMachineReactorBreeding(player.inventory, this);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public Object provideGUI(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new GUIMachineReactorBreeding(player.inventory, this);
	}

	@Override
	public void provideExtraInfo(NBTTagCompound data) {
		data.setInteger(CompatEnergyControl.I_FLUX, flux);
	}
}
