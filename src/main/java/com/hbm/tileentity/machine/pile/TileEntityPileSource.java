package com.hbm.tileentity.machine.pile;

import com.hbm.blocks.ModBlocks;
import com.hbm.machine.MachineExecutionStrategy;

public class TileEntityPileSource extends TileEntityPileBase {
	private static final int TASK_EMIT = 1;

	@Override
	public int getMachineExecutionStrategies() {
		return MachineExecutionStrategy.EVENT_DRIVEN | MachineExecutionStrategy.SCHEDULED;
	}

	@Override
	public void onMachineRuntimeDirty(int causes) {
		if(worldObj != null && !worldObj.isRemote) this.scheduleMachineTransition(worldObj.getTotalWorldTime() + 1L, TASK_EMIT, 0);
	}

	@Override
	public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(taskType != TASK_EMIT || taskSlot != 0 || worldObj == null || worldObj.isRemote) return;
		this.emitNeutrons();
		this.scheduleMachineTransition(worldObj.getTotalWorldTime() + 1L, TASK_EMIT, 0);
	}

	private void emitNeutrons() {

		if(!worldObj.isRemote) {

			int n =
				this.getBlockType() ==
					ModBlocks.block_graphite_source
					? 4 : 8;

			int rays = 8 + worldObj.rand.nextInt(5);

			for(int i = 0; i < rays; i++) {
				this.castRay(n, 5);
			}
		}
	}
}
