package api.hbm.energymk2;

import java.util.Iterator;

import com.hbm.uninos.NodeNet;

import java.util.Map.Entry;

import api.hbm.energymk2.IEnergyReceiverMK2.ConnectionPriority;
import api.hbm.energymk2.Nodespace.PowerNode;

/**
 * Technically MK3 since it's now UNINOS compatible, although UNINOS was build out of 95% nodespace code
 *
 * @author hbm
 */
public class PowerNetMK2 extends NodeNet<IEnergyReceiverMK2, IEnergyProviderMK2, PowerNode> {

	public long energyTracker = 0L;

	protected static int timeout = 3_000;
	private static final ConnectionPriority[] PRIORITIES = ConnectionPriority.values();

	private final EndpointScratch<IEnergyProviderMK2> providerScratch = new EndpointScratch<IEnergyProviderMK2>();
	private final EndpointScratch<IEnergyReceiverMK2>[] receiverScratch = createReceiverScratch();
	private final EndpointScratch<IEnergyReceiverMK2>[] diodeReceiverScratch = createReceiverScratch();
	private final long[] demandScratch = new long[PRIORITIES.length];
	private final long[] diodeDemandScratch = new long[PRIORITIES.length];

	@Override public void resetTrackers() { this.energyTracker = 0; }

	@Override
	public void update() {
		this.providerScratch.clear();
		clearReceiverScratch(this.receiverScratch, this.demandScratch);

		if(providerEntries.isEmpty()) return;
		if(receiverEntries.isEmpty()) return;

		long timestamp = System.currentTimeMillis();

		long powerAvailable = 0;

		// sum up available power
		Iterator<Entry<IEnergyProviderMK2, Long>> provIt = providerEntries.entrySet().iterator();
		while(provIt.hasNext()) {
			Entry<IEnergyProviderMK2, Long> entry = provIt.next();
			if(timestamp - entry.getValue() > timeout || isBadLink(entry.getKey())) { provIt.remove(); continue; }
			IEnergyProviderMK2 provider = entry.getKey();
			long src = Math.min(provider.getPower(), provider.getProviderSpeed());
			if(src > 0) {
				this.providerScratch.add(provider, src);
				powerAvailable += src;
			}
		}

		// sum up total demand, categorized by priority
		long totalDemand = 0;

		Iterator<Entry<IEnergyReceiverMK2, Long>> recIt = receiverEntries.entrySet().iterator();

		while(recIt.hasNext()) {
			Entry<IEnergyReceiverMK2, Long> entry = recIt.next();
			if(timestamp - entry.getValue() > timeout || isBadLink(entry.getKey())) { recIt.remove(); continue; }
			IEnergyReceiverMK2 receiver = entry.getKey();
			long rec = Math.min(receiver.getMaxPower() - receiver.getPower(), receiver.getReceiverSpeed());
			if(rec > 0) {
				int p = receiver.getPriority().ordinal();
				this.receiverScratch[p].add(receiver, rec);
				this.demandScratch[p] += rec;
				totalDemand += rec;
			}
		}

		long toTransfer = Math.min(powerAvailable, totalDemand);
		long energyUsed = 0;

		// add power to receivers, ordered by priority
		for(int i = PRIORITIES.length - 1; i >= 0; i--) {
			EndpointScratch<IEnergyReceiverMK2> list = this.receiverScratch[i];
			long priorityDemand = this.demandScratch[i];

			for(int j = 0; j < list.size; j++) {
				long requested = list.amounts[j];
				double weight = (double) requested / (double) priorityDemand;
				long toSend = (long) Math.min(Math.max(toTransfer * weight, 0D), requested);
				energyUsed += (toSend - list.get(j).transferPower(toSend)); //leftovers are subtracted from the intended amount to use up
			}

			toTransfer -= energyUsed;
		}

		this.energyTracker += energyUsed;
		long leftover = energyUsed;

		// remove power from providers
		for(int i = 0; i < this.providerScratch.size; i++) {
			double weight = (double) this.providerScratch.amounts[i] / (double) powerAvailable;
			long toUse = (long) Math.max(energyUsed * weight, 0D);
			this.providerScratch.get(i).usePower(toUse);
			leftover -= toUse;
		}

		// rounding error compensation, detects surplus that hasn't been used and removes it from random providers
		int iterationsLeft = 100; // whiles without emergency brakes are a bad idea
		while(iterationsLeft > 0 && leftover > 0 && this.providerScratch.size > 0) {
			iterationsLeft--;

			IEnergyProviderMK2 scapegoat = this.providerScratch.get(rand.nextInt(this.providerScratch.size));

			long toUse = Math.min(leftover, scapegoat.getPower());
			scapegoat.usePower(toUse);
			leftover -= toUse;
		}
	}

	public long sendPowerDiode(long power) {
		clearReceiverScratch(this.diodeReceiverScratch, this.diodeDemandScratch);

		if(receiverEntries.isEmpty()) return power;

		long timestamp = System.currentTimeMillis();

		long totalDemand = 0;

		Iterator<Entry<IEnergyReceiverMK2, Long>> recIt = receiverEntries.entrySet().iterator();

		while(recIt.hasNext()) {
			Entry<IEnergyReceiverMK2, Long> entry = recIt.next();
			if(timestamp - entry.getValue() > timeout) { recIt.remove(); continue; }
			IEnergyReceiverMK2 receiver = entry.getKey();
			long rec = Math.min(receiver.getMaxPower() - receiver.getPower(), receiver.getReceiverSpeed());
			int p = receiver.getPriority().ordinal();
			this.diodeReceiverScratch[p].add(receiver, rec);
			this.diodeDemandScratch[p] += rec;
			totalDemand += rec;
		}

		long toTransfer = Math.min(power, totalDemand);
		long energyUsed = 0;

		for(int i = PRIORITIES.length - 1; i >= 0; i--) {
			EndpointScratch<IEnergyReceiverMK2> list = this.diodeReceiverScratch[i];
			long priorityDemand = this.diodeDemandScratch[i];

			for(int j = 0; j < list.size; j++) {
				long requested = list.amounts[j];
				double weight = (double) requested / (double) priorityDemand;
				long toSend = (long) Math.max(toTransfer * weight, 0D);
				energyUsed += (toSend - list.get(j).transferPower(toSend)); //leftovers are subtracted from the intended amount to use up
			}

			toTransfer -= energyUsed;
		}

		this.energyTracker += energyUsed;

		return power - energyUsed;
	}

	private static void clearReceiverScratch(EndpointScratch<IEnergyReceiverMK2>[] receivers, long[] demand) {
		for(int i = 0; i < receivers.length; i++) {
			receivers[i].clear();
			demand[i] = 0;
		}
	}

	@SuppressWarnings("unchecked")
	private static EndpointScratch<IEnergyReceiverMK2>[] createReceiverScratch() {
		EndpointScratch<IEnergyReceiverMK2>[] scratch = new EndpointScratch[PRIORITIES.length];
		for(int i = 0; i < scratch.length; i++) scratch[i] = new EndpointScratch<IEnergyReceiverMK2>();
		return scratch;
	}

	private static class EndpointScratch<T> {
		private Object[] endpoints = new Object[4];
		private long[] amounts = new long[4];
		private int size;

		private void clear() {
			for(int i = 0; i < this.size; i++) this.endpoints[i] = null;
			this.size = 0;
		}

		private void add(T endpoint, long amount) {
			if(this.size == this.endpoints.length) {
				int capacity = this.size << 1;
				Object[] grownEndpoints = new Object[capacity];
				long[] grownAmounts = new long[capacity];
				System.arraycopy(this.endpoints, 0, grownEndpoints, 0, this.size);
				System.arraycopy(this.amounts, 0, grownAmounts, 0, this.size);
				this.endpoints = grownEndpoints;
				this.amounts = grownAmounts;
			}
			this.endpoints[this.size] = endpoint;
			this.amounts[this.size] = amount;
			this.size++;
		}

		@SuppressWarnings("unchecked")
		private T get(int index) {
			return (T) this.endpoints[index];
		}
	}
}
