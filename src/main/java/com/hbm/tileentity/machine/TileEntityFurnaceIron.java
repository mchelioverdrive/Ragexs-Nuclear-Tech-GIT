package com.hbm.tileentity.machine;

import java.util.List;

import com.hbm.blocks.ModBlocks;
import com.hbm.handler.pollution.PollutionHandler;
import com.hbm.handler.pollution.PollutionHandler.PollutionType;
import com.hbm.inventory.UpgradeManagerNT;
import com.hbm.inventory.container.ContainerFurnaceIron;
import com.hbm.inventory.gui.GUIFurnaceIron;
import com.hbm.items.machine.ItemMachineUpgrade.UpgradeType;
import com.hbm.machine.MachineDirtyCause;
import com.hbm.machine.MachineExecutionStrategy;
import com.hbm.module.ModuleBurnTime;
import com.hbm.tileentity.IGUIProvider;
import com.hbm.tileentity.IUpgradeInfoProvider;
import com.hbm.tileentity.TileEntityMachineBase;
import com.hbm.util.FurnaceGasEmission;
import com.hbm.util.I18nUtil;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

public class TileEntityFurnaceIron extends TileEntityMachineBase implements IGUIProvider, IUpgradeInfoProvider {
	private final UpgradeManagerNT upgradeManager = new UpgradeManagerNT();

	
	public int maxBurnTime;
	public int burnTime;
	public boolean wasOn = false;

	public int progress;
	public int processingTime;
	public static final int baseTime = 160;
	
	public ModuleBurnTime burnModule;
	private ItemStack cachedResult;
	private int observedInventoryFingerprint;
	private boolean inventoryFingerprintInitialized;
	private int observedRecipeCount;
	private static final int TASK_PROCESS = 1;
	private static final int TASK_SLOT_MAIN = 0;
	private boolean runtimeInitialized;

	public TileEntityFurnaceIron() {
		super(5);
		
		burnModule = new ModuleBurnTime()
				.setLigniteTimeMod(1.25)
				.setCoalTimeMod(1.25)
				.setCokeTimeMod(1.5)
				.setSolidTimeMod(2)
				.setRocketTimeMod(2)
				.setBalefireTimeMod(2);
	}

	@Override
	public String getName() {
		return "container.furnaceIron";
	}

	@Override
	public void updateEntity() {
		if(!worldObj.isRemote) return;
			
		if(this.progress > 0) {
			ForgeDirection dir = ForgeDirection.getOrientation(this.getBlockMetadata() - 10);
			ForgeDirection rot = dir.getRotation(ForgeDirection.UP);
			
			double offset = this.progress % 2 == 0 ? 1 : 0.5;
			worldObj.spawnParticle("smoke", xCoord + 0.5 - dir.offsetX * offset - rot.offsetX * 0.1875, yCoord + 2, zCoord + 0.5 - dir.offsetZ * offset - rot.offsetZ * 0.1875, 0.0, 0.01, 0.0);
			
			if(this.progress % 5 == 0) {
				double rand = worldObj.rand.nextDouble();
				worldObj.spawnParticle("flame", xCoord + 0.5 + dir.offsetX * 0.25 + rot.offsetX * rand, yCoord + 0.25 + worldObj.rand.nextDouble() * 0.25, zCoord + 0.5 + dir.offsetZ * 0.25 + rot.offsetZ * rand, 0.0, 0.0, 0.0);
			}
		}
	}

	@Override public int getMachineExecutionStrategies() {
		return MachineExecutionStrategy.EVENT_DRIVEN | MachineExecutionStrategy.SCHEDULED | MachineExecutionStrategy.COARSE_5 | MachineExecutionStrategy.COARSE_100;
	}

	@Override public void onMachineRuntimeDirty(int causes) {
		if(worldObj == null || worldObj.isRemote) return;
		runtimeInitialized = true;
		this.refreshRuntimeState();
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
		this.sendRuntimeState();
	}

	@Override public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(taskType != TASK_PROCESS || taskSlot != TASK_SLOT_MAIN || worldObj == null || worldObj.isRemote || !runtimeInitialized) return;
		int oldBurnTime = burnTime;
		int oldProgress = progress;
		wasOn = false;
		if(burnTime <= 0) this.consumeFuel();
		if(this.canSmelt() && this.breatheAir(worldObj.getTotalWorldTime() % 5 == 0 ? 1 : 0)) {
			wasOn = true;
			FurnaceGasEmission.emitCarbonMonoxide(worldObj, xCoord, yCoord, zCoord, 600);
			progress++;
			burnTime--;
			if(progress % 15 == 0 && !muffled) worldObj.playSoundEffect(xCoord, yCoord, zCoord, "fire.fire", 1.0F, 0.5F + worldObj.rand.nextFloat() * 0.5F);
			if(progress >= processingTime) {
				if(slots[3] == null) slots[3] = cachedResult.copy();
				else slots[3].stackSize += cachedResult.stackSize;
				this.decrStackSize(0, 1);
				progress = 0;
				this.refreshCachedResult();
			}
			if(worldObj.getTotalWorldTime() % 20 == 0) PollutionHandler.incrementPollution(worldObj, xCoord, yCoord, zCoord, PollutionType.SOOT, PollutionHandler.SOOT_PER_SECOND);
		} else {
			progress = 0;
		}
		boolean inventoryChanged = this.observeInventoryFingerprint();
		if(inventoryChanged) this.markNetworkDirty();
		if(oldBurnTime != burnTime || oldProgress != progress || inventoryChanged) this.markDirty();
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
		this.sendRuntimeState();
	}

	@Override public void onMachineCoarsePoll(int cadence) {
		if(worldObj == null || worldObj.isRemote) return;
		if(cadence == 5 && this.observeInventoryFingerprint()) this.markMachineDirty(MachineDirtyCause.INVENTORY | MachineDirtyCause.RECIPE | MachineDirtyCause.UPGRADE);
		else if(cadence == 100) {
			int count = FurnaceRecipes.smelting().getSmeltingList().size();
			if(count != observedRecipeCount) {
				observedRecipeCount = count;
				this.markMachineDirty(MachineDirtyCause.RECIPE);
			}
		}
	}

	private void refreshRuntimeState() {
		this.upgradeManager.checkSlots(slots, 4, 4);
		this.processingTime = baseTime - ((baseTime / 2) * Math.min(this.upgradeManager.getLevel(UpgradeType.SPEED), 3) / 3);
		this.refreshCachedResult();
		this.observeInventoryFingerprint();
		this.observedRecipeCount = FurnaceRecipes.smelting().getSmeltingList().size();
		if(!this.canSmelt()) progress = 0;
	}

	private void refreshCachedResult() {
		ItemStack result = slots[0] == null ? null : FurnaceRecipes.smelting().getSmeltingResult(slots[0]);
		cachedResult = result == null ? null : result.copy();
	}

	private void consumeFuel() {
		for(int i = 1; i < 3; i++) {
			if(slots[i] == null) continue;
			int fuel = burnModule.getBurnTime(slots[i]);
			if(fuel <= 0) continue;
			maxBurnTime = burnTime = fuel;
			slots[i].stackSize--;
			if(slots[i].stackSize == 0) slots[i] = slots[i].getItem().getContainerItem(slots[i]);
			break;
		}
	}

	public boolean canSmelt() {
		if(burnTime <= 0 || cachedResult == null) return false;
		if(slots[3] == null) return true;
		if(!cachedResult.isItemEqual(slots[3])) return false;
		return cachedResult.stackSize + slots[3].stackSize <= slots[3].getMaxStackSize();
	}

	private boolean hasFuel() {
		return slots[1] != null && burnModule.getBurnTime(slots[1]) > 0 || slots[2] != null && burnModule.getBurnTime(slots[2]) > 0;
	}

	private void evaluateAndSchedule(long now) {
		if(!runtimeInitialized) return;
		if(burnTime > 0 && cachedResult != null && (slots[3] == null || cachedResult.isItemEqual(slots[3]) && cachedResult.stackSize + slots[3].stackSize <= slots[3].getMaxStackSize()) || burnTime <= 0 && this.hasFuel()) this.scheduleMachineTransition(now + 1L, TASK_PROCESS, TASK_SLOT_MAIN);
		else this.cancelMachineTransition(TASK_PROCESS, TASK_SLOT_MAIN);
	}

	private void sendRuntimeState() {
		NBTTagCompound data = new NBTTagCompound();
		data.setInteger("maxBurnTime", maxBurnTime);
		data.setInteger("burnTime", burnTime);
		data.setInteger("progress", progress);
		data.setInteger("processingTime", processingTime);
		data.setBoolean("wasOn", wasOn);
		this.networkPack(data, 50);
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

	@Override
	public void networkUnpack(NBTTagCompound nbt) {
		super.networkUnpack(nbt);
		
		this.maxBurnTime = nbt.getInteger("maxBurnTime");
		this.burnTime = nbt.getInteger("burnTime");
		this.progress = nbt.getInteger("progress");
		this.processingTime = nbt.getInteger("processingTime");
		this.wasOn = nbt.getBoolean("wasOn");
	}
	
	@Override
	public int[] getAccessibleSlotsFromSide(int meta) {
		return new int[] { 0, 1, 2, 3 };
	}

	@Override
	public boolean isItemValidForSlot(int i, ItemStack itemStack) {
		
		if(i == 0)
			return FurnaceRecipes.smelting().getSmeltingResult(itemStack) != null;
		
		if(i < 3)
			return burnModule.getBurnTime(itemStack) > 0;
			
		return false;
	}

	@Override
	public boolean canExtractItem(int i, ItemStack itemStack, int j) {
		return i == 3;
	}
	
	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);

		this.maxBurnTime = nbt.getInteger("maxBurnTime");
		this.burnTime = nbt.getInteger("burnTime");
		this.progress = nbt.getInteger("progress");
	}
	
	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);

		nbt.setInteger("maxBurnTime", maxBurnTime);
		nbt.setInteger("burnTime", burnTime);
		nbt.setInteger("progress", progress);
	}

	@Override
	public Container provideContainer(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new ContainerFurnaceIron(player.inventory, this);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public Object provideGUI(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new GUIFurnaceIron(player.inventory, this);
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
					yCoord + 3,
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
	public boolean canProvideInfo(UpgradeType type, int level, boolean extendedInfo) {
		return type == UpgradeType.SPEED;
	}

	@Override
	public void provideInfo(UpgradeType type, int level, List<String> info, boolean extendedInfo) {
		info.add(IUpgradeInfoProvider.getStandardLabel(ModBlocks.furnace_iron));
		if(type == UpgradeType.SPEED) {
			info.add(EnumChatFormatting.GREEN + I18nUtil.resolveKey(this.KEY_DELAY, "-" + (level * 50 / 3) + "%"));
		}
	}

	@Override
	public int getMaxLevel(UpgradeType type) {
		if(type == UpgradeType.SPEED) return 3;
		return 0;
	}
}
