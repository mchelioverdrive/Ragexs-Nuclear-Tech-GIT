package com.hbm.tileentity.machine;

import java.util.ArrayList;
import java.util.List;

import com.hbm.handler.CompatHandler;
import com.hbm.handler.radiation.ChunkRadiationManager;
import com.hbm.machine.MachineExecutionStrategy;
import com.hbm.tileentity.TileEntityLoadedBase;
import com.hbm.util.CompatEnergyControl;
import com.hbm.util.ContaminationUtil;

import api.hbm.tile.IInfoProviderEC;
import cpw.mods.fml.common.Optional;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.SimpleComponent;
import net.minecraft.nbt.NBTTagCompound;

@Optional.InterfaceList({@Optional.Interface(iface = "li.cil.oc.api.network.SimpleComponent", modid = "OpenComputers")})
public class TileEntityGeiger extends TileEntityLoadedBase implements SimpleComponent, IInfoProviderEC, CompatHandler.OCComponent {

	int timer = 0;
	int ticker = 0;

	@Override
	public int getMachineExecutionStrategies() {
		return MachineExecutionStrategy.COARSE_5;
	}

	@Override
	public void onMachineCoarsePoll(int cadence) {
		if(cadence != 5 || worldObj == null || worldObj.isRemote) return;
		timer += 5;
		if(timer >= 10) {
			timer = 0;
			ticker = check();
		}
		playReadingSound();
	}

	private void playReadingSound() {
		if(ticker > 0) {
			List<Integer> list = new ArrayList<Integer>();
			if(ticker < 1) list.add(0);
			if(ticker < 5) list.add(0);
			if(ticker < 10) list.add(1);
			if(ticker > 5 && ticker < 15) list.add(2);
			if(ticker > 10 && ticker < 20) list.add(3);
			if(ticker > 15 && ticker < 25) list.add(4);
			if(ticker > 20 && ticker < 30) list.add(5);
			if(ticker > 25) list.add(6);
			int sound = list.get(worldObj.rand.nextInt(list.size()));
			if(sound > 0) worldObj.playSoundEffect(xCoord, yCoord, zCoord, "hbm:item.geiger" + sound, 1.0F, 1.0F);
		} else if(worldObj.rand.nextInt(50) == 0) {
			worldObj.playSoundEffect(xCoord, yCoord, zCoord, "hbm:item.geiger" + (1 + worldObj.rand.nextInt(1)), 1.0F, 1.0F);
		}
	}

	@Override
	public void updateEntity() { }

	public int check() {
		int rads = (int)Math.ceil(ChunkRadiationManager.proxy.getRadiation(worldObj, xCoord, yCoord, zCoord));
		return rads;
	}
	@Override
	@Optional.Method(modid = "OpenComputers")
	public String getComponentName() {
		return "ntm_geiger";
	}

	@Callback(direct = true)
	@Optional.Method(modid = "OpenComputers")
	public Object[] getRads(Context context, Arguments args) {
		return new Object[] {check()};
	}

	@Override
	public void provideExtraInfo(NBTTagCompound data) {
		int rads = check();
		String chunkPrefix = ContaminationUtil.getPreffixFromRad(rads);
		data.setString(CompatEnergyControl.S_CHUNKRAD, chunkPrefix + rads + " mSv/s");
	}
}
