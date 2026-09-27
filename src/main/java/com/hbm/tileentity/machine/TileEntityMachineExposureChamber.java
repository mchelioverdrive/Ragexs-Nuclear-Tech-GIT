package com.hbm.tileentity.machine;

import api.hbm.energymk2.EnergyUnits;
import java.util.List;

import com.hbm.blocks.ModBlocks;
import com.hbm.inventory.UpgradeManagerNT;
import com.hbm.inventory.container.ContainerMachineExposureChamber;
import com.hbm.inventory.gui.GUIMachineExposureChamber;
import com.hbm.inventory.recipes.ExposureChamberRecipes;
import com.hbm.inventory.recipes.ExposureChamberRecipes.ExposureChamberRecipe;
import com.hbm.inventory.recipes.loader.SerializableRecipe;
import com.hbm.items.machine.ItemMachineUpgrade.UpgradeType;
import com.hbm.lib.Library;
import com.hbm.machine.MachineDirtyCause;
import com.hbm.machine.MachineExecutionStrategy;
import com.hbm.tileentity.IGUIProvider;
import com.hbm.tileentity.IUpgradeInfoProvider;
import com.hbm.tileentity.TileEntityMachineBase;
import com.hbm.util.BobMathUtil;
import com.hbm.util.I18nUtil;
import com.hbm.util.fauxpointtwelve.DirPos;

import api.hbm.energymk2.IEnergyReceiverMK2;
import api.hbm.energymk2.IBatteryItem;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

public class TileEntityMachineExposureChamber extends TileEntityMachineBase implements IGUIProvider, IEnergyReceiverMK2, IUpgradeInfoProvider {
	private final UpgradeManagerNT upgradeManager = new UpgradeManagerNT();

	
	public long energyQuanta;
	public static final long maxPower = 1_000_000;
	
	public int progress;
	public static final int processTimeBase = 200;
	public int processTime = processTimeBase;
	public static final int consumptionBase = 10_000;
	public int consumption = consumptionBase;
	public int savedParticles;
	public static final int maxParticles = 8;
	public boolean isOn = false;
	public float rotation;
	public float prevRotation;
	private boolean runtimeInitialized;
	private boolean runtimeEnergyMutation;
	private boolean runtimeSettling;
	private long lastAccountingTick = Long.MIN_VALUE;
	private long lastBatteryChargeTick = Long.MIN_VALUE;
	private long clientProgressTick;
	private int observedInventoryFingerprint;
	private boolean inventoryFingerprintInitialized;
	private long observedRecipeRevision;
	private ExposureChamberRecipe cachedInputRecipe;
	private ExposureChamberRecipe cachedLoadedRecipe;
	private static final int TASK_PROCESS = 1;
	private static final int TASK_BATTERY = 2;
	private static final int TASK_SETUP = 3;
	private static final int TASK_SLOT_MAIN = 0;
	private static final int TASK_SLOT_BATTERY = 1;
	private static final int TASK_SLOT_SETUP = 2;
	
	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		this.progress = nbt.getInteger("progress");
		this.energyQuanta = EnergyUnits.readEnergyQuanta(nbt, "power");
		this.savedParticles = nbt.getInteger("savedParticles");
		this.runtimeInitialized = false;
		this.inventoryFingerprintInitialized = false;
		this.cachedInputRecipe = null;
		this.cachedLoadedRecipe = null;
		this.lastAccountingTick = Long.MIN_VALUE;
		this.lastBatteryChargeTick = Long.MIN_VALUE;
	}
	
	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		if(worldObj != null && !worldObj.isRemote) this.settleProgressThrough(worldObj.getTotalWorldTime() - 1L);
		super.writeToNBT(nbt);
		nbt.setInteger("progress", progress);
		EnergyUnits.writeEnergyQuanta(nbt, energyQuanta);
		nbt.setInteger("savedParticles", savedParticles);
	}

	public TileEntityMachineExposureChamber() {
		/*
		 * 0: Particle
		 * 1: Particle internal
		 * 2: Particle container
		 * 3: Ingredient
		 * 4: Output
		 * 5: Battery
		 * 6-7: Upgrades
		 */
		super(8);
	}

	@Override
	public String getName() {
		return "container.exposureChamber";
	}

	@Override
	public void updateEntity() {
		if(!worldObj.isRemote) return;

		this.prevRotation = this.rotation;
		
		if(this.isOn) {
			
			this.rotation += 10D;
			
			if(this.rotation >= 720D) {
				this.rotation -= 720D;
				this.prevRotation -= 720D;
			}
		}
	}

	@Override public int getMachineExecutionStrategies() {
		return MachineExecutionStrategy.EVENT_DRIVEN | MachineExecutionStrategy.SCHEDULED | MachineExecutionStrategy.COARSE_5 | MachineExecutionStrategy.COARSE_20 | MachineExecutionStrategy.COARSE_100;
	}

	@Override public void onMachineRuntimeDirty(int causes) {
		if(worldObj == null || worldObj.isRemote) return;
		long now = worldObj.getTotalWorldTime();
		boolean wasActive = runtimeInitialized && isOn;
		if(lastAccountingTick == Long.MIN_VALUE) lastAccountingTick = now;
		else this.settleProgressThrough(now - 1L);
		runtimeInitialized = true;
		this.refreshUpgradeSettings();
		this.refreshRuntimeRecipes();
		this.observeInventoryFingerprint();
		observedRecipeRevision = SerializableRecipe.getRegistryRevision();
		if((causes & (MachineDirtyCause.LIFECYCLE | MachineDirtyCause.TOPOLOGY)) != 0) this.updateConnections();
		this.reconcileRuntimeState();
		isOn = this.canProcessNow() || progress > 0;
		if(wasActive && this.canProcessNow()) this.settleProgressThrough(now);
		this.evaluateAndSchedule(now);
		this.networkPackNTIfDirty(50);
	}

	@Override public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(worldObj == null || worldObj.isRemote || !runtimeInitialized) return;
		long now = worldObj.getTotalWorldTime();
		if(taskType == TASK_BATTERY && taskSlot == TASK_SLOT_BATTERY) {
			this.settleProgressThrough(now);
			if(lastBatteryChargeTick != now) this.chargeBattery(now);
			if(this.cachedLoadedRecipe != null && slots[1] != null && savedParticles > 0 && energyQuanta >= consumption)
				this.scheduleMachineTransition(now, TASK_PROCESS, TASK_SLOT_MAIN);
			if(this.hasBatteryWork()) this.scheduleMachineTransition(now + 1L, TASK_BATTERY, TASK_SLOT_BATTERY);
			else this.cancelMachineTransition(TASK_BATTERY, TASK_SLOT_BATTERY);
			return;
		} else if(taskType == TASK_SETUP && taskSlot == TASK_SLOT_SETUP) {
			this.settleProgressThrough(now - 1L);
			runtimeSettling = true;
			try { this.loadParticleIfPossible(); } finally { runtimeSettling = false; }
			this.refreshRuntimeRecipes();
			if(lastBatteryChargeTick != now && this.hasBatteryWork()) this.chargeBattery(now);
			this.settleProgressThrough(now);
		} else if(taskType == TASK_PROCESS && taskSlot == TASK_SLOT_MAIN) this.settleProgressThrough(now);
		else return;
		this.observeInventoryFingerprint();
		this.markDirty();
		this.markNetworkDirty();
		this.evaluateAndSchedule(now);
		this.networkPackNTIfDirty(50);
	}

	private void chargeBattery(long now) {
		long oldEnergy = energyQuanta;
		runtimeEnergyMutation = true;
		try { this.setStoredEnergyQuanta(Library.chargeTEFromItems(slots, 5, energyQuanta, maxPower)); }
		finally { runtimeEnergyMutation = false; }
		lastBatteryChargeTick = now;
		if(oldEnergy != energyQuanta) this.markDirty();
	}

	@Override public void onMachineCoarsePoll(int cadence) {
		if(worldObj == null || worldObj.isRemote) return;
		if(cadence == 5) {
			if(this.observeInventoryFingerprint()) this.markMachineDirty(MachineDirtyCause.INVENTORY | MachineDirtyCause.RECIPE | MachineDirtyCause.UPGRADE);
		} else if(cadence == 20) {
			this.updateConnections();
		} else if(cadence == 100) {
			long revision = SerializableRecipe.getRegistryRevision();
			if(revision != observedRecipeRevision) {
				observedRecipeRevision = revision;
				this.markMachineDirty(MachineDirtyCause.RECIPE);
			}
		}
	}

	private void refreshUpgradeSettings() {
		this.upgradeManager.checkSlots(slots, 6, 7);
		int speedLevel = Math.min(this.upgradeManager.getLevel(UpgradeType.SPEED), 3);
		int powerLevel = Math.min(this.upgradeManager.getLevel(UpgradeType.POWER), 3);
		int overdriveLevel = Math.min(this.upgradeManager.getLevel(UpgradeType.OVERDRIVE), 3);
		this.consumption = this.consumptionBase;
		this.processTime = this.processTimeBase - this.processTimeBase / 4 * speedLevel;
		this.consumption *= (speedLevel / 2 + 1);
		this.processTime *= (powerLevel / 2 + 1);
		this.consumption /= (powerLevel + 1);
		this.processTime /= (overdriveLevel + 1);
		this.consumption *= (overdriveLevel * 2 + 1);
	}

	private void reconcileRuntimeState() {
		if(savedParticles <= 0) {
			slots[1] = null;
			cachedLoadedRecipe = null;
		}
		isOn = false;
		if(!this.canProcessNow()) progress = 0;
	}

	private void refreshRuntimeRecipes() {
		cachedInputRecipe = slots[0] != null && slots[3] != null ? this.getRecipe(slots[0], slots[3]) : null;
		cachedLoadedRecipe = slots[1] != null && savedParticles > 0 && slots[3] != null ? this.getRecipe(slots[1], slots[3]) : null;
	}

	private boolean canLoadParticle() {
		return slots[1] == null && slots[0] != null && slots[3] != null && savedParticles <= 0
				&& cachedInputRecipe != null && this.canStoreParticleContainer();
	}

	private boolean canStoreParticleContainer() {
		ItemStack container = slots[0].getItem().getContainerItem(slots[0]);
		return container == null || slots[2] == null
				|| slots[2].getItem() == container.getItem() && slots[2].getItemDamage() == container.getItemDamage() && slots[2].stackSize < slots[2].getMaxStackSize();
	}

	private boolean loadParticleIfPossible() {
		if(!this.canLoadParticle()) return false;
		ItemStack container = slots[0].getItem().getContainerItem(slots[0]);
		if(container != null) {
			if(slots[2] == null) slots[2] = container.copy();
			else slots[2].stackSize++;
		}
		slots[1] = slots[0].copy();
		slots[1].stackSize = 0;
		this.decrStackSize(0, 1);
		savedParticles = maxParticles;
		cachedLoadedRecipe = cachedInputRecipe;
		return true;
	}

	private boolean hasOutputRoom(ExposureChamberRecipe recipe) {
		return recipe != null && (slots[4] == null || slots[4].getItem() == recipe.output.getItem() && slots[4].getItemDamage() == recipe.output.getItemDamage() && slots[4].stackSize + recipe.output.stackSize <= slots[4].getMaxStackSize());
	}

	private boolean canProcessNow() {
		if(slots[1] == null || savedParticles <= 0 || energyQuanta < consumption) return false;
		return this.hasOutputRoom(cachedLoadedRecipe);
	}

	private void settleProgressThrough(long target) {
		if(lastAccountingTick == Long.MIN_VALUE || target <= lastAccountingTick) return;
		long elapsed = target - lastAccountingTick;
		lastAccountingTick = target;
		long remaining = elapsed;
		boolean changed = false;
		while(remaining > 0L) {
			if(savedParticles <= 0) {
				slots[1] = null;
				cachedLoadedRecipe = null;
			}
			if(!this.canProcessNow()) {
				if(progress != 0 || isOn) changed = true;
				progress = 0;
				isOn = false;
				break;
			}
			ExposureChamberRecipe recipe = cachedLoadedRecipe;
			long cost = Math.max(1L, consumption);
			long affordable = energyQuanta / cost;
			if(affordable <= 0L) {
				progress = 0;
				isOn = false;
				changed = true;
				break;
			}
			long steps = Math.min(remaining, Math.min(affordable, Math.max(1, processTime - progress)));
			runtimeEnergyMutation = true;
			try { this.setStoredEnergyQuanta(energyQuanta - steps * cost); }
			finally { runtimeEnergyMutation = false; }
			progress += (int) steps;
			remaining -= steps;
			isOn = true;
			changed = true;
			if(progress >= processTime) {
				progress = 0;
				runtimeSettling = true;
				try {
					savedParticles--;
					this.decrStackSize(3, 1);
					if(slots[4] == null) slots[4] = recipe.output.copy();
					else slots[4].stackSize += recipe.output.stackSize;
					if(savedParticles <= 0) { slots[1] = null; cachedLoadedRecipe = null; }
				} finally { runtimeSettling = false; }
				continue;
			}
			if(remaining > 0L && steps >= affordable) {
				progress = 0;
				isOn = false;
				changed = true;
				break;
			}
		}
		if(changed) { this.markDirty(); this.markNetworkDirty(); }
	}

	private boolean hasBatteryWork() {
		if(energyQuanta >= maxPower || slots[5] == null) return false;
		if(slots[5].getItem() == com.hbm.items.ModItems.battery_creative || slots[5].getItem() == com.hbm.items.ModItems.fusion_core_infinite) return true;
		if(!(slots[5].getItem() instanceof IBatteryItem)) return false;
		IBatteryItem battery = (IBatteryItem) slots[5].getItem();
		return battery.getMaxOutputQuantaPerTick() > 0 && battery.getStoredEnergyQuanta(slots[5]) > 0;
	}

	private void evaluateAndSchedule(long now) {
		if(!runtimeInitialized) return;
		if(this.canLoadParticle()) this.scheduleMachineTransition(now + 1L, TASK_SETUP, TASK_SLOT_SETUP);
		else this.cancelMachineTransition(TASK_SETUP, TASK_SLOT_SETUP);
		if(this.canProcessNow() || progress > 0) {
			long cost = Math.max(1L, consumption);
			long needed = Math.max(1L, processTime - progress);
			long affordable = energyQuanta / cost;
			long resetBoundary = affordable >= needed ? Long.MAX_VALUE : affordable + 1L;
			long delay = Math.max(1L, Math.min(needed, resetBoundary));
			this.scheduleMachineTransition(now + delay, TASK_PROCESS, TASK_SLOT_MAIN);
		} else this.cancelMachineTransition(TASK_PROCESS, TASK_SLOT_MAIN);
		if(this.hasBatteryWork()) this.scheduleMachineTransition(now + 1L, TASK_BATTERY, TASK_SLOT_BATTERY);
		else this.cancelMachineTransition(TASK_BATTERY, TASK_SLOT_BATTERY);
	}

	private void updateConnections() {
		for(DirPos pos : getConPos()) this.trySubscribe(worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
	}

	private int inventoryFingerprint() {
		int hash = 1;
		for(ItemStack stack : slots) {
			hash = 31 * hash + (stack == null ? 0 : System.identityHashCode(stack));
			if(stack != null) {
				hash = 31 * hash + stack.stackSize;
				hash = 31 * hash + stack.getItemDamage();
				hash = 31 * hash + (stack.getTagCompound() == null ? 0 : stack.getTagCompound().hashCode());
			}
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
	
	public DirPos[] getConPos() {
		ForgeDirection dir = ForgeDirection.getOrientation(this.getBlockMetadata() - 10);
		ForgeDirection rot = dir.getRotation(ForgeDirection.UP).getOpposite();
		return new DirPos[] {
				new DirPos(xCoord + rot.offsetX * 7 + dir.offsetX * 2, yCoord, zCoord + rot.offsetZ * 7 + dir.offsetZ * 2, dir),
				new DirPos(xCoord + rot.offsetX * 7 - dir.offsetX * 2, yCoord, zCoord + rot.offsetZ * 7 - dir.offsetZ * 2, dir.getOpposite()),
				new DirPos(xCoord + rot.offsetX * 8 + dir.offsetX * 2, yCoord, zCoord + rot.offsetZ * 8 + dir.offsetZ * 2, dir),
				new DirPos(xCoord + rot.offsetX * 8 - dir.offsetX * 2, yCoord, zCoord + rot.offsetZ * 8 - dir.offsetZ * 2, dir.getOpposite()),
				new DirPos(xCoord + rot.offsetX * 9, yCoord, zCoord + rot.offsetZ * 9, rot)
		};
	}
	
	public ExposureChamberRecipe getRecipe(ItemStack particle, ItemStack ingredient) {
		return ExposureChamberRecipes.getRecipe(particle, ingredient);
	}

	@Override
	public boolean isItemValidForSlot(int i, ItemStack stack) {
		
		//will only load new capsules if there's no cached particles, this should prevent clogging

		//accept items when the slots are already partially filled, i.e. applicable
		if(i == 0 && slots[0] != null) return true;
		if(i == 3 && slots[3] != null) return true;
		
		//if there's no particle stored, use the un-consumed capsule for reference
		ItemStack particle = slots[1] != null ? slots[1] : slots[0];
		
		//if no particle is loaded and an ingot is present
		if(i == 0 && particle == null && slots[3] != null) {
			ExposureChamberRecipe recipe = getRecipe(stack, slots[3]);
			return recipe != null;
		}
		
		//if a particle is loaded but no ingot present
		if(i == 3 && particle != null && slots[3] == null) {
			ExposureChamberRecipe recipe = getRecipe(slots[0], stack);
			return recipe != null;
		}
		
		//if there's nothing at all, find a reference recipe and see if the item matches anything
		if(particle == null && slots[3] == null) {
			
			for(ExposureChamberRecipe recipe : ExposureChamberRecipes.recipes) {
				if(i == 0 && recipe.particle.matchesRecipe(stack, true)) return true;
				if(i == 3 && recipe.ingredient.matchesRecipe(stack, true)) return true; 
			}
		}
		
		return false;
	}

	@Override
	public boolean canExtractItem(int i, ItemStack itemStack, int j) {
		return i == 2 || i == 4;
	}

	@Override
	public int[] getAccessibleSlotsFromSide(int side) {
		return new int[] {0, 2, 3, 4};
	}

	@Override
	public void serialize(ByteBuf buf) {
		buf.writeBoolean(this.isOn);
		buf.writeInt(this.progress);
		buf.writeInt(this.processTime);
		buf.writeInt(this.consumption);
		buf.writeLong(this.energyQuanta);
		buf.writeByte((byte) this.savedParticles);
		buf.writeLong(worldObj == null ? 0L : worldObj.getTotalWorldTime());
	}
	
	@Override
	public void deserialize(ByteBuf buf) {
		this.isOn = buf.readBoolean();
		this.progress = buf.readInt();
		this.processTime = buf.readInt();
		this.consumption = buf.readInt();
		this.energyQuanta = buf.readLong();
		this.savedParticles = buf.readByte();
		this.clientProgressTick = buf.readLong();
	}

	@Override
	public long getStoredEnergyQuanta() {
		return energyQuanta;
	}

	@Override
	public void setStoredEnergyQuanta(long energyQuanta) {
		if(this.energyQuanta == energyQuanta) return;
		if(!runtimeEnergyMutation && !runtimeSettling && runtimeInitialized && worldObj != null && !worldObj.isRemote) this.settleProgressThrough(worldObj.getTotalWorldTime() - 1L);
		this.energyQuanta = energyQuanta;
		this.markPowerNetDirty();
		if(!runtimeEnergyMutation) this.markMachineEnergyDirty();
	}

	@Override
	public long getEnergyCapacityQuanta() {
		return maxPower;
	}

	public int getProjectedProgress() {
		if(worldObj == null || !worldObj.isRemote || !isOn) return progress;
		long projected = progress + Math.max(0L, worldObj.getTotalWorldTime() - clientProgressTick);
		return (int) Math.min(Math.max(1, processTime), projected);
	}

	@Override protected void beforeInventorySlotChanged(int slot) {
		if(!runtimeSettling && runtimeInitialized && worldObj != null && !worldObj.isRemote) this.settleProgressThrough(worldObj.getTotalWorldTime() - 1L);
	}

	@Override public void onChunkUnload() {
		if(worldObj != null && !worldObj.isRemote) this.settleProgressThrough(worldObj.getTotalWorldTime() - 1L);
		lastAccountingTick = Long.MIN_VALUE;
		lastBatteryChargeTick = Long.MIN_VALUE;
		super.onChunkUnload();
	}

	AxisAlignedBB bb = null;
	
	@Override
	public AxisAlignedBB getRenderBoundingBox() {
		
		if(bb == null) {
			bb = AxisAlignedBB.getBoundingBox(
					xCoord - 8,
					yCoord,
					zCoord - 8,
					xCoord + 9,
					yCoord + 5,
					zCoord + 9
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
		return new ContainerMachineExposureChamber(player.inventory, this);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public Object provideGUI(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new GUIMachineExposureChamber(player.inventory, this);
	}

	@Override
	public boolean canProvideInfo(UpgradeType type, int level, boolean extendedInfo) {
		return type == UpgradeType.SPEED || type == UpgradeType.POWER || type == UpgradeType.OVERDRIVE;
	}

	@Override
	public void provideInfo(UpgradeType type, int level, List<String> info, boolean extendedInfo) {
		info.add(IUpgradeInfoProvider.getStandardLabel(ModBlocks.machine_exposure_chamber));
		if(type == UpgradeType.SPEED) {
			info.add(EnumChatFormatting.GREEN + I18nUtil.resolveKey(this.KEY_DELAY, "-" + (level * 25) + "%"));
			info.add(EnumChatFormatting.RED + I18nUtil.resolveKey(this.KEY_CONSUMPTION, "+" + (level * 50) + "%"));
		}
		if(type == UpgradeType.POWER) {
			info.add(EnumChatFormatting.GREEN + I18nUtil.resolveKey(this.KEY_CONSUMPTION, "-" + (100 - 100 / (level + 1)) + "%"));
			info.add(EnumChatFormatting.RED + I18nUtil.resolveKey(this.KEY_DELAY, "+" + (level * 50) + "%"));
		}
		if(type == UpgradeType.OVERDRIVE) {
			info.add((BobMathUtil.getBlink() ? EnumChatFormatting.RED : EnumChatFormatting.DARK_GRAY) + "YES");
		}
	}

	@Override
	public int getMaxLevel(UpgradeType type) {
		if(type == UpgradeType.SPEED) return 3;
		if(type == UpgradeType.POWER) return 3;
		if(type == UpgradeType.OVERDRIVE) return 3;
		return 0;
	}
}
