package com.hbm.tileentity.machine;

import com.hbm.blocks.ModBlocks;
import com.hbm.inventory.container.ContainerMachinePress;
import com.hbm.inventory.gui.GUIMachinePress;
import com.hbm.inventory.recipes.PressRecipes;
import com.hbm.inventory.recipes.loader.SerializableRecipe;
import com.hbm.items.machine.ItemStamp;
import com.hbm.machine.MachineDirtyCause;
import com.hbm.machine.MachineExecutionStrategy;
import com.hbm.tileentity.IGUIProvider;
import com.hbm.tileentity.TileEntityMachineBase;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

public class TileEntityMachinePress extends TileEntityMachineBase implements IGUIProvider {

	public int speed = 0; // speed ticks up once (or four times if preheated) when operating
	public static final int maxSpeed = 400; // max speed ticks for acceleration
	public static final int progressAtMax = 25; // max progress speed when hot
	public int burnTime = 0; // burn ticks of the loaded fuel, 200 ticks equal one operation

	public int press; // extension of the press, operation is completed if maxPress is reached
	public double renderPress; // client-side version of the press var, a double for smoother rendering
	public double lastPress; // for interp
	private int syncPress; // for interp
	private int turnProgress; // for interp 3: revenge of the sith
	public final static int maxPress = 200; // max tick count per operation assuming speed is 1
	boolean isRetracting = false; // direction the press is currently going
	private int delay; // delay between direction changes to look a bit more appealing
	private static final int TASK_PRESS = 1;
	private static final int TASK_SLOT_MAIN = 0;
	private boolean runtimeInitialized;
	private boolean preheated;
	private ItemStack cachedStamp;
	private int cachedStampDamage;
	private NBTTagCompound cachedStampTag;
	private ItemStack cachedInput;
	private int cachedInputDamage;
	private NBTTagCompound cachedInputTag;
	private long cachedRecipeRevision = -1L;
	private long observedRecipeRevision = -1L;
	private ItemStack cachedOutput;
	private int observedInventoryFingerprint;
	private boolean inventoryFingerprintInitialized;
	
	public ItemStack syncStack;
	
	public TileEntityMachinePress() {
		super(4);
	}

	@Override
	public String getName() {
		return "container.press";
	}
	
	@Override
	public void updateEntity() {
		if(worldObj.isRemote) {
			// approach-based interpolation, GO!
			this.lastPress = this.renderPress;
			
			if(this.turnProgress > 0) {
				this.renderPress = this.renderPress + ((this.syncPress - this.renderPress) / (double) this.turnProgress);
				--this.turnProgress;
			} else {
				this.renderPress = this.syncPress;
			}
		}
	}

	@Override public int getMachineExecutionStrategies() {
		return MachineExecutionStrategy.EVENT_DRIVEN | MachineExecutionStrategy.SCHEDULED | MachineExecutionStrategy.COARSE_5 | MachineExecutionStrategy.COARSE_20;
	}

	@Override public void onMachineRuntimeDirty(int causes) {
		if(worldObj == null || worldObj.isRemote) return;
		this.updatePreheaterState();
		this.resolveCachedOutput();
		observedRecipeRevision = SerializableRecipe.getRegistryRevision();
		runtimeInitialized = true;
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
	}

	@Override public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(taskType != TASK_PRESS || taskSlot != TASK_SLOT_MAIN || worldObj == null || worldObj.isRemote || !runtimeInitialized) return;
		int oldSpeed = speed;
		int oldPress = press;
		int oldBurnTime = burnTime;
		int oldDelay = delay;
		boolean oldRetracting = isRetracting;
		boolean canProcess = this.canProcess();
		if((canProcess || this.isRetracting) && this.burnTime >= 200) {
			this.speed += preheated ? 4 : 1;
			if(this.speed > this.maxSpeed) this.speed = this.maxSpeed;
		} else {
			this.speed -= 1;
			if(this.speed < 0) this.speed = 0;
		}
		if(delay <= 0) {
			int stampSpeed = speed * progressAtMax / maxSpeed;
			if(this.isRetracting) {
				this.press -= stampSpeed;
				if(this.press <= 0) {
					this.isRetracting = false;
					this.delay = 5;
				}
			} else if(canProcess) {
				this.press += stampSpeed;
				if(this.press >= this.maxPress) this.completePressOperation();
			} else if(this.press > 0) {
				this.isRetracting = true;
			}
		} else {
			delay--;
		}
		this.loadFuelIfNeeded();
		if(oldSpeed != speed || oldPress != press || oldBurnTime != burnTime || oldDelay != delay || oldRetracting != isRetracting) this.markDirty();
		this.sendRuntimeState();
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
	}

	@Override public void onMachineCoarsePoll(int cadence) {
		if(worldObj == null || worldObj.isRemote) return;
		if(cadence == 5 && this.observeInventoryFingerprint()) {
			this.markMachineDirty(MachineDirtyCause.INVENTORY | MachineDirtyCause.RECIPE);
		} else if(cadence == 20) {
			boolean oldPreheated = preheated;
			this.updatePreheaterState();
			if(oldPreheated != preheated) this.markMachineDirty(MachineDirtyCause.ENVIRONMENT | MachineDirtyCause.TOPOLOGY);
			if(observedRecipeRevision != SerializableRecipe.getRegistryRevision()) {
				observedRecipeRevision = SerializableRecipe.getRegistryRevision();
				this.markMachineDirty(MachineDirtyCause.RECIPE);
			}
		}
	}

	private void updatePreheaterState() {
		preheated = false;
		for(ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
			if(worldObj.getBlock(xCoord + dir.offsetX, yCoord + dir.offsetY, zCoord + dir.offsetZ) == ModBlocks.press_preheater) {
				preheated = true;
				break;
			}
		}
	}

	private void completePressOperation() {
		this.resolveCachedOutput();
		if(cachedOutput == null || !this.hasOutputSpace(cachedOutput)) return;
		this.worldObj.playSoundEffect(this.xCoord, this.yCoord, this.zCoord, "hbm:block.pressOperate", getVolume(1.5F), 1.0F);
		if(slots[3] == null) slots[3] = cachedOutput.copy();
		else slots[3].stackSize += cachedOutput.stackSize;
		this.decrStackSize(2, 1);
		if(slots[1].getMaxDamage() != 0) {
			slots[1].setItemDamage(slots[1].getItemDamage() + 1);
			if(slots[1].getItemDamage() >= slots[1].getMaxDamage()) slots[1] = null;
		}
		this.isRetracting = true;
		this.delay = 5;
		if(this.burnTime >= 200) this.burnTime -= 200;
		this.markDirty();
		this.markMachineDirty(MachineDirtyCause.INVENTORY | MachineDirtyCause.RECIPE);
	}

	private void loadFuelIfNeeded() {
		if(slots[0] == null || burnTime >= 200) return;
		int burn = TileEntityFurnace.getItemBurnTime(slots[0]);
		if(burn <= 0) return;
		burnTime += burn;
		if(slots[0].stackSize == 1 && slots[0].getItem().hasContainerItem(slots[0])) slots[0] = slots[0].getItem().getContainerItem(slots[0]).copy();
		else this.decrStackSize(0, 1);
		this.markChanged();
		this.markDirty();
		this.markMachineDirty(MachineDirtyCause.INVENTORY | MachineDirtyCause.RECIPE);
	}

	private void resolveCachedOutput() {
		long revision = SerializableRecipe.getRegistryRevision();
		ItemStack stamp = slots[1];
		ItemStack input = slots[2];
		int stampDamage = stamp == null ? -1 : stamp.getItemDamage();
		int inputDamage = input == null ? -1 : input.getItemDamage();
		NBTTagCompound stampTag = stamp == null || stamp.getTagCompound() == null ? null : (NBTTagCompound) stamp.getTagCompound().copy();
		NBTTagCompound inputTag = input == null || input.getTagCompound() == null ? null : (NBTTagCompound) input.getTagCompound().copy();
		if(cachedRecipeRevision != revision || cachedStamp != stamp || cachedStampDamage != stampDamage || !tagsEqual(cachedStampTag, stampTag) || cachedInput != input || cachedInputDamage != inputDamage || !tagsEqual(cachedInputTag, inputTag)) {
			cachedStamp = stamp;
			cachedStampDamage = stampDamage;
			cachedStampTag = stampTag;
			cachedInput = input;
			cachedInputDamage = inputDamage;
			cachedInputTag = inputTag;
			cachedRecipeRevision = revision;
			cachedOutput = stamp == null || input == null ? null : PressRecipes.getOutput(input, stamp);
		}
	}

	private static boolean tagsEqual(NBTTagCompound first, NBTTagCompound second) {
		return first == null ? second == null : first.equals(second);
	}

	private boolean hasOutputSpace(ItemStack output) {
		return output != null && (slots[3] == null || slots[3].stackSize + output.stackSize <= slots[3].getMaxStackSize() && slots[3].getItem() == output.getItem() && slots[3].getItemDamage() == output.getItemDamage());
	}

	private boolean hasRuntimeWork() {
		return this.canProcess() || this.isRetracting || this.delay > 0 || this.speed > 0 || this.press > 0 || (slots[0] != null && burnTime < 200 && TileEntityFurnace.getItemBurnTime(slots[0]) > 0);
	}

	private void evaluateAndSchedule(long now) {
		if(!runtimeInitialized) return;
		if(this.hasRuntimeWork()) this.scheduleMachineTransition(now + 1L, TASK_PRESS, TASK_SLOT_MAIN);
		else this.cancelMachineTransition(TASK_PRESS, TASK_SLOT_MAIN);
	}

	private boolean observeInventoryFingerprint() {
		int hash = 1;
		for(ItemStack stack : slots) {
			int slot = stack == null ? 0 : System.identityHashCode(stack.getItem());
			if(stack != null) {
				slot = 31 * slot + stack.stackSize;
				slot = 31 * slot + stack.getItemDamage();
				slot = 31 * slot + (stack.getTagCompound() == null ? 0 : stack.getTagCompound().hashCode());
			}
			hash = 31 * hash + slot;
		}
		boolean changed = inventoryFingerprintInitialized && hash != observedInventoryFingerprint;
		observedInventoryFingerprint = hash;
		inventoryFingerprintInitialized = true;
		return changed;
	}

	private void sendRuntimeState() {
		NBTTagCompound data = new NBTTagCompound();
		data.setInteger("speed", speed);
		data.setInteger("burnTime", burnTime);
		data.setInteger("press", press);
		if(slots[2] != null) {
			NBTTagCompound stack = new NBTTagCompound();
			slots[2].writeToNBT(stack);
			data.setTag("stack", stack);
		}
		this.networkPack(data, 50);
	}
	
	@Override
	public void networkUnpack(NBTTagCompound nbt) {
		super.networkUnpack(nbt);
		
		this.speed = nbt.getInteger("speed");
		this.burnTime = nbt.getInteger("burnTime");
		this.syncPress = nbt.getInteger("press");
		
		if(nbt.hasKey("stack")) {
			NBTTagCompound stack = nbt.getCompoundTag("stack");
			this.syncStack = ItemStack.loadItemStackFromNBT(stack);
		} else {
			this.syncStack = null;
		}
		
		this.turnProgress = 2;
	}
	
	public boolean canProcess() {
		if(burnTime < 200) return false;
		this.resolveCachedOutput();
		return this.hasOutputSpace(cachedOutput);
	}

	@Override
	public boolean isItemValidForSlot(int i, ItemStack stack) {
		
		if(stack.getItem() instanceof ItemStamp)
			return i == 1;
		
		if(TileEntityFurnace.getItemBurnTime(stack) > 0 && i == 0)
			return true;
		
		return i == 2;
	}
	
	@Override
	public int[] getAccessibleSlotsFromSide(int side) {
		return new int[] { 0, 1, 2, 3 };
	}

	@Override
	public boolean canInsertItem(int i, ItemStack itemStack, int j) {
		return this.isItemValidForSlot(i, itemStack);
	}

	@Override
	public boolean canExtractItem(int i, ItemStack itemStack, int j) {
		return i == 3;
	}
	
	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		press = nbt.getInteger("press");
		burnTime = nbt.getInteger("burnTime");
		speed = nbt.getInteger("speed");
		isRetracting = nbt.getBoolean("ret");
		delay = nbt.getInteger("delay");
		runtimeInitialized = false;
		inventoryFingerprintInitialized = false;
		cachedRecipeRevision = -1L;
		observedRecipeRevision = -1L;
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		nbt.setInteger("press", press);
		nbt.setInteger("burnTime", burnTime);
		nbt.setInteger("speed", speed);
		nbt.setBoolean("ret", isRetracting);
		nbt.setInteger("delay", delay);
	}
	
	AxisAlignedBB aabb;
	
	@Override
	public AxisAlignedBB getRenderBoundingBox() {
		
		if(aabb != null)
			return aabb;
		
		aabb = AxisAlignedBB.getBoundingBox(xCoord, yCoord, zCoord, xCoord + 1, yCoord + 3, zCoord + 1);
		return aabb;
	}
	
	@Override
	@SideOnly(Side.CLIENT)
	public double getMaxRenderDistanceSquared() {
		return 65536.0D;
	}

	@Override
	public Container provideContainer(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new ContainerMachinePress(player.inventory, this);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public Object provideGUI(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new GUIMachinePress(player.inventory, this);
	}
}
