package api.hbm.energymk2;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

/** Owning-thread circuit breaker shared by nested networks and direct diode chains. */
public final class ElectricalWorkGuard implements AutoCloseable {
    private static final long MAX_NANOS = 250000000L;
    private static final int MAX_VISITS = 1000000;
    private static final int MAX_DEPTH = 128;
    private static final ThreadLocal<Context> CURRENT = new ThreadLocal<Context>();
    private static final ThreadLocal<TickBudget> TICK_BUDGET = new ThreadLocal<TickBudget>();
    private static long lastWarning;
    private static String lastAbnormal = "No abnormal electrical distribution recorded";
    private final Context context;
    private final Object owner;
    private final boolean root;
    private boolean closed;

    private static final class Context {
        final long started = System.nanoTime();
        final Set<Object> active = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
        int visits, attempts, repeats;
        long requested, accepted;
        Object source, destination;
        String failure;
        boolean halted;
        TickBudget tickBudget;
        long budget, delivered;
    }
    private static final class TickBudget {
        long tick, nanos;
        int visits;
        TickBudget(long tick) { this.tick = tick; }
    }

    private ElectricalWorkGuard(Context context, Object owner, boolean root) {
        this.context = context; this.owner = owner; this.root = root;
    }

    public static ElectricalWorkGuard enter(Object owner) {
        Context context = CURRENT.get();
        boolean root = context == null;
        if(root) {
            context = new Context(); CURRENT.set(context);
            World world = owner instanceof PowerNetMK2 ? ((PowerNetMK2) owner).getWorld()
                    : owner instanceof TileEntity ? ((TileEntity) owner).getWorldObj() : null;
            if(world != null) {
                TickBudget budget = TICK_BUDGET.get();
                if(budget == null || budget.tick != world.getTotalWorldTime()) {
                    budget = new TickBudget(world.getTotalWorldTime()); TICK_BUDGET.set(budget);
                }
                context.tickBudget = budget;
            }
        }
        if(context.active.contains(owner) || context.active.size() >= MAX_DEPTH) {
            context.repeats++;
            context.failure = "cyclic/reentrant electrical path or excessive depth at " + describe(owner);
            return null;
        }
        context.active.add(owner);
        return new ElectricalWorkGuard(context, owner, root);
    }

    /** Checks between callbacks; it cannot interrupt a mod callback blocked inside its own code. */
    public boolean visit(Object endpoint) {
        if(context.halted) return false;
        context.destination = endpoint;
        int previousVisits = context.tickBudget == null ? 0 : context.tickBudget.visits;
        long previousNanos = context.tickBudget == null ? 0 : context.tickBudget.nanos;
        if(++context.visits + previousVisits <= MAX_VISITS && System.nanoTime() - context.started + previousNanos <= MAX_NANOS) return true;
        context.failure = "electrical work/time guard";
        context.halted = true;
        return false;
    }

    public void source(Object source) { context.source = source; }
    public boolean halted() { return context.halted; }
    public void budget(long amount) { if(root) context.budget = amount; }
    public void delivered(long amount) { if(root) context.delivered = amount; }
    public void offered(long requested, long accepted) {
        context.attempts++;
        context.requested = add(context.requested, requested);
        context.accepted = add(context.accepted, accepted);
    }
    public void invalid(String reason) { context.failure = reason; }
    public static long add(long a, long b) { return a > Long.MAX_VALUE - b ? Long.MAX_VALUE : a + b; }
    public static long room(IEnergyReceiverMK2 receiver) {
        receiver.prepareEnergyTransfer();
        long stored = receiver.getStoredEnergyQuanta(), capacity = receiver.getEnergyCapacityQuanta();
        if(stored < 0 || capacity <= stored) return 0;
        return Math.max(0, Math.min(capacity - stored, receiver.getMaxInputQuantaPerTick()));
    }
    /** Receivers return the remainder, not the accepted quantity. Never retry a rejection. */
    public long offer(IEnergyReceiverMK2 receiver, long requested) {
        if(requested <= 0 || !visit(receiver)) return 0;
        long remainder = receiver.receiveEnergyQuanta(requested);
        if(remainder < 0 || remainder > requested) {
            invalid("invalid receiver remainder " + remainder + " for " + requested);
            remainder = Math.max(0, Math.min(requested, remainder));
        }
        long accepted = requested - remainder;
        offered(requested, accepted);
        return accepted;
    }

    public static String describe(Object object) {
        if(object == null) return "none";
        String name = object.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(object));
        if(object instanceof TileEntity) {
            TileEntity tile = (TileEntity) object;
            name += "[dim=" + (tile.getWorldObj() == null ? "none" : tile.getWorldObj().provider.dimensionId)
                    + ",pos=" + tile.xCoord + "," + tile.yCoord + "," + tile.zCoord + "]";
        }
        return name;
    }

    @Override public void close() {
        if(closed) return;
        closed = true;
        context.active.remove(owner);
        if(!root) return;
        CURRENT.remove();
        long elapsed = System.nanoTime() - context.started;
        if(context.tickBudget != null) {
            context.tickBudget.nanos += elapsed;
            context.tickBudget.visits += context.visits;
        }
        if(context.failure == null && elapsed <= 50000000L) return;
        String topology = owner instanceof PowerNetMK2 ? ((PowerNetMK2) owner).describeTopology() : describe(owner);
        lastAbnormal = (context.failure == null ? "slow electrical distribution" : context.failure) + "; " + topology
                + "; source=" + describe(context.source) + "; destination=" + describe(context.destination)
                + "; offeredQuanta=" + context.requested + "; accepted=" + context.accepted
                + "; rejected=" + Math.max(0, context.requested - context.accepted)
                + "; sourceBudget=" + context.budget + "; remainingBudget=" + Math.max(0, context.budget - context.delivered)
                + "; visited=" + context.visits + "; attempts=" + context.attempts
                + "; repeatedPaths=" + context.repeats + "; elapsedMs=" + elapsed / 1000000L;
        long now = System.nanoTime();
        if(lastWarning == 0 || now - lastWarning >= 10000000000L) {
            lastWarning = now;
            LogManager.getLogger("HBM-Electrical").warn(lastAbnormal);
        }
    }
    public static String lastAbnormalReport() { return lastAbnormal; }
}
