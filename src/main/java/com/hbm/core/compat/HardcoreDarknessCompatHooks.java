package com.hbm.core.compat;

/** Early-load-safe shared state and numeric hooks. Deliberately has no Minecraft or HD references. */
public final class HardcoreDarknessCompatHooks {
	private static final float EPSILON = 0.0001F;
	private static volatile boolean clientEnabled;
	private static volatile boolean detected;
	private static volatile boolean patched;
	private static volatile float skyCarrier = 0.06F;

	private HardcoreDarknessCompatHooks() { }

	public static void configure(boolean enabled, float carrier) {
		clientEnabled = enabled;
		skyCarrier = Math.max(0F, Math.min(0.12F, carrier));
	}

	public static boolean shouldOverrideUp(float value) {
		return clientEnabled && detected && patched && Math.abs(value - 0.95F) < EPSILON;
	}

	public static boolean shouldOverrideDown(float value) {
		return clientEnabled && detected && patched && Math.abs(value - 0.05F) < EPSILON;
	}

	public static float overrideUp(float value) { return 1F - skyCarrier; }
	public static float overrideDown(float value) { return skyCarrier; }
	public static void markDetected() { detected = true; }
	public static void markPatched() { patched = true; }
	public static boolean isDetected() { return detected; }
	public static boolean isCompatEnabled() { return clientEnabled && detected && patched; }
	public static boolean isHookPatched() { return patched; }
	public static float getSkyCarrier() { return skyCarrier; }
	public static float getSkyMultiplierOverride() { return 1F - skyCarrier; }
	public static float getSkyMinimumOverride() { return skyCarrier; }
}
