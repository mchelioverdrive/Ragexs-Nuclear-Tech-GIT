package com.hbm.tileentity.machine;

import java.util.List;
import java.util.ArrayList;

import com.hbm.blocks.BlockDummyable;
import com.hbm.dim.CelestialBody;
import com.hbm.dim.SolarSystem;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.inventory.fluid.trait.FT_Rocket;
import com.hbm.main.MainRegistry;
import com.hbm.sound.AudioWrapper;
import com.hbm.tileentity.TileEntityMachineBase;
import com.hbm.util.I18nUtil;
import com.hbm.util.fauxpointtwelve.DirPos;

import api.hbm.fluid.IFluidStandardReceiver;
import api.hbm.tile.IPropulsion;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.common.util.ForgeDirection;

public class TileEntityMachineHTR3 extends TileEntityMachineBase implements IPropulsion, IFluidStandardReceiver {

	public FluidTank[] tanks;

	private boolean isOn;
	private float speed;
	public double lastTime;
	public double time;
	private float soundtime;
	private AudioWrapper audio;

	private boolean hasRegistered;

	private int fuelCost;

	public TileEntityMachineHTR3() {
		super(7);
		tanks = new FluidTank[1];
		tanks[0] = new FluidTank(Fluids.WASTEGAS, 1_280_000);
		tanks[0] = new FluidTank(Fluids.GAS_WATZ, 1_280_000);
		tanks[0] = new FluidTank(Fluids.GASEOUS_URANIUM_BROMIDE, 1_280_000);
		tanks[0] = new FluidTank(Fluids.GASEOUS_PLUTONIUM_BROMIDE, 1_280_000);
		tanks[0] = new FluidTank(Fluids.GASEOUS_THORIUM_BROMIDE, 1_280_000);
		tanks[0] = new FluidTank(Fluids.GASEOUS_SCHRABIDIUM_BROMIDE, 1_280_000);
		tanks[0] = new FluidTank(Fluids.SUPERHEATED_HYDROGEN, 1_280_000);
		tanks[0] = new FluidTank(Fluids.NONE, 1_280_000);
	}

	@Override
	public void updateEntity() {
		if(!worldObj.isRemote && CelestialBody.inOrbit(worldObj)) {
			if(!hasRegistered) {
				if(isFacingPrograde()) registerPropulsion();
				hasRegistered = true;
			}

			for(DirPos pos : getConPos()) {
				for(FluidTank tank : tanks) {
					trySubscribe(tank.getTankType(), worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
				}
			}

			if(isOn) {
				soundtime++;

				if(soundtime == 1) {
					this.worldObj.playSoundEffect(this.xCoord, this.yCoord, this.zCoord, "hbm:misc.htrstart", 1.5F, 1F);
				} else if(soundtime > 20) {
					soundtime = 20;
				}
			}else {
				soundtime--;

				if(soundtime == 19) {
					this.worldObj.playSoundEffect(this.xCoord, this.yCoord, this.zCoord, "hbm:misc.htrstop", 2.0F, 1F);
				} else if(soundtime <= 0) {
					soundtime = 0;
				}
			}

			networkPackNT(250);
		} else {
			if(isOn) {
				speed += 0.05D;
				if(speed > 1) speed = 1;

				if(soundtime > 18) {
					if(audio == null) {
						audio = createAudioLoop();
						audio.startSound();
					} else if(!audio.isPlaying()) {
						audio = rebootAudio(audio);
					}

					audio.updateVolume(getVolume(1F));
					audio.keepAlive();

					{
						List<FluidType> types = new ArrayList() {{ add(tanks[0].getTankType()); }};

						if(types.contains(Fluids.SUPERHEATED_HYDROGEN)) {

							ForgeDirection dir = ForgeDirection.getOrientation(this.getBlockMetadata() - BlockDummyable.offset).getRotation(ForgeDirection.UP);

							NBTTagCompound data = new NBTTagCompound();
							data.setDouble("posX", xCoord + dir.offsetX * 12);
							data.setDouble("posY", yCoord + 4);
							data.setDouble("posZ", zCoord + dir.offsetZ * 12);
							data.setString("type", "missileContrail");
							data.setFloat("scale", 3);
							data.setDouble("moX", dir.offsetX * 10);
							data.setDouble("moY", 0);
							data.setDouble("moZ", dir.offsetZ * 10);
							data.setInteger("maxAge", 40 + worldObj.rand.nextInt(40));
							MainRegistry.proxy.effectNT(data);
							return;
						}
					}

					{
						List<FluidType> types = new ArrayList() {{ add(tanks[0].getTankType()); }};

						if(types.contains(Fluids.GAS_WATZ) || types.contains(Fluids.WASTEGAS) || types.contains(Fluids.GASEOUS_THORIUM_BROMIDE)) {

							ForgeDirection dir = ForgeDirection.getOrientation(this.getBlockMetadata() - BlockDummyable.offset).getRotation(ForgeDirection.UP);

							NBTTagCompound data = new NBTTagCompound();
							data.setDouble("posX", xCoord + dir.offsetX * 12);
							data.setDouble("posY", yCoord + 4);
							data.setDouble("posZ", zCoord + dir.offsetZ * 12);
							data.setString("type", "missileContrailMUD");
							data.setFloat("scale", 3);
							data.setDouble("moX", dir.offsetX * 10);
							data.setDouble("moY", 0);
							data.setDouble("moZ", dir.offsetZ * 10);
							data.setInteger("maxAge", 40 + worldObj.rand.nextInt(40));
							MainRegistry.proxy.effectNT(data);
							return;
						}
					}

					{
						List<FluidType> types = new ArrayList() {{ add(tanks[0].getTankType()); }};

						if(types.contains(Fluids.GASEOUS_SCHRABIDIUM_BROMIDE)) {

							ForgeDirection dir = ForgeDirection.getOrientation(this.getBlockMetadata() - BlockDummyable.offset).getRotation(ForgeDirection.UP);

							NBTTagCompound data = new NBTTagCompound();
							data.setDouble("posX", xCoord + dir.offsetX * 12);
							data.setDouble("posY", yCoord + 4);
							data.setDouble("posZ", zCoord + dir.offsetZ * 12);
							data.setString("type", "missileContrailSCH");
							data.setFloat("scale", 3);
							data.setDouble("moX", dir.offsetX * 10);
							data.setDouble("moY", 0);
							data.setDouble("moZ", dir.offsetZ * 10);
							data.setInteger("maxAge", 40 + worldObj.rand.nextInt(40));
							MainRegistry.proxy.effectNT(data);
							return;
						}
					}

					{
						List<FluidType> types = new ArrayList() {{ add(tanks[0].getTankType()); }};

						if(types.contains(Fluids.GASEOUS_URANIUM_BROMIDE) || types.contains(Fluids.GASEOUS_PLUTONIUM_BROMIDE)) {

							ForgeDirection dir = ForgeDirection.getOrientation(this.getBlockMetadata() - BlockDummyable.offset).getRotation(ForgeDirection.UP);

							NBTTagCompound data = new NBTTagCompound();
							data.setDouble("posX", xCoord + dir.offsetX * 12);
							data.setDouble("posY", yCoord + 4);
							data.setDouble("posZ", zCoord + dir.offsetZ * 12);
							data.setString("type", "missileContrailUP");
							data.setFloat("scale", 3);
							data.setDouble("moX", dir.offsetX * 10);
							data.setDouble("moY", 0);
							data.setDouble("moZ", dir.offsetZ * 10);
							data.setInteger("maxAge", 40 + worldObj.rand.nextInt(40));
							MainRegistry.proxy.effectNT(data);
							return;
						}
					}
				}
			} else {
				speed -= 0.05D;
				if(speed < 0) speed = 0;

				if(audio != null) {
					audio.stopSound();
					audio = null;
				}
			}

		}

		lastTime = time;
		time += speed;
	}

	private DirPos[] getConPos() {
		ForgeDirection dir = ForgeDirection.getOrientation(this.getBlockMetadata() - BlockDummyable.offset);
		ForgeDirection rot = dir.getRotation(ForgeDirection.UP);

		return new DirPos[] {
			new DirPos(xCoord + 6, yCoord + 3, zCoord + 0, rot),
			new DirPos(xCoord + 6, yCoord + 3, zCoord + 0, rot.getOpposite())
		};
	}

	@Override
	public AudioWrapper createAudioLoop() {
		return MainRegistry.proxy.getLoopedSound("hbm:misc.htrloop", xCoord, yCoord, zCoord, 0.25F, 27.5F, 1.0F, 20);
	}

	@Override
	public void invalidate() {
		super.invalidate();

		if(hasRegistered) {
			unregisterPropulsion();
			hasRegistered = false;
		}

		if(audio != null) {
			audio.stopSound();
			audio = null;
		}
	}

	@Override
	public void onChunkUnload() {
		super.onChunkUnload();

		if(hasRegistered) {
			unregisterPropulsion();
			hasRegistered = false;
		}

		if(audio != null) {
			audio.stopSound();
			audio = null;
		}
	}

	@Override
	public void serialize(ByteBuf buf) {
		super.serialize(buf);
		buf.writeBoolean(isOn);
		buf.writeFloat(soundtime);
		buf.writeInt(fuelCost);
		for(int i = 0; i < tanks.length; i++) tanks[i].serialize(buf);
	}

	@Override
	public void deserialize(ByteBuf buf) {
		super.deserialize(buf);
		isOn = buf.readBoolean();
		soundtime = buf.readFloat();
		fuelCost = buf.readInt();
		for(int i = 0; i < tanks.length; i++) tanks[i].deserialize(buf);
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		nbt.setBoolean("on", isOn);
		for(int i = 0; i < tanks.length; i++) tanks[i].writeToNBT(nbt, "t" + i);
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		isOn = nbt.getBoolean("on");
		for(int i = 0; i < tanks.length; i++) tanks[i].readFromNBT(nbt, "t" + i);
	}

	public boolean isFacingPrograde() {
		return ForgeDirection.getOrientation(this.getBlockMetadata() - BlockDummyable.offset) == ForgeDirection.SOUTH;
	}

	AxisAlignedBB bb = null;

	@Override
	public AxisAlignedBB getRenderBoundingBox() {
		if(bb == null) bb = AxisAlignedBB.getBoundingBox(xCoord - 10, yCoord, zCoord - 10, xCoord + 11, yCoord + 7, zCoord + 11);
		return bb;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public double getMaxRenderDistanceSquared() {
		return 65536.0D;
	}

	@Override
	public TileEntity getTileEntity() {
		return this;
	}

	@Override
	public boolean canPerformBurn(int shipMass, double deltaV) {
		FT_Rocket trait = tanks[0].getTankType().getTrait(FT_Rocket.class);
		int isp = trait != null ? trait.getISP() : 300;

		fuelCost = SolarSystem.getFuelCost(deltaV, shipMass, isp);

		for(FluidTank tank : tanks) {
			if(tank.getFill() < fuelCost) return false;
		}

		return true;
	}

	@Override
	public void addErrors(List<String> errors) {
		for(FluidTank tank : tanks) {
			if(tank.getFill() < fuelCost) {
				errors.add(EnumChatFormatting.RED + I18nUtil.resolveKey(getBlockType().getUnlocalizedName() + ".name") + " - Insufficient fuel: needs " + fuelCost + "mB");
			}
		}
	}

	@Override
	public float getThrust() {
		return 800_000_000.0F;
	}

	@Override
	public int startBurn() {
		isOn = true;
		for(FluidTank tank : tanks) {
			tank.setFill(tank.getFill() - fuelCost);
		}
		return 20;
	}

	@Override
	public int endBurn() {
		isOn = false;
		return 20; // Cooldown
	}

	@Override
	public String getName() {
		return "container.htr";
	}

	@Override
	public FluidTank[] getAllTanks() {
		return tanks;
	}

	@Override
	public FluidTank[] getReceivingTanks() {
		return tanks;
	}
}
