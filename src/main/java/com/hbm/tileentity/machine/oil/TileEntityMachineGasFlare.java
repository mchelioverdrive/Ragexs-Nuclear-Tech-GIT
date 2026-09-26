package com.hbm.tileentity.machine.oil;

import api.hbm.energymk2.EnergyUnits;
import java.util.List;

import com.hbm.blocks.ModBlocks;
import com.hbm.dim.CelestialBody;
import com.hbm.interfaces.IControlReceiver;
import com.hbm.inventory.UpgradeManagerNT;
import com.hbm.inventory.container.ContainerMachineGasFlare;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.inventory.fluid.trait.FT_Flammable;
import com.hbm.inventory.fluid.trait.FT_Polluting;
import com.hbm.inventory.fluid.trait.FluidTrait.FluidReleaseType;
import com.hbm.inventory.fluid.trait.FT_Gaseous;
import com.hbm.inventory.fluid.trait.FluidTraitSimple.FT_Gaseous_ART;
import com.hbm.inventory.gui.GUIMachineGasFlare;
import com.hbm.items.machine.ItemMachineUpgrade.UpgradeType;
import com.hbm.lib.Library;
import com.hbm.main.MainRegistry;
import com.hbm.machine.MachineDirtyCause;
import com.hbm.machine.MachineExecutionStrategy;
import com.hbm.tileentity.IFluidCopiable;
import com.hbm.tileentity.IGUIProvider;
import com.hbm.tileentity.IUpgradeInfoProvider;
import com.hbm.tileentity.TileEntityMachineBase;
import com.hbm.util.CompatEnergyControl;
import com.hbm.util.FurnaceGasEmission;
import com.hbm.util.I18nUtil;
import com.hbm.util.ParticleUtil;
import com.hbm.util.fauxpointtwelve.DirPos;

import api.hbm.energymk2.IEnergyProviderMK2;
import api.hbm.fluid.IFluidStandardReceiver;
import api.hbm.tile.IInfoProviderEC;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;

public class TileEntityMachineGasFlare extends TileEntityMachineBase implements IEnergyProviderMK2, IFluidStandardReceiver, IControlReceiver, IGUIProvider, IUpgradeInfoProvider, IInfoProviderEC, IFluidCopiable {
	private static final int TASK_PROCESS = 1;
	private static final int TASK_SLOT_MAIN = 0;
	private final UpgradeManagerNT upgradeManager = new UpgradeManagerNT();
	private boolean runtimeInitialized;
	private boolean runtimeEnergyMutation;
	private DirPos[] runtimeConnections;
	private int observedOrientation = Integer.MIN_VALUE;


	public long energyQuanta;
	public static final long maxPower = 100000;
	public FluidTank tank;
	public boolean isOn = false;
	public boolean doesBurn = false;
	protected int fluidUsed = 0;
	protected int output = 0;

	public TileEntityMachineGasFlare() {
		super(6);
		tank = new FluidTank(Fluids.GAS, 64000);
		this.trackMachineFluidTank(tank);
	}

	@Override
	public String getName() {
		return "container.gasFlare";
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		this.energyQuanta = EnergyUnits.readEnergyQuanta(nbt, "powerTime");
		tank.readFromNBT(nbt, "gas");
		isOn = nbt.getBoolean("isOn");
		doesBurn = nbt.getBoolean("doesBurn");
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		EnergyUnits.writeEnergyQuanta(nbt, energyQuanta);
		tank.writeToNBT(nbt, "gas");
		nbt.setBoolean("isOn", isOn);
		nbt.setBoolean("doesBurn", doesBurn);
	}

	public long getPowerScaled(long i) {
		return (energyQuanta * i) / maxPower;
	}

	@Override
	public boolean hasPermission(EntityPlayer player) {
		return player.getDistanceSq(xCoord, yCoord, zCoord) <= 256;
	}

	@Override
	public void receiveControl(NBTTagCompound data) {
		if(data.hasKey("valve")) this.isOn = !this.isOn;
		if(data.hasKey("dial")) this.doesBurn = !this.doesBurn;
		this.worldObj.markTileEntityChunkModified(this.xCoord, this.yCoord, this.zCoord, this);
		this.markMachineDirty(MachineDirtyCause.CONFIGURATION);
	}

	@Override
	public void updateEntity() {
		if(worldObj.isRemote) {
			if(isOn && tank.getFill() > 0) {

				if((!doesBurn || !(tank.getTankType().hasTrait(FT_Flammable.class))) && (tank.getTankType().hasTrait(FT_Gaseous.class) || tank.getTankType().hasTrait(FT_Gaseous_ART.class))) {

					NBTTagCompound data = new NBTTagCompound();
					data.setString("type", "tower");
					data.setFloat("lift", 1F);
					data.setFloat("base", 0.25F);
					data.setFloat("max", 3F);
					data.setInteger("life", 150 + worldObj.rand.nextInt(20));
					data.setInteger("color", tank.getTankType().getColor());

					data.setDouble("posX", xCoord + 0.5);
					data.setDouble("posZ", zCoord + 0.5);
					data.setDouble("posY", yCoord + 11);

					MainRegistry.proxy.effectNT(data);

				}

				if(doesBurn && tank.getTankType().hasTrait(FT_Flammable.class) && MainRegistry.proxy.me().getDistanceSq(xCoord, yCoord + 10, zCoord) <= 1024) {

					NBTTagCompound data = new NBTTagCompound();
					data.setString("type", "vanillaExt");
					data.setString("mode", "smoke");
					data.setBoolean("noclip", true);
					data.setInteger("overrideAge", 50);

					if(worldObj.getTotalWorldTime() % 2 == 0) {
						data.setDouble("posX", xCoord + 1.5);
						data.setDouble("posZ", zCoord + 1.5);
						data.setDouble("posY", yCoord + 10.75);
					} else {
						data.setDouble("posX", xCoord + 1.125);
						data.setDouble("posZ", zCoord - 0.5);
						data.setDouble("posY", yCoord + 11.75);
					}

					MainRegistry.proxy.effectNT(data);
				}
			}
		}
	}

	@Override public int getMachineExecutionStrategies() {
		return MachineExecutionStrategy.EVENT_DRIVEN | MachineExecutionStrategy.SCHEDULED | MachineExecutionStrategy.COARSE_20;
	}

	@Override public void onMachineRuntimeDirty(int causes) {
		if(worldObj == null || worldObj.isRemote) return;
		runtimeInitialized = true;
		this.beginMachineFluidMutation();
		try {
			tank.setType(3, slots);
			tank.loadTank(1, 2, slots);
		} finally {
			this.endMachineFluidMutation();
		}
		this.upgradeManager.checkSlots(slots, 4, 5);
		this.refreshRuntimeConnections();
		this.subscribeToFluid();
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
		this.sendRuntimeState();
	}

	@Override public void onMachineCoarsePoll(int cadence) {
		if(cadence != 20 || worldObj == null || worldObj.isRemote || !runtimeInitialized) return;
		this.refreshRuntimeConnections();
		this.subscribeToFluid();
		this.sendRuntimeState();
	}

	@Override public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(taskType != TASK_PROCESS || taskSlot != TASK_SLOT_MAIN || worldObj == null || worldObj.isRemote || !runtimeInitialized) return;
		this.beginMachineFluidMutation();
		runtimeEnergyMutation = true;
		try {
			fluidUsed = 0;
			output = 0;
			for(DirPos pos : runtimeConnections) this.tryProvide(worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
			tank.setType(3, slots);
			tank.loadTank(1, 2, slots);
			this.processGas();
			this.setStoredEnergyQuanta(Library.chargeItemsFromTE(slots, 0, energyQuanta, maxPower));
			this.sendRuntimeState();
		} finally {
			runtimeEnergyMutation = false;
			this.endMachineFluidMutation();
		}
		this.markDirty();
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
	}

	private void processGas() {
		int maxVent = 50;
		int maxBurn = 10;
		if(isOn && tank.getFill() > 0) {
			int burn = Math.min(this.upgradeManager.getLevel(UpgradeType.SPEED), 3);
			int yield = Math.min(this.upgradeManager.getLevel(UpgradeType.EFFECT), 3);
			maxVent += maxVent * burn;
			maxBurn += maxBurn * burn;
			if(!doesBurn || !tank.getTankType().hasTrait(FT_Flammable.class) || !breatheAir(Math.min(maxBurn, tank.getFill()))) {
				if(tank.getTankType().hasTrait(FT_Gaseous.class) || tank.getTankType().hasTrait(FT_Gaseous_ART.class)) {
					int eject = Math.min(maxVent, tank.getFill());
					fluidUsed = eject;
					tank.setFill(tank.getFill() - eject);
					tank.getTankType().onFluidRelease(this, tank, eject);
					if(worldObj.getTotalWorldTime() % 7 == 0) worldObj.playSoundEffect(xCoord, yCoord + 11, zCoord, "random.fizz", getVolume(1.5F), 0.5F);
					if(worldObj.getTotalWorldTime() % 5 == 0 && eject > 0) FT_Polluting.pollute(worldObj, xCoord, yCoord, zCoord, tank.getTankType(), FluidReleaseType.SPILL, eject * 5);
					CelestialBody.emitGas(worldObj, tank.getTankType(), eject);
				}
			} else if(tank.getTankType().hasTrait(FT_Flammable.class)) {
				int eject = Math.min(maxBurn, tank.getFill());
				fluidUsed = eject;
				tank.setFill(tank.getFill() - eject);
				int penalty = (!tank.getTankType().hasTrait(FT_Gaseous.class) && !tank.getTankType().hasTrait(FT_Gaseous_ART.class)) ? 10 : 5;
				long powerProd = tank.getTankType().getTrait(FT_Flammable.class).getHeatEnergy() * eject / 1_000;
				powerProd /= penalty;
				powerProd += powerProd * yield / 3;
				output = (int) powerProd;
				this.setStoredEnergyQuanta(energyQuanta + powerProd);
				if(energyQuanta > maxPower) this.setStoredEnergyQuanta(maxPower);
				ParticleUtil.spawnGasFlame(worldObj, xCoord + 0.5F, yCoord + 11.75F, zCoord + 0.5F, worldObj.rand.nextGaussian() * 0.15, 0.2, worldObj.rand.nextGaussian() * 0.15);
				List<Entity> list = worldObj.getEntitiesWithinAABB(Entity.class, AxisAlignedBB.getBoundingBox(xCoord - 1, yCoord + 12, zCoord - 2, xCoord + 2, yCoord + 17, zCoord + 2));
				for(Entity e : list) {
					e.setFire(5);
					e.attackEntityFrom(DamageSource.onFire, 5F);
				}
				if(worldObj.getTotalWorldTime() % 3 == 0) worldObj.playSoundEffect(xCoord, yCoord + 11, zCoord, "hbm:weapon.flamethrowerShoot", getVolume(1.5F), 0.75F);
				if(worldObj.getTotalWorldTime() % 5 == 0 && eject > 0) FT_Polluting.pollute(worldObj, xCoord, yCoord, zCoord, tank.getTankType(), FluidReleaseType.BURN, eject * 5);
				FurnaceGasEmission.emitCarbonMonoxide(worldObj, xCoord, yCoord + 11, zCoord, Math.max(100, 600 / Math.max(eject, 1)));
			}
		}
	}

	private void evaluateAndSchedule(long now) {
		if(!runtimeInitialized) return;
		boolean gaseous = tank.getTankType().hasTrait(FT_Gaseous.class) || tank.getTankType().hasTrait(FT_Gaseous_ART.class);
		boolean flammable = tank.getTankType().hasTrait(FT_Flammable.class);
		boolean processableGas = isOn && tank.getFill() > 0 && (gaseous || (doesBurn && flammable));
		if(processableGas || energyQuanta > 0L) this.scheduleMachineTransition(now + 1L, TASK_PROCESS, TASK_SLOT_MAIN);
		else {
			fluidUsed = 0;
			output = 0;
			this.cancelMachineTransition(TASK_PROCESS, TASK_SLOT_MAIN);
		}
	}

	private void refreshRuntimeConnections() {
		int orientation = this.getBlockMetadata();
		if(runtimeConnections == null || observedOrientation != orientation) {
			runtimeConnections = this.buildConnections();
			observedOrientation = orientation;
		}
	}

	private void subscribeToFluid() {
		for(DirPos pos : runtimeConnections) this.trySubscribe(tank.getTankType(), worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
	}

	private void sendRuntimeState() {
		NBTTagCompound data = new NBTTagCompound();
		EnergyUnits.writeEnergyQuanta(data, energyQuanta);
		data.setBoolean("isOn", isOn);
		data.setBoolean("doesBurn", doesBurn);
		tank.writeToNBT(data, "t");
		this.networkPack(data, 50);
	}

	public DirPos[] getConPos() {
		this.refreshRuntimeConnections();
		return runtimeConnections;
	}

	private DirPos[] buildConnections() {
		return new DirPos[] {
			new DirPos(xCoord + 2, yCoord, zCoord, Library.POS_X),
			new DirPos(xCoord - 2, yCoord, zCoord, Library.NEG_X),
			new DirPos(xCoord, yCoord, zCoord + 2, Library.POS_Z),
			new DirPos(xCoord, yCoord, zCoord - 2, Library.NEG_Z)
		};
	}

	@Override
	public void networkUnpack(NBTTagCompound nbt) {
		super.networkUnpack(nbt);

		this.energyQuanta = EnergyUnits.readEnergyQuanta(nbt, "power");
		this.isOn = nbt.getBoolean("isOn");
		this.doesBurn = nbt.getBoolean("doesBurn");
		tank.readFromNBT(nbt, "t");
	}

	@Override
	public AxisAlignedBB getRenderBoundingBox() {
		return TileEntity.INFINITE_EXTENT_AABB;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public double getMaxRenderDistanceSquared() {
		return 65536.0D;
	}

	@Override
	public long getStoredEnergyQuanta() {
		return this.energyQuanta;
	}

	@Override
	public long getEnergyCapacityQuanta() {
		return this.maxPower;
	}

	@Override
	public void setStoredEnergyQuanta(long i) {
		if(this.energyQuanta == i) return;
		this.energyQuanta = i;
		this.markPowerNetDirty();
		if(!runtimeEnergyMutation) this.markMachineEnergyDirty();
	}

	public long getPowerOutputWatts() {
		return EnergyUnits.quantaPerTickToWatts(output);
	}

	@Override
	public FluidTank[] getReceivingTanks() {
		return new FluidTank[] { tank };
	}

	@Override
	public FluidTank[] getAllTanks() {
		return new FluidTank[] { tank };
	}

	@Override
	public Container provideContainer(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new ContainerMachineGasFlare(player.inventory, this);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public Object provideGUI(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new GUIMachineGasFlare(player.inventory, this);
	}

	@Override
	public boolean canProvideInfo(UpgradeType type, int level, boolean extendedInfo) {
		return type == UpgradeType.SPEED || type == UpgradeType.EFFECT;
	}

	@Override
	public void provideInfo(UpgradeType type, int level, List<String> info, boolean extendedInfo) {
		info.add(IUpgradeInfoProvider.getStandardLabel(ModBlocks.machine_flare));
		if(type == UpgradeType.SPEED) {
			info.add(EnumChatFormatting.GREEN + I18nUtil.resolveKey(this.KEY_CONSUMPTION, "+" + (level * 100) + "%"));
		}
		if(type == UpgradeType.EFFECT) {
			info.add(EnumChatFormatting.GREEN + I18nUtil.resolveKey(this.KEY_EFFICIENCY, "+" + (100 * level / 3) + "%"));
		}
	}

	@Override
	public int getMaxLevel(UpgradeType type) {
		if(type == UpgradeType.SPEED) return 3;
		if(type == UpgradeType.EFFECT) return 3;
		return 0;
	}

	@Override
	public void provideExtraInfo(NBTTagCompound data) {
		data.setBoolean(CompatEnergyControl.B_ACTIVE, this.fluidUsed > 0);
		data.setDouble(CompatEnergyControl.D_CONSUMPTION_MB, this.fluidUsed);
		data.setDouble(CompatEnergyControl.D_OUTPUT_HE, EnergyUnits.quantaToLegacyHe(this.output));
	}

	@Override
	public NBTTagCompound getSettings(World world, int x, int y, int z) {
		NBTTagCompound tag = new NBTTagCompound();
		tag.setIntArray("fluidID", new int[]{tank.getTankType().getID()});
		tag.setBoolean("isOn", isOn);
		tag.setBoolean("doesBurn", doesBurn);
		return tag;
	}

	@Override
	public void pasteSettings(NBTTagCompound nbt, int index, World world, EntityPlayer player, int x, int y, int z) {
		int id = nbt.getIntArray("fluidID")[index];
		tank.setTankType(Fluids.fromID(id));
		if(nbt.hasKey("isOn")) isOn = nbt.getBoolean("isOn");
		if(nbt.hasKey("doesBurn")) doesBurn = nbt.getBoolean("doesBurn");
		this.markMachineDirty(MachineDirtyCause.CONFIGURATION | MachineDirtyCause.FLUID);
	}
}
