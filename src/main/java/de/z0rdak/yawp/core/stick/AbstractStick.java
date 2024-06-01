package de.z0rdak.yawp.core.stick;

import de.z0rdak.yawp.util.StickType;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.INBTSerializable;

import static de.z0rdak.yawp.util.StickUtil.STICK_TYPE;

public abstract class AbstractStick implements INBTSerializable<CompoundTag> {

    private StickType stickType;

    public AbstractStick(StickType stickType) {
        this.stickType = stickType;
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag nbt = new CompoundTag();
        nbt.putString(STICK_TYPE, this.stickType.stickName);
        return nbt;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag nbt) {
        this.stickType = StickType.of(nbt.getString(STICK_TYPE));
    }

    public StickType getStickType() {
        return stickType;
    }

    public abstract void cycleMode();
}
