package de.z0rdak.yawp.core.area;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.apache.commons.lang3.NotImplementedException;

import java.util.ArrayList;
import java.util.List;

public class PrismArea extends AbstractArea {

    public List<BlockPos> blockNodes;

    public PrismArea(CompoundTag nbt) {
        super(nbt);
        this.deserializeNBT(nbt);
    }

    public PrismArea() {
        super(AreaType.PRISM);
        this.blockNodes = new ArrayList<>();
    }

    public PrismArea(List<BlockPos> blockNodes){
        this();
        this.blockNodes = blockNodes;
    }

    // TODO: implementation
    @Override
    public boolean contains(BlockPos pos) {
        throw new NotImplementedException("Missing contains implementation in PrismArea");
    }

    // TODO: implementation
    @Override
    public CompoundTag serializeNBT() {
        CompoundTag nbt = super.serializeNBT();
        throw new NotImplementedException("Missing serializeNBT implementation in PrismArea");
    }

    // TODO: implementation
    @Override
    public void deserializeNBT(CompoundTag nbt) {
        super.deserializeNBT(nbt);
        throw new NotImplementedException("Missing deserializeNBT implementation in PrismArea");
    }
}
