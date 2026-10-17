package com.hbm.realsim;

import static org.junit.Assert.*;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Test;
import api.hbm.energymk2.*;
import api.hbm.energymk2.IEnergyReceiverMK2.ConnectionPriority;
import api.hbm.tile.IProcessHeatSource;
import com.hbm.tileentity.machine.TileEntityMachineElectricFurnace;
import com.hbm.tileentity.machine.TileEntityStirling;
import com.hbm.uninos.UniNodespace;
import com.hbm.util.fauxpointtwelve.BlockPos;

/** Real network/heat conversion code with headless endpoints; not a loaded-world or GUI acceptance test. */
public class ElectricalNetworkTest {
    private final List<PowerNetMK2> networks = new ArrayList<>();
    private PowerNetMK2 network() { PowerNetMK2 net = new PowerNetMK2(); networks.add(net); return net; }
    @After public void cleanup() { for(PowerNetMK2 net : networks) net.destroy(); UniNodespace.worlds.remove(null); }

    private static class Receiver implements IEnergyReceiverMK2 {
        long stored, capacity, input = Long.MAX_VALUE;
        int attempts;
        boolean reject, loaded = true;
        ConnectionPriority priority = ConnectionPriority.NORMAL;
        Runnable callback;
        Receiver(long capacity) { this.capacity = capacity; }
        @Override public boolean isLoaded() { return loaded; }
        @Override public long getStoredEnergyQuanta() { return stored; }
        @Override public void setStoredEnergyQuanta(long value) { stored = value; }
        @Override public long getEnergyCapacityQuanta() { return capacity; }
        @Override public long getMaxInputQuantaPerTick() { return input; }
        @Override public ConnectionPriority getPriority() { return priority; }
        @Override public long receiveEnergyQuanta(long offered) {
            attempts++;
            if(callback != null) callback.run();
            return reject ? offered : IEnergyReceiverMK2.super.receiveEnergyQuanta(offered);
        }
    }
    private static class Provider implements IEnergyProviderMK2 {
        long stored, rate;
        Provider(long stored, long rate) { this.stored = stored; this.rate = rate; }
        @Override public boolean isLoaded() { return true; }
        @Override public long getStoredEnergyQuanta() { return stored; }
        @Override public void setStoredEnergyQuanta(long value) { assertTrue(value >= 0); stored = value; }
        @Override public long getEnergyCapacityQuanta() { return Long.MAX_VALUE; }
        @Override public long getMaxOutputQuantaPerTick() { return rate; }
    }
    private static class Heat implements IProcessHeatSource {
        int remaining = 1000000000, drawn;
        @Override public int getHeatStored() { return remaining; }
        @Override public int getProcessTemperatureC() { return 900; }
        @Override public void useUpHeat(int amount) { remaining -= amount; drawn += amount; }
    }
    private static class Heavy extends TileEntityStirling {
        long tick;
        @Override public int getGeatMeta() { return 1; }
        void heat(Heat heat, long tick) { this.tick = tick; convertProcessHeat(heat, tick); }
        // Headless port clock; production IRatedGenerator obtains the same budget from the owning world.
        @Override public long getMaxOutputQuantaPerTick() { return getOutputBudget().available(tick, getRatedOutputWatts() / 10); }
        @Override public void extractEnergyQuanta(long amount) {
            getOutputBudget().use(tick, getRatedOutputWatts() / 10, amount);
            setStoredEnergyQuanta(getStoredEnergyQuanta() - amount);
        }
    }

    @Test(timeout=3000) public void rejectingEveryDestinationNeverRetries() {
        PowerNetMK2 net = network(); Provider source = new Provider(8000, 4000); net.addProvider(source);
        List<Receiver> receivers = new ArrayList<>();
        for(int i = 0; i < 200; i++) { Receiver r = new Receiver(100); r.reject = true; receivers.add(r); net.addReceiver(r); }
        net.update();
        assertEquals(8000, source.stored); assertEquals(0, net.transferredEnergyQuantaThisTick);
        for(Receiver r : receivers) assertEquals(1, r.attempts);
    }
    @Test(timeout=3000) public void fullConsumerIsNotOfferedPower() {
        PowerNetMK2 net = network(); Provider source = new Provider(8000, 4000); net.addProvider(source);
        Receiver r = new Receiver(100); r.stored = 100; net.addReceiver(r); net.update();
        assertEquals(8000, source.stored); assertEquals(0, r.attempts);
    }
    @Test(timeout=3000) public void partialConsumerAcceptsOnlyItsRoom() {
        PowerNetMK2 net = network(); Provider source = new Provider(8000, 4000); net.addProvider(source);
        Receiver r = new Receiver(100); r.stored = 73; net.addReceiver(r); net.update();
        assertEquals(100, r.stored); assertEquals(7973, source.stored);
    }
    @Test(timeout=3000) public void severalGeneratorsAndOneFullConsumerTerminate() {
        PowerNetMK2 net = network(); List<Provider> sources = new ArrayList<>();
        for(int i = 0; i < 30; i++) { Provider p = new Provider(8000, 4000); sources.add(p); net.addProvider(p); }
        Receiver r = new Receiver(100); r.stored = 100; net.addReceiver(r); net.update();
        for(Provider p : sources) assertEquals(8000, p.stored);
    }
    @Test(timeout=3000) public void oneGeneratorAndSeveralFullConsumersTerminate() {
        PowerNetMK2 net = network(); Provider p = new Provider(8000, 4000); net.addProvider(p);
        for(int i = 0; i < 300; i++) { Receiver r = new Receiver(100); r.stored = 100; net.addReceiver(r); }
        net.update(); assertEquals(8000, p.stored);
    }
    @Test(timeout=3000) public void mixedFullAndAcceptingConsumersConserveEnergy() {
        PowerNetMK2 net = network(); Provider p = new Provider(8000, 4000); net.addProvider(p);
        Receiver full = new Receiver(100); full.stored = 100; Receiver accepting = new Receiver(5000);
        net.addReceiver(full); net.addReceiver(accepting); net.update();
        assertEquals(4000, accepting.stored); assertEquals(8000, p.stored + accepting.stored); assertEquals(0, full.attempts);
    }
    @Test(timeout=3000) public void threePriorityGroupsDoNotDoubleSubtractEarlierDelivery() {
        PowerNetMK2 net = network(); Provider p = new Provider(300, 300); net.addProvider(p);
        List<Receiver> receivers = new ArrayList<>();
        for(ConnectionPriority priority : new ConnectionPriority[] {ConnectionPriority.HIGHEST, ConnectionPriority.HIGH, ConnectionPriority.NORMAL}) {
            Receiver r = new Receiver(100); r.priority = priority; receivers.add(r); net.addReceiver(r);
        }
        net.update(); for(Receiver r : receivers) assertEquals(100, r.stored); assertEquals(0, p.stored);
    }
    @Test(timeout=3000) public void diodePriorityAccountingAlsoConservesEnergy() {
        PowerNetMK2 net = network(); List<Receiver> receivers = new ArrayList<>();
        for(ConnectionPriority priority : ConnectionPriority.values()) { Receiver r = new Receiver(10); r.priority = priority; receivers.add(r); net.addReceiver(r); }
        assertEquals(0, net.sendEnergyQuantaThroughDiode(50)); for(Receiver r : receivers) assertEquals(10, r.stored);
    }
    @Test(timeout=3000) public void rejectedHigherPriorityPowerCanReachLowerPriority() {
        PowerNetMK2 net = network(); Provider p = new Provider(100, 100); net.addProvider(p);
        Receiver reject = new Receiver(100); reject.reject = true; reject.priority = ConnectionPriority.HIGHEST;
        Receiver accept = new Receiver(100); net.addReceiver(reject); net.addReceiver(accept); net.update();
        assertEquals(100, accept.stored); assertEquals(0, p.stored); assertEquals(1, reject.attempts);
    }
    @Test(timeout=3000) public void overflowingSupplyAndDemandCannotCreateNegativeTransfers() {
        PowerNetMK2 net = network(); Provider p = new Provider(Long.MAX_VALUE, Long.MAX_VALUE);
        Provider q = new Provider(Long.MAX_VALUE, Long.MAX_VALUE); Receiver r = new Receiver(Long.MAX_VALUE);
        net.addProvider(p); net.addProvider(q); net.addReceiver(r); net.update();
        assertEquals(Long.MAX_VALUE, r.stored); assertTrue(p.stored == 0 || q.stored == 0);
        assertTrue(p.stored == Long.MAX_VALUE || q.stored == Long.MAX_VALUE);
    }
    @Test(timeout=3000) public void removedEndpointDuringDeliveryDoesNotInvalidateIteration() {
        PowerNetMK2 net = network(); Provider p = new Provider(400, 400); net.addProvider(p);
        Receiver a = new Receiver(100), b = new Receiver(100);
        a.callback = () -> net.removeReceiver(b); b.callback = () -> net.removeReceiver(a);
        net.addReceiver(a); net.addReceiver(b); net.update();
        assertEquals(100, a.stored + b.stored); assertEquals(300, p.stored);
    }
    @Test(timeout=3000) public void reentrantNetworkCannotOverwriteItsScratchOrRevisitItself() {
        PowerNetMK2 net = network(); Provider p = new Provider(100, 100); net.addProvider(p);
        Receiver r = new Receiver(100); r.callback = net::update; net.addReceiver(r); net.update();
        assertEquals(100, r.stored); assertEquals(0, p.stored); assertEquals(1, r.attempts);
    }
    @Test(timeout=3000) public void cyclicDiodeNetworksRejectWithoutRecursingForever() {
        PowerNetMK2 a = network(), b = network();
        Receiver ab = new Receiver(100) { @Override public long receiveEnergyQuanta(long amount) { attempts++; return b.sendEnergyQuantaThroughDiode(amount); } };
        Receiver ba = new Receiver(100) { @Override public long receiveEnergyQuanta(long amount) { attempts++; return a.sendEnergyQuantaThroughDiode(amount); } };
        a.addReceiver(ab); b.addReceiver(ba);
        assertEquals(100, a.sendEnergyQuantaThroughDiode(100)); assertEquals(1, ab.attempts); assertEquals(1, ba.attempts);
    }
    @Test(timeout=5000) public void largeLegitimateNetworkHasLinearBoundedAttempts() {
        PowerNetMK2 net = network(); Provider p = new Provider(100000, 100000); net.addProvider(p);
        List<Receiver> receivers = new ArrayList<>();
        for(int i = 0; i < 20000; i++) { Receiver r = new Receiver(1); receivers.add(r); net.addReceiver(r); }
        net.update(); int attempts = 0; long received = 0;
        for(Receiver r : receivers) { attempts += r.attempts; received += r.stored; }
        assertEquals(20000, attempts); assertEquals(20000, received); assertEquals(100000, p.stored + received);
    }
    private void stirlingCase(long initialConsumer, boolean connected) {
        Heavy heavy = new Heavy(); Heat heat = new Heat(); PowerNetMK2 net = network();
        Receiver r = new Receiver(4000); r.stored = initialConsumer;
        net.addProvider(heavy); if(connected) net.addReceiver(r);
        for(long tick = 1; tick <= 100; tick++) { heavy.heat(heat, tick); net.update(); }
        assertEquals(2000000, heat.drawn); assertTrue(heavy.powerBuffer <= 8000);
        assertEquals(connected ? 4000 : initialConsumer, r.stored);
    }
    @Test(timeout=3000) public void stirlingFeedsAcceptingConsumer() { stirlingCase(0, true); }
    @Test(timeout=3000) public void stirlingFeedsPartialConsumerThenKeepsTakingHeat() { stirlingCase(3000, true); }
    @Test(timeout=3000) public void stirlingKeepsTakingHeatWithFullConsumer() { stirlingCase(4000, true); }
    @Test(timeout=3000) public void stirlingKeepsTakingHeatWithoutConsumer() { stirlingCase(0, false); }
    @Test(timeout=3000) public void fullStirlingBufferRejectsEnergyWithoutHiddenAccumulation() {
        Heavy heavy = new Heavy(); Heat heat = new Heat(); heavy.powerBuffer = 8000;
        for(long tick = 1; tick <= 1000; tick++) heavy.heat(heat, tick);
        assertEquals(20000000, heat.drawn); assertEquals(8000, heavy.powerBuffer);
    }
    @Test(timeout=3000) public void nativeHeavyStirlingFeedsNativeElectricFurnaceThenSurvivesSaturation() {
        Heavy heavy = new Heavy(); Heat heat = new Heat(); TileEntityMachineElectricFurnace furnace = new TileEntityMachineElectricFurnace();
        PowerNetMK2 net = network(); net.addProvider(heavy); net.addReceiver(furnace);
        for(long tick = 1; tick <= 12000; tick++) { heavy.heat(heat, tick); net.update(); }
        assertEquals(furnace.getEnergyCapacityQuanta(), furnace.energyQuanta); assertEquals(8000, heavy.powerBuffer);
        assertEquals(240000000, heat.drawn);
        net.removeReceiver(furnace); heavy.heat(heat, 12001); net.update();
        furnace.energyQuanta = 0; net.addReceiver(furnace); heavy.heat(heat, 12002); net.update();
        assertEquals(furnace.getEnergyCapacityQuanta(), furnace.energyQuanta);
    }
    @Test(timeout=3000) public void repeatedNetworkPassesCannotSpendTheGeneratorBudgetTwice() {
        Heavy heavy = new Heavy(); Heat heat = new Heat(); heavy.powerBuffer = 8000; heavy.heat(heat, 1);
        PowerNetMK2 a = network(), b = network(); Receiver r = new Receiver(10000), s = new Receiver(10000);
        a.addProvider(heavy); b.addProvider(heavy); a.addReceiver(r); b.addReceiver(s);
        a.update(); b.update(); a.update(); assertEquals(4000, r.stored + s.stored); assertEquals(4000, heavy.powerBuffer);
    }
    @Test public void receiverSettlesOldWorkBeforeComputingTheNewStock() {
        Receiver r = new Receiver(100) {
            boolean pending = true;
            @Override public void prepareEnergyTransfer() { if(pending) { stored -= 30; pending = false; } }
        };
        r.stored = 70; assertEquals(0, r.receiveEnergyQuanta(20)); assertEquals(60, r.stored);
    }
    @Test public void machineLifetimeLedgerCanBeReflectivelyReloadedByVanillaMapStorage() throws Exception {
        Class<?> type = Class.forName("com.hbm.machine.MachineRuntimeSavedData");
        net.minecraft.world.WorldSavedData data = (net.minecraft.world.WorldSavedData) type.getConstructor(String.class).newInstance("hbm_machine_runtime");
        net.minecraft.nbt.NBTTagCompound saved = new net.minecraft.nbt.NBTTagCompound(); saved.setLong("nextGeneration", 9876);
        data.readFromNBT(saved); net.minecraft.nbt.NBTTagCompound written = new net.minecraft.nbt.NBTTagCompound(); data.writeToNBT(written);
        assertEquals(9876, written.getLong("nextGeneration"));
    }
    @Test(timeout=5000) public void diagnosticGuardStopsRunawayCallbackWorkAndKeepsAReport() {
        Object owner = new Object(); ElectricalWorkGuard guard = ElectricalWorkGuard.enter(owner);
        int visits = 0;
        try { while(guard.visit(owner)) visits++; }
        finally { guard.close(); }
        assertTrue(visits <= 1000000);
        assertTrue(ElectricalWorkGuard.lastAbnormalReport().contains("electrical work/time guard"));
        assertTrue(ElectricalWorkGuard.lastAbnormalReport().contains("remainingBudget="));
    }
    @Test public void negativeEndpointStockCannotCreateSupplyOrDemand() {
        PowerNetMK2 net = network(); Provider p = new Provider(-1, 100); Receiver r = new Receiver(100);
        net.addProvider(p); net.addReceiver(r); net.update(); assertEquals(0, r.stored);
        p.stored = 100; r.stored = -1; net.update(); assertEquals(100, p.stored); assertEquals(-1, r.stored);
    }
    @Test public void overCapFurnaceStockDrainsAndUpgradesHaveEnoughWorkingRoom() {
        TileEntityMachineElectricFurnace furnace = new TileEntityMachineElectricFurnace(); furnace.energyQuanta = 100000;
        assertEquals(1600, furnace.getEnergyCapacityQuanta()); assertEquals(100, furnace.receiveEnergyQuanta(100));
        assertEquals(100000, furnace.energyQuanta); assertEquals(52, furnace.getPowerScaled(52));
        furnace.operatingPowerWatts = 32000; assertEquals(6400, furnace.getEnergyCapacityQuanta());
    }
    private static void field(Object owner, String name, Object value) throws Exception {
        java.lang.reflect.Field field = owner.getClass().getDeclaredField(name); field.setAccessible(true); field.set(owner, value);
    }
    private static Object field(Object owner, String name) throws Exception {
        java.lang.reflect.Field field = owner.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(owner);
    }
    @Test(timeout=3000) public void twoTickFurnaceReserveCompletesPaidJobsAcrossRefillBoundaries() throws Exception {
        net.minecraft.item.Item input = new net.minecraft.item.Item(), output = new net.minecraft.item.Item();
        net.minecraft.item.ItemStack key = new net.minecraft.item.ItemStack(input);
        java.util.Map recipes = net.minecraft.item.crafting.FurnaceRecipes.smelting().getSmeltingList();
        recipes.put(key, new net.minecraft.item.ItemStack(output));
        try {
            TileEntityMachineElectricFurnace furnace = new TileEntityMachineElectricFurnace();
            furnace.slots[1] = new net.minecraft.item.ItemStack(input, 2); furnace.energyQuanta = 1600;
            field(furnace, "lastAccountingTick", 0L); field(furnace, "cachedRecipeEligible", true);
            Method settle = furnace.getClass().getDeclaredMethod("settleAccountingThrough", long.class); settle.setAccessible(true);
            Method schedule = furnace.getClass().getDeclaredMethod("scheduleNextBoundary", long.class); schedule.setAccessible(true);
            schedule.invoke(furnace, 0L); assertEquals(2L, field(furnace, "nextRuntimeTick"));
            long supplied = 1600;
            for(long tick=1;tick<=100;tick++) {
                settle.invoke(furnace, tick); schedule.invoke(furnace, tick);
                if(tick < 100) assertEquals((int) tick, furnace.progress);
                long offer = furnace.getEnergyCapacityQuanta() - furnace.energyQuanta;
                supplied += offer - furnace.receiveEnergyQuanta(offer);
            }
            assertNotNull(furnace.slots[2]); assertSame(output, furnace.slots[2].getItem()); assertEquals(1, furnace.slots[2].stackSize);
            assertEquals(80000, supplied - furnace.energyQuanta);
            furnace.energyQuanta = 0; furnace.progress = 2;
            schedule.invoke(furnace, 100L); assertEquals(2, furnace.progress); assertEquals(101L, field(furnace, "nextRuntimeTick"));
            settle.invoke(furnace, 101L); assertEquals(0, furnace.progress);
        } finally { recipes.remove(key); }
    }
    @Test(timeout=3000) public void furnaceContainerConstructionAndSyncNeverCalculateTheNetwork() {
        Heavy heavy = new Heavy(); Heat heat = new Heat(); TileEntityMachineElectricFurnace furnace = new TileEntityMachineElectricFurnace();
        PowerNetMK2 net = network(); net.addProvider(heavy); net.addReceiver(furnace);
        heavy.heat(heat, 1); net.update(); long power = heavy.powerBuffer, consumedHeat = heat.drawn;
        for(int i=0;i<100;i++) {
            com.hbm.inventory.container.ContainerElectricFurnace container = new com.hbm.inventory.container.ContainerElectricFurnace(
                    new net.minecraft.entity.player.InventoryPlayer(null), furnace);
            container.detectAndSendChanges();
            assertEquals(40, container.inventorySlots.size());
        }
        assertEquals(power, heavy.powerBuffer); assertEquals(consumedHeat, heat.drawn);
        furnace.operatingPowerWatts = 32000;
        io.netty.buffer.ByteBuf packet = io.netty.buffer.Unpooled.buffer();
        try {
            furnace.serialize(packet); TileEntityMachineElectricFurnace client = new TileEntityMachineElectricFurnace(); client.deserialize(packet);
            assertEquals(furnace.energyQuanta, client.energyQuanta);
            assertEquals(furnace.getEnergyCapacityQuanta(), client.getEnergyCapacityQuanta());
        } finally { packet.release(); }
    }
    @Test(timeout=3000) public void cyclicCableSplitAndRepeatedRebuildsHaveBoundedWork() throws Exception {
        UniNodespace.UniNodeWorld nodeWorld = new UniNodespace.UniNodeWorld(); UniNodespace.worlds.put(null, nodeWorld);
        PowerNetMK2 net = network(); Nodespace.PowerNode[] nodes = new Nodespace.PowerNode[4];
        int[][] positions = {{0,0},{1,0},{1,1},{0,1}};
        for(int i=0;i<4;i++) {
            int x=positions[i][0], z=positions[i][1];
            nodes[i] = new Nodespace.PowerNode(new BlockPos(x, 64, z)); nodes[i].setStandardConnections(x, 64, z);
            nodeWorld.pushNode(nodes[i]); net.joinLink(nodes[i]);
        }
        Method process = UniNodespace.UniNodeWorld.class.getDeclaredMethod("processTopology", net.minecraft.world.World.class, int.class); process.setAccessible(true);
        for(int tick=0;tick<100;tick++) {
            for(Nodespace.PowerNode node : nodes) UniNodespace.markNodeDirty(null, node);
            process.invoke(nodeWorld, null, 2); process.invoke(nodeWorld, null, 2);
        }
        nodeWorld.popNode(null, nodes[0]);
        for(int tick=0;tick<20;tick++) process.invoke(nodeWorld, null, 1);
        assertFalse(net.isTopologyRepairing()); assertEquals(3, net.links.size());
        for(int i=1;i<4;i++) assertSame(net, nodes[i].net);
    }
}
