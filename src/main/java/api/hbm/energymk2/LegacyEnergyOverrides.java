package api.hbm.energymk2;

/** Cached dispatch information for addons compiled against the former HE methods. */
final class LegacyEnergyOverrides {

	private static final ClassValue<LegacyEnergyOverrides> CACHE = new ClassValue<LegacyEnergyOverrides>() {
		@Override protected LegacyEnergyOverrides computeValue(Class<?> type) {
			return new LegacyEnergyOverrides(type);
		}
	};

	final boolean receiverTransfer;
	final boolean receiverSpeed;
	final boolean providerExtract;
	final boolean providerSpeed;

	private LegacyEnergyOverrides(Class<?> type) {
		this.receiverTransfer = overrides(type, IEnergyReceiverMK2.class, "transferPower", long.class);
		this.receiverSpeed = overrides(type, IEnergyReceiverMK2.class, "getReceiverSpeed");
		this.providerExtract = overrides(type, IEnergyProviderMK2.class, "usePower", long.class);
		this.providerSpeed = overrides(type, IEnergyProviderMK2.class, "getProviderSpeed");
	}

	static LegacyEnergyOverrides of(Class<?> type) { return CACHE.get(type); }

	private static boolean overrides(Class<?> type, Class<?> owner, String name, Class<?>... parameters) {
		try {
			return type.getMethod(name, parameters).getDeclaringClass() != owner;
		} catch(NoSuchMethodException ignored) {
			return false;
		}
	}
}
