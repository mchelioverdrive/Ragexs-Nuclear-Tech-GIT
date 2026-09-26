package com.hbm.tileentity.machine;

import java.io.IOException;

import com.google.gson.JsonObject;
import com.google.gson.stream.JsonWriter;
import com.hbm.inventory.OreDictManager.DictFrame;
import com.hbm.inventory.container.ContainerAshpit;
import com.hbm.inventory.gui.GUIAshpit;
import com.hbm.items.ItemEnums.EnumAshType;
import com.hbm.items.ModItems;
import com.hbm.machine.MachineDirtyCause;
import com.hbm.machine.MachineExecutionStrategy;
import com.hbm.tileentity.IConfigurableMachine;
import com.hbm.tileentity.IGUIProvider;
import com.hbm.tileentity.TileEntityMachineBase;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

public class TileEntityAshpit extends TileEntityMachineBase implements IGUIProvider, IConfigurableMachine {
	private static final int TASK_PROCESS_ASH = 1;
	private static final int TASK_SLOT_SHARED = 0;
	private boolean runtimeInitialized;
	private final int[] observedAshLevels = new int[5];
	private int observedInventoryFingerprint;
	private boolean inventoryFingerprintInitialized;
	
	private int playersUsing = 0;
	public float doorAngle = 0;
	public float prevDoorAngle = 0;
	public boolean isFull;

	public int ashLevelWood;
	public int ashLevelCoal;
	public int ashLevelMisc;
	public int ashLevelFly;
	public int ashLevelSoot;
	
	//Configurable values
	public static int thresholdWood = 2000;
	public static int thresholdCoal = 2000;
	public static int thresholdMisc = 2000;
	public static int thresholdFly = 2000;
	public static int thresholdSoot = 8000;

	public TileEntityAshpit() {
		super(5);
	}
	
	@Override
	public String getConfigName() {
		return "ashpit";
	}

	@Override
	public void readIfPresent(JsonObject obj) {
		thresholdWood = IConfigurableMachine.grab(obj, "I:thresholdWood", thresholdWood);
		thresholdCoal = IConfigurableMachine.grab(obj, "I:thresholdCoal", thresholdCoal);
		thresholdMisc = IConfigurableMachine.grab(obj, "I:thresholdMisc", thresholdMisc);
		thresholdFly = IConfigurableMachine.grab(obj, "I:thresholdFly", thresholdFly);
		thresholdSoot = IConfigurableMachine.grab(obj, "I:thresholdSoot", thresholdSoot);
	}

	@Override
	public void writeConfig(JsonWriter writer) throws IOException {
		writer.name("I:thresholdWood").value(thresholdWood);
		writer.name("I:thresholdCoal").value(thresholdCoal);
		writer.name("I:thresholdMisc").value(thresholdMisc);
		writer.name("I:thresholdFly").value(thresholdFly);
		writer.name("I:thresholdSoot").value(thresholdSoot);
	}
	
	@Override
	public void openInventory() {
		if(!worldObj.isRemote) { this.playersUsing++; this.markMachineDirty(MachineDirtyCause.CONFIGURATION); }
	}
	
	@Override
	public void closeInventory() {
		if(!worldObj.isRemote) { this.playersUsing--; this.markMachineDirty(MachineDirtyCause.CONFIGURATION); }
	}

	@Override
	public String getName() {
		return "container.ashpit";
	}

	@Override
	public void updateEntity() {
		if(worldObj.isRemote) {
			this.prevDoorAngle = this.doorAngle;
			float swingSpeed = (doorAngle / 10F) + 3;
			
			if(this.playersUsing > 0) {
				this.doorAngle += swingSpeed;
			} else {
				this.doorAngle -= swingSpeed;
			}
			
			this.doorAngle = MathHelper.clamp_float(this.doorAngle, 0F, 135F);
		}
	}

	@Override public int getMachineExecutionStrategies() {
		return MachineExecutionStrategy.EVENT_DRIVEN | MachineExecutionStrategy.SCHEDULED | MachineExecutionStrategy.COARSE_5;
	}

	@Override public void onMachineRuntimeDirty(int causes) {
		if(worldObj == null || worldObj.isRemote) return;
		runtimeInitialized = true;
		this.refreshRuntimeState();
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
		this.sendRuntimeState();
	}

	@Override public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(taskType != TASK_PROCESS_ASH || taskSlot != TASK_SLOT_SHARED || worldObj == null || worldObj.isRemote || !runtimeInitialized) return;
		int beforeFingerprint = this.inventoryFingerprint();
		int beforeWood = ashLevelWood;
		int beforeCoal = ashLevelCoal;
		int beforeMisc = ashLevelMisc;
		int beforeFly = ashLevelFly;
		int beforeSoot = ashLevelSoot;
		if(processAsh(ashLevelWood, EnumAshType.WOOD, thresholdWood)) ashLevelWood -= thresholdWood;
		if(processAsh(ashLevelCoal, EnumAshType.COAL, thresholdCoal)) ashLevelCoal -= thresholdCoal;
		if(processAsh(ashLevelMisc, EnumAshType.MISC, thresholdMisc)) ashLevelMisc -= thresholdMisc;
		if(processAsh(ashLevelFly, EnumAshType.FLY, thresholdFly)) ashLevelFly -= thresholdFly;
		if(processAsh(ashLevelSoot, EnumAshType.SOOT, thresholdSoot)) ashLevelSoot -= thresholdSoot;
		this.refreshRuntimeState();
		if(beforeFingerprint != observedInventoryFingerprint || beforeWood != ashLevelWood || beforeCoal != ashLevelCoal || beforeMisc != ashLevelMisc || beforeFly != ashLevelFly || beforeSoot != ashLevelSoot) {
			this.markDirty();
			this.markNetworkDirty();
		}
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
		this.sendRuntimeState();
	}

	@Override public void onMachineCoarsePoll(int cadence) {
		if(worldObj == null || worldObj.isRemote || cadence != 5) return;
		boolean levelsChanged = this.observeAshLevels();
		boolean inventoryChanged = this.observeInventoryFingerprint();
		if(levelsChanged || inventoryChanged) this.markMachineDirty((levelsChanged ? MachineDirtyCause.ENVIRONMENT : 0) | (inventoryChanged ? MachineDirtyCause.INVENTORY : 0));
	}

	public void addAsh(EnumAshType type, int amount) {
		if(type == EnumAshType.WOOD) ashLevelWood += amount;
		if(type == EnumAshType.COAL) ashLevelCoal += amount;
		if(type == EnumAshType.MISC) ashLevelMisc += amount;
		if(type == EnumAshType.FLY) ashLevelFly += amount;
		if(type == EnumAshType.SOOT) ashLevelSoot += amount;
		this.markDirty();
		this.markMachineDirty(MachineDirtyCause.ENVIRONMENT);
	}

	private void refreshRuntimeState() {
		isFull = false;
		for(int i = 0; i < 5; i++) if(slots[i] != null) isFull = true;
		this.observeAshLevels();
		this.observeInventoryFingerprint();
	}

	private boolean hasProcessableAsh() {
		for(int type = 0; type < 5; type++) {
			int level = this.getAshLevel(type);
			int threshold = this.getAshThreshold(type);
			if(level < threshold) continue;
			for(int slot = 0; slot < 5; slot++) {
				ItemStack stack = slots[slot];
				if(stack == null || stack.stackSize < stack.getMaxStackSize() && stack.getItem() == ModItems.powder_ash && stack.getItemDamage() == type) return true;
			}
		}
		return false;
	}

	private int getAshLevel(int type) {
		switch(type) {
			case 0: return ashLevelWood;
			case 1: return ashLevelCoal;
			case 2: return ashLevelMisc;
			case 3: return ashLevelFly;
			default: return ashLevelSoot;
		}
	}

	private int getAshThreshold(int type) {
		switch(type) {
			case 0: return thresholdWood;
			case 1: return thresholdCoal;
			case 2: return thresholdMisc;
			case 3: return thresholdFly;
			default: return thresholdSoot;
		}
	}

	private void evaluateAndSchedule(long now) {
		if(runtimeInitialized && this.hasProcessableAsh()) this.scheduleMachineTransition(now + 1L, TASK_PROCESS_ASH, TASK_SLOT_SHARED);
		else this.cancelMachineTransition(TASK_PROCESS_ASH, TASK_SLOT_SHARED);
	}

	private boolean observeAshLevels() {
		boolean changed = observedAshLevels[0] != ashLevelWood || observedAshLevels[1] != ashLevelCoal || observedAshLevels[2] != ashLevelMisc || observedAshLevels[3] != ashLevelFly || observedAshLevels[4] != ashLevelSoot;
		observedAshLevels[0] = ashLevelWood;
		observedAshLevels[1] = ashLevelCoal;
		observedAshLevels[2] = ashLevelMisc;
		observedAshLevels[3] = ashLevelFly;
		observedAshLevels[4] = ashLevelSoot;
		return changed;
	}

	private int inventoryFingerprint() {
		int hash = 1;
		for(int i = 0; i < 5; i++) {
			ItemStack stack = slots[i];
			hash = 31 * hash + (stack == null ? 0 : System.identityHashCode(stack));
			if(stack != null) { hash = 31 * hash + stack.stackSize; hash = 31 * hash + stack.getItemDamage(); }
		}
		return hash;
	}

	private boolean observeInventoryFingerprint() {
		int current = this.inventoryFingerprint();
		boolean changed = inventoryFingerprintInitialized && current != observedInventoryFingerprint;
		observedInventoryFingerprint = current;
		inventoryFingerprintInitialized = true;
		return changed;
	}

	private void sendRuntimeState() {
		NBTTagCompound data = new NBTTagCompound();
		data.setInteger("playersUsing", this.playersUsing);
		data.setBoolean("isFull", this.isFull);
		this.networkPack(data, 50);
	}
	
	protected boolean processAsh(int level, EnumAshType type, int threshold) {
		
		if(level >= threshold) {
			for(int i = 0; i < 5; i++) {
				if(slots[i] == null) {
					slots[i] = DictFrame.fromOne(ModItems.powder_ash, type);
					ashLevelWood -= threshold;
					return true;
				} else if(slots[i].stackSize < slots[i].getMaxStackSize() && slots[i].getItem() == ModItems.powder_ash && slots[i].getItemDamage() == type.ordinal()) {
					slots[i].stackSize++;
					return true;
				}
			}
		}
		
		return false;
	}

	@Override
	public void networkUnpack(NBTTagCompound nbt) {
		super.networkUnpack(nbt);
		this.playersUsing = nbt.getInteger("playersUsing");
		this.isFull = nbt.getBoolean("isFull");
	}

	@Override
	public int[] getAccessibleSlotsFromSide(int meta) {
		return new int[] { 0, 1, 2, 3, 4 };
	}

	@Override
	public boolean canExtractItem(int i, ItemStack itemStack, int j) {
		return true;
	}
	
	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);

		this.ashLevelWood = nbt.getInteger("ashLevelWood");
		this.ashLevelCoal = nbt.getInteger("ashLevelCoal");
		this.ashLevelMisc = nbt.getInteger("ashLevelMisc");
		this.ashLevelFly = nbt.getInteger("ashLevelFly");
		this.ashLevelSoot = nbt.getInteger("ashLevelSoot");
	}
	
	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);

		nbt.setInteger("ashLevelWood", ashLevelWood);
		nbt.setInteger("ashLevelCoal", ashLevelCoal);
		nbt.setInteger("ashLevelMisc", ashLevelMisc);
		nbt.setInteger("ashLevelFly", ashLevelFly);
		nbt.setInteger("ashLevelSoot", ashLevelSoot);
	}
	
	AxisAlignedBB bb = null;
	
	@Override
	public AxisAlignedBB getRenderBoundingBox() {
		
		if(bb == null) {
			bb = AxisAlignedBB.getBoundingBox(
					xCoord - 1,
					yCoord,
					zCoord - 1,
					xCoord + 2,
					yCoord + 1,
					zCoord + 2
					);
		}
		
		return bb;
	}
	
	@Override
	@SideOnly(Side.CLIENT)
	public double getMaxRenderDistanceSquared() {
		return 65536.0D;
	}

	@Override
	public Container provideContainer(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new ContainerAshpit(player.inventory, this);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public Object provideGUI(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new GUIAshpit(player.inventory, this);
	}
}
