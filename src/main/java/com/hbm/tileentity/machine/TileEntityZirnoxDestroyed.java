package com.hbm.tileentity.machine;

import java.util.List;

import com.hbm.util.ContaminationUtil;
import com.hbm.util.ContaminationUtil.ContaminationType;
import com.hbm.util.ContaminationUtil.HazardType;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

public class TileEntityZirnoxDestroyed extends TileEntity {
	
	// Compatibility field only: no oxidant/fire simulation exists for this reactor.
	public boolean onFire = false;
	public double contaminationScale = 1.0D;
	public String terminalReason = "legacy";
	
	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		onFire = false;
		contaminationScale = nbt.hasKey("contaminationScale") ? Math.max(0, Math.min(1, nbt.getDouble("contaminationScale"))) : 1.0D;
		terminalReason = nbt.hasKey("terminalReason") ? nbt.getString("terminalReason") : "legacy";
	}
	
	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		nbt.setBoolean("onFire", onFire);
		nbt.setDouble("contaminationScale", contaminationScale);
		nbt.setString("terminalReason", terminalReason);
	}
	
	@Override
	public void updateEntity() {
		if(!worldObj.isRemote) {
			radiate(worldObj, this.xCoord, this.yCoord, this.zCoord);
		}
	}

	private void radiate(World world, int x, int y, int z) {

		if(contaminationScale <= 0) return;
		float rads = (float)(75000F * contaminationScale);
		double range = 100D;

		List<EntityLivingBase> entities = world.getEntitiesWithinAABB(EntityLivingBase.class, AxisAlignedBB.getBoundingBox(x + 0.5, y + 0.5, z + 0.5, x + 0.5, y + 0.5, z + 0.5).expand(range, range, range));

		for(EntityLivingBase e : entities) {

			Vec3 vec = Vec3.createVectorHelper(e.posX - (x + 0.5), (e.posY + e.getEyeHeight()) - (y + 0.5), e.posZ - (z + 0.5));
			double len = vec.lengthVector();
			vec = vec.normalize();

			float res = 0;

			for(int i = 1; i < len; i++) {

				int ix = (int)Math.floor(x + 0.5 + vec.xCoord * i);
				int iy = (int)Math.floor(y + 0.5 + vec.yCoord * i);
				int iz = (int)Math.floor(z + 0.5 + vec.zCoord * i);

				res += world.getBlock(ix, iy, iz).getExplosionResistance(null);
			}

			if(res < 1)
				res = 1;

			float eRads = rads;
			eRads /= (float)res;
			eRads /= (float)Math.max(1.0D, len * len);

			ContaminationUtil.contaminate(e, HazardType.RADIATION, ContaminationType.CREATIVE, eRads);

		}
	}

	public AxisAlignedBB getRenderBoundingBox() {
		return AxisAlignedBB.getBoundingBox(xCoord - 3, yCoord, zCoord - 3, xCoord + 4, yCoord + 3, zCoord + 4);
	}

	@SideOnly(Side.CLIENT)
	public double getMaxRenderDistanceSquared() {
		return 65536.0D;
	}
}
