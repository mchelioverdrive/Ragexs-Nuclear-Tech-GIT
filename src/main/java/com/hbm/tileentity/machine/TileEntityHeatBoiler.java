package com.hbm.tileentity.machine;

import java.io.IOException;

import com.google.gson.JsonObject;
import com.google.gson.stream.JsonWriter;
import com.hbm.blocks.BlockDummyable;
import com.hbm.explosion.vanillant.ExplosionVNT;
import com.hbm.explosion.vanillant.standard.EntityProcessorStandard;
import com.hbm.explosion.vanillant.standard.ExplosionEffectStandard;
import com.hbm.explosion.vanillant.standard.PlayerProcessorStandard;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.lib.Library;
import com.hbm.tileentity.IConfigurableMachine;
import com.hbm.util.fauxpointtwelve.DirPos;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.common.util.ForgeDirection;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class TileEntityHeatBoiler extends TileEntityHeatBoilerBase {
	/* CONFIGURABLE */
	public static int maxHeat = 3_200_000;
	public static double diffusion = 0.1D;
	public static boolean canExplode = true;

	public boolean hasExploded;
	private AxisAlignedBB renderBounds;

	public TileEntityHeatBoiler() {
		tanks = new FluidTank[2];
		tanks[0] = new FluidTank(Fluids.FRESH_WATER, 16_000).migrateFrom(Fluids.WATER);
		tanks[1] = new FluidTank(Fluids.STEAM, 16_000 * 100);
	}

	@Override protected int getMaxBoilerHeat() { return maxHeat; }
	@Override protected double getBoilerDiffusion() { return diffusion; }
	@Override protected boolean isBoilerDisabled() { return hasExploded; }
	@Override protected boolean shouldExplodeOnBlockedOutput() { return canExplode; }

	@Override protected DirPos[] getBoilerConnections() {
		ForgeDirection dir = ForgeDirection.getOrientation(this.getBlockMetadata() - BlockDummyable.offset).getRotation(ForgeDirection.UP);
		return new DirPos[] {
				new DirPos(xCoord + dir.offsetX * 2, yCoord, zCoord + dir.offsetZ * 2, dir),
				new DirPos(xCoord - dir.offsetX * 2, yCoord, zCoord - dir.offsetZ * 2, dir.getOpposite()),
				new DirPos(xCoord, yCoord + 4, zCoord, Library.POS_Y),
		};
	}

	@Override protected void handleBlockedOutput() {
		hasExploded = true;
		BlockDummyable.safeRem = true;
		try {
			for(int x = xCoord - 1; x <= xCoord + 1; x++) {
				for(int y = yCoord + 2; y <= yCoord + 3; y++) {
					for(int z = zCoord - 1; z <= zCoord + 1; z++) worldObj.setBlockToAir(x, y, z);
				}
			}
			worldObj.setBlockToAir(xCoord, yCoord + 1, zCoord);
			ExplosionVNT explosion = new ExplosionVNT(worldObj, xCoord + 0.5, yCoord + 2, zCoord + 0.5, 5F);
			explosion.setEntityProcessor(new EntityProcessorStandard().withRangeMod(3F));
			explosion.setPlayerProcessor(new PlayerProcessorStandard());
			explosion.setSFX(new ExplosionEffectStandard());
			explosion.explode();
		} finally {
			BlockDummyable.safeRem = false;
		}
	}

	@Override protected void writeExtraPacketData(NBTTagCompound data) {
		data.setBoolean("exploded", this.hasExploded);
	}

	@Override public void networkUnpack(NBTTagCompound nbt) {
		super.networkUnpack(nbt);
		this.hasExploded = nbt.getBoolean("exploded");
	}

	@Override public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		this.hasExploded = nbt.getBoolean("exploded");
	}

	@Override public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		nbt.setBoolean("exploded", this.hasExploded);
	}

	@Override public String getConfigName() { return "boiler"; }

	@Override public void readIfPresent(JsonObject obj) {
		maxHeat = IConfigurableMachine.grab(obj, "I:maxHeat", maxHeat);
		diffusion = IConfigurableMachine.grab(obj, "D:diffusion", diffusion);
		canExplode = IConfigurableMachine.grab(obj, "B:canExplode", canExplode);
	}

	@Override public void writeConfig(JsonWriter writer) throws IOException {
		writer.name("I:maxHeat").value(maxHeat);
		writer.name("D:diffusion").value(diffusion);
		writer.name("B:canExplode").value(canExplode);
	}

	@Override public AxisAlignedBB getRenderBoundingBox() {
		if(renderBounds == null) renderBounds = AxisAlignedBB.getBoundingBox(xCoord - 1, yCoord, zCoord - 1, xCoord + 2, yCoord + 4, zCoord + 2);
		return renderBounds;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public double getMaxRenderDistanceSquared() { return 65536.0D; }
}
