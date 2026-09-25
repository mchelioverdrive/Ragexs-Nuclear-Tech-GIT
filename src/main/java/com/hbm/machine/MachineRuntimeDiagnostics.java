package com.hbm.machine;

import com.hbm.config.GeneralConfig;

/** Disabled-by-default aggregate counters for the world-scoped machine runtime. */
final class MachineRuntimeDiagnostics {

	long registrations;
	long binds;
	long unbinds;
	long removals;
	long reloadRebindings;
	long dirtySignals;
	long dirtyProcessed;
	long maxDirtyDepth;
	long scheduledCreated;
	long scheduledExecuted;
	long scheduledCancelled;
	long staleRejected;
	long coarsePolls;

	static boolean isEnabled() {
		return GeneralConfig.enableMachineRuntimeDiagnostics;
	}

	void registration() { if(isEnabled()) registrations++; }
	void bind() { if(isEnabled()) binds++; }
	void unbind() { if(isEnabled()) unbinds++; }
	void removal() { if(isEnabled()) removals++; }
	void reloadRebinding() { if(isEnabled()) reloadRebindings++; }
	void dirtySignal(int depth) { if(isEnabled()) { dirtySignals++; maxDirtyDepth = Math.max(maxDirtyDepth, depth); } }
	void dirtyProcessed() { if(isEnabled()) dirtyProcessed++; }
	void scheduledCreated() { if(isEnabled()) scheduledCreated++; }
	void scheduledExecuted() { if(isEnabled()) scheduledExecuted++; }
	void scheduledCancelled() { if(isEnabled()) scheduledCancelled++; }
	void staleRejected() { if(isEnabled()) staleRejected++; }
	void coarsePoll() { if(isEnabled()) coarsePolls++; }
}
