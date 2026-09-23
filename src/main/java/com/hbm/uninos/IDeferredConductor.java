package com.hbm.uninos;

/** A conductor whose live node may only be reconciled after its chunk has loaded. */
public interface IDeferredConductor {

	void reconcileConductorNode();
}
