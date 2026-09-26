package com.hbm.tileentity.machine;

import java.io.IOException;

import com.google.gson.JsonObject;
import com.google.gson.stream.JsonWriter;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.lib.Library;
import com.hbm.main.MainRegistry;
import com.hbm.sound.AudioWrapper;
import com.hbm.tileentity.IConfigurableMachine;
import com.hbm.util.fauxpointtwelve.DirPos;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;

public class TileEntityHeatBoilerIndustrial extends TileEntityHeatBoilerBase {
	/* CONFIGURABLE */
	public static int maxHeat = 12_800_000;
	public static double diffusion = 0.1D;

	private AudioWrapper audio;
	private int audioTime;
	private AxisAlignedBB renderBounds;

	public TileEntityHeatBoilerIndustrial() {
		tanks = new FluidTank[2];
		tanks[0] = new FluidTank(Fluids.FRESH_WATER, 64_000).migrateFrom(Fluids.WATER);
		tanks[1] = new FluidTank(Fluids.STEAM, 64_000 * 100);
	}

	@Override protected int getMaxBoilerHeat() { return maxHeat; }
	@Override protected double getBoilerDiffusion() { return diffusion; }

	@Override protected DirPos[] getBoilerConnections() {
		return new DirPos[] {
				new DirPos(xCoord + 2, yCoord, zCoord, Library.POS_X),
				new DirPos(xCoord - 2, yCoord, zCoord, Library.NEG_X),
				new DirPos(xCoord, yCoord, zCoord + 2, Library.POS_Z),
				new DirPos(xCoord, yCoord, zCoord - 2, Library.NEG_Z),
				new DirPos(xCoord, yCoord + 5, zCoord, Library.POS_Y),
		};
	}

	@Override protected boolean shouldExplodeOnBlockedOutput() { return false; }
	@Override protected void handleBlockedOutput() { }

	@Override public void updateEntity() {
		if(!worldObj.isRemote) return;
		if(this.isOn) audioTime = 20;
		if(audioTime > 0) {
			audioTime--;
			if(audio == null) {
				audio = createAudioLoop();
				audio.startSound();
			} else if(!audio.isPlaying()) {
				audio = rebootAudio(audio);
			}
			audio.updateVolume(getVolume(1F));
			audio.keepAlive();
		} else if(audio != null) {
			audio.stopSound();
			audio = null;
		}
	}

	@Override public AudioWrapper createAudioLoop() {
		return MainRegistry.proxy.getLoopedSound("hbm:block.boiler", xCoord, yCoord, zCoord, 0.125F, 10F, 1.0F, 20);
	}

	@Override public void onChunkUnload() {
		super.onChunkUnload();
		this.stopAudio();
	}

	@Override public void invalidate() {
		super.invalidate();
		this.stopAudio();
	}

	private void stopAudio() {
		if(audio != null) {
			audio.stopSound();
			audio = null;
		}
	}

	@Override public String getConfigName() { return "boilerIndustrial"; }

	@Override public void readIfPresent(JsonObject obj) {
		maxHeat = IConfigurableMachine.grab(obj, "I:maxHeat", maxHeat);
		diffusion = IConfigurableMachine.grab(obj, "D:diffusion", diffusion);
	}

	@Override public void writeConfig(JsonWriter writer) throws IOException {
		writer.name("I:maxHeat").value(maxHeat);
		writer.name("D:diffusion").value(diffusion);
	}

	@Override public AxisAlignedBB getRenderBoundingBox() {
		if(renderBounds == null) renderBounds = AxisAlignedBB.getBoundingBox(xCoord - 1, yCoord, zCoord - 1, xCoord + 2, yCoord + 5, zCoord + 2);
		return renderBounds;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public double getMaxRenderDistanceSquared() { return 65536.0D; }
}
