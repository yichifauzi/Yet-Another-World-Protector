package de.z0rdak.yawp.core.area;

import de.z0rdak.yawp.util.AreaUtil;
import de.z0rdak.yawp.util.constants.AreaNBT;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Represents and wraps a simple AxisAlignedBB.
 * This area is marked by two positions and thus spans a cuboid shape
 */
public class CuboidArea extends AbstractArea {

    private AABB area;

    public CuboidArea(AABB area) {
        super(AreaType.CUBOID);
        this.area = area;
    }

    public CuboidArea(BlockPos p1, BlockPos p2) {
        this(new AABB(p1, p2));
    }

    public CuboidArea(List<BlockPos> blocks){
        this(blocks.get(0), blocks.get(1));
    }

    private CuboidArea() {
        super(AreaType.CUBOID);
    }

    public CuboidArea(CompoundTag nbt) {
        this();
        this.deserializeNBT(nbt);
    }

    @Override
    public boolean contains(BlockPos pos) {
        // INFO: this.area.contains(x,y,z); does not work, because the max checks are exclusive by default.
        // TODO: Maybe replace with net.minecraft.util.math.MutableBoundingBox::intersectsWith which has inclusive checks
        return pos.getX() >= area.minX && pos.getX() <= area.maxX
                && pos.getY() >= this.area.minY && pos.getY() <= this.area.maxY
                && pos.getZ() >= this.area.minZ && pos.getZ() <= this.area.maxZ;
    }

    public AABB getArea() {
        return area;
    }

    public BlockPos getAreaP1(){
        return new BlockPos(this.area.minX, this.area.minY, this.area.minZ);
    }

    public BlockPos getAreaP2(){
        return new BlockPos(this.area.maxX, this.area.maxY, this.area.maxZ);
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag nbt = new CompoundTag();
        nbt.put(AreaNBT.P1, NbtUtils.writeBlockPos(this.getAreaP1()));
        nbt.put(AreaNBT.P2, NbtUtils.writeBlockPos(this.getAreaP2()));
        return nbt;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        BlockPos p1 = NbtUtils.readBlockPos(nbt.getCompound(AreaNBT.P1));
        BlockPos p2 = NbtUtils.readBlockPos(nbt.getCompound(AreaNBT.P2));
        this.area = new AABB(p1, p2);
    }

    @Override
    public String toString() {
        return "Cuboid " + AreaUtil.blockPosStr(this.getAreaP1()) + " -> " + AreaUtil.blockPosStr(this.getAreaP2());
    }

}
