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
	private boolean runtimeSettling;
	private long lastAccountingTick = Long.MIN_VALUE;
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
	private long clientPressTick;
	private int clientDelay;
	private int clientProjectedSpeed;
	private int clientProjectedPress;
	private int clientProjectedBurnTime;
	private boolean clientRetracting;
	private boolean clientPreheated;
	private boolean clientCanProcess;
	private boolean clientProjectionActive;
	
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
			this.lastPress = this.renderPress;
			this.advanceClientProjection();
			this.renderPress = clientProjectedPress;
		}
	}

	@Override public int getMachineExecutionStrategies() {
		return MachineExecutionStrategy.EVENT_DRIVEN | MachineExecutionStrategy.SCHEDULED | MachineExecutionStrategy.COARSE_5 | MachineExecutionStrategy.COARSE_20;
	}

	@Override public void onMachineRuntimeDirty(int causes) {
		if(worldObj == null || worldObj.isRemote) return;
		long now = worldObj.getTotalWorldTime();
		if(runtimeInitialized) this.settlePressThrough(now - 1L);
		else lastAccountingTick = now;
		this.updatePreheaterState();
		this.resolveCachedOutput();
		observedRecipeRevision = SerializableRecipe.getRegistryRevision();
		runtimeInitialized = true;
		this.sendRuntimeState();
		this.evaluateAndSchedule(now);
	}

	@Override public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(taskType != TASK_PRESS || taskSlot != TASK_SLOT_MAIN || worldObj == null || worldObj.isRemote || !runtimeInitialized) return;
		this.settlePressThrough(worldObj.getTotalWorldTime());
		this.sendRuntimeState();
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
	}

	private void settlePressThrough(long targetTick) {
		if(worldObj == null || worldObj.isRemote || runtimeSettling) return;
		long now = worldObj.getTotalWorldTime();
		if(lastAccountingTick == Long.MIN_VALUE || targetTick <= lastAccountingTick) {
			if(lastAccountingTick == Long.MIN_VALUE) lastAccountingTick = Math.min(targetTick, now);
			return;
		}
		int oldSpeed = speed;
		int oldPress = press;
		int oldBurnTime = burnTime;
		int oldDelay = delay;
		boolean oldRetracting = isRetracting;
		runtimeSettling = true;
		try {
			while(lastAccountingTick < targetTick) {
				if(!hasPressStepWork()) {
					lastAccountingTick = targetTick;
					break;
				}
				this.applyPressStep();
				lastAccountingTick++;
			}
		} finally {
			runtimeSettling = false;
		}
		if(oldSpeed != speed || oldPress != press || oldBurnTime != burnTime || oldDelay != delay || oldRetracting != isRetracting) {
			this.markDirty();
			this.markNetworkDirty();
		}
	}

	private boolean hasPressStepWork() {
		return this.canProcessState() || (this.isRetracting && burnTime >= 200) || this.delay > 0 || this.speed > 0 || (this.press > 0 && burnTime >= 200)
				|| (slots[0] != null && burnTime < 200 && TileEntityFurnace.getItemBurnTime(slots[0]) > 0);
	}

	private boolean canProcessState() {
		return burnTime >= 200 && this.hasOutputSpace(cachedOutput);
	}

	private void applyPressStep() {
		boolean canProcess = this.canProcessState();
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
					this.press = 0;
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
		return this.hasPressStepWork();
	}

	private void evaluateAndSchedule(long now) {
		if(!runtimeInitialized) return;
		this.resolveCachedOutput();
		if(!this.hasRuntimeWork()) {
			this.cancelMachineTransition(TASK_PRESS, TASK_SLOT_MAIN);
			return;
		}
		long ticks = this.ticksUntilNextBoundary();
		this.scheduleMachineTransition(now + Math.max(1L, ticks), TASK_PRESS, TASK_SLOT_MAIN);
	}

	private long ticksUntilNextBoundary() {
		if(slots[0] != null && burnTime < 200 && TileEntityFurnace.getItemBurnTime(slots[0]) > 0) return 1L;
		int simSpeed = speed;
		int simPress = press;
		int simDelay = delay;
		int simBurn = burnTime;
		boolean simRetracting = isRetracting;
		boolean process = this.canProcessState();
		for(long ticks = 1L; ticks <= 10000L; ticks++) {
			if((process || simRetracting) && simBurn >= 200) {
				simSpeed = Math.min(maxSpeed, simSpeed + (preheated ? 4 : 1));
			} else {
				simSpeed = Math.max(0, simSpeed - 1);
			}
			if(simDelay <= 0) {
				int stampSpeed = simSpeed * progressAtMax / maxSpeed;
				if(simRetracting) {
					simPress -= stampSpeed;
					if(simPress <= 0) {
						simPress = 0;
						simRetracting = false;
					simDelay = 5;
					}
				} else if(process) {
					simPress += stampSpeed;
					if(simPress >= maxPress) return ticks;
				} else if(simPress > 0) {
					simRetracting = true;
				}
			} else {
				simDelay--;
			}
			if(!process && simSpeed == 0 && simDelay == 0 && (simPress == 0 || simRetracting)) return ticks;
		}
		return 1L;
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
		data.setInteger("delay", delay);
		data.setBoolean("ret", isRetracting);
		data.setBoolean("preheated", preheated);
		data.setBoolean("canProcess", this.canProcessState());
		data.setLong("tick", worldObj.getTotalWorldTime());
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
		this.clientProjectedSpeed = speed;
		this.clientProjectedPress = syncPress;
		this.clientProjectedBurnTime = burnTime;
		this.clientDelay = nbt.getInteger("delay");
		this.clientRetracting = nbt.getBoolean("ret");
		this.clientPreheated = nbt.getBoolean("preheated");
		this.clientCanProcess = nbt.getBoolean("canProcess");
		this.clientPressTick = nbt.getLong("tick");
		this.clientProjectionActive = true;
		
		if(nbt.hasKey("stack")) {
			NBTTagCompound stack = nbt.getCompoundTag("stack");
			this.syncStack = ItemStack.loadItemStackFromNBT(stack);
		} else {
			this.syncStack = null;
		}
		
		this.turnProgress = 0;
	}

	public int getProjectedSpeed() {
		return worldObj != null && worldObj.isRemote ? clientProjectedSpeed : speed;
	}

	public int getProjectedBurnTime() {
		return worldObj != null && worldObj.isRemote ? clientProjectedBurnTime : burnTime;
	}

	private double getProjectedPress() {
		if(worldObj == null || !worldObj.isRemote || !clientProjectionActive) return syncPress;
		return clientProjectedPress;
	}

	private void advanceClientProjection() {
		if(!clientProjectionActive) {
			clientProjectedPress = syncPress;
			return;
		}
		long currentTick = worldObj.getTotalWorldTime();
		while(clientPressTick < currentTick) {
			if((clientCanProcess || clientRetracting) && clientProjectedBurnTime >= 200) clientProjectedSpeed = Math.min(maxSpeed, clientProjectedSpeed + (clientPreheated ? 4 : 1));
			else clientProjectedSpeed = Math.max(0, clientProjectedSpeed - 1);
			if(clientDelay <= 0) {
				int stampSpeed = clientProjectedSpeed * progressAtMax / maxSpeed;
				if(clientRetracting) {
					clientProjectedPress -= stampSpeed;
					if(clientProjectedPress <= 0) { clientProjectedPress = 0; clientRetracting = false; clientDelay = 5; }
				} else if(clientCanProcess) {
					clientProjectedPress += stampSpeed;
					if(clientProjectedPress >= maxPress) { clientProjectedBurnTime = Math.max(0, clientProjectedBurnTime - 200); clientRetracting = true; clientDelay = 5; clientCanProcess = false; }
				} else if(clientProjectedPress > 0) clientRetracting = true;
			} else clientDelay--;
			clientPressTick++;
		}
	}
	
	public boolean canProcess() {
		if(burnTime < 200) return false;
		this.resolveCachedOutput();
		return this.hasOutputSpace(cachedOutput);
	}

	@Override protected void beforeInventorySlotChanged(int slot) {
		if(!runtimeSettling && runtimeInitialized && worldObj != null && !worldObj.isRemote) this.settlePressThrough(worldObj.getTotalWorldTime() - 1L);
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
		runtimeSettling = false;
		lastAccountingTick = Long.MIN_VALUE;
		clientProjectionActive = false;
		inventoryFingerprintInitialized = false;
		cachedRecipeRevision = -1L;
		observedRecipeRevision = -1L;
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		if(worldObj != null && !worldObj.isRemote) this.settlePressThrough(worldObj.getTotalWorldTime() - 1L);
		super.writeToNBT(nbt);
		nbt.setInteger("press", press);
		nbt.setInteger("burnTime", burnTime);
		nbt.setInteger("speed", speed);
		nbt.setBoolean("ret", isRetracting);
		nbt.setInteger("delay", delay);
	}

	@Override public void onChunkUnload() {
		if(worldObj != null && !worldObj.isRemote) this.settlePressThrough(worldObj.getTotalWorldTime() - 1L);
		lastAccountingTick = Long.MIN_VALUE;
		super.onChunkUnload();
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
