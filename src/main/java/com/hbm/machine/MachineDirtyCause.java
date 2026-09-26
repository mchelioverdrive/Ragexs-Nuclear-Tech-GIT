package com.hbm.machine;

/** Bit flags describing why an opted-in logical machine needs reevaluation. */
public final class MachineDirtyCause {

	public static final int INVENTORY = 1 << 0;
	public static final int FLUID = 1 << 1;
	public static final int ENERGY = 1 << 2;
	public static final int CONFIGURATION = 1 << 3;
	public static final int REDSTONE = 1 << 4;
	public static final int TOPOLOGY = 1 << 5;
	public static final int RECIPE = 1 << 6;
	public static final int ENVIRONMENT = 1 << 7;
	public static final int LIFECYCLE = 1 << 8;
	public static final int UPGRADE = 1 << 9;

	private MachineDirtyCause() { }
}
