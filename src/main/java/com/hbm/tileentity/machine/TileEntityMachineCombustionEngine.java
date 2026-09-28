package com.hbm.tileentity.machine;

import api.hbm.energymk2.EnergyUnits;
import com.hbm.blocks.BlockDummyable;
import com.hbm.interfaces.IControlReceiver;
import com.hbm.inventory.container.ContainerCombustionEngine;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.inventory.fluid.trait.FT_Combustible;
import com.hbm.inventory.fluid.trait.FT_Polluting;
import com.hbm.inventory.fluid.trait.FluidTrait.FluidReleaseType;
import com.hbm.inventory.gui.GUICombustionEngine;
import com.hbm.items.ModItems;
import com.hbm.items.machine.ItemPistons.EnumPistonType;
import com.hbm.lib.Library;
import com.hbm.machine.MachineDirtyCause;
import com.hbm.machine.MachineExecutionStrategy;
import com.hbm.main.MainRegistry;
import com.hbm.sound.AudioWrapper;
import com.hbm.tileentity.IFluidCopiable;
import com.hbm.tileentity.IGUIProvider;
import com.hbm.tileentity.TileEntityMachinePolluting;
import com.hbm.util.EnumUtil;
import com.hbm.util.FurnaceGasEmission;
import com.hbm.util.fauxpointtwelve.DirPos;

import api.hbm.energymk2.IEnergyProviderMK2;
import api.hbm.fluid.IFluidStandardTransceiver;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

public class TileEntityMachineCombustionEngine extends TileEntityMachinePolluting implements IEnergyProviderMK2, IFluidStandardTransceiver, IControlReceiver, IGUIProvider, IFluidCopiable {
	private static final int TASK_GENERATE = 1;
	private boolean runtimeInitialized;
	private boolean runtimeEnergyMutation;
	private boolean runtimeWaterlogged;
	private long runtimeGeneratedQuanta;
	private DirPos[] runtimeConnections;
	private int observedOrientation = Integer.MIN_VALUE;
	
	public boolean isOn = false;
	public static long maxPower = 2_500_000;
	public long energyQuanta;
	private int playersUsing = 0;
	public int setting = 0;
	public boolean wasOn = false;
	
	public float doorAngle = 0;
	public float prevDoorAngle = 0;
	
	private AudioWrapper audio;
	
	public FluidTank tank;
	public int tenth = 0;

	public TileEntityMachineCombustionEngine() {
		super(5, 50);
		this.tank = new FluidTank(Fluids.DIESEL, 24_000);
		this.trackMachineFluidTank(tank);
	}

	@Override public int getMachineExecutionStrategies() {
		return MachineExecutionStrategy.EVENT_DRIVEN | MachineExecutionStrategy.SCHEDULED | MachineExecutionStrategy.COARSE_5 | MachineExecutionStrategy.COARSE_20;
	}

	@Override public void onMachineRuntimeDirty(int causes) {
		if(worldObj == null || worldObj.isRemote) return;
		runtimeInitialized = true;
		runtimeWaterlogged = this.isWaterlogged();
		this.refreshRuntimeConnections();
		if((causes & MachineDirtyCause.LIFECYCLE) != 0) this.updatePowerConnections();
		this.beginMachineFluidMutation();
		try { this.loadFuel(); }
		finally { this.endMachineFluidMutation(); }
		this.subscribeToFuel();
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
		this.sendRuntimePacket();
	}

	@Override public void onMachineCoarsePoll(int cadence) {
		if(worldObj == null || worldObj.isRemote || !runtimeInitialized) return;
		if(cadence == 5) {
			boolean waterlogged = this.isWaterlogged();
			if(waterlogged != runtimeWaterlogged) {
				runtimeWaterlogged = waterlogged;
				this.markMachineDirty(MachineDirtyCause.ENVIRONMENT);
			}
		} else if(cadence == 20) {
			this.refreshRuntimeConnections();
			this.updatePowerConnections();
			this.subscribeToFuel();
			this.sendRuntimePacket();
		}
	}

	@Override public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(taskType != TASK_GENERATE || taskSlot != 0 || worldObj == null || worldObj.isRemote || !runtimeInitialized) return;
		runtimeEnergyMutation = true;
		this.beginMachineFluidMutation();
		try { this.runGeneratorStep(); }
		finally {
			this.endMachineFluidMutation();
			runtimeEnergyMutation = false;
		}
		this.markDirty();
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
		this.sendRuntimePacket();
	}

	private void loadFuel() {
		this.tank.loadTank(0, 1, slots);
		if(this.tank.setType(4, slots)) this.tenth = 0;
	}

	private void evaluateAndSchedule(long now) {
		boolean fuelReady = isOn && setting > 0 && !runtimeWaterlogged && slots[2] != null && slots[2].getItem() == ModItems.piston_set &&
			(tank.getFill() > 0 || tenth > 0) && tank.getTankType().hasTrait(FT_Combustible.class);
		if(fuelReady || energyQuanta > 0 || smoke.getFill() > 0 || smoke_leaded.getFill() > 0 || smoke_poison.getFill() > 0) {
			this.scheduleMachineTransition(now + 1L, TASK_GENERATE, 0);
		} else {
			wasOn = false;
			runtimeGeneratedQuanta = 0L;
			this.cancelMachineTransition(TASK_GENERATE, 0);
		}
	}

	private void refreshRuntimeConnections() {
		int orientation = this.getBlockMetadata();
		if(runtimeConnections == null || orientation != observedOrientation) {
			runtimeConnections = this.buildConnections();
			observedOrientation = orientation;
		}
	}

	private void subscribeToFuel() {
		for(DirPos pos : runtimeConnections) this.trySubscribe(tank.getTankType(), worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
	}

	private void updatePowerConnections() {
		for(DirPos pos : runtimeConnections) this.registerPowerConnection(worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
	}

	@Override
	public String getName() {
		return "container.combustionEngine";
	}

	@Override
	public void updateEntity() {
		if(worldObj.isRemote) this.updateClientAnimation();
	}

	private void runGeneratorStep() {
			this.loadFuel();
			wasOn = false;
			runtimeGeneratedQuanta = 0L;

			int fill = tank.getFill() * 10 + tenth;
			if(!runtimeWaterlogged && isOn && setting > 0 && slots[2] != null && slots[2].getItem() == ModItems.piston_set && fill > 0 && tank.getTankType().hasTrait(FT_Combustible.class)) {
				EnumPistonType piston = EnumUtil.grabEnumSafely(EnumPistonType.class, slots[2].getItemDamage());
				FT_Combustible trait = tank.getTankType().getTrait(FT_Combustible.class);
				
				double eff = piston.eff[trait.getGrade().ordinal()];
				
				if(eff > 0) {
					if(breatheAir(worldObj.getTotalWorldTime() % 5 == 0 ? setting : 0)) {
						int speed = setting * 2;
						
						int toBurn = Math.min(fill, speed);
						runtimeGeneratedQuanta = (long) (toBurn * (trait.getCombustionEnergyQuanta() / 10_000D) * eff);
						this.setStoredEnergyQuanta(this.energyQuanta + runtimeGeneratedQuanta);
						fill -= toBurn;
	
						if(worldObj.getTotalWorldTime() % 5 == 0 && toBurn > 0) {
							super.pollute(tank.getTankType(), FluidReleaseType.BURN, toBurn * 0.5F);
						}
						
						if(toBurn > 0) {
							wasOn = true;
							FurnaceGasEmission.emitCarbonMonoxide(worldObj, xCoord, yCoord, zCoord, Math.max(150, 900 / Math.max(toBurn, 1)));
						}
						
						tank.setFill(fill / 10);
						tenth = fill % 10;
					}
				}
			}
			
			this.setStoredEnergyQuanta(Library.chargeItemsFromTE(slots, 3, energyQuanta, energyQuanta));
			
			for(DirPos pos : runtimeConnections) {
				this.providePowerToDirectReceiver(worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
				this.sendSmoke(pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
			}
			
			if(energyQuanta > maxPower)
				this.setStoredEnergyQuanta(maxPower);
			
	}

	private void sendRuntimePacket() {
		NBTTagCompound data = new NBTTagCompound();
		EnergyUnits.writeEnergyQuanta(data, Math.min(energyQuanta, maxPower));
		data.setInteger("playersUsing", playersUsing);
		data.setInteger("setting", setting);
		data.setBoolean("isOn", isOn);
		data.setBoolean("wasOn", wasOn);
		tank.writeToNBT(data, "tank");
		this.networkPack(data, 50);
	}

	private void updateClientAnimation() {
			this.prevDoorAngle = this.doorAngle;
			float swingSpeed = (doorAngle / 10F) + 3;
			
			if(this.playersUsing > 0) {
				this.doorAngle += swingSpeed;
			} else {
				this.doorAngle -= swingSpeed;
			}
			
			this.doorAngle = MathHelper.clamp_float(this.doorAngle, 0F, 135F);

			if(wasOn) {
				
				if(audio == null) {
					audio = createAudioLoop();
					audio.startSound();
				} else if(!audio.isPlaying()) {
					audio = rebootAudio(audio);
				}

				audio.keepAlive();
				audio.updateVolume(this.getVolume(1F));
				
			} else {
				
				if(audio != null) {
					audio.stopSound();
					audio = null;
				}
			}
	}
	
	private DirPos[] buildConnections() {
		ForgeDirection dir = ForgeDirection.getOrientation(this.getBlockMetadata() - BlockDummyable.offset);
		ForgeDirection rot = dir.getRotation(ForgeDirection.UP);
		
		return new DirPos[] {
				new DirPos(xCoord + dir.offsetX * 1 + rot.offsetX, yCoord, zCoord + dir.offsetZ * 1 + rot.offsetZ, dir),
				new DirPos(xCoord + dir.offsetX * 1 - rot.offsetX, yCoord, zCoord + dir.offsetZ * 1 - rot.offsetZ, dir),
				new DirPos(xCoord - dir.offsetX * 2 + rot.offsetX, yCoord, zCoord - dir.offsetZ * 2 + rot.offsetZ, dir.getOpposite()),
				new DirPos(xCoord - dir.offsetX * 2 - rot.offsetX, yCoord, zCoord - dir.offsetZ * 2 - rot.offsetZ, dir.getOpposite())
		};
	}
	
	@Override
	public AudioWrapper createAudioLoop() {
		return MainRegistry.proxy.getLoopedSound("hbm:block.igeneratorOperate", xCoord, yCoord, zCoord, 1.0F, 10F, 1.0F, 20);
	}

	@Override
	public void onChunkUnload() {
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

	@Override
	public boolean canConnect(ForgeDirection dir) {
		return dir != ForgeDirection.DOWN;
	}

	@Override
	public boolean canConnect(FluidType type, ForgeDirection dir) {
		return dir != ForgeDirection.DOWN;
	}

	@Override
	public void networkUnpack(NBTTagCompound nbt) {
		super.networkUnpack(nbt);
		this.playersUsing = nbt.getInteger("playersUsing");
		this.setting = nbt.getInteger("setting");
		this.energyQuanta = EnergyUnits.readEnergyQuanta(nbt, "power");
		this.isOn = nbt.getBoolean("isOn");
		this.wasOn = nbt.getBoolean("wasOn");
		this.tank.readFromNBT(nbt, "tank");
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		this.setting = nbt.getInteger("setting");
		this.energyQuanta = EnergyUnits.readEnergyQuanta(nbt, "power");
		this.isOn = nbt.getBoolean("isOn");
		this.tank.readFromNBT(nbt, "tank");
		this.tenth = nbt.getInteger("tenth");
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		nbt.setInteger("setting", setting);
		EnergyUnits.writeEnergyQuanta(nbt, energyQuanta);
		nbt.setBoolean("isOn", isOn);
		tank.writeToNBT(nbt, "tank");
		nbt.setInteger("tenth", tenth);
	}
	
	@Override
	public void openInventory() {
		if(!worldObj.isRemote) {
			this.playersUsing++;
			this.sendRuntimePacket();
		}
	}
	
	@Override
	public void closeInventory() {
		if(!worldObj.isRemote) {
			this.playersUsing--;
			this.sendRuntimePacket();
		}
	}

	@Override
	public void setStoredEnergyQuanta(long energyQuanta) {
		if(this.energyQuanta == energyQuanta) return;
		this.energyQuanta = energyQuanta;
		this.markPowerNetDirty();
		if(!runtimeEnergyMutation) this.markMachineEnergyDirty();
	}

	@Override
	public long getStoredEnergyQuanta() {
		return energyQuanta;
	}

	@Override
	public long getEnergyCapacityQuanta() {
		return maxPower;
	}

	public long getPowerOutputWatts() {
		return EnergyUnits.quantaPerTickToWatts(runtimeGeneratedQuanta);
	}

	@Override
	public Container provideContainer(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new ContainerCombustionEngine(player.inventory, this);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public Object provideGUI(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new GUICombustionEngine(player.inventory, this);
	}

	@Override
	public FluidTank[] getAllTanks() {
		return new FluidTank[] {tank};
	}

	@Override
	public boolean isFluidDemandObservable() { return true; }

	@Override
	public FluidTank[] getReceivingTanks() {
		return new FluidTank[] {tank};
	}

	@Override
	public FluidTank[] getSendingTanks() {
		return this.getSmokeTanks();
	}
	
	AxisAlignedBB bb = null;
	
	@Override
	public AxisAlignedBB getRenderBoundingBox() {
		
		if(bb == null) {
			bb = AxisAlignedBB.getBoundingBox(
					xCoord - 3,
					yCoord,
					zCoord - 3,
					xCoord + 4,
					yCoord + 2,
					zCoord + 4
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
	public boolean hasPermission(EntityPlayer player) {
		return player.getDistance(xCoord + 0.5, yCoord + 0.5, zCoord + 0.5) < 25;
	}

	@Override
	public void receiveControl(NBTTagCompound data) {
		if(data.hasKey("turnOn")) this.isOn = !this.isOn;
		if(data.hasKey("setting")) this.setting = data.getInteger("setting");
		
		this.markChanged();
		this.markMachineDirty(MachineDirtyCause.CONFIGURATION);
	}

	@Override
	public NBTTagCompound getSettings(World world, int x, int y, int z) {
		NBTTagCompound tag = new NBTTagCompound();
		tag.setIntArray("fluidID", new int[]{tank.getTankType().getID()});
		tag.setBoolean("isOn", isOn);
		tag.setInteger("burnRate", setting);
		return tag;
	}

	@Override
	public void pasteSettings(NBTTagCompound nbt, int index, World world, EntityPlayer player, int x, int y, int z) {
		int id = nbt.getIntArray("fluidID")[index];
		tank.setTankType(Fluids.fromID(id));
		if(nbt.hasKey("isOn")) isOn = nbt.getBoolean("isOn");
		if(nbt.hasKey("burnRate")) setting = nbt.getInteger("burnRate");
		this.markMachineDirty(MachineDirtyCause.CONFIGURATION | MachineDirtyCause.FLUID);
	}
}
