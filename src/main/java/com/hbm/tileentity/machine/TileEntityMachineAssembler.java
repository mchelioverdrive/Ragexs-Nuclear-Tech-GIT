package com.hbm.tileentity.machine;

import java.util.List;
import java.util.Random;

import com.hbm.blocks.BlockDummyable;
import com.hbm.blocks.ModBlocks;
import com.hbm.handler.MultiblockHandlerXR;
import com.hbm.inventory.UpgradeManagerNT;
import com.hbm.inventory.container.ContainerMachineAssembler;
import com.hbm.inventory.gui.GUIMachineAssembler;
import com.hbm.inventory.recipes.AssemblerRecipes;
import com.hbm.items.ModItems;
import com.hbm.items.machine.ItemMachineUpgrade.UpgradeType;
import com.hbm.lib.Library;
import com.hbm.main.MainRegistry;
import com.hbm.machine.MachineDirtyCause;
import com.hbm.machine.MachineExecutionStrategy;
import com.hbm.machine.MachineRuntimeManager;
import com.hbm.sound.AudioWrapper;
import com.hbm.tileentity.IUpgradeInfoProvider;
import com.hbm.util.BobMathUtil;
import com.hbm.util.I18nUtil;
import com.hbm.util.fauxpointtwelve.DirPos;

import api.hbm.energymk2.EnergyUnits;
import api.hbm.energymk2.IBatteryItem;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

public class TileEntityMachineAssembler extends TileEntityMachineAssemblerBase implements IUpgradeInfoProvider {
	private final UpgradeManagerNT upgradeManager = new UpgradeManagerNT();
	private static final int TASK_ACCOUNTING = 1;
	private static final int TASK_SLOT_ASSEMBLER = 0;
	private boolean cachedEligible;
	private boolean needsInputTransfer = true;
	private boolean runtimeStateInitialized;
	private boolean runtimeEnergyMutation;
	private long nextRuntimeTick = -1L;
	private long lastAccountingTick = Long.MIN_VALUE;
	private long observedPower;
	private ItemStack observedTemplate;
	private Item observedTemplateItem;
	private int observedTemplateMeta;
	private int observedTemplateNbtHash;
	private long observedRecipeGeneration = -1L;
	private int observedInventoryFingerprint;
	private boolean inventoryFingerprintInitialized;
	private int cachedPositionMeta = -1;
	private DirPos[] cachedConnectionPositions;
	private DirPos[] cachedInputPositions;
	private DirPos[] cachedOutputPositions;

	
	public int recipe = -1;

	Random rand = new Random();
	
	public TileEntityMachineAssembler() {
		super(18);
	}

	@Override
	public String getName() {
		return "container.assembler";
	}

	@Override
	public boolean isItemValidForSlot(int i, ItemStack itemStack) {
		if(i == 0)
			if(itemStack.getItem() instanceof IBatteryItem)
				return true;
		
		if(i == 1)
			return true;
		
		return false;
	}
	
	@Override
	public void updateEntity() {
		if(!worldObj.isRemote) {
			this.networkPackNTIfDirty(150);
		} else {
			
			float volume = this.getVolume(2F);

			if(isProgressing && volume > 0) {
				
				if(audio == null) {
					audio = this.createAudioLoop();
					audio.updateVolume(volume);
					audio.startSound();
				} else if(!audio.isPlaying()) {
					audio = rebootAudio(audio);
					audio.updateVolume(volume);
				}
				
			} else {
				
				if(audio != null) {
					audio.stopSound();
					audio = null;
				}
			}
		}
	}

	@Override
	public int getMachineExecutionStrategies() {
		return MachineExecutionStrategy.EVENT_DRIVEN | MachineExecutionStrategy.SCHEDULED | MachineExecutionStrategy.COARSE_5 | MachineExecutionStrategy.COARSE_20;
	}

	@Override
	public String getMachineRuntimeType() {
		return "hbm:assembler";
	}

	@Override
	public void onMachineRuntimeDirty(int causes) {
		if(worldObj == null || worldObj.isRemote) return;
		if((causes & MachineDirtyCause.LIFECYCLE) != 0 && this.upgradeLegacyMetadata()) return;
		if(this.refreshUpgrades(false)) {
			causes |= MachineDirtyCause.CONFIGURATION;
			this.markDirty();
			this.markNetworkDirty();
		}
		if((causes & (MachineDirtyCause.LIFECYCLE | MachineDirtyCause.INVENTORY | MachineDirtyCause.RECIPE | MachineDirtyCause.CONFIGURATION)) != 0) {
			this.observeTemplate();
			this.observedRecipeGeneration = AssemblerRecipes.recipeGeneration;
			this.needsInputTransfer = !this.hasAssemblerInputs(0);
			this.cachedEligible = !this.needsInputTransfer && this.hasAssemblerOutputSpace(0);
		}
		this.runtimeStateInitialized = true;

		long now = worldObj.getTotalWorldTime();
		if((causes & MachineDirtyCause.LIFECYCLE) != 0 && this.nextRuntimeTick == now + 1L && this.progress[0] > 0 && this.cachedEligible && this.energyQuanta >= EnergyUnits.wattsToQuantaPerTick(this.operatingPowerWatts)) {
			this.scheduleMachineTransition(this.nextRuntimeTick, TASK_ACCOUNTING, TASK_SLOT_ASSEMBLER);
		} else {
			this.runAccountingTick(now);
		}
	}

	@Override
	public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(taskType != TASK_ACCOUNTING || taskSlot != TASK_SLOT_ASSEMBLER || worldObj == null || worldObj.isRemote) return;
		this.nextRuntimeTick = -1L;
		if(!this.runtimeStateInitialized) return;
		this.runAccountingTick(worldObj.getTotalWorldTime());
	}

	@Override
	public void onMachineCoarsePoll(int cadence) {
		if(worldObj == null || worldObj.isRemote) return;
		if(cadence == 5) {
			int beforeTransfer = this.currentInventoryFingerprint();
			if(slots[5] != null || needsTemplateSwitch[0] && slots[4] != null) this.unloadItems(0);
			boolean transferred = (!runtimeStateInitialized || needsInputTransfer) && this.loadItems(0);
			boolean inventoryChanged = this.observeInventoryFingerprint() || beforeTransfer != this.currentInventoryFingerprint();
			if(transferred || inventoryChanged) {
				this.needsInputTransfer = !this.hasAssemblerInputs(0);
				this.cancelAccountingTransition();
				this.markDirty();
				this.markNetworkDirty();
				this.markMachineDirty(MachineDirtyCause.INVENTORY | MachineDirtyCause.RECIPE);
			}
			return;
		}
		if(cadence != 20) return;
		boolean templateChanged = this.observeTemplate();
		boolean recipeChanged = this.observedRecipeGeneration != AssemblerRecipes.recipeGeneration;
		this.observedRecipeGeneration = AssemblerRecipes.recipeGeneration;
		boolean inventoryChanged = this.observeInventoryFingerprint();
		boolean upgradeChanged = this.refreshUpgrades(true);
		if(upgradeChanged) {
			this.markDirty();
			this.markNetworkDirty();
		}
		if(templateChanged || recipeChanged || inventoryChanged || upgradeChanged || this.energyQuanta != this.observedPower || this.hasBatteryWork() && this.nextRuntimeTick < 0L) {
			this.cancelAccountingTransition();
			if(inventoryChanged) this.upgradeManager.invalidate();
			this.markMachineDirty((templateChanged || inventoryChanged ? MachineDirtyCause.INVENTORY : 0) | (recipeChanged ? MachineDirtyCause.RECIPE : 0) | (upgradeChanged ? MachineDirtyCause.CONFIGURATION : 0) | (this.energyQuanta != this.observedPower ? MachineDirtyCause.ENERGY : 0));
		}
		this.updateConnections();
	}

	private boolean upgradeLegacyMetadata() {
		if(this.getBlockMetadata() >= 12) return false;
		int meta = this.getBlockMetadata();
		if(meta == 2 || meta == 14) meta = 4;
		else if(meta == 4 || meta == 13) meta = 3;
		else if(meta == 3 || meta == 15) meta = 5;
		else if(meta == 5 || meta == 12) meta = 2;
		ForgeDirection dir = ForgeDirection.getOrientation(meta);
		MachineRuntimeManager.remove(this);
		worldObj.removeTileEntity(xCoord, yCoord, zCoord);
		worldObj.setBlock(xCoord, yCoord, zCoord, ModBlocks.machine_assembler, dir.ordinal() + 10, 3);
		MultiblockHandlerXR.fillSpace(worldObj, xCoord, yCoord, zCoord, ((BlockDummyable) ModBlocks.machine_assembler).getDimensions(), ModBlocks.machine_assembler, dir);
		NBTTagCompound data = new NBTTagCompound();
		this.writeToNBT(data);
		TileEntityMachineAssembler replacement = (TileEntityMachineAssembler) worldObj.getTileEntity(xCoord, yCoord, zCoord);
		if(replacement != null) {
			long generation = replacement.getMachineLifecycleGeneration();
			replacement.readFromNBT(data);
			replacement.adoptMachineLifecycleGeneration(generation);
		}
		return true;
	}

	private boolean observeInventoryFingerprint() {
		int fingerprint = this.currentInventoryFingerprint();
		boolean changed = inventoryFingerprintInitialized && fingerprint != observedInventoryFingerprint;
		observedInventoryFingerprint = fingerprint;
		inventoryFingerprintInitialized = true;
		return changed;
	}

	private int currentInventoryFingerprint() {
		int hash = 1;
		for(int i = 0; i < slots.length; i++) {
			ItemStack stack = slots[i];
			int slotHash = stack == null ? 0 : 31 * (31 * (31 * System.identityHashCode(stack.getItem()) + stack.getItemDamage()) + stack.stackSize) + (stack.getTagCompound() == null ? 0 : stack.getTagCompound().hashCode());
			hash = 31 * hash + slotHash;
		}
		return hash;
	}

	private void runAccountingTick(long now) {
		if(this.lastAccountingTick == now) return;
		this.lastAccountingTick = now;
		if(this.observedRecipeGeneration != AssemblerRecipes.recipeGeneration) {
			this.observedRecipeGeneration = AssemblerRecipes.recipeGeneration;
			this.needsInputTransfer = !this.hasAssemblerInputs(0);
			this.cachedEligible = !this.needsInputTransfer && this.hasAssemblerOutputSpace(0);
		}
		long oldPower = this.energyQuanta;
		int oldProgress = this.progress[0];
		int oldMaxProgress = this.maxProgress[0];
		boolean oldProgressing = this.isProgressing;
		boolean completed = false;

		this.isProgressing = false;
		this.setPowerInternal(Library.chargeTEFromItems(slots, getPowerSlot(), energyQuanta, getEnergyCapacityQuanta()));
		if(this.cachedEligible && this.energyQuanta >= EnergyUnits.wattsToQuantaPerTick(this.operatingPowerWatts)) {
			int duration = this.getProcessTime(0) * this.speed / 100;
			if(this.progress[0] + 1 >= duration && !this.hasValidProcessInputs(0)) {
				this.cachedEligible = false;
				this.needsInputTransfer = !this.hasAssemblerInputs(0);
				this.progress[0] = 0;
			} else {
				this.isProgressing = true;
				this.runtimeEnergyMutation = true;
				try {
					this.process(0);
				} finally {
					this.runtimeEnergyMutation = false;
				}
				if(this.progress[0] == 0) {
					completed = true;
					this.needsInputTransfer = !this.hasAssemblerInputs(0);
					this.cachedEligible = !this.needsInputTransfer && this.hasAssemblerOutputSpace(0);
				}
			}
		} else {
			this.progress[0] = 0;
		}

		this.observedPower = this.energyQuanta;
		if(oldPower != this.energyQuanta || oldProgress != this.progress[0] || oldMaxProgress != this.maxProgress[0] || oldProgressing != this.isProgressing || completed) {
			this.markDirty();
			this.markNetworkDirty();
			this.networkPackNTIfDirty(150);
		}
		if(this.isProgressing || this.hasBatteryWork() || this.cachedEligible && this.energyQuanta >= EnergyUnits.wattsToQuantaPerTick(this.operatingPowerWatts)) {
			this.nextRuntimeTick = now + 1L;
			this.scheduleMachineTransition(this.nextRuntimeTick, TASK_ACCOUNTING, TASK_SLOT_ASSEMBLER);
		} else {
			this.nextRuntimeTick = -1L;
		}
	}

	private boolean refreshUpgrades(boolean contentAware) {
		int oldSpeed = this.speed;
		long oldOperatingPowerWatts = this.operatingPowerWatts;
		if(contentAware) this.upgradeManager.checkSlots(slots, 1, 3);
		else this.upgradeManager.checkSlotsIfDirty(slots, 1, 3);
		int speedLevel = Math.min(this.upgradeManager.getLevel(UpgradeType.SPEED), 3);
		int powerLevel = Math.min(this.upgradeManager.getLevel(UpgradeType.POWER), 3);
		int overLevel = this.upgradeManager.getLevel(UpgradeType.OVERDRIVE);
		this.speed = (100 - speedLevel * 25 + powerLevel * 5) / (overLevel + 1);
		long consumptionQuantaPerTick = (100L + speedLevel * 300L - powerLevel * 30L) * (overLevel + 1L);
		this.operatingPowerWatts = EnergyUnits.quantaPerTickToWatts(consumptionQuantaPerTick);
		return oldSpeed != this.speed || oldOperatingPowerWatts != this.operatingPowerWatts;
	}

	private boolean hasBatteryWork() {
		if(this.energyQuanta >= this.getEnergyCapacityQuanta() || slots[0] == null) return false;
		if(slots[0].getItem() == ModItems.battery_creative || slots[0].getItem() == ModItems.fusion_core_infinite) return true;
		if(!(slots[0].getItem() instanceof IBatteryItem)) return false;
		IBatteryItem battery = (IBatteryItem) slots[0].getItem();
		return battery.getMaxOutputQuantaPerTick() > 0 && battery.getStoredEnergyQuanta(slots[0]) > 0;
	}

	private boolean observeTemplate() {
		ItemStack template = slots[4];
		Item item = template == null ? null : template.getItem();
		int meta = template == null ? 0 : template.getItemDamage();
		int nbtHash = template == null || template.getTagCompound() == null ? 0 : template.getTagCompound().hashCode();
		boolean changed = observedTemplate != template || observedTemplateItem != item || observedTemplateMeta != meta || observedTemplateNbtHash != nbtHash;
		observedTemplate = template;
		observedTemplateItem = item;
		observedTemplateMeta = meta;
		observedTemplateNbtHash = nbtHash;
		return changed;
	}

	private void setPowerInternal(long value) {
		this.runtimeEnergyMutation = true;
		try {
			this.setStoredEnergyQuanta(value);
		} finally {
			this.runtimeEnergyMutation = false;
		}
	}

	private void cancelAccountingTransition() {
		this.cancelMachineTransition(TASK_ACCOUNTING, TASK_SLOT_ASSEMBLER);
		this.nextRuntimeTick = -1L;
	}
	
	@Override
	public void serialize(ByteBuf buf) {
		super.serialize(buf);
		buf.writeLong(energyQuanta);
		for(int i = 0; i < getRecipeCount(); i++) {
			buf.writeInt(progress[i]);
			buf.writeInt(maxProgress[i]);
		}
		
		buf.writeBoolean(isProgressing);
		buf.writeInt(recipe);
	}
	
	@Override
	public void deserialize(ByteBuf buf) {
		super.deserialize(buf);
		energyQuanta = buf.readLong();
		for(int i = 0; i < getRecipeCount(); i++) {
			progress[i] = buf.readInt();
			maxProgress[i] = buf.readInt();
		}
		
		isProgressing = buf.readBoolean();
		recipe = buf.readInt();
	}
	
	@Override
	public AudioWrapper createAudioLoop() {
		return MainRegistry.proxy.getLoopedSound("hbm:block.assemblerOperate", xCoord, yCoord, zCoord, 1.0F, 10F, 1.0F);
	}
	
	private void updateConnections() {
		
		for(DirPos pos : getConPos()) {
			this.trySubscribe(worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
		}
	}
	
	public DirPos[] getConPos() {
		this.refreshPositionCache();
		if(this.cachedConnectionPositions != null) return this.cachedConnectionPositions;

		ForgeDirection dir = ForgeDirection.getOrientation(this.getBlockMetadata() - BlockDummyable.offset).getOpposite();
		ForgeDirection rot = dir.getRotation(ForgeDirection.DOWN);
		
		return this.cachedConnectionPositions = new DirPos[] {
				new DirPos(xCoord + rot.offsetX * 3,				yCoord,	zCoord + rot.offsetZ * 3,				rot),
				new DirPos(xCoord - rot.offsetX * 2,				yCoord,	zCoord - rot.offsetZ * 2,				rot.getOpposite()),
				new DirPos(xCoord + rot.offsetX * 3 + dir.offsetX,	yCoord,	zCoord + rot.offsetZ * 3 + dir.offsetZ, rot),
				new DirPos(xCoord - rot.offsetX * 2 + dir.offsetX,	yCoord,	zCoord - rot.offsetZ * 2 + dir.offsetZ, rot.getOpposite())
		};
	}

	@Override
	public void onChunkUnload() {
		this.runtimeStateInitialized = false;
		super.onChunkUnload();

		if(audio != null) {
			audio.stopSound();
			audio = null;
		}
	}

	@Override
	public void invalidate() {

		super.invalidate();

		if(audio != null) {
			audio.stopSound();
			audio = null;
		}
	}
	
	private AudioWrapper audio;

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		this.nextRuntimeTick = nbt.hasKey("runtimeAssemblerNextTick") ? nbt.getLong("runtimeAssemblerNextTick") : -1L;
		this.isProgressing = nbt.hasKey("runtimeAssemblerActive") ? nbt.getBoolean("runtimeAssemblerActive") : this.progress[0] > 0;
		this.needsTemplateSwitch[0] = nbt.getBoolean("runtimeAssemblerTemplateSwitch");
		this.runtimeStateInitialized = false;
		this.needsInputTransfer = true;
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		nbt.setLong("runtimeAssemblerNextTick", this.nextRuntimeTick);
		nbt.setBoolean("runtimeAssemblerActive", this.isProgressing);
		nbt.setBoolean("runtimeAssemblerTemplateSwitch", this.needsTemplateSwitch[0]);
	}

	@Override
	protected void onInventorySlotChanged(int slot) {
		super.onInventorySlotChanged(slot);
		this.cancelAccountingTransition();
		if(slot == 4 || slot >= 6 && slot <= 17) this.needsInputTransfer = true;
		if(slot >= 1 && slot <= 3) this.upgradeManager.invalidate();
	}

	@Override
	public void setStoredEnergyQuanta(long energyQuanta) {
		if(this.energyQuanta == energyQuanta) return;
		super.setStoredEnergyQuanta(energyQuanta);
		this.markNetworkDirty();
		if(!this.runtimeEnergyMutation) {
			this.cancelAccountingTransition();
			this.markMachineEnergyDirty();
		}
	}

	@Override
	public int getRecipeCount() {
		return 1;
	}

	@Override
	public int getTemplateIndex(int index) {
		return 4;
	}

	@Override
	public int[] getSlotIndicesFromIndex(int index) {
		return new int[] {6, 17, 5};
	}

	@Override
	public DirPos[] getInputPositions() {
		this.refreshPositionCache();
		if(this.cachedInputPositions != null) return this.cachedInputPositions;
		ForgeDirection dir = ForgeDirection.getOrientation(this.getBlockMetadata() - BlockDummyable.offset);
		ForgeDirection rot = dir.getRotation(ForgeDirection.UP);
		return this.cachedInputPositions = new DirPos[] {new DirPos(xCoord - dir.offsetX * 3 + rot.offsetX, yCoord, zCoord - dir.offsetZ * 3 + rot.offsetZ, dir.getOpposite())};
	}

	@Override
	public DirPos[] getOutputPositions() {
		this.refreshPositionCache();
		if(this.cachedOutputPositions != null) return this.cachedOutputPositions;
		ForgeDirection dir = ForgeDirection.getOrientation(this.getBlockMetadata() - BlockDummyable.offset);
		return this.cachedOutputPositions = new DirPos[] {new DirPos(xCoord + dir.offsetX * 2, yCoord, zCoord + dir.offsetZ * 2, dir)};
	}

	private void refreshPositionCache() {
		int meta = this.getBlockMetadata();
		if(this.cachedPositionMeta == meta) return;
		this.cachedPositionMeta = meta;
		this.cachedConnectionPositions = null;
		this.cachedInputPositions = null;
		this.cachedOutputPositions = null;
	}

	@Override
	public int getPowerSlot() {
		return 0;
	}

	@Override
	public long getEnergyCapacityQuanta() {
		return 100_000;
	}
	
	@Override
	public AxisAlignedBB getRenderBoundingBox() {
		return AxisAlignedBB.getBoundingBox(xCoord, yCoord, zCoord, xCoord + 1, yCoord + 1, zCoord + 1).expand(2, 1, 2);
	}
	
	@Override
	@SideOnly(Side.CLIENT)
	public double getMaxRenderDistanceSquared() {
		return 65536.0D;
	}

	@Override
	public Container provideContainer(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new ContainerMachineAssembler(player.inventory, this);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public Object provideGUI(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new GUIMachineAssembler(player.inventory, this);
	}

	@Override
	public boolean canProvideInfo(UpgradeType type, int level, boolean extendedInfo) {
		return type == UpgradeType.SPEED || type == UpgradeType.POWER || type == UpgradeType.OVERDRIVE;
	}

	@Override
	public void provideInfo(UpgradeType type, int level, List<String> info, boolean extendedInfo) {
		info.add(IUpgradeInfoProvider.getStandardLabel(ModBlocks.machine_assembler));
		if(type == UpgradeType.SPEED) {
			info.add(EnumChatFormatting.GREEN + I18nUtil.resolveKey(this.KEY_DELAY, "-" + (level * 25) + "%"));
			info.add(EnumChatFormatting.RED + I18nUtil.resolveKey(this.KEY_CONSUMPTION, "+" + (level * 300) + "%"));
		}
		if(type == UpgradeType.POWER) {
			info.add(EnumChatFormatting.GREEN + I18nUtil.resolveKey(this.KEY_CONSUMPTION, "-" + (level * 30) + "%"));
			info.add(EnumChatFormatting.RED + I18nUtil.resolveKey(this.KEY_DELAY, "+" + (level * 5) + "%"));
		}
		if(type == UpgradeType.OVERDRIVE) {
			info.add((BobMathUtil.getBlink() ? EnumChatFormatting.RED : EnumChatFormatting.DARK_GRAY) + "YES");
		}
	}

	@Override
	public int getMaxLevel(UpgradeType type) {
		if(type == UpgradeType.SPEED) return 3;
		if(type == UpgradeType.POWER) return 3;
		if(type == UpgradeType.OVERDRIVE) return 9;
		return 0;
	}
}
