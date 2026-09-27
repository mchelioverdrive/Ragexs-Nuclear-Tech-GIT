package com.hbm.tileentity.machine;

import com.hbm.blocks.ModBlocks;
import com.hbm.blocks.machine.ReactorResearch;
import com.hbm.handler.CompatHandler;
import com.hbm.interfaces.IControlReceiver;
import com.hbm.inventory.container.ContainerReactorControl;
import com.hbm.inventory.gui.GUIReactorControl;
import com.hbm.items.ModItems;
import com.hbm.machine.MachineDirtyCause;
import com.hbm.machine.MachineExecutionStrategy;
import com.hbm.tileentity.IGUIProvider;
import com.hbm.tileentity.TileEntityMachineBase;

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
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

@Optional.InterfaceList({@Optional.Interface(iface = "li.cil.oc.api.network.SimpleComponent", modid = "OpenComputers")})
public class TileEntityReactorControl extends TileEntityMachineBase implements IControlReceiver, IGUIProvider, SimpleComponent, CompatHandler.OCComponent {
	private boolean inventoryFingerprintInitialized;
	private int observedInventoryFingerprint;
	private int lastSyncFingerprint;
	private long lastSyncTick = Long.MIN_VALUE;

	public TileEntityReactorControl() {
		super(1);
	}
	
	@Override
	public String getName() {
		return "container.reactorControl";
	}
	
	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		NBTTagList list = nbt.getTagList("items", 10);

		isLinked = nbt.getBoolean("isLinked");
		levelLower = nbt.getDouble("levelLower");
		levelUpper = nbt.getDouble("levelUpper");
		heatLower = nbt.getDouble("heatLower");
		heatUpper = nbt.getDouble("heatUpper");
		function = RodFunction.values()[nbt.getInteger("function")];
		
		slots = new ItemStack[getSizeInventory()];
		
		for(int i = 0; i < list.tagCount(); i++)
		{
			NBTTagCompound nbt1 = list.getCompoundTagAt(i);
			byte b0 = nbt1.getByte("slot");
			if(b0 >= 0 && b0 < slots.length)
			{
				slots[b0] = ItemStack.loadItemStackFromNBT(nbt1);
			}
		}
	}
	
	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		NBTTagList list = new NBTTagList();
		
		nbt.setBoolean("isLinked", isLinked);
		nbt.setDouble("levelLower", levelLower);
		nbt.setDouble("levelUpper", levelUpper);
		nbt.setDouble("heatLower", heatLower);
		nbt.setDouble("heatUpper", heatUpper);
		nbt.setInteger("function", function.ordinal());

		
		for(int i = 0; i < slots.length; i++)
		{
			if(slots[i] != null)
			{
				NBTTagCompound nbt1 = new NBTTagCompound();
				nbt1.setByte("slot", (byte)i);
				slots[i].writeToNBT(nbt1);
				list.appendTag(nbt1);
			}
		}
		nbt.setTag("items", list);
	}
	
	public TileEntityReactorResearch reactor;
	
	public boolean isLinked;
	
	public int flux;
	public double level;
	public int heat;
	
	public double levelLower;
	public double levelUpper;
	public double heatLower;
	public double heatUpper;
	public RodFunction function = RodFunction.LINEAR;
	
	@Override
	public int getMachineExecutionStrategies() {
		return MachineExecutionStrategy.EVENT_DRIVEN | MachineExecutionStrategy.COARSE_5 | MachineExecutionStrategy.COARSE_20;
	}

	private void updateController() {
		boolean wasLinked = isLinked;
		isLinked = establishLink();
		if(isLinked) {
			double fauxLevel;
			double lowerBound = Math.min(this.heatLower, this.heatUpper);
			double upperBound = Math.max(this.heatLower, this.heatUpper);
			if(this.heat < lowerBound) fauxLevel = this.levelLower;
			else if(this.heat > upperBound) fauxLevel = this.levelUpper;
			else fauxLevel = getTargetLevel(this.function, this.heat);
			double target = MathHelper.clamp_double(fauxLevel * 0.01D, 0D, 1D);
			if(target != this.level) reactor.setTarget(target);
		}
		int fingerprint = runtimeStateFingerprint();
		long now = worldObj.getTotalWorldTime();
		if(lastSyncTick == Long.MIN_VALUE || fingerprint != lastSyncFingerprint || wasLinked != isLinked || now - lastSyncTick >= 20L) {
			NBTTagCompound data = new NBTTagCompound();
			data.setInteger("heat", heat);
			data.setDouble("level", level);
			data.setInteger("flux", flux);
			data.setBoolean("isLinked", isLinked);
			data.setDouble("levelLower", levelLower);
			data.setDouble("levelUpper", levelUpper);
			data.setDouble("heatLower", heatLower);
			data.setDouble("heatUpper", heatUpper);
			data.setInteger("function", function.ordinal());
			this.networkPack(data, 150);
			lastSyncFingerprint = fingerprint;
			lastSyncTick = now;
		}
	}

	private int runtimeStateFingerprint() {
		int hash = heat;
		hash = 31 * hash + Double.valueOf(level).hashCode();
		hash = 31 * hash + flux;
		hash = 31 * hash + (isLinked ? 1 : 0);
		hash = 31 * hash + Double.valueOf(levelLower).hashCode();
		hash = 31 * hash + Double.valueOf(levelUpper).hashCode();
		hash = 31 * hash + Double.valueOf(heatLower).hashCode();
		hash = 31 * hash + Double.valueOf(heatUpper).hashCode();
		hash = 31 * hash + function.ordinal();
		return hash;
	}

	private int inventoryFingerprint() {
		ItemStack stack = slots[0];
		int hash = stack == null ? 0 : System.identityHashCode(stack);
		if(stack != null) {
			hash = 31 * hash + stack.stackSize;
			hash = 31 * hash + stack.getItemDamage();
			hash = 31 * hash + (stack.getTagCompound() == null ? 0 : stack.getTagCompound().hashCode());
		}
		return hash;
	}

	@Override
	public void onMachineRuntimeDirty(int causes) {
		if(worldObj == null || worldObj.isRemote) return;
		if((causes & (MachineDirtyCause.LIFECYCLE | MachineDirtyCause.INVENTORY | MachineDirtyCause.CONFIGURATION)) != 0) updateController();
	}

	@Override
	public void onMachineCoarsePoll(int cadence) {
		if(worldObj == null || worldObj.isRemote) return;
		if(cadence == 5 && observeInventoryFingerprint()) markMachineDirty(MachineDirtyCause.INVENTORY);
		if((cadence == 5 && isLinked) || cadence == 20) updateController();
	}

	private boolean observeInventoryFingerprint() {
		int current = inventoryFingerprint();
		boolean changed = inventoryFingerprintInitialized && current != observedInventoryFingerprint;
		observedInventoryFingerprint = current;
		inventoryFingerprintInitialized = true;
		return changed;
	}

	@Override
	public void updateEntity() { }
	
	public void networkUnpack(NBTTagCompound data) {
		super.networkUnpack(data);
		
		this.heat = data.getInteger("heat");
		this.level = data.getDouble("level");
		this.flux = data.getInteger("flux");
		isLinked = data.getBoolean("isLinked");
		levelLower = data.getDouble("levelLower");
		levelUpper = data.getDouble("levelUpper");
		heatLower = data.getDouble("heatLower");
		heatUpper = data.getDouble("heatUpper");
		function = RodFunction.values()[data.getInteger("function")];
	}
	
	private boolean establishLink() {
		reactor = null;
		if(slots[0] != null && slots[0].getItem() == ModItems.reactor_sensor && slots[0].stackTagCompound != null) {
			int xCoord = slots[0].stackTagCompound.getInteger("x");
    		int yCoord = slots[0].stackTagCompound.getInteger("y");
    		int zCoord = slots[0].stackTagCompound.getInteger("z");
    		
    		Block b = worldObj.getBlock(xCoord, yCoord, zCoord);
    		
    		if(b == ModBlocks.reactor_research) {
    			
    			int[] pos = ((ReactorResearch) ModBlocks.reactor_research).findCore(worldObj, xCoord, yCoord, zCoord);
    			
    			if(pos != null) {

					TileEntity tile = worldObj.getTileEntity(pos[0], pos[1], pos[2]);

					if(tile instanceof TileEntityReactorResearch) {
						reactor = (TileEntityReactorResearch) tile;
						
						this.flux = reactor.totalFlux;
						this.level = reactor.level;
						this.heat = reactor.heat;
						
						return true;
					}
				}
    		}
		}
		
		return false;
	}
	
	public double getTargetLevel(RodFunction function, int heat) {
		double fauxLevel = 0;
		
		switch(function) {
		case LINEAR:
			fauxLevel = (heat - this.heatLower) * ((this.levelUpper - this.levelLower) / (this.heatUpper - this.heatLower)) + this.levelLower;
			return fauxLevel;
		case LOG:
			fauxLevel = Math.pow((heat - this.heatUpper) / (this.heatLower - this.heatUpper), 2) * (this.levelLower - this.levelUpper) + this.levelUpper;
			return fauxLevel;
		case QUAD:
			fauxLevel = Math.pow((heat - this.heatLower) / (this.heatUpper - this.heatLower), 2) * (this.levelUpper - this.levelLower) + this.levelLower;
			return fauxLevel;
		default:
			return 0.0D;
		}
	}
	
	public int[] getDisplayData() {
		if(this.isLinked) {
			int[] data = new int[3];
			data[0] = (int) (this.level * 100);
			data[1] = this.flux;
			data[2] = (int) Math.round((this.heat) * 0.00002 * 980 + 20);
			return data;
		} else {
			return new int[] { 0, 0, 0 };
		}
	}
	
	@Override
	public void receiveControl(NBTTagCompound data) {
		
		if(data.hasKey("function")) {
			this.function = RodFunction.values()[data.getInteger("function")];
		} else {
			this.levelLower = data.getDouble("levelLower");
			this.levelUpper = data.getDouble("levelUpper");
			this.heatLower = data.getDouble("heatLower");
			this.heatUpper = data.getDouble("heatUpper");
		}
		
		this.markDirty();
		this.markMachineDirty(MachineDirtyCause.CONFIGURATION);
	}
	
	@Override
	public boolean hasPermission(EntityPlayer player) {
		return Vec3.createVectorHelper(xCoord - player.posX, yCoord - player.posY, zCoord - player.posZ).lengthVector() < 20;
	}
	
	public enum RodFunction {
		LINEAR,
		QUAD,
		LOG
	}

	// do some opencomputer stuff
	@Override
	@Optional.Method(modid = "OpenComputers")
	public String getComponentName() {
		return "reactor_control";
	}

	@Callback(direct = true)
	@Optional.Method(modid = "OpenComputers")
	public Object[] isLinked(Context context, Arguments args) {
		return new Object[] {isLinked};
	}

	@Callback(direct = true)
	@Optional.Method(modid = "OpenComputers")
	public Object[] getReactor(Context context, Arguments args) {
		return new Object[] {getDisplayData()};
	}

	@Callback(direct = true, limit = 4)
	@Optional.Method(modid = "OpenComputers")
	public Object[] setParams(Context context, Arguments args) { //i hate my life
		int newFunction = args.checkInteger(0);
		double newMaxHeat = args.checkDouble(1);
		double newMinHeat = args.checkDouble(2);
		double newMaxLevel = args.checkDouble(3)/100.0;
		double newMinLevel = args.checkDouble(4)/100.0;
		function = RodFunction.values()[MathHelper.clamp_int(newFunction, 0, 2)];
		heatUpper = MathHelper.clamp_double(newMaxHeat, 0, 9999);
		heatLower = MathHelper.clamp_double(newMinHeat, 0, 9999);
		levelUpper = MathHelper.clamp_double(newMaxLevel, 0, 1);
		levelLower = MathHelper.clamp_double(newMinLevel, 0, 1);
		markMachineDirty(MachineDirtyCause.CONFIGURATION);
		return new Object[] {};
	}

	@Callback(direct = true)
	@Optional.Method(modid = "OpenComputers")
	public Object[] getParams(Context context, Arguments args) {
		return new Object[] {function.ordinal(), heatUpper, heatLower, levelUpper, levelLower};
	}

	@Override
	public Container provideContainer(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new ContainerReactorControl(player.inventory, this);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public Object provideGUI(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new GUIReactorControl(player.inventory, this);
	}
}
