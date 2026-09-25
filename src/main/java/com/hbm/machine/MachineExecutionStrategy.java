package com.hbm.machine;

/**
 * Composable execution capabilities for the machine runtime. LEGACY remains the
 * default and is deliberately not registered with the logical machine graph.
 */
public final class MachineExecutionStrategy {

	public static final int LEGACY = 0;
	public static final int EVENT_DRIVEN = 1 << 0;
	public static final int SCHEDULED = 1 << 1;
	public static final int COARSE_5 = 1 << 2;
	public static final int COARSE_20 = 1 << 3;
	public static final int COARSE_100 = 1 << 4;
	public static final int REALTIME = 1 << 5;

	public static final int COARSE_MASK = COARSE_5 | COARSE_20 | COARSE_100;

	private MachineExecutionStrategy() { }
}
