package com.hbm.tileentity.machine;

import api.hbm.energymk2.EnergyUnits;
import java.util.HashMap;

import com.hbm.blocks.BlockDummyable;
import com.hbm.handler.CompatHandler;
import com.hbm.handler.pollution.PollutionHandler;
import com.hbm.handler.pollution.PollutionHandler.PollutionType;
import com.hbm.interfaces.IControlReceiver;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.inventory.fluid.trait.FT_Combustible;
import com.hbm.inventory.fluid.trait.FT_Combustible.FuelGrade;
import com.hbm.inventory.gui.GUIMachineTurbineGas;
import com.hbm.inventory.container.ContainerMachineTurbineGas;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.items.machine.IItemFluidIdentifier;
import com.hbm.lib.Library;
import com.hbm.machine.MachineDirtyCause;
import com.hbm.machine.MachineExecutionStrategy;
import com.hbm.main.MainRegistry;
import com.hbm.sound.AudioWrapper;
import com.hbm.tileentity.IFluidCopiable;
import com.hbm.tileentity.IGUIProvider;
import com.hbm.tileentity.TileEntityMachineBase;
import com.hbm.util.CompatEnergyControl;
import com.hbm.util.FurnaceGasEmission;

import api.hbm.fluid.IFluidStandardTransceiver;
import api.hbm.tile.IInfoProviderEC;
import cpw.mods.fml.common.Optional;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.SimpleComponent;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

@Optional.InterfaceList({@Optional.Interface(iface = "li.cil.oc.api.network.SimpleComponent", modid = "OpenComputers")})
public class TileEntityMachineTurbineGas extends TileEntityMachineBase implements IFluidStandardTransceiver, api.hbm.energymk2.IRatedGenerator, IControlReceiver, IGUIProvider, SimpleComponent, IInfoProviderEC, CompatHandler.OCComponent, IFluidCopiable, com.hbm.realsim.IMachineInfo {
	private static final int TASK_SIMULATE = 1;
	private boolean runtimeInitialized;
	private boolean runtimeEnergyMutation;
	private ForgeDirection runtimeDirection;
	private ForgeDirection runtimeRotation;
	private int observedOrientation = Integer.MIN_VALUE;
	
	public long energyQuanta;
	public static final long maxPower = 600_000L; // Two ticks of gross nameplate
	
	public int rpm; //0-100, crescent moon gauge, used for calculating the amount of power generated, starts past 10%
	public int temp; //0-800, used for figuring out how much water to boil, starts boiling at 300°C
	public int rpmIdle = 10;
	public int tempIdle = 300;
	
	public int powerSliderPos; //goes from 0 to 60, 0 is idle, 60 is max power
	public int throttle; //the same thing, but goes from 0 to 100
	
	public boolean autoMode;
	public int state = 0; //0 is offline, -1 is startup, 1 is online
	
	public int counter = 0; //used to startup and shutdown
	public int instantPowerOutput;
	
	public FluidTank[] tanks;
	
	private AudioWrapper audio;
	
    public static final int ELECTRICAL_EFFICIENCY_PPM = 300000;
    public static final long RATED_ELECTRIC_WATTS = 3_000_000L;
    public static final long RATED_CHEMICAL_WATTS = 10_000_000L;
    public static final long HRSG_WATTS = 6_000_000L;
    public final com.hbm.realsim.TurbineFuelAccount thermalFuel = new com.hbm.realsim.TurbineFuelAccount();
    public final com.hbm.realsim.ThermalAccount hrsg = new com.hbm.realsim.ThermalAccount();
    private final com.hbm.realsim.OutputBudget outputBudget = new com.hbm.realsim.OutputBudget();
    private long lubricantMicroliters, lastSimulationTick = Long.MIN_VALUE;
    public long currentFuelMicroJ, currentExhaustMicroJ, currentHrsgQuanta, currentAuxiliaryQuanta;
    public double fuelStandardLitersPerMinute;
    public long displayedExhaustMicroJ;
    @Override public com.hbm.realsim.OutputBudget getOutputBudget() { return outputBudget; }
    @Override public long getRatedOutputWatts() { return RATED_ELECTRIC_WATTS; }

	//TODO particles from heat exchanger maybe? maybe in a future
	
	public TileEntityMachineTurbineGas() {
		super(2);
		this.tanks = new FluidTank[4];
		tanks[0] = new FluidTank(Fluids.GAS, 6000).withDefaultCapacity();
		tanks[1] = new FluidTank(Fluids.LUBRICANT, 2).withDefaultCapacity();
		tanks[2] = new FluidTank(Fluids.FRESH_WATER, 20).withDefaultCapacity().migrateFrom(Fluids.WATER);
		tanks[3] = new FluidTank(Fluids.HOTSTEAM, 200).withDefaultCapacity();
		for(FluidTank tank : tanks) this.trackMachineFluidTank(tank);
	}

	@Override public int getMachineExecutionStrategies() {
		return MachineExecutionStrategy.EVENT_DRIVEN | MachineExecutionStrategy.SCHEDULED | MachineExecutionStrategy.COARSE_20;
	}

	@Override public void onMachineRuntimeDirty(int causes) {
		if(worldObj == null || worldObj.isRemote) return;
		runtimeInitialized = true;
		this.refreshOrientation();
		if((causes & MachineDirtyCause.LIFECYCLE) != 0) this.updatePowerConnections();
		this.beginMachineFluidMutation();
		try { this.selectFuelType(); }
		finally { this.endMachineFluidMutation(); }
		this.subscribeToInputs();
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
		this.sendRuntimePacket(energyQuanta);
	}

	@Override public void onMachineCoarsePoll(int cadence) {
		if(cadence != 20 || worldObj == null || worldObj.isRemote || !runtimeInitialized) return;
		this.refreshOrientation();
		this.updatePowerConnections();
		this.subscribeToInputs();
		this.sendRuntimePacket(energyQuanta);
	}

	@Override public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(taskType != TASK_SIMULATE || taskSlot != 0 || worldObj == null || worldObj.isRemote || !runtimeInitialized) return;
		this.beginMachineFluidMutation();
		runtimeEnergyMutation = true;
		try { this.runGasTurbineStep(); }
		finally {
			runtimeEnergyMutation = false;
			this.endMachineFluidMutation();
		}
		this.markDirty();
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
	}

	private void selectFuelType() {
		if(slots[1] != null && slots[1].getItem() instanceof IItemFluidIdentifier) {
			FluidType fluid = ((IItemFluidIdentifier) slots[1].getItem()).getType(worldObj, xCoord, yCoord, zCoord, slots[1]);
			if(com.hbm.realsim.TurbineFuelAccount.suitable(fluid.getName()) && tanks[0].getFill() == 0) tanks[0].setTankType(fluid);
		}
	}

	private void refreshOrientation() {
		int orientation = this.getBlockMetadata();
		if(observedOrientation != orientation) {
			runtimeDirection = ForgeDirection.getOrientation(orientation - BlockDummyable.offset);
			runtimeRotation = runtimeDirection.getRotation(ForgeDirection.UP);
			observedOrientation = orientation;
		}
	}

	private void subscribeToInputs() {
		ForgeDirection dir = runtimeDirection;
		ForgeDirection rot = runtimeRotation;
		for(int i = 0; i < 2; i++) {
			this.trySubscribe(tanks[i].getTankType(), worldObj, xCoord - dir.offsetX * 2 + rot.offsetX, yCoord, zCoord - dir.offsetZ * 2 + rot.offsetZ, dir.getOpposite());
			this.trySubscribe(tanks[i].getTankType(), worldObj, xCoord + dir.offsetX * 2 + rot.offsetX, yCoord, zCoord + dir.offsetZ * 2 + rot.offsetZ, dir);
		}
		this.trySubscribe(tanks[2].getTankType(), worldObj, xCoord - dir.offsetX * 2 - rot.offsetX * 4, yCoord, zCoord - dir.offsetZ * 2 - rot.offsetZ * 4, dir.getOpposite());
		this.trySubscribe(tanks[2].getTankType(), worldObj, xCoord + dir.offsetX * 2 - rot.offsetX * 4, yCoord, zCoord + dir.offsetZ * 2 - rot.offsetZ * 4, dir);
	}

	private void updatePowerConnections() {
		ForgeDirection dir = runtimeDirection;
		ForgeDirection rot = runtimeRotation;
		this.registerPowerConnection(worldObj, xCoord - dir.offsetZ * 5, yCoord + 1, zCoord + dir.offsetX * 5, rot);
	}

	private void evaluateAndSchedule(long now) {
		if(thermalFuel.exhaust.remainingMicroJ() > 0 || hrsg.ownedMass() > 0 || state != 0 || rpm > 0 || counter > 0 || powerSliderPos > 0 || energyQuanta > 0 || tanks[3].getFill() > 0)
			this.scheduleMachineTransition(now + 1L, TASK_SIMULATE, 0);
		else this.cancelMachineTransition(TASK_SIMULATE, 0);
	}
	
	@Override
	public void updateEntity() {
		if(worldObj.isRemote) this.updateClientAudio();
	}

	private void runGasTurbineStep() {
            long now = worldObj.getTotalWorldTime();
            if(now <= lastSimulationTick) return;
            lastSimulationTick = now;
            powerSliderPos = Math.max(0, Math.min(60, powerSliderPos));
            instantPowerOutput = 0;
            currentFuelMicroJ = currentExhaustMicroJ = currentHrsgQuanta = currentAuxiliaryQuanta = 0;
			throttle = powerSliderPos * 100 / 60;
			
			if(autoMode) { //power production depending on power requirement
				
				//scales the slider proportionally to the power gauge
				int powerSliderTarget = 60 - (int) (60 * energyQuanta / maxPower);
				
				if(powerSliderTarget > powerSliderPos) { //makes the auto slider slide instead of snapping into position
					powerSliderPos++;
				}
				else if(powerSliderTarget < powerSliderPos) {
					powerSliderPos--;
				}
			}
			
			switch(state) { //what to do when turbine offline, starting up and online			
			case 0:
				shutdown();	
				break;
			case -1:
				stopIfNotReady();
                if(state == -1 && burnFuel(false, 5)) startup();
				break;
			case 1:			
				stopIfNotReady();
                if(state == 1) run();
				break;
			default:
				break;
			}
			
            recoverExhaust();
			ForgeDirection dir = runtimeDirection;
			ForgeDirection rot = runtimeRotation;
			long packetEnergy = this.energyQuanta;
			
			//do net/battery deductions first...
			this.setStoredEnergyQuanta(this.chargeRatedItems(slots, 0, maxPower));
			this.providePowerToDirectReceiver(worldObj, xCoord - dir.offsetZ * 5, yCoord + 1, zCoord + dir.offsetX * 5, rot);
			//...and then cap it. Prevents potential future cases where power would be limited due to the fuel being too strong and the buffer too small.
			if(this.energyQuanta > this.maxPower)
				this.setStoredEnergyQuanta(this.maxPower);
			
			if(tanks[3].getFill() > 0) this.sendFluid(tanks[3], worldObj, xCoord + dir.offsetZ * 6, yCoord + 1, zCoord - dir.offsetX * 6, rot.getOpposite());
			this.sendRuntimePacket(packetEnergy);
	}

	private void sendRuntimePacket(long packetEnergy) {
		NBTTagCompound data = new NBTTagCompound();
            data.setLong("exhaustMicroJ", thermalFuel.exhaust.remainingMicroJ());
            data.setLong("hrsgQuanta", currentHrsgQuanta);
            data.setDouble("fuelStandardLMin", fuelStandardLitersPerMinute);
		EnergyUnits.writeEnergyQuanta(data, packetEnergy);
			data.setInteger("rpm",  this.rpm);
			data.setInteger("temp",  this.temp);
			data.setInteger("state", this.state);
			data.setBoolean("automode", this.autoMode);
			data.setInteger("throttle",  this.throttle);
			data.setInteger("slidpos",  this.powerSliderPos);
			
			if(state != 1) {
				data.setInteger("counter", this.counter); //sent during startup and shutdown
			} else {
				data.setInteger("instantPow", this.instantPowerOutput); //sent while running
			}
			
			tanks[0].writeToNBT(data, "fuel");
			tanks[1].writeToNBT(data, "lube");
			tanks[2].writeToNBT(data, "water");
			tanks[3].writeToNBT(data, "steam");
				
			this.networkPack(data, 150);
	}

	private void updateClientAudio() {
			if(rpm >= 10 && state != -1) { //if conditions are right, play the sound
				
				if(audio == null) { //if there is no sound playing, start it
					
					audio = MainRegistry.proxy.getLoopedSound("hbm:block.turbinegasRunning", xCoord, yCoord, zCoord, getVolume(1.0F), 20F, 2.0F);
					audio.startSound();
					
				} else if(!audio.isPlaying()) {
					audio.stopSound();
					audio = MainRegistry.proxy.getLoopedSound("hbm:block.turbinegasRunning", xCoord, yCoord, zCoord, getVolume(1.0F), 20F, 2.0F);
					audio.startSound();
				}
				
				audio.updatePitch((float) (0.55 + 0.1 * rpm / 10)); //dynamic pitch update based on rpm
				audio.updateVolume(getVolume(2F)); //yeah i need this
				
			} else {
				
				if(audio != null) {
					audio.stopSound();
					audio = null;
				}
			}
	}
	
    private void stopIfNotReady() {
        if((tanks[0].getFill() == 0 && thermalFuel.chemicalCredit() == 0) || (tanks[1].getFill() == 0 && lubricantMicroliters < 50) || !hasAcceptableFuel()) state = 0;
    }
    public boolean hasAcceptableFuel() {
        return com.hbm.realsim.TurbineFuelAccount.suitable(thermalFuel.chemicalCredit() > 0 ? thermalFuel.fuel() : tanks[0].getTankType().getName());
    }

	private void startup() {
		
		counter++;
		
        rpm = com.hbm.realsim.GasTurbineCycle.startupRpm(counter, rpmIdle);
        temp = com.hbm.realsim.GasTurbineCycle.startupTemperature(counter, tempIdle);

		if(counter == 50) {
			worldObj.playSoundEffect(xCoord, yCoord + 2, zCoord, "hbm:block.turbinegasStartup", getVolume(1.0F), 1.0F);
		}
			
		if(counter == com.hbm.realsim.GasTurbineCycle.STARTUP_TICKS) {
			state = 1;
		}
	}
	
	
	int rpmLast; //used to progressively slow down and cool the turbine without immediatly setting rpm and temp to 0
	int tempLast;
	
	private void shutdown() {
		
		autoMode = false;
		instantPowerOutput = 0;
		
		if(powerSliderPos > 0)
			powerSliderPos--;
		
		if(rpm <= 10 && counter > 0) {
			
			if(counter == 225) {
				
				worldObj.playSoundEffect(xCoord, yCoord + 2, zCoord, "hbm:block.turbinegasShutdown", getVolume(1.0F), 1.0F);
				
				rpmLast = rpm;
				tempLast = temp;
			}
			
			counter--;
			
			rpm = (int) (rpmLast * (counter) / 225);
			temp = (int) (tempLast * (counter) / 225);
			
		} else if(rpm > 11) { //quickly slows down the turbine to idle before shutdown
			counter = 42069; //absolutely necessary to avoid fuckeries on shutdown
			rpm--;
		} else if(rpm == 11) {
			counter = 225;
			rpm--;
		}
	}
	
    private void run() {
        int target = com.hbm.realsim.GasTurbineCycle.targetRpm(powerSliderPos, throttle, rpmIdle);
        if(rpm < target && worldObj.getTotalWorldTime() % 5 == 0) rpm++;
        if(rpm > target && worldObj.getTotalWorldTime() % 2 == 0) rpm--;
        int load = com.hbm.realsim.GasTurbineCycle.load(powerSliderPos, throttle, rpm, rpmIdle);
        burnFuel(load > 0, load > 0 ? load : 5);
        int targetTemp = load >= 75 ? 500 : 350;
        if(temp < targetTemp) temp++; else if(temp > targetTemp) temp--;
        if(currentFuelMicroJ > 0) {
            if(worldObj.getTotalWorldTime() % 20 == 0) PollutionHandler.incrementPollution(worldObj, xCoord, yCoord, zCoord, PollutionType.SOOT, PollutionHandler.SOOT_PER_SECOND * 3);
            FurnaceGasEmission.emitCarbonMonoxide(worldObj, xCoord, yCoord, zCoord, 600);
        }
    }
    private boolean burnFuel(boolean generate, int load) {
        long request = RATED_CHEMICAL_WATTS * 50000L * Math.max(1, Math.min(100, load)) / 100;
        if(generate) request = Math.min(request, com.hbm.realsim.SteamLedger.mulDiv(Math.max(0, maxPower - energyQuanta), 500000L * 1000000L, ELECTRICAL_EFFICIENCY_PPM));
        if(request == 0 || !hasAcceptableFuel()) return false;
        // ~13 kg/s excess air at full load, using the existing atmosphere/intake boundary.
        if(!breatheAir(Math.toIntExact(com.hbm.realsim.SteamLedger.ceilDiv(request, 1_000_000_000L)))) { state = 0; return false; }
        if(lubricantMicroliters < 50) {
            if(tanks[1].getFill() <= 0) { state = 0; return false; }
            tanks[1].setFill(tanks[1].getFill() - 1); lubricantMicroliters += 1000000;
        }
        lubricantMicroliters -= com.hbm.realsim.GasTurbineCycle.LUBE_MICROLITERS_PER_TICK; // 0.06 L/min while spooling/running
        long gross = thermalFuel.burn(worldObj.getTotalWorldTime(), tanks[0], request, generate ? ELECTRICAL_EFFICIENCY_PPM : 0, generate ? 550000 : 0, Math.min(temp, load >= 75 ? 500 : 350));
        long aux = gross / 50; // 2% compressor controls/lube auxiliaries; Brayton compressor already in cycle efficiency.
        instantPowerOutput = Math.toIntExact(gross - aux);
        this.setStoredEnergyQuanta(Math.addExact(energyQuanta, instantPowerOutput));
        currentFuelMicroJ = thermalFuel.chemicalInputMicroJ;
        currentExhaustMicroJ = thermalFuel.recoverableMicroJ;
        currentAuxiliaryQuanta = aux;
        com.hbm.realsim.PhysicalState fuel = com.hbm.realsim.PhysicalRegistry.get(thermalFuel.fuel());
        fuelStandardLitersPerMinute = fuel.isResolved() ? currentFuelMicroJ * 1200D / fuel.chemicalMicrojoulesPerUnit() : 0;
        return currentFuelMicroJ > 0;
    }
    private void recoverExhaust() {
        com.hbm.realsim.SteamLedger.State out = hrsg.ownedMass() > 0 ? hrsg.outlet() : com.hbm.realsim.ThermalMachine.state(tanks[3]);
        // Preserve saved output stock. Empty output selects the highest thermally legal state.
        if(hrsg.ownedMass() == 0 && tanks[3].getFill() == 0) out = thermalFuel.exhaust.availableMicroJ(480) > 0
                ? com.hbm.realsim.SteamLedger.State.SUPERHOT : com.hbm.realsim.SteamLedger.State.HOT;
        if(out != com.hbm.realsim.SteamLedger.State.HOT && out != com.hbm.realsim.SteamLedger.State.SUPERHOT) return;
        if(!com.hbm.realsim.ThermalMachine.configure(tanks[3], out)) return;
        tanks[3].resizePreservingContents(com.hbm.realsim.GeneratorContract.steamCapacity(2000, out));
        com.hbm.realsim.ThermalAccount.Result r = com.hbm.realsim.HeatRecovery.advance(worldObj.getTotalWorldTime(), thermalFuel.exhaust, hrsg,
                out, tanks[2].getFill(), Math.max(0, tanks[3].getMaxFill() - tanks[3].getFill()), energyQuanta);
        tanks[2].setFill(tanks[2].getFill() - r.consumed); tanks[3].setFill(tanks[3].getFill() + r.produced);
        this.setStoredEnergyQuanta(energyQuanta - r.workQuanta);
        currentHrsgQuanta = r.heatQuanta; currentAuxiliaryQuanta += r.workQuanta;
        // Finite plenum: unavailable low-grade exhaust cannot be upgraded or accumulated forever.
        if(state == 0 && hrsg.ownedMass() == 0 && tanks[2].getFill() == 0) thermalFuel.exhaust.vent();
    }

	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	
	@Override
	public void networkUnpack(NBTTagCompound nbt) {
		super.networkUnpack(nbt);
        displayedExhaustMicroJ = nbt.getLong("exhaustMicroJ"); currentHrsgQuanta = nbt.getLong("hrsgQuanta"); fuelStandardLitersPerMinute = nbt.getDouble("fuelStandardLMin");
		
		this.energyQuanta = EnergyUnits.readEnergyQuanta(nbt, "power");
		this.rpm = nbt.getInteger("rpm");
		this.temp = nbt.getInteger("temp");
		this.state = nbt.getInteger("state");
		this.autoMode = nbt.getBoolean("automode");
		this.powerSliderPos = nbt.getInteger("slidpos");
		this.throttle = nbt.getInteger("throttle");			
		
		if(nbt.hasKey("counter"))
			this.counter = nbt.getInteger("counter"); //state 0 and -1
		else
			this.instantPowerOutput = nbt.getInteger("instantPow"); //state 1
		
		com.hbm.realsim.GeneratorContract.readTankStatus(tanks[0], nbt, "fuel");
		com.hbm.realsim.GeneratorContract.readTankStatus(tanks[1], nbt, "lube");
		com.hbm.realsim.GeneratorContract.readTankStatus(tanks[2], nbt, "water");
		com.hbm.realsim.GeneratorContract.readTankStatus(tanks[3], nbt, "steam");
	}
	
	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
        thermalFuel.read(nbt); hrsg.read(nbt); outputBudget.read(nbt); lubricantMicroliters = nbt.getLong("realSimLubeMicroL"); lastSimulationTick = nbt.hasKey("realSimThermalTick") ? nbt.getLong("realSimThermalTick") : Long.MIN_VALUE;
		this.tanks[0].readFromNBT(nbt, "gas");
		this.tanks[1].readFromNBT(nbt, "lube");
		this.tanks[2].readFromNBT(nbt, "water");
		this.tanks[3].readFromNBT(nbt, "densesteam");
		this.autoMode = nbt.getBoolean("automode");
		this.energyQuanta = EnergyUnits.readEnergyQuanta(nbt, "power");
		this.state = nbt.getInteger("state");
		this.rpm = nbt.getInteger("rpm");
		this.temp = nbt.getInteger("temperature");
		this.powerSliderPos = nbt.getInteger("slidPos");
		this.instantPowerOutput = nbt.getInteger("instPwr");
		this.counter = nbt.getInteger("counter");
        rpmLast = nbt.getInteger("realSimRpmLast"); tempLast = nbt.getInteger("realSimTempLast");
        if(lubricantMicroliters < 0 || lubricantMicroliters >= 1000000) throw new IllegalStateException("Invalid turbine lubricant ownership");
	}
	
	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
        thermalFuel.write(nbt); hrsg.write(nbt); outputBudget.write(nbt); nbt.setLong("realSimLubeMicroL", lubricantMicroliters); nbt.setLong("realSimThermalTick", lastSimulationTick);
		
		tanks[0].writeToNBT(nbt, "gas");
		tanks[1].writeToNBT(nbt, "lube");
		tanks[2].writeToNBT(nbt, "water");
		tanks[3].writeToNBT(nbt, "densesteam");
		nbt.setBoolean("automode", autoMode);
		EnergyUnits.writeEnergyQuanta(nbt, energyQuanta);
        nbt.setInteger("state", state); nbt.setInteger("rpm", rpm); nbt.setInteger("temperature", temp);
        nbt.setInteger("slidPos", powerSliderPos); nbt.setInteger("instPwr", instantPowerOutput); nbt.setInteger("counter", counter);
        nbt.setInteger("realSimRpmLast", rpmLast); nbt.setInteger("realSimTempLast", tempLast);
	}

    private void requestStart() { if(state == 0) { state = -1; counter = 0; rpm = 0; temp = 20; markDirty(); } }

	@Override
	public void receiveControl(NBTTagCompound data) {
		
		if(data.hasKey("slidPos"))
			powerSliderPos = Math.max(0, Math.min(60, data.getInteger("slidPos")));
		
		if(data.hasKey("autoMode"))
			autoMode = data.getBoolean("autoMode");
		
		if(data.hasKey("state")) { int requested = data.getInteger("state"); if(requested == -1) requestStart(); else if(requested == 0) state = 0; }

		this.markDirty();
		this.markMachineDirty(MachineDirtyCause.CONFIGURATION);
	}
	
	@Override
	public boolean hasPermission(EntityPlayer player) {
		return Vec3.createVectorHelper(xCoord - player.posX, yCoord - player.posY, zCoord - player.posZ).lengthVector() < 25;
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
	public void setStoredEnergyQuanta(long energyQuanta) {
		if(this.energyQuanta == energyQuanta) return;
		this.energyQuanta = energyQuanta;
		this.markPowerNetDirty();
		if(!runtimeEnergyMutation) this.markMachineEnergyDirty();
	}

	@Override
	public long getStoredEnergyQuanta() {
		return this.energyQuanta;
	}
	
	@Override
	public long getEnergyCapacityQuanta() {
		return this.maxPower;
	}

	public long getPowerOutputWatts() {
		return state == 1 ? EnergyUnits.quantaPerTickToWatts(instantPowerOutput) : 0L;
	}
	
	AxisAlignedBB bb = null;
	
	@Override
	public AxisAlignedBB getRenderBoundingBox() {
		
		if(bb == null) {
			bb = AxisAlignedBB.getBoundingBox(
					xCoord - 5,
					yCoord,
					zCoord - 5,
					xCoord + 6,
					yCoord + 3,
					zCoord + 6
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
	public String getName() {
		return "container.turbinegas";
	}

	@Override
	public FluidTank[] getAllTanks() {
		return tanks;
	}

	@Override
	public boolean isFluidDemandObservable() { return true; }

	@Override
	public FluidTank[] getReceivingTanks() {
		return new FluidTank[] { tanks[0], tanks[1], tanks[2] };
	}

	@Override
	public FluidTank[] getSendingTanks() {
		return new FluidTank[] { tanks[3] };
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
	@Optional.Method(modid = "OpenComputers")
	public String getComponentName() {
		return "ntm_gas_turbine";
	}

	@Callback(direct = true)
	@Optional.Method(modid = "OpenComputers")
	public Object[] getFluid(Context context, Arguments args) {
		return new Object[] {
				tanks[0].getFill(), tanks[0].getMaxFill(),
				tanks[1].getFill(), tanks[1].getMaxFill(),
				tanks[2].getFill(), tanks[2].getMaxFill(),
				tanks[3].getFill(), tanks[3].getMaxFill()
		};
	}

	@Callback(direct = true)
	@Optional.Method(modid = "OpenComputers")
	public Object[] getType(Context context, Arguments args) {
		return new Object[] {tanks[0].getTankType().getName()};
	}

	@Callback(direct = true)
	@Optional.Method(modid = "OpenComputers")
	public Object[] getPower(Context context, Arguments args) {
		return new Object[] {energyQuanta};
	}

	/** SI value for new computer programs; getPower retains its legacy numeric contract. */
	@Callback(direct = true)
	@Optional.Method(modid = "OpenComputers")
	public Object[] getStoredEnergyJoules(Context context, Arguments args) {
		return new Object[] {EnergyUnits.toJoules(energyQuanta)};
	}

	@Callback(direct = true)
	@Optional.Method(modid = "OpenComputers")
	public Object[] getThrottle(Context context, Arguments args) {
		return new Object[] {throttle};
	}

	@Callback(direct = true)
	@Optional.Method(modid = "OpenComputers")
	public Object[] getState(Context context, Arguments args) {
		return new Object[] {state};
	}

	@Callback(direct = true)
	@Optional.Method(modid = "OpenComputers")
	public Object[] getAuto(Context context, Arguments args) {
		return new Object[] {autoMode};
	}

	@Callback(direct = false, limit = 4)
	@Optional.Method(modid = "OpenComputers")
	public Object[] setThrottle(Context context, Arguments args) {
		powerSliderPos = (int) (Math.min(100, Math.max(0, args.checkInteger(0))) * 60D / 100D);
		markMachineDirty(MachineDirtyCause.CONFIGURATION);
		return new Object[] {};
	}

	@Callback(direct = false, limit = 4)
	@Optional.Method(modid = "OpenComputers")
	public Object[] setAuto(Context context, Arguments args) {
		autoMode = args.checkBoolean(0);
		markMachineDirty(MachineDirtyCause.CONFIGURATION);
		return new Object[] {};
	}

	@Callback(direct = false, limit = 4)
	@Optional.Method(modid = "OpenComputers")
	public Object[] start(Context context, Arguments args) {
        requestStart();
		markMachineDirty(MachineDirtyCause.CONFIGURATION);
		return new Object[] {};
	}

	@Callback(direct = false, limit = 4)
	@Optional.Method(modid = "OpenComputers")
	public Object[] stop(Context context, Arguments args) {
		state = 0;
		markMachineDirty(MachineDirtyCause.CONFIGURATION);
		return new Object[] {};
	}

	@Callback(direct = true)
	@Optional.Method(modid = "OpenComputers")
	public Object[] getInfo(Context context, Arguments args) {
		return new Object[] {throttle, state,
				tanks[0].getFill(), tanks[0].getMaxFill(),
				tanks[1].getFill(), tanks[1].getMaxFill(),
				tanks[2].getFill(), tanks[2].getMaxFill(),
				tanks[3].getFill(), tanks[3].getMaxFill()};
	}

	@Override
	@Optional.Method(modid = "OpenComputers")
	public String[] methods() {
		return new String[] {
				"getFluid",
				"getType",
				"getPower",
				"getStoredEnergyJoules",
				"getThrottle",
				"getState",
				"getAuto",
				"setThrottle",
				"setAuto",
				"start",
				"stop",
				"getInfo"
		};
	}

	@Override
	@Optional.Method(modid = "OpenComputers")
	public Object[] invoke(String method, Context context, Arguments args) throws Exception {
		switch(method) {
			case ("getFluid"):
				return getFluid(context, args);
			case ("getType"):
				return getType(context, args);
			case ("getPower"):
				return getPower(context, args);
			case ("getStoredEnergyJoules"):
				return getStoredEnergyJoules(context, args);
			case ("getThrottle"):
				return getThrottle(context, args);
			case ("getState"):
				return getState(context, args);
			case ("getAuto"):
				return getAuto(context, args);
			case ("setThrottle"):
				return setThrottle(context, args);
			case ("setAuto"):
				return setAuto(context, args);
			case ("start"):
				return start(context, args);
			case ("stop"):
				return stop(context, args);
			case ("getInfo"):
				return getInfo(context, args);
		}
		throw new NoSuchMethodException();
	}

	@Override
	public Container provideContainer(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new ContainerMachineTurbineGas(player.inventory, this);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public Object provideGUI(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new GUIMachineTurbineGas(player.inventory, this);
	}

	@Override
	public void provideExtraInfo(NBTTagCompound data) {
		data.setBoolean(CompatEnergyControl.B_ACTIVE, this.state == 1);
		data.setDouble(CompatEnergyControl.D_HEAT_C, Math.max(20D, this.temp));
		data.setDouble(CompatEnergyControl.D_TURBINE_PERCENT, this.powerSliderPos * 100D / 60D);
		data.setInteger(CompatEnergyControl.I_TURBINE_SPEED, this.rpm);
		data.setDouble(CompatEnergyControl.D_OUTPUT_HE, EnergyUnits.quantaToLegacyHe(this.instantPowerOutput));
		data.setDouble(CompatEnergyControl.D_CONSUMPTION_MB, (hrsg.currentMass / 1000000D));
		data.setDouble(CompatEnergyControl.D_OUTPUT_MB, (hrsg.currentMass / 1000000D) * 10);
	}

    @Override public void appendMachineInfo(java.util.List<String> lines, boolean live) {
        if(live) { lines.add("Current electrical output: " + com.hbm.realsim.MachineInfo.watts(getPowerOutputWatts())); lines.add(String.format(java.util.Locale.US, "Fuel flow: %.2f standard L/min", fuelStandardLitersPerMinute)); }
        lines.add(String.format(java.util.Locale.US, "Gross LHV efficiency: %.1f%%; auxiliaries reduce net output", ELECTRICAL_EFFICIENCY_PPM / 10000D));
        lines.add("Rated fuel input: " + com.hbm.realsim.MachineInfo.thermalWatts(RATED_CHEMICAL_WATTS));
        lines.add("HRSG thermal ceiling: " + com.hbm.realsim.MachineInfo.thermalWatts(HRSG_WATTS));
        lines.add("Requires clean compatible fuel gas and lubricant; HRSG recovers exhaust heat");
    }
}
