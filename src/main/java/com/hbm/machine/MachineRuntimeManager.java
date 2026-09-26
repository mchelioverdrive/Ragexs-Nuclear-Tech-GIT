package com.hbm.machine;

import java.util.IdentityHashMap;
import java.util.Map;

import com.hbm.tileentity.TileEntityLoadedBase;

import net.minecraft.world.World;

/** World lifecycle boundary and public entry point for machine runtime operations. */
public final class MachineRuntimeManager {

	private static final Map<World, MachineRuntime> RUNTIMES = new IdentityHashMap<World, MachineRuntime>();

	private MachineRuntimeManager() { }

	public static void onWorldLoad(World world) {
		if(world != null && !world.isRemote) getOrCreate(world);
	}

	public static void onWorldUnload(World world) {
		if(world == null || world.isRemote) return;
		MachineRuntime runtime = RUNTIMES.remove(world);
		if(runtime != null) runtime.unload();
	}

	public static void tick(World world) {
		if(world == null || world.isRemote) return;
		getOrCreate(world).tick();
	}

	public static void bind(TileEntityLoadedBase tile) {
		if(isEligible(tile)) getOrCreate(tile.getWorldObj()).bind(tile);
	}

	public static void unbind(TileEntityLoadedBase tile) {
		MachineRuntime runtime = runtime(tile);
		if(runtime != null) runtime.unbind(tile);
	}

	public static void remove(TileEntityLoadedBase tile) {
		MachineRuntime runtime = runtime(tile);
		if(runtime != null) runtime.remove(tile);
	}

	public static void beginRetainedTransition(TileEntityLoadedBase tile) {
		MachineRuntime runtime = runtime(tile);
		if(runtime != null) runtime.beginRetainedTransition(tile);
	}

	public static void endRetainedTransition(TileEntityLoadedBase tile) {
		MachineRuntime runtime = runtime(tile);
		if(runtime != null) runtime.endRetainedTransition(tile);
	}

	public static void markDirty(TileEntityLoadedBase tile, int causes) {
		MachineRuntime runtime = runtime(tile);
		if(runtime != null) runtime.markDirty(tile, causes);
	}

	public static boolean schedule(TileEntityLoadedBase tile, long dueTick, int taskType, int taskSlot) {
		MachineRuntime runtime = runtime(tile);
		return runtime != null && runtime.schedule(tile, dueTick, taskType, taskSlot);
	}

	public static boolean cancel(TileEntityLoadedBase tile, int taskType, int taskSlot) {
		MachineRuntime runtime = runtime(tile);
		return runtime != null && runtime.cancel(tile, taskType, taskSlot);
	}

	public static String[] getReport(World world) {
		if(world == null || world.isRemote) return new String[] { "Machine runtime is only available for server worlds." };
		return getOrCreate(world).getReport();
	}

	private static boolean isEligible(TileEntityLoadedBase tile) {
		return tile != null && tile.getWorldObj() != null && !tile.getWorldObj().isRemote && tile.getMachineExecutionStrategies() != MachineExecutionStrategy.LEGACY;
	}

	private static MachineRuntime runtime(TileEntityLoadedBase tile) {
		if(tile == null || tile.getWorldObj() == null || tile.getWorldObj().isRemote) return null;
		return RUNTIMES.get(tile.getWorldObj());
	}

	private static MachineRuntime getOrCreate(World world) {
		MachineRuntime runtime = RUNTIMES.get(world);
		if(runtime == null) {
			runtime = new MachineRuntime(world);
			RUNTIMES.put(world, runtime);
		}
		return runtime;
	}
}
