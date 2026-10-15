package com.hbm.tileentity.machine;

import com.hbm.blocks.BlockDummyable;
import com.hbm.inventory.container.ContainerBlastFurnace;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.inventory.gui.GUIBlastFurnace;
import com.hbm.inventory.recipes.IndustrialBlastFurnaceRecipes;
import com.hbm.inventory.recipes.IndustrialBlastFurnaceRecipes.Recipe;
import com.hbm.machine.MachineExecutionStrategy;
import com.hbm.realsim.BlastFurnaceProcess;
import com.hbm.realsim.PhysicalRegistry;
import com.hbm.realsim.SteamLedger;
import com.hbm.tileentity.IGUIProvider;
import com.hbm.tileentity.TileEntityMachineBase;
import com.hbm.util.fauxpointtwelve.DirPos;
import api.hbm.fluidmk2.IFluidStandardTransceiverMK2;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

/** Upstream seven-high machine; RTM MachineRuntime owns all server processing. */
public class TileEntityMachineBlastFurnace extends TileEntityMachineBase implements IFluidStandardTransceiverMK2, IGUIProvider, com.hbm.realsim.IMachineInfo {
    public final FluidTank[] tanks = new FluidTank[] {
        new FluidTank(Fluids.AIRBLAST, 16000), new FluidTank(Fluids.BLAST_FURNACE_GAS, 16000),
        new FluidTank(Fluids.AIR, 16000), new FluidTank(Fluids.AIR_HEATED, 16000), new FluidTank(Fluids.OXYGEN_GAS, 4000), new FluidTank(Fluids.CARBONDIOXIDE, 4000)
    };
    private final FluidTank[] receiving = {tanks[0], tanks[2], tanks[3], tanks[4]};
    private final FluidTank[] sending = {tanks[1], tanks[5]};
    private final FluidTank[] blasts = {tanks[0], tanks[3], tanks[2]};
    private final com.hbm.realsim.MassRemainder fluxGas = new com.hbm.realsim.MassRemainder();
    private static final long FLUX_CO2_MASS = java.math.BigDecimal.valueOf(com.hbm.realsim.PhysicalState.MASS_UNITS_PER_KG)
            .multiply(new java.math.BigDecimal("44.0095")).divide(new java.math.BigDecimal("100.0869"), 0, java.math.RoundingMode.DOWN).longValueExact();
    private long pendingFluxCO2;
    private static final int TASK_PROCESS = 1, TASK_GAS = 2, CADENCE = 5;
    private DirPos[] connections;
    private int orientation = -1;
    private Recipe cachedRecipe;
    private long recipeRevision = -1, lastStep = Long.MIN_VALUE;
    private ItemStack ownedFeed, result, slag;
    private boolean coke, enriched;
    private long heatRequired, heatPaid, fuelQuanta, gasMicroJ;
    private int airRequired, airPaid, oxygenPaid;
    public boolean isProgressing;
    public float progress, speed;
    public int fuel;
    public static final int MAX_FUEL = 64_000_000; // 32 MJ in existing half-joule quanta; display only

    public TileEntityMachineBlastFurnace() {
        super(6);
        tanks[0].withPressure(1); tanks[2].withPressure(1); tanks[3].withPressure(1);
        for(FluidTank tank : tanks) trackMachineFluidTank(tank);
    }
    @Override public String getName() { return "container.blastFurnace"; }
    @Override public void updateEntity() { /* No constant tile ticking: MachineRuntime owns operation. */ }
    @Override public int getMachineExecutionStrategies() {
        return MachineExecutionStrategy.EVENT_DRIVEN | MachineExecutionStrategy.SCHEDULED | MachineExecutionStrategy.COARSE_20;
    }
    @Override public void onMachineRuntimeDirty(int causes) {
        if(worldObj == null || worldObj.isRemote) return;
        if(recipeRevision < 0 || (causes & (com.hbm.machine.MachineDirtyCause.INVENTORY | com.hbm.machine.MachineDirtyCause.RECIPE)) != 0) refreshRecipe();
        boolean needsConnections = connections == null || orientation != getBlockMetadata();
        refreshConnections(); if(needsConnections) connect(); schedule(); sync();
    }
    private void refreshRecipe() {
        cachedRecipe = IndustrialBlastFurnaceRecipes.find(slots[1], slots[2]);
        recipeRevision = com.hbm.inventory.recipes.loader.SerializableRecipe.getRegistryRevision();
    }
    @Override public void onMachineCoarsePoll(int cadence) {
        if(cadence != 20 || worldObj == null || worldObj.isRemote) return;
        if(recipeRevision != com.hbm.inventory.recipes.loader.SerializableRecipe.getRegistryRevision()) refreshRecipe();
        refreshConnections(); connect(); schedule(); sync();
    }
    private void refreshConnections() {
        if(orientation == getBlockMetadata() && connections != null) return;
        orientation = getBlockMetadata();
        ForgeDirection dir = ForgeDirection.getOrientation(orientation - BlockDummyable.offset);
        connections = new DirPos[] {
            new DirPos(xCoord + 2, yCoord, zCoord, ForgeDirection.EAST), new DirPos(xCoord - 2, yCoord, zCoord, ForgeDirection.WEST),
            new DirPos(xCoord, yCoord, zCoord + 2, ForgeDirection.SOUTH), new DirPos(xCoord, yCoord, zCoord - 2, ForgeDirection.NORTH),
            new DirPos(xCoord + dir.offsetX * 2, yCoord + 3, zCoord + dir.offsetZ * 2, dir),
            new DirPos(xCoord + dir.offsetX * 2, yCoord + 5, zCoord + dir.offsetZ * 2, dir),
            new DirPos(xCoord, yCoord + 7, zCoord, ForgeDirection.UP)
        };
    }
    private void connect() {
        for(int i = 0; i < connections.length; i++) {
            if(i < connections.length - 1) for(FluidTank tank : receiving) trySubscribe(tank.getTankType(), worldObj, connections[i]);
            for(FluidTank tank : sending) if(tank.getFill() > 0) tryProvide(tank, worldObj, connections[i]);
        }
    }
    private FluidTank blast() {
        long required = result != null ? heatRequired : cachedRecipe == null ? 0 : cachedRecipe.heatJoules;
        if(required == 0) return null;
        long paid = result == null ? 0 : heatPaid;
        int totalAir = result == null ? cachedRecipe.air : airRequired;
        boolean useCoke = result == null ? cachedRecipe.coke : coke;
        for(FluidTank tank : blasts) {
            long step = Math.min(required - paid, BlastFurnaceProcess.ratedWatts(useCoke, tank.getTankType().getName(), result != null && enriched) * CADENCE / 20L);
            int amount = (int) SteamLedger.ceilDiv((paid + step) * totalAir, required) - (result == null ? 0 : airPaid);
            if(tank.getFill() >= amount && amount > 0) return tank;
        }
        return null;
    }
    private boolean outputFits(ItemStack output, int slot) {
        return output == null || slots[slot] == null || slots[slot].isItemEqual(output) && ItemStack.areItemStackTagsEqual(slots[slot], output)
                && slots[slot].stackSize + output.stackSize <= Math.min(output.getMaxStackSize(), getInventoryStackLimit());
    }
    private boolean canRun() {
        if(result != null && heatPaid >= heatRequired) return outputFits(result, 3) && outputFits(slag, 4) && fluxGas.preview("CARBONDIOXIDE", pendingFluxCO2) <= Math.max(0, tanks[5].getMaxFill() - tanks[5].getFill());
        FluidTank air = blast();
        if(air == null) return false;
        if(result == null) return cachedRecipe != null && gasRoom(slots[2]) && outputFits(cachedRecipe.steel, 3) && outputFits(cachedRecipe.slag, 4);
        if(!outputFits(result, 3) || !outputFits(slag, 4)) return false;
        if(heatPaid >= heatRequired) return true;
        long step = Math.min(heatRequired - heatPaid, BlastFurnaceProcess.ratedWatts(coke, air.getTankType().getName(), enriched) * CADENCE / 20L);
        int amount = (int) SteamLedger.ceilDiv((heatPaid + step) * airRequired, heatRequired) - airPaid;
        if(air.getFill() < amount || enriched && tanks[4].getFill() < (airPaid + amount + 9) / 10 - oxygenPaid) return false;
        return gasRoom(slots[0]) || BlastFurnaceProcess.blastJoulesPerLiter(air.getTankType().getName()) * amount + fuelQuanta / 2 >= step;
    }
    private void schedule() {
        long now = worldObj.getTotalWorldTime();
        if(canRun()) scheduleMachineTransition(now + CADENCE, TASK_PROCESS, 0);
        else { cancelMachineTransition(TASK_PROCESS, 0); isProgressing = false; speed = 0; }
        if((tanks[1].getFill() > 0 || tanks[5].getFill() > 0) && worldObj.isAirBlock(xCoord, yCoord + 7, zCoord)) scheduleMachineTransition(now + 20, TASK_GAS, 0);
        else cancelMachineTransition(TASK_GAS, 0);
    }
    @Override public void onMachineScheduledTransition(int task, int slot, long due) {
        if(worldObj == null || worldObj.isRemote) return;
        if(task == TASK_GAS) {
            connect();
            if(worldObj.isAirBlock(xCoord, yCoord + 7, zCoord)) for(FluidTank tank : sending) if(tank.getFill() > 0) {
                int amount = Math.min(tank.getFill(), 200);
                tank.getTankType().onFluidRelease(worldObj, xCoord, yCoord + 7, zCoord, tank, amount);
                com.hbm.inventory.fluid.trait.FT_Polluting.pollute(worldObj, xCoord, yCoord, zCoord, tank.getTankType(), com.hbm.inventory.fluid.trait.FluidTrait.FluidReleaseType.SPILL, amount);
                tank.setFill(tank.getFill() - amount);
            }
        } else if(task == TASK_PROCESS && worldObj.getTotalWorldTime() > lastStep) {
            lastStep = worldObj.getTotalWorldTime();
            beginMachineFluidMutation();
            try { step(); } finally { endMachineFluidMutation(); }
        }
        markDirty(); markNetworkDirty(); schedule(); sync();
    }
    private void addFuel(ItemStack stack) {
        String identity = BlastFurnaceProcess.fuelIdentity(stack);
        long chemical = PhysicalRegistry.get(identity).chemicalMicrojoulesPerUnit();
        long useful = SteamLedger.mulDiv(chemical, BlastFurnaceProcess.efficiency(identity), 1000000);
        fuelQuanta = Math.addExact(fuelQuanta, useful / 500000L);
        gasMicroJ = Math.addExact(gasMicroJ, SteamLedger.mulDiv(chemical, BlastFurnaceProcess.gasRecovery(identity), 1000000));
        com.hbm.handler.pollution.PollutionHandler.incrementPollution(worldObj, xCoord, yCoord, zCoord,
                com.hbm.handler.pollution.PollutionHandler.PollutionType.SOOT, identity.equals("COKE_ITEM") ? 0.2F : 1F);
    }
    private boolean gasRoom(ItemStack fuelStack) {
        if(fuelStack == null) return false;
        String identity = BlastFurnaceProcess.fuelIdentity(fuelStack);
        if(identity == null) return false;
        long chemical = PhysicalRegistry.get(identity).chemicalMicrojoulesPerUnit();
        long gas = (gasMicroJ + SteamLedger.mulDiv(chemical, BlastFurnaceProcess.gasRecovery(identity), 1000000)) / PhysicalRegistry.get("BLAST_FURNACE_GAS").chemicalMicrojoulesPerUnit();
        return gas <= Math.max(0, tanks[1].getMaxFill() - tanks[1].getFill());
    }
    private void releaseGas() {
        long unit = PhysicalRegistry.get("BLAST_FURNACE_GAS").chemicalMicrojoulesPerUnit();
        int amount = (int) Math.min(gasMicroJ / unit, Math.max(0, tanks[1].getMaxFill() - tanks[1].getFill()));
        tanks[1].setFill(tanks[1].getFill() + amount); gasMicroJ -= amount * unit;
    }
    private void step() {
        isProgressing = false; speed = 0;
        if(!canRun()) return;
        if(result == null) {
            if(!gasRoom(slots[2])) return;
            Recipe recipe = cachedRecipe;
            coke = recipe.coke; enriched = coke && tanks[4].getFill() >= (recipe.air + 9) / 10;
            ownedFeed = slots[1].copy(); ownedFeed.stackSize = recipe.feed.stacksize;
            result = recipe.steel.copy(); slag = recipe.slag == null ? null : recipe.slag.copy();
            heatRequired = enriched ? recipe.heatJoules * 9 / 10 : recipe.heatJoules;
            if(recipe.slag != null && recipe.slag.stackSize < recipe.slag.getMaxStackSize() && isFlux(slots[5])) {
                // CaCO3 -> CaO + CO2. 1.8 MJ/kg calcination, offset by 3 MJ less slagging/heat loss.
                heatRequired = Math.max(1, heatRequired - 1_200_000L); pendingFluxCO2 = FLUX_CO2_MASS;
                slag.stackSize = Math.min(slag.getMaxStackSize(), slag.stackSize + 1); decrStackSize(5, 1);
            }
            heatPaid = 0; airRequired = recipe.air; airPaid = 0; oxygenPaid = 0;
            addFuel(slots[2]); decrStackSize(1, recipe.feed.stacksize); decrStackSize(2, 1); releaseGas(); refreshRecipe();
        }
        if(heatPaid < heatRequired) {
            FluidTank air = blast();
            if(air == null) return;
            long rating = BlastFurnaceProcess.ratedWatts(coke, air.getTankType().getName(), enriched);
            long step = Math.min(heatRequired - heatPaid, rating * CADENCE / 20L);
            int amount = (int) SteamLedger.ceilDiv((heatPaid + step) * airRequired, heatRequired) - airPaid;
            int oxygen = enriched ? (airPaid + amount + 9) / 10 - oxygenPaid : 0;
            if(air.getFill() < amount || tanks[4].getFill() < oxygen) return;
            long blastHeat = Math.min(step, BlastFurnaceProcess.blastJoulesPerLiter(air.getTankType().getName()) * amount);
            long needed = (step - blastHeat) * 2;
            if(fuelQuanta < needed) {
                if(!gasRoom(slots[0])) return;
                addFuel(slots[0]); decrStackSize(0, 1); releaseGas();
            }
            if(fuelQuanta < needed) return;
            fuelQuanta -= needed;
            air.setFill(air.getFill() - amount); tanks[4].setFill(tanks[4].getFill() - oxygen);
            heatPaid += step; airPaid += amount; oxygenPaid += oxygen;
            isProgressing = true; speed = rating / 1000000F;
        }
        if(heatPaid == heatRequired && canRun()) {
            tanks[5].setFill(tanks[5].getFill() + fluxGas.convert("CARBONDIOXIDE", pendingFluxCO2)); pendingFluxCO2 = 0;
            insertResult(result, 3); insertResult(slag, 4); result = null; slag = null; ownedFeed = null; heatPaid = 0; heatRequired = 0;
        }
    }
    private void insertResult(ItemStack stack, int slot) {
        if(stack == null) return;
        if(slots[slot] == null) setInventorySlotContents(slot, stack.copy()); else { slots[slot].stackSize += stack.stackSize; onInventorySlotChanged(slot); }
    }
    /** Controller teardown drops the owned feed, or its fully paid products, once. Burned reductant is never refunded. */
    public void dropOwnedCharge() {
        if(result == null || worldObj.isRemote) return;
        if(heatPaid >= heatRequired) { drop(result); drop(slag); } else drop(ownedFeed);
        result = null; slag = null; ownedFeed = null;
    }
    private void drop(ItemStack stack) { if(stack != null) worldObj.spawnEntityInWorld(new EntityItem(worldObj, xCoord + 0.5, yCoord + 0.5, zCoord + 0.5, stack.copy())); }
    public int getBurnTime(ItemStack stack) { return BlastFurnaceProcess.fuelIdentity(stack) == null ? 0 : 1; }
    private boolean isFlux(ItemStack stack) { return stack != null && stack.getItem() == com.hbm.items.ModItems.powder_limestone && stack.stackSize > 0; }
    @Override public boolean isItemValidForSlot(int slot, ItemStack stack) {
        if(slot == 0 || slot == 2) return getBurnTime(stack) > 0;
        if(slot == 5) return isFlux(stack);
        if(slot == 1) for(Recipe recipe : IndustrialBlastFurnaceRecipes.recipes) if(recipe.feed.matchesRecipe(stack, true)) return true;
        return false;
    }
    @Override public boolean canInsertItem(int slot, ItemStack stack, int side) { return isItemValidForSlot(slot, stack); }
    @Override public boolean canExtractItem(int slot, ItemStack stack, int side) { return slot == 3 || slot == 4; }
    @Override public int[] getAccessibleSlotsFromSide(int side) { return new int[] {1, 2, 0, 5, 3, 4}; }
    private void sync() {
        progress = heatRequired == 0 ? 0 : (float) ((double) heatPaid / heatRequired);
        fuel = (int) Math.min(MAX_FUEL, fuelQuanta); networkPackNT(40);
    }
    @Override public void serialize(ByteBuf buf) {
        super.serialize(buf); buf.writeBoolean(isProgressing); buf.writeFloat(progress); buf.writeFloat(speed); buf.writeInt(fuel);
        buf.writeLong(heatRequired); buf.writeLong(heatPaid); buf.writeLong(fuelQuanta); buf.writeInt(airRequired); buf.writeInt(airPaid); buf.writeInt(oxygenPaid); buf.writeBoolean(coke); buf.writeBoolean(enriched);
        for(FluidTank tank : tanks) tank.serialize(buf);
    }
    @Override public void deserialize(ByteBuf buf) {
        super.deserialize(buf); isProgressing = buf.readBoolean(); progress = buf.readFloat(); speed = buf.readFloat(); fuel = buf.readInt();
        heatRequired = buf.readLong(); heatPaid = buf.readLong(); fuelQuanta = buf.readLong(); airRequired = buf.readInt(); airPaid = buf.readInt(); oxygenPaid = buf.readInt(); coke = buf.readBoolean(); enriched = buf.readBoolean();
        for(FluidTank tank : tanks) tank.deserialize(buf);
    }
    @Override public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt); NBTTagCompound bill = new NBTTagCompound(); bill.setInteger("version", 1);
        bill.setLong("heatRequired", heatRequired); bill.setLong("heatPaid", heatPaid); bill.setLong("fuelQuanta", fuelQuanta); bill.setLong("gasMicroJ", gasMicroJ);
        bill.setInteger("airRequired", airRequired); bill.setInteger("airPaid", airPaid); bill.setInteger("oxygenPaid", oxygenPaid);
        bill.setBoolean("coke", coke); bill.setBoolean("enriched", enriched);
        bill.setLong("pendingFluxCO2", pendingFluxCO2); fluxGas.write(bill);
        if(result != null) { bill.setTag("result", result.writeToNBT(new NBTTagCompound())); bill.setTag("feed", ownedFeed.writeToNBT(new NBTTagCompound())); }
        if(slag != null) bill.setTag("slag", slag.writeToNBT(new NBTTagCompound()));
        nbt.setTag("realSimBlast", bill); for(int i = 0; i < tanks.length; i++) tanks[i].writeToNBT(nbt, "t" + i);
    }
    @Override public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt); NBTTagCompound bill = nbt.getCompoundTag("realSimBlast");
        if(bill.hasKey("version") && bill.getInteger("version") != 1) throw new IllegalStateException("Unknown blast furnace accounting version");
        heatRequired = bill.getLong("heatRequired"); heatPaid = bill.getLong("heatPaid"); fuelQuanta = bill.getLong("fuelQuanta"); gasMicroJ = bill.getLong("gasMicroJ");
        airRequired = bill.getInteger("airRequired"); airPaid = bill.getInteger("airPaid"); oxygenPaid = bill.getInteger("oxygenPaid"); coke = bill.getBoolean("coke"); enriched = bill.getBoolean("enriched");
        pendingFluxCO2 = bill.getLong("pendingFluxCO2"); fluxGas.read(bill);
        result = ItemStack.loadItemStackFromNBT(bill.getCompoundTag("result")); slag = ItemStack.loadItemStackFromNBT(bill.getCompoundTag("slag")); ownedFeed = ItemStack.loadItemStackFromNBT(bill.getCompoundTag("feed"));
        if(heatRequired < 0 || heatRequired > 1_000_000_000L || heatPaid < 0 || heatPaid > heatRequired || fuelQuanta < 0 || gasMicroJ < 0 || pendingFluxCO2 < 0
                || airRequired < 0 || airRequired > 1_000_000 || airPaid < 0 || airPaid > airRequired || oxygenPaid < 0 || oxygenPaid > (airRequired + 9) / 10
                || result != null && (ownedFeed == null || heatRequired <= 0 || airRequired <= 0) || result == null && (ownedFeed != null || slag != null || heatRequired != 0 || pendingFluxCO2 != 0)) throw new IllegalStateException("Invalid owned blast furnace bill");
        for(int i = 0; i < tanks.length; i++) tanks[i].readFromNBT(nbt, "t" + i);
    }
    @Override public FluidTank[] getReceivingTanks() { return receiving; }
    @Override public FluidTank[] getSendingTanks() { return sending; }
    @Override public FluidTank[] getAllTanks() { return tanks; }
    @Override public long getDemand(FluidType type, int pressure) {
        long room = 0;
        for(FluidTank tank : receiving) if(tank.getTankType() == type && tank.getPressure() == pressure) room += Math.max(0L, (long) tank.getMaxFill() - tank.getFill());
        return room;
    }
    @Override public long transferFluid(FluidType type, int pressure, long amount) {
        long remaining = amount;
        for(FluidTank tank : receiving) if(tank.getTankType() == type && tank.getPressure() == pressure) {
            int accepted = (int) Math.min(Math.max(0L, remaining), Math.max(0L, (long) tank.getMaxFill() - tank.getFill()));
            if(accepted > 0) { tank.setFill(tank.getFill() + accepted); remaining -= accepted; }
        }
        return remaining;
    }
    @Override public long getProviderSpeed(FluidType type, int pressure) { return 200; }
    @Override public void appendMachineInfo(java.util.List<String> lines, boolean live) {
        lines.add("Coke-fired shaft furnace; downstream hot-metal refining remains abstracted");
        lines.add("Coal coke: 28.2 MJ/kg LHV; 55% process heat, 0.25% recoverable gas, 44.75% rejected");
        lines.add("Raw coal: 25.8 MJ/kg LHV; 30% process heat, 0.125% recoverable gas, 69.875% rejected");
        lines.add("Coke thermal ceilings: 2 / 4 / 8 MW; no electrical furnace input");
        lines.add("Dry top-gas composition: 22% CO, 22% CO2, 54% N2, 2% H2 (molar)");
        lines.add("Optional flux: CaCO3 -> CaO + CO2; basic slag binds silicate gangue");
        if(live) {
            lines.add(String.format(java.util.Locale.US, "Process heating: %.3f / %.3f MJ", heatPaid / 1000000D, heatRequired / 1000000D));
            lines.add(String.format(java.util.Locale.US, "Available heat: %.3f MJ", fuelQuanta / 2000000D));
            lines.add("Blast consumed: " + airPaid + " / " + airRequired + " standard L; O2: " + oxygenPaid + " standard L");
            lines.add(enriched ? "O2 enrichment: lower heat loss and 25% higher thermal throughput" : "O2 enrichment is optional");
        }
    }
    @Override public Container provideContainer(int id, EntityPlayer player, World world, int x, int y, int z) { return new ContainerBlastFurnace(player.inventory, this); }
    @Override @SideOnly(Side.CLIENT) public Object provideGUI(int id, EntityPlayer player, World world, int x, int y, int z) { return new GUIBlastFurnace(player.inventory, this); }
    @Override @SideOnly(Side.CLIENT) public AxisAlignedBB getRenderBoundingBox() { return AxisAlignedBB.getBoundingBox(xCoord - 1, yCoord, zCoord - 1, xCoord + 2, yCoord + 7, zCoord + 2); }
    @Override @SideOnly(Side.CLIENT) public double getMaxRenderDistanceSquared() { return 65536D; }
}
